#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import ssl
import http.client
from pathlib import Path
from typing import Any, Mapping

SOURCE_REPOSITORY = "wsg138/EnthusiaStaff"
STAGING_REPOSITORY = "wsg138/EnthusiaStaff-Staging"
PUBLIC_WORKFLOW = "pi-staging-check.yml"
PRIVATE_WORKFLOW_PATH = ".github/workflows/plugin-live-test.yml"
API_HOST = "api.github.com"
USER_AGENT = "EnthusiaStaff-Pi-Staging-Supersede/1.0"
PUBLISHER_USER_ID = 41898282
ACTIVE_STATES = frozenset({"queued", "in_progress", "waiting", "requested", "pending"})
SAFE_PRE_PAPER_STATES = frozenset({"queued", "waiting", "requested", "pending"})
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
PUBLIC_TITLE_RE = re.compile(r"^Pi Staging PR #(\d+) / ([0-9a-f]{40}) / ([A-Za-z0-9._:-]{1,80})$")
PRIVATE_TITLE_RE = re.compile(r"^EnthusiaStaff bridge ([1-9][0-9]*-[1-9][0-9]*) / ([0-9a-f]{40})$")
MARKER_RE = re.compile(r"<!-- enthusia-pi-staging pr=(\d+) sha=([0-9a-f]{40}) -->")
PRIVATE_ID_RE = re.compile(r"^- Private run ID: `([1-9][0-9]*)`$", re.MULTILINE)
PAPER_STEP = "Run guarded disposable Paper boot and restart test"


class SupersedeError(RuntimeError):
    pass


