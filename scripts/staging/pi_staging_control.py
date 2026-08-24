#!/usr/bin/env python3
"""Trusted control-plane helpers for canonical EnthusiaStaff Pi staging."""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Protocol

SOURCE_REPOSITORY = "wsg138/EnthusiaStaff"
PUBLIC_WORKFLOW = "pi-staging-check.yml"
EXACT_COMMAND = "@enthusia-staging test"
STATUS_CONTEXT = "Pi Staging"
AUTHORIZED_ASSOCIATIONS = frozenset({"OWNER", "MEMBER", "COLLABORATOR"})
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
REF_RE = re.compile(r"^[A-Za-z0-9._/-]{1,255}$")
CORRELATION_RE = re.compile(r"^[A-Za-z0-9._:-]{1,80}$")
REQUESTER_RE = re.compile(r"^[A-Za-z0-9_.:@\[\]-]{1,100}$")
RUN_URL_RE = re.compile(r"/actions/runs/([1-9][0-9]*)(?:$|[/?#])")


class ControlError(RuntimeError):
    pass


class Api(Protocol):
    def get(self, path: str) -> Any: ...
    def post(self, path: str, payload: Mapping[str, Any]) -> Any: ...
    def patch(self, path: str, payload: Mapping[str, Any]) -> Any: ...


@dataclass(frozen=True)
class PrBinding:
    source_repository: str
    pr_number: int
    head_repository: str
    head_ref: str
    head_sha: str


@dataclass(frozen=True)
class Record:
    pr_number: int
    head_sha: str
    requester: str
    public_run_id: int
    public_attempt: int
    public_run_url: str
    state: str
    private_run_id: int | None = None
    private_run_url: str | None = None
    conclusion: str | None = None
    cleanup: str | None = None
    evidence_identity: str | None = None