class RepoApi:
    def __init__(self, repository: str, token: str) -> None:
        if repository not in {SOURCE_REPOSITORY, STAGING_REPOSITORY}:
            raise SupersedeError(f"unexpected repository: {repository!r}")
        if not token:
            raise SupersedeError(f"token unavailable for {repository}")
        self.repository = repository
        self.base_path = f"/repos/{repository}"
        self.token = token

    def _request(self, method: str, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        if method not in {"GET", "POST"}:
            raise SupersedeError(f"unsupported method: {method}")
        if not path.startswith("/") or path.startswith("//") or "\r" in path or "\n" in path:
            raise SupersedeError("invalid repository-relative GitHub API path")
        data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Authorization": f"Bearer {self.token}",
            "User-Agent": USER_AGENT,
        }
        if data is not None:
            headers["Content-Type"] = "application/json"
        connection = http.client.HTTPSConnection(API_HOST, timeout=30, context=ssl.create_default_context())
        try:
            connection.request(method, self.base_path + path, body=data, headers=headers)
            response = connection.getresponse()
            raw = response.read()
        except (OSError, http.client.HTTPException) as exc:
            raise SupersedeError(f"GitHub API {method} {path} failed: {exc}") from exc
        finally:
            connection.close()
        if not 200 <= response.status < 300:
            raise SupersedeError(f"GitHub API {method} {path} failed: HTTP {response.status}: {raw.decode('utf-8', 'replace')[:500]}")
        return None if not raw else json.loads(raw)

    def get(self, path: str) -> Any:
        return self._request("GET", path)

    def post(self, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        return self._request("POST", path, payload)


def positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise SupersedeError(f"{label} must be a positive integer")
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise SupersedeError(f"{label} must be a positive integer") from exc
    if parsed <= 0:
        raise SupersedeError(f"{label} must be a positive integer")
    return parsed


def exact_sha(value: Any, label: str = "SHA") -> str:
    if not isinstance(value, str) or not SHA_RE.fullmatch(value):
        raise SupersedeError(f"{label} must be an exact lowercase SHA")
    return value


def parse_public_title(title: Any) -> tuple[int, str] | None:
    if not isinstance(title, str):
        return None
    match = PUBLIC_TITLE_RE.fullmatch(title)
    return None if match is None else (int(match.group(1)), match.group(2))


def iter_workflow_runs(api: RepoApi):
    for page in range(1, 6):
        payload = api.get(f"/actions/workflows/{PUBLIC_WORKFLOW}/runs?per_page=100&page={page}")
        runs = payload.get("workflow_runs") if isinstance(payload, Mapping) else None
        if not isinstance(runs, list):
            raise SupersedeError("public workflow-runs response is invalid")
        for run in runs:
            if isinstance(run, Mapping):
                yield run
        if len(runs) < 100:
            return
    raise SupersedeError("refusing to inspect more than 500 public staging runs")


def public_binding(run: Mapping[str, Any]) -> tuple[int, str] | None:
    parsed = parse_public_title(run.get("display_title"))
    if parsed is not None:
        return parsed
    # Legacy automatic pull_request_target runs may expose the PR title instead
    # of the configured run-name. Bind those only through GitHub's immutable PR
    # metadata so they can be drained after the scheduling migration.
    if run.get("event") != "pull_request_target":
        return None
    pull_requests = run.get("pull_requests")
    if not isinstance(pull_requests, list) or len(pull_requests) != 1:
        return None
    pr = pull_requests[0]
    head = pr.get("head") if isinstance(pr, Mapping) else None
    if not isinstance(head, Mapping):
        return None
    try:
        number = positive_int(pr.get("number"), "legacy public PR number")
        sha = exact_sha(head.get("sha"), "legacy public PR SHA")
    except SupersedeError:
        return None
    return number, sha


def stale_public_correlations(api: RepoApi, pr_number: int, keep_sha: str | None) -> list[tuple[str, str]]:
    correlations: list[tuple[str, str]] = []
    for run in iter_workflow_runs(api):
        parsed = public_binding(run)
        if parsed is None or parsed[0] != pr_number or (keep_sha is not None and parsed[1] == keep_sha):
            continue
        run_id = positive_int(run.get("id"), "public run ID")
        attempt = positive_int(run.get("run_attempt", 1), "public run attempt")
        correlations.append((parsed[1], f"{run_id}-{attempt}"))
    return correlations


def public_state(api: RepoApi, pr_number: int, current_sha: str) -> tuple[bool, list[int]]:
    exact_sha(current_sha, "current PR SHA")
    exact_active = False
    cancelled: list[int] = []
    for run in iter_workflow_runs(api):
        parsed = public_binding(run)
        if parsed is None or parsed[0] != pr_number or run.get("status") not in ACTIVE_STATES:
            continue
        run_id = positive_int(run.get("id"), "public run ID")
        if parsed[1] == current_sha:
            exact_active = True
            continue
        api.post(f"/actions/runs/{run_id}/cancel")
        cancelled.append(run_id)
    return exact_active, cancelled


def private_records(source_api: RepoApi, pr_number: int, keep_sha: str | None) -> list[tuple[str, int]]:
    records: list[tuple[str, int]] = []
    for page in range(1, 11):
        payload = source_api.get(f"/issues/{pr_number}/comments?per_page=100&page={page}")
        if not isinstance(payload, list):
            raise SupersedeError("PR comments response is invalid")
        for item in payload:
            if not isinstance(item, Mapping):
                continue
            user = item.get("user")
            body = item.get("body")
            if not isinstance(user, Mapping) or user.get("id") != PUBLISHER_USER_ID or not isinstance(body, str):
                continue
            marker = MARKER_RE.search(body)
            private_id = PRIVATE_ID_RE.search(body)
            if marker is None or private_id is None or int(marker.group(1)) != pr_number:
                continue
            sha = marker.group(2)
            if keep_sha is not None and sha == keep_sha:
                continue
            records.append((sha, int(private_id.group(1))))
        if len(payload) < 100:
            break
    return records


def private_cancel_is_safe(staging_api: RepoApi, run: Mapping[str, Any], expected_sha: str) -> bool:
    if run.get("status") not in ACTIVE_STATES:
        return False
    if run.get("path") != PRIVATE_WORKFLOW_PATH or run.get("event") != "workflow_dispatch":
        raise SupersedeError("refusing to cancel an unexpected private workflow")
    title = run.get("display_title")
    match = PRIVATE_TITLE_RE.fullmatch(title) if isinstance(title, str) else None
    if match is None or match.group(2) != expected_sha:
        raise SupersedeError("private workflow title is not bound to the stale source SHA")
    if run.get("status") in SAFE_PRE_PAPER_STATES:
        return True
    run_id = positive_int(run.get("id"), "private run ID")
    payload = staging_api.get(f"/actions/runs/{run_id}/jobs?per_page=100")
    jobs = payload.get("jobs") if isinstance(payload, Mapping) else None
    if not isinstance(jobs, list):
        raise SupersedeError("private jobs response is invalid")
    if not jobs:
        return True
    for job in jobs:
        if not isinstance(job, Mapping):
            raise SupersedeError("private job entry is invalid")
        for step in job.get("steps") or []:
            if isinstance(step, Mapping) and step.get("name") == PAPER_STEP:
                if step.get("status") in {"in_progress", "completed"}:
                    return False
    return True


def iter_private_workflow_runs(api: RepoApi):
    for page in range(1, 6):
        payload = api.get(f"/actions/workflows/plugin-live-test.yml/runs?event=workflow_dispatch&per_page=100&page={page}")
        runs = payload.get("workflow_runs") if isinstance(payload, Mapping) else None
        if not isinstance(runs, list):
            raise SupersedeError("private workflow-runs response is invalid")
        for run in runs:
            if isinstance(run, Mapping):
                yield run
        if len(runs) < 100:
            return
    raise SupersedeError("refusing to inspect more than 500 private staging runs")


def private_runs_from_correlations(staging_api: RepoApi, correlations: list[tuple[str, str]]) -> list[tuple[str, int]]:
    wanted = set(correlations)
    if not wanted:
        return []
    found: list[tuple[str, int]] = []
    for run in iter_private_workflow_runs(staging_api):
        title = run.get("display_title")
        match = PRIVATE_TITLE_RE.fullmatch(title) if isinstance(title, str) else None
        if match is None or (match.group(2), match.group(1)) not in wanted:
            continue
        found.append((match.group(2), positive_int(run.get("id"), "private run ID")))
    return found


def cancel_stale_private(
    source_api: RepoApi,
    staging_api: RepoApi,
    pr_number: int,
    keep_sha: str | None,
    correlations: list[tuple[str, str]] | None = None,
) -> tuple[list[int], list[int]]:
    cancelled: list[int] = []
    preserved_unsafe: list[int] = []
    seen: set[int] = set()
    records = private_records(source_api, pr_number, keep_sha)
    records.extend(private_runs_from_correlations(staging_api, correlations or []))
    for sha, run_id in records:
        if run_id in seen:
            continue
        seen.add(run_id)
        run = staging_api.get(f"/actions/runs/{run_id}")
        if not isinstance(run, Mapping) or run.get("status") not in ACTIVE_STATES:
            continue
        if private_cancel_is_safe(staging_api, run, sha):
            staging_api.post(f"/actions/runs/{run_id}/cancel")
            cancelled.append(run_id)
        else:
            preserved_unsafe.append(run_id)
    return cancelled, preserved_unsafe


def load_event() -> Mapping[str, Any]:
    event_path = os.environ.get("GITHUB_EVENT_PATH", "")
    if not event_path:
        raise SupersedeError("GITHUB_EVENT_PATH is required")
    payload = json.loads(Path(event_path).read_text(encoding="utf-8"))
    if not isinstance(payload, Mapping):
        raise SupersedeError("GitHub event payload is invalid")
    return payload


def live_binding(source_api: RepoApi, pr_number: int) -> tuple[str, str]:
    pr = source_api.get(f"/pulls/{pr_number}")
    if not isinstance(pr, Mapping) or pr.get("state") != "open" or pr.get("draft") is not False:
        raise SupersedeError("PR is not an open non-draft staging candidate")
    head = pr.get("head")
    repo = head.get("repo") if isinstance(head, Mapping) else None
    if not isinstance(repo, Mapping) or repo.get("full_name") != SOURCE_REPOSITORY:
        raise SupersedeError("fork PRs are not eligible for Pi staging")
    sha = exact_sha(head.get("sha") if isinstance(head, Mapping) else None, "current PR SHA")
    ref = head.get("ref") if isinstance(head, Mapping) else None
    if not isinstance(ref, str) or not ref or len(ref) > 255:
        raise SupersedeError("current PR head ref is invalid")
    return sha, ref


def emit(name: str, value: str) -> None:
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write(f"{name}={value}\n")
    print(f"{name}={value}")


def mode_request(source_api: RepoApi) -> int:
    payload = load_event()
    comment = payload.get("comment")
    issue = payload.get("issue")
    repository = payload.get("repository")
    if not isinstance(comment, Mapping) or not isinstance(issue, Mapping) or not isinstance(repository, Mapping):
        emit("eligible", "false")
        emit("current_active", "false")
        return 0
    body = comment.get("body")
    if not isinstance(body, str) or body.strip() != "@enthusia-staging test":
        emit("eligible", "false")
        emit("current_active", "false")
        return 0
    if repository.get("full_name") != SOURCE_REPOSITORY or not issue.get("pull_request"):
        raise SupersedeError("staging request is not a source-repository pull request")
    if comment.get("author_association") not in {"OWNER", "MEMBER", "COLLABORATOR"}:
        raise SupersedeError("staging requester is not authorized")
    pr_number = positive_int(issue.get("number"), "PR number")
    current_sha, _ = live_binding(source_api, pr_number)
    exact_active, cancelled = public_state(source_api, pr_number, current_sha)
    emit("eligible", "true")
    emit("current_active", "true" if exact_active else "false")
    emit("cancelled_public", ",".join(str(item) for item in cancelled) or "none")
    return 0


def mode_candidate(source_api: RepoApi, staging_api: RepoApi, pr_number: int, requested_sha: str) -> int:
    current_sha, _ = live_binding(source_api, pr_number)
    if current_sha != requested_sha:
        raise SupersedeError(f"PR head moved before private staging dispatch: requested {requested_sha}, current {current_sha}")
    correlations = stale_public_correlations(source_api, pr_number, current_sha)
    _, cancelled_public = public_state(source_api, pr_number, current_sha)
    cancelled_private, unsafe = cancel_stale_private(source_api, staging_api, pr_number, current_sha, correlations)
    emit("cancelled_public", ",".join(map(str, cancelled_public)) or "none")
    emit("cancelled_private", ",".join(map(str, cancelled_private)) or "none")
    emit("preserved_unsafe_private", ",".join(map(str, unsafe)) or "none")
    return 0


def mode_advance(source_api: RepoApi, staging_api: RepoApi) -> int:
    payload = load_event()
    pr_event = payload.get("pull_request")
    if not isinstance(pr_event, Mapping):
        raise SupersedeError("pull_request_target payload is missing pull_request")
    pr_number = positive_int(pr_event.get("number"), "PR number")
    action = payload.get("action")
    if action == "closed":
        keep_sha = None
    elif action == "synchronize":
        keep_sha, _ = live_binding(source_api, pr_number)
    else:
        raise SupersedeError(f"unsupported supersession action: {action!r}")
    correlations = stale_public_correlations(source_api, pr_number, keep_sha)
    current_for_public = keep_sha or ("0" * 40)
    _, cancelled_public = public_state(source_api, pr_number, current_for_public)
    cancelled_private, unsafe = cancel_stale_private(source_api, staging_api, pr_number, keep_sha, correlations)
    emit("cancelled_public", ",".join(map(str, cancelled_public)) or "none")
    emit("cancelled_private", ",".join(map(str, cancelled_private)) or "none")
    emit("preserved_unsafe_private", ",".join(map(str, unsafe)) or "none")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="mode", required=True)
    sub.add_parser("request")
    sub.add_parser("advance")
    candidate = sub.add_parser("candidate")
    candidate.add_argument("--pr-number", type=int, required=True)
    candidate.add_argument("--sha", required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    source_api = RepoApi(SOURCE_REPOSITORY, os.environ.get("GITHUB_TOKEN", ""))
    if args.mode == "request":
        return mode_request(source_api)
    staging_api = RepoApi(STAGING_REPOSITORY, os.environ.get("STAGING_TOKEN", ""))
    if args.mode == "candidate":
        return mode_candidate(source_api, staging_api, positive_int(args.pr_number, "PR number"), exact_sha(args.sha, "requested SHA"))
    return mode_advance(source_api, staging_api)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (SupersedeError, OSError, json.JSONDecodeError) as exc:
        print(f"::error::{exc}", file=os.sys.stderr)
        raise SystemExit(1) from exc