class GitHubApi:
    def __init__(self, api_url: str, repository: str, token: str) -> None:
        if not token:
            raise ControlError("GITHUB_TOKEN is required")
        self.base = f"{api_url.rstrip('/')}/repos/{repository}"
        self.token = token

    def request(self, method: str, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        url = path if path.startswith("https://") else self.base + path
        data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Accept", "application/vnd.github+json")
        req.add_header("X-GitHub-Api-Version", "2022-11-28")
        req.add_header("Authorization", f"Bearer {self.token}")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                raw = response.read()
                return None if not raw else json.loads(raw)
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", "replace")
            raise ControlError(f"GitHub API {method} {path} failed: HTTP {exc.code}: {body[:500]}") from exc
        except urllib.error.URLError as exc:
            raise ControlError(f"GitHub API {method} {path} failed: {exc}") from exc

    def get(self, path: str) -> Any:
        return self.request("GET", path)

    def post(self, path: str, payload: Mapping[str, Any]) -> Any:
        return self.request("POST", path, payload)

    def patch(self, path: str, payload: Mapping[str, Any]) -> Any:
        return self.request("PATCH", path, payload)


def normalize_command(body: str) -> str:
    return body.strip()


def is_exact_command(body: str) -> bool:
    return normalize_command(body) == EXACT_COMMAND


def validate_correlation(value: str) -> str:
    if not CORRELATION_RE.fullmatch(value):
        raise ControlError("request correlation must be 1-80 characters from [A-Za-z0-9._:-]")
    return value


def validate_requester(value: str) -> str:
    if not REQUESTER_RE.fullmatch(value):
        raise ControlError("requester identity is invalid")
    return value


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise ControlError(f"{label} must be a positive integer")
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise ControlError(f"{label} must be a positive integer") from exc
    if parsed <= 0:
        raise ControlError(f"{label} must be a positive integer")
    return parsed


def command_event(payload: Mapping[str, Any], expected_repository: str = SOURCE_REPOSITORY) -> tuple[int, str, str] | None:
    comment = payload.get("comment")
    issue = payload.get("issue")
    repository = payload.get("repository")
    if not isinstance(comment, Mapping) or not isinstance(issue, Mapping) or not isinstance(repository, Mapping):
        raise ControlError("malformed issue_comment event")
    body = comment.get("body")
    if not isinstance(body, str):
        raise ControlError("comment body is missing")
    if not is_exact_command(body):
        return None
    repo_name = repository.get("full_name")
    if repo_name != expected_repository:
        raise ControlError(f"command received for unexpected repository: {repo_name!r}")
    if not issue.get("pull_request"):
        raise ControlError("Pi staging command is only valid on pull requests")
    association = comment.get("author_association")
    if association not in AUTHORIZED_ASSOCIATIONS:
        raise ControlError(f"comment author association is not authorized: {association!r}")
    user = comment.get("user")
    if not isinstance(user, Mapping):
        raise ControlError("comment user is missing")
    requester = validate_requester(str(user.get("login", "")))
    pr_number = _positive_int(issue.get("number"), "PR number")
    comment_id = _positive_int(comment.get("id"), "comment ID")
    return pr_number, requester, validate_correlation(f"comment-{comment_id}")


def binding_from_pr(pr: Mapping[str, Any], expected_repository: str = SOURCE_REPOSITORY) -> PrBinding:
    state = pr.get("state")
    if state != "open":
        raise ControlError("PR is not open")
    if pr.get("draft") is not False:
        raise ControlError("PR is draft or draft state is unavailable")
    number = _positive_int(pr.get("number"), "PR number")
    head = pr.get("head")
    if not isinstance(head, Mapping):
        raise ControlError("PR head metadata is missing")
    repo = head.get("repo")
    if not isinstance(repo, Mapping) or repo.get("full_name") != expected_repository:
        raise ControlError("fork or invalid PR head repository is not eligible for Pi staging")
    head_ref = head.get("ref")
    if not isinstance(head_ref, str) or not REF_RE.fullmatch(head_ref) or ".." in head_ref or head_ref.startswith("/") or head_ref.endswith("/"):
        raise ControlError("PR head ref is invalid")
    head_sha = head.get("sha")
    if not isinstance(head_sha, str) or not SHA_RE.fullmatch(head_sha):
        raise ControlError("PR head SHA is not an exact 40-character lowercase SHA")
    return PrBinding(expected_repository, number, expected_repository, head_ref, head_sha)


def require_same_binding(live_pr: Mapping[str, Any], expected: PrBinding) -> PrBinding:
    actual = binding_from_pr(live_pr, expected.source_repository)
    if actual != expected:
        raise ControlError(
            "PR head moved or metadata changed before dispatch: "
            f"expected {expected.head_repository}:{expected.head_ref}@{expected.head_sha}, "
            f"got {actual.head_repository}:{actual.head_ref}@{actual.head_sha}"
        )
    return actual


def dispatch_payload(binding: PrBinding, correlation: str, requester: str) -> dict[str, Any]:
    validate_correlation(correlation)
    validate_requester(requester)
    return {
        "ref": "main",
        "inputs": {
            "source_sha": binding.head_sha,
            "source_pr_number": str(binding.pr_number),
            "source_pr_head_repository": binding.head_repository,
            "source_pr_head_ref": binding.head_ref,
            "source_pr_head_sha": binding.head_sha,
            "run_pi_test": True,
            "request_correlation": correlation,
            "request_requester": requester,
        },
    }


def expected_run_title(binding: PrBinding, correlation: str) -> str:
    validate_correlation(correlation)
    return f"Pi Staging PR #{binding.pr_number} / {binding.head_sha} / {correlation}"


def marker(pr_number: int, sha: str) -> str:
    if pr_number <= 0 or not SHA_RE.fullmatch(sha):
        raise ControlError("invalid marker identity")
    return f"<!-- enthusia-pi-staging pr={pr_number} sha={sha} -->"


def _bounded_description(value: str) -> str:
    return value[:140]


def status_payload(record: Record) -> dict[str, Any]:
    if record.state not in {"queued", "in_progress", "terminal"}:
        raise ControlError(f"invalid record state: {record.state}")
    if record.state == "terminal":
        canonical_success = record.conclusion == "success" and record.cleanup == "success"
        state = "success" if canonical_success else ("error" if record.conclusion == "error" else "failure")
        description = "Canonical Pi staging passed" if state == "success" else f"Canonical Pi staging {record.conclusion or 'failed'}"
    else:
        state = "pending"
        description = f"Canonical Pi staging {record.state} for PR #{record.pr_number}"
    return {
        "state": state,
        "target_url": record.public_run_url,
        "description": _bounded_description(description),
        "context": STATUS_CONTEXT,
    }


def render_record(record: Record) -> str:
    if not SHA_RE.fullmatch(record.head_sha):
        raise ControlError("record SHA is invalid")
    state = record.state
    lines = [
        marker(record.pr_number, record.head_sha),
        "### Canonical Pi Staging",
        "",
        f"- Source PR: `#{record.pr_number}`",
        f"- Exact source SHA: `{record.head_sha}`",
        f"- Requester: `{record.requester}`",
        f"- Public run ID: `{record.public_run_id}`",
        f"- Public attempt: `{record.public_attempt}`",
        f"- Public run: {record.public_run_url}",
        f"- State: `{state}`",
        f"- Private run ID: `{record.private_run_id}`" if record.private_run_id else "- Private run ID: `pending`",
        f"- Private run: {record.private_run_url}" if record.private_run_url else "- Private run: pending",
        f"- Canonical conclusion: `{record.conclusion}`" if record.conclusion else "- Canonical conclusion: `pending`",
        f"- Transient transfer cleanup: `{record.cleanup}`" if record.cleanup else "- Transient transfer cleanup: `pending`",
    ]
    if record.evidence_identity:
        lines.append(f"- Sanitized evidence identity: `{record.evidence_identity}`")
    lines.extend(["", "This record is updated in place for this exact PR/head identity."])
    return "\n".join(lines)


def _iter_issue_comments(api: Api, pr_number: int) -> Iterable[Mapping[str, Any]]:
    for page in range(1, 11):
        payload = api.get(f"/issues/{pr_number}/comments?per_page=100&page={page}")
        if not isinstance(payload, list):
            raise ControlError("issue comments response is not a list")
        for item in payload:
            if isinstance(item, Mapping):
                yield item
        if len(payload) < 100:
            return
    raise ControlError("refusing to scan more than 1000 PR comments for staging marker")


def upsert_comment(api: Api, record: Record) -> int:
    wanted = marker(record.pr_number, record.head_sha)
    body = render_record(record)
    matches: list[int] = []
    for item in _iter_issue_comments(api, record.pr_number):
        text = item.get("body")
        if isinstance(text, str) and wanted in text:
            matches.append(_positive_int(item.get("id"), "comment ID"))
    if len(matches) > 1:
        raise ControlError("multiple canonical Pi staging marker comments exist for the same PR/head")
    if matches:
        api.patch(f"/issues/comments/{matches[0]}", {"body": body})
        return matches[0]
    created = api.post(f"/issues/{record.pr_number}/comments", {"body": body})
    if not isinstance(created, Mapping):
        raise ControlError("created comment response is invalid")
    return _positive_int(created.get("id"), "comment ID")


def publish_record(api: Api, record: Record) -> int:
    api.post(f"/statuses/{record.head_sha}", status_payload(record))
    return upsert_comment(api, record)


def _run_id_from_url(url: Any) -> int | None:
    if not isinstance(url, str):
        return None
    match = RUN_URL_RE.search(url)
    return int(match.group(1)) if match else None


def find_pending_run(api: Api, sha: str) -> Mapping[str, Any] | None:
    if not SHA_RE.fullmatch(sha):
        raise ControlError("invalid SHA for pending-run lookup")
    statuses = api.get(f"/commits/{sha}/statuses?per_page=100")
    if not isinstance(statuses, list):
        raise ControlError("commit statuses response is not a list")
    for status in statuses:
        if not isinstance(status, Mapping):
            continue
        if status.get("context") != STATUS_CONTEXT or status.get("state") != "pending":
            continue
        run_id = _run_id_from_url(status.get("target_url"))
        if run_id is None:
            continue
        run = api.get(f"/actions/runs/{run_id}")
        if isinstance(run, Mapping) and run.get("status") in {"queued", "in_progress", "waiting", "requested", "pending"}:
            return run
    return None


def locate_correlated_run(api: Api, title: str, attempts: int = 60, delay_seconds: float = 2.0) -> Mapping[str, Any]:
    for attempt in range(attempts):
        runs = api.get(f"/actions/workflows/{PUBLIC_WORKFLOW}/runs?event=workflow_dispatch&per_page=100")
        if not isinstance(runs, Mapping) or not isinstance(runs.get("workflow_runs"), list):
            raise ControlError("workflow runs response is invalid")
        for run in runs["workflow_runs"]:
            if isinstance(run, Mapping) and run.get("display_title") == title:
                run_id = _positive_int(run.get("id"), "workflow run ID")
                html_url = run.get("html_url")
                if not isinstance(html_url, str) or not html_url.startswith("https://github.com/"):
                    raise ControlError("correlated workflow run URL is invalid")
                return run
        if attempt + 1 < attempts:
            time.sleep(delay_seconds)
    raise ControlError(f"unable to locate correlated public Pi Staging run: {title}")


def record_from_run(binding: PrBinding, requester: str, run: Mapping[str, Any], state: str = "queued") -> Record:
    run_id = _positive_int(run.get("id"), "workflow run ID")
    attempt = _positive_int(run.get("run_attempt", 1), "workflow run attempt")
    url = run.get("html_url")
    if not isinstance(url, str) or not url.startswith("https://github.com/"):
        raise ControlError("workflow run URL is invalid")
    return Record(binding.pr_number, binding.head_sha, requester, run_id, attempt, url, state)


def handle_command(api: Api, payload: Mapping[str, Any]) -> str:
    parsed = command_event(payload)
    if parsed is None:
        return "ignored"
    pr_number, requester, correlation = parsed
    first = api.get(f"/pulls/{pr_number}")
    if not isinstance(first, Mapping):
        raise ControlError("PR response is invalid")
    binding = binding_from_pr(first)
    if binding.pr_number != pr_number:
        raise ControlError("PR number mismatch")

    pending = find_pending_run(api, binding.head_sha)
    if pending is not None:
        publish_record(api, record_from_run(binding, requester, pending, "in_progress"))
        return "deduplicated"

    second = api.get(f"/pulls/{pr_number}")
    if not isinstance(second, Mapping):
        raise ControlError("PR revalidation response is invalid")
    require_same_binding(second, binding)
    api.post(f"/actions/workflows/{PUBLIC_WORKFLOW}/dispatches", dispatch_payload(binding, correlation, requester))
    run = locate_correlated_run(api, expected_run_title(binding, correlation))
    run_status = str(run.get("status", "queued"))
    if run_status != "completed":
        visible_state = "queued" if run_status in {"queued", "requested", "waiting", "pending"} else "in_progress"
        publish_record(api, record_from_run(binding, requester, run, visible_state))
    return "dispatched"


def parse_record_args(args: argparse.Namespace) -> Record:
    pr_number = _positive_int(args.pr_number, "PR number")
    if not SHA_RE.fullmatch(args.sha):
        raise ControlError("record SHA is invalid")
    requester = validate_requester(args.requester)
    public_run_id = _positive_int(args.public_run_id, "public run ID")
    public_attempt = _positive_int(args.public_attempt, "public attempt")
    private_id = None if not args.private_run_id else _positive_int(args.private_run_id, "private run ID")
    return Record(
        pr_number=pr_number,
        head_sha=args.sha,
        requester=requester,
        public_run_id=public_run_id,
        public_attempt=public_attempt,
        public_run_url=args.public_run_url,
        state=args.state,
        private_run_id=private_id,
        private_run_url=args.private_run_url or None,
        conclusion=args.conclusion or None,
        cleanup=args.cleanup or None,
        evidence_identity=args.evidence_identity or None,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="mode", required=True)
    sub.add_parser("command")
    publish = sub.add_parser("publish")
    publish.add_argument("--pr-number", required=True)
    publish.add_argument("--sha", required=True)
    publish.add_argument("--requester", required=True)
    publish.add_argument("--head-ref", default="")
    publish.add_argument("--require-current-binding", action="store_true")
    publish.add_argument("--public-run-id", required=True)
    publish.add_argument("--public-attempt", required=True)
    publish.add_argument("--public-run-url", required=True)
    publish.add_argument("--state", choices=("queued", "in_progress", "terminal"), required=True)
    publish.add_argument("--private-run-id", default="")
    publish.add_argument("--private-run-url", default="")
    publish.add_argument("--conclusion", default="")
    publish.add_argument("--cleanup", default="")
    publish.add_argument("--evidence-identity", default="")
    args = parser.parse_args(argv)

    repository = os.environ.get("GITHUB_REPOSITORY", "")
    if repository != SOURCE_REPOSITORY:
        raise ControlError(f"unexpected repository: {repository!r}")
    api = GitHubApi(os.environ.get("GITHUB_API_URL", "https://api.github.com"), repository, os.environ.get("GITHUB_TOKEN", ""))
    if args.mode == "command":
        event_path = os.environ.get("GITHUB_EVENT_PATH", "")
        if not event_path:
            raise ControlError("GITHUB_EVENT_PATH is required")
        payload = json.loads(Path(event_path).read_text(encoding="utf-8"))
        result = handle_command(api, payload)
        print(f"Pi staging command result: {result}")
        return 0
    record = parse_record_args(args)
    if args.require_current_binding:
        if not args.head_ref or not REF_RE.fullmatch(args.head_ref):
            raise ControlError("--head-ref is required for current-binding validation")
        expected = PrBinding(SOURCE_REPOSITORY, record.pr_number, SOURCE_REPOSITORY, args.head_ref, record.head_sha)
        live = api.get(f"/pulls/{record.pr_number}")
        if not isinstance(live, Mapping):
            raise ControlError("PR response is invalid during publication")
        require_same_binding(live, expected)
    comment_id = publish_record(api, record)
    print(f"Pi staging record comment ID: {comment_id}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ControlError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        raise SystemExit(1)
