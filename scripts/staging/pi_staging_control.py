#!/usr/bin/env python3
"""Trusted control-plane helpers for canonical EnthusiaStaff Pi staging."""
from __future__ import annotations

import argparse
import http.client
import json
import os
import re
import ssl
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Protocol

SOURCE_REPOSITORY = "wsg138/EnthusiaStaff"
STAGING_REPOSITORY = "wsg138/EnthusiaStaff-Staging"
API_HOST = "api.github.com"
PUBLIC_WORKFLOW = "pi-staging-check.yml"
EXACT_COMMAND = "@enthusia-staging test"
STATUS_CONTEXT = "Pi Staging"
USER_AGENT = "EnthusiaStaff-Pi-Staging-Control/1.0"
# GitHub's immutable system-account ID for comments created by GITHUB_TOKEN.
PUBLISHER_USER_ID = 41898282
STATUS_PAGE_LIMIT = 10
AUTHORIZED_ASSOCIATIONS = frozenset({"OWNER", "MEMBER", "COLLABORATOR"})
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
REF_RE = re.compile(r"^[A-Za-z0-9._/-]{1,255}$")
CORRELATION_RE = re.compile(r"^[A-Za-z0-9._:-]{1,80}$")
REQUESTER_RE = re.compile(r"^[A-Za-z0-9_.:@\[\]-]{1,100}$")
EVIDENCE_RE = re.compile(r"^[A-Za-z0-9._=;:+/-]{1,500}$")
ACTIVE_RUN_STATES = frozenset({"queued", "in_progress", "waiting", "requested", "pending"})


class ControlError(RuntimeError):
    """Reject unsafe or malformed staging control-plane state."""


class Api(Protocol):
    """Minimal GitHub API surface used by the control-plane logic."""

    def get(self, path: str) -> Any: ...
    def post(self, path: str, payload: Mapping[str, Any]) -> Any: ...
    def patch(self, path: str, payload: Mapping[str, Any]) -> Any: ...


@dataclass(frozen=True)
class PrBinding:
    """Immutable same-repository pull-request source identity."""

    source_repository: str
    pr_number: int
    head_repository: str
    head_ref: str
    head_sha: str


@dataclass(frozen=True)
class Record:
    """Durable public staging evidence for one exact pull-request head."""

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
    """Fixed-origin GitHub REST client that never forwards credentials elsewhere."""

    def __init__(self, repository: str, token: str) -> None:
        if repository != SOURCE_REPOSITORY:
            raise ControlError(f"unexpected repository for API client: {repository!r}")
        if not token:
            raise ControlError("GITHUB_TOKEN is required")
        self.base_path = f"/repos/{repository}"
        self.token = token

    def _request_path(self, path: str) -> str:
        if not path.startswith("/") or path.startswith("//") or "\r" in path or "\n" in path:
            raise ControlError("GitHub API path must be a repository-relative absolute path")
        return self.base_path + path

    def _perform_request(self, method: str, path: str, data: bytes | None, headers: Mapping[str, str]) -> tuple[int, bytes]:
        context = ssl.create_default_context()
        connection = http.client.HTTPSConnection(API_HOST, timeout=30, context=context)
        try:
            connection.request(method, self._request_path(path), body=data, headers=dict(headers))
            response = connection.getresponse()
            return response.status, response.read()
        except (OSError, http.client.HTTPException) as exc:
            raise ControlError(f"GitHub API {method} {path} failed: {exc}") from exc
        finally:
            connection.close()

    def request(self, method: str, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        if method not in {"GET", "POST", "PATCH"}:
            raise ControlError(f"unsupported GitHub API method: {method}")
        data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Authorization": f"Bearer {self.token}",
            "User-Agent": USER_AGENT,
        }
        if data is not None:
            headers["Content-Type"] = "application/json"
        status, raw = self._perform_request(method, path, data, headers)
        if not 200 <= status < 300:
            body = raw.decode("utf-8", "replace")
            raise ControlError(f"GitHub API {method} {path} failed: HTTP {status}: {body[:500]}")
        if not raw:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ControlError(f"GitHub API {method} {path} returned invalid JSON") from exc

    def get(self, path: str) -> Any:
        return self.request("GET", path)

    def post(self, path: str, payload: Mapping[str, Any]) -> Any:
        return self.request("POST", path, payload)

    def patch(self, path: str, payload: Mapping[str, Any]) -> Any:
        return self.request("PATCH", path, payload)


def normalize_command(body: str) -> str:
    """Normalize only surrounding whitespace; command contents stay exact."""
    return body.strip()


def is_exact_command(body: str) -> bool:
    """Return whether a comment is exactly the supported staging command."""
    return normalize_command(body) == EXACT_COMMAND


def validate_correlation(value: str) -> str:
    """Validate a bounded observability-only correlation identifier."""
    if not CORRELATION_RE.fullmatch(value):
        raise ControlError("request correlation must be 1-80 characters from [A-Za-z0-9._:-]")
    return value


def validate_requester(value: str) -> str:
    """Validate a requester display identity before durable publication."""
    if not REQUESTER_RE.fullmatch(value):
        raise ControlError("requester identity is invalid")
    return value


def validate_evidence_identity(value: str) -> str:
    """Validate sanitized evidence text before embedding it in a PR comment."""
    if not EVIDENCE_RE.fullmatch(value):
        raise ControlError("evidence identity is invalid")
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


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ControlError(f"{label} is missing or invalid")
    return value


def command_event(payload: Mapping[str, Any], expected_repository: str = SOURCE_REPOSITORY) -> tuple[int, str, str] | None:
    """Parse and authorize an exact issue_comment command event."""
    comment = _mapping(payload.get("comment"), "comment metadata")
    issue = _mapping(payload.get("issue"), "issue metadata")
    repository = _mapping(payload.get("repository"), "repository metadata")
    body = comment.get("body")
    if not isinstance(body, str):
        raise ControlError("comment body is missing")
    if not is_exact_command(body):
        return None
    if repository.get("full_name") != expected_repository:
        raise ControlError("command received for unexpected repository")
    if not issue.get("pull_request"):
        raise ControlError("Pi staging command is only valid on pull requests")
    if comment.get("author_association") not in AUTHORIZED_ASSOCIATIONS:
        raise ControlError("comment author association is not authorized")
    user = _mapping(comment.get("user"), "comment user")
    requester = validate_requester(str(user.get("login", "")))
    pr_number = _positive_int(issue.get("number"), "PR number")
    comment_id = _positive_int(comment.get("id"), "comment ID")
    correlation = validate_correlation(f"comment-{comment_id}")
    return pr_number, requester, correlation


def _validate_head_ref(value: Any) -> str:
    if not isinstance(value, str) or not REF_RE.fullmatch(value):
        raise ControlError("PR head ref is invalid")
    if ".." in value or value.startswith("/") or value.endswith("/"):
        raise ControlError("PR head ref is invalid")
    return value


def _validate_sha(value: Any, label: str = "PR head SHA") -> str:
    if not isinstance(value, str) or not SHA_RE.fullmatch(value):
        raise ControlError(f"{label} is not an exact 40-character lowercase SHA")
    return value


def binding_from_pr(pr: Mapping[str, Any], expected_repository: str = SOURCE_REPOSITORY) -> PrBinding:
    """Validate a live PR and return its immutable exact-head binding."""
    if pr.get("state") != "open":
        raise ControlError("PR is not open")
    if pr.get("draft") is not False:
        raise ControlError("PR is draft or draft state is unavailable")
    number = _positive_int(pr.get("number"), "PR number")
    head = _mapping(pr.get("head"), "PR head metadata")
    repo = _mapping(head.get("repo"), "PR head repository")
    if repo.get("full_name") != expected_repository:
        raise ControlError("fork or invalid PR head repository is not eligible for Pi staging")
    head_ref = _validate_head_ref(head.get("ref"))
    head_sha = _validate_sha(head.get("sha"))
    return PrBinding(expected_repository, number, expected_repository, head_ref, head_sha)


def require_same_binding(live_pr: Mapping[str, Any], expected: PrBinding) -> PrBinding:
    """Fail closed if any exact PR source identity changed since capture."""
    actual = binding_from_pr(live_pr, expected.source_repository)
    if actual != expected:
        raise ControlError(
            "PR head moved or metadata changed before dispatch: "
            f"expected {expected.head_repository}:{expected.head_ref}@{expected.head_sha}, "
            f"got {actual.head_repository}:{actual.head_ref}@{actual.head_sha}"
        )
    return actual


def dispatch_payload(binding: PrBinding, correlation: str, requester: str) -> dict[str, Any]:
    """Build the only allowed public Pi Staging workflow_dispatch payload."""
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
    """Return deterministic public workflow display title for correlation."""
    validate_correlation(correlation)
    return f"Pi Staging PR #{binding.pr_number} / {binding.head_sha} / {correlation}"


def marker(pr_number: int, sha: str) -> str:
    """Return the stable machine-readable PR/head comment marker."""
    if pr_number <= 0:
        raise ControlError("invalid marker PR number")
    _validate_sha(sha, "marker SHA")
    return f"<!-- enthusia-pi-staging pr={pr_number} sha={sha} -->"


def _bounded_description(value: str) -> str:
    return value[:140]


def _run_id_from_url(url: Any, repository: str = SOURCE_REPOSITORY) -> int | None:
    if not isinstance(url, str):
        return None
    prefix = f"https://github.com/{repository}/actions/runs/"
    if not url.startswith(prefix):
        return None
    suffix = url[len(prefix):]
    if not suffix.isdigit() or suffix.startswith("0"):
        return None
    return int(suffix)


def _require_run_url(url: Any, repository: str, expected_run_id: int | None = None) -> str:
    run_id = _run_id_from_url(url, repository)
    if run_id is None:
        raise ControlError(f"workflow run URL is invalid for {repository}")
    if expected_run_id is not None and run_id != expected_run_id:
        raise ControlError("workflow run URL does not match its run ID")
    return str(url)


def _validate_record(record: Record) -> None:
    if record.pr_number <= 0:
        raise ControlError("record PR number is invalid")
    _validate_sha(record.head_sha, "record SHA")
    validate_requester(record.requester)
    if record.public_run_id <= 0 or record.public_attempt <= 0:
        raise ControlError("public run identity is invalid")
    _require_run_url(record.public_run_url, SOURCE_REPOSITORY, record.public_run_id)
    if record.state not in {"queued", "in_progress", "terminal"}:
        raise ControlError(f"invalid record state: {record.state}")
    if (record.private_run_id is None) != (record.private_run_url is None):
        raise ControlError("private run ID and URL must be published together")
    if record.private_run_id is not None:
        _require_run_url(record.private_run_url, STAGING_REPOSITORY, record.private_run_id)
    if record.evidence_identity:
        validate_evidence_identity(record.evidence_identity)


def status_payload(record: Record) -> dict[str, Any]:
    """Map canonical staging state to an exact-head GitHub commit status."""
    _validate_record(record)
    if record.state != "terminal":
        state = "pending"
        description = f"Canonical Pi staging {record.state} for PR #{record.pr_number}"
    else:
        canonical_success = record.conclusion == "success" and record.cleanup == "success"
        state = "success" if canonical_success else ("error" if record.conclusion == "error" else "failure")
        description = "Canonical Pi staging passed" if state == "success" else f"Canonical Pi staging {record.conclusion or 'failed'}"
    return {
        "state": state,
        "target_url": record.public_run_url,
        "description": _bounded_description(description),
        "context": STATUS_CONTEXT,
    }


def render_record(record: Record) -> str:
    """Render the stable, sanitized human/machine-readable PR record."""
    _validate_record(record)
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
        f"- State: `{record.state}`",
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


def _is_publisher_marker(item: Mapping[str, Any], wanted: str) -> bool:
    """Accept a canonical marker only when it was authored by github-actions[bot]."""
    body = item.get("body")
    user = item.get("user")
    if not isinstance(body, str) or wanted not in body or not isinstance(user, Mapping):
        return False
    user_id = user.get("id")
    return isinstance(user_id, int) and not isinstance(user_id, bool) and user_id == PUBLISHER_USER_ID


def upsert_comment(api: Api, record: Record) -> int:
    """Create or update the single stable record for an exact PR/head."""
    wanted = marker(record.pr_number, record.head_sha)
    body = render_record(record)
    matches = [
        _positive_int(item.get("id"), "comment ID")
        for item in _iter_issue_comments(api, record.pr_number)
        if _is_publisher_marker(item, wanted)
    ]
    if len(matches) > 1:
        raise ControlError("multiple canonical Pi staging marker comments exist for the same PR/head")
    if matches:
        api.patch(f"/issues/comments/{matches[0]}", {"body": body})
        return matches[0]
    created = api.post(f"/issues/{record.pr_number}/comments", {"body": body})
    created_map = _mapping(created, "created comment response")
    return _positive_int(created_map.get("id"), "comment ID")


def publish_record(api: Api, record: Record) -> int:
    """Publish exact-head status first, then upsert its durable PR record."""
    api.post(f"/statuses/{record.head_sha}", status_payload(record))
    return upsert_comment(api, record)


def _run_title_matches_source(run: Mapping[str, Any], pr_number: int, sha: str) -> bool:
    """Bind a workflow_dispatch run to the exact source PR/head encoded in its run name."""
    if pr_number <= 0:
        raise ControlError("pending-run PR number is invalid")
    _validate_sha(sha, "pending-run SHA")
    title = run.get("display_title")
    if not isinstance(title, str):
        return False
    prefix = f"Pi Staging PR #{pr_number} / {sha} / "
    if not title.startswith(prefix):
        return False
    correlation = title[len(prefix):]
    try:
        validate_correlation(correlation)
    except ControlError:
        return False
    return True


def _pending_run_from_status(api: Api, status: Mapping[str, Any], pr_number: int, sha: str) -> Mapping[str, Any] | None:
    """Return an active run only when its durable title matches the requested PR/head."""
    if status.get("context") != STATUS_CONTEXT or status.get("state") != "pending":
        return None
    run_id = _run_id_from_url(status.get("target_url"))
    if run_id is None:
        return None
    run = api.get(f"/actions/runs/{run_id}")
    if not isinstance(run, Mapping) or run.get("status") not in ACTIVE_RUN_STATES:
        return None
    return run if _run_title_matches_source(run, pr_number, sha) else None


def find_pending_run(api: Api, pr_number: int, sha: str) -> Mapping[str, Any] | None:
    """Find an already-active exact-PR/head Pi run across bounded status pages."""
    if pr_number <= 0:
        raise ControlError("pending-run PR number is invalid")
    _validate_sha(sha, "pending-run SHA")
    for page in range(1, STATUS_PAGE_LIMIT + 1):
        statuses = api.get(f"/commits/{sha}/statuses?per_page=100&page={page}")
        if not isinstance(statuses, list):
            raise ControlError("commit statuses response is not a list")
        for status in statuses:
            if isinstance(status, Mapping):
                run = _pending_run_from_status(api, status, pr_number, sha)
                if run is not None:
                    return run
        if len(statuses) < 100:
            return None
    raise ControlError(f"refusing to scan more than {STATUS_PAGE_LIMIT * 100} commit statuses for staging deduplication")


def _named_run(runs: Any, title: str) -> Mapping[str, Any] | None:
    if not isinstance(runs, Mapping) or not isinstance(runs.get("workflow_runs"), list):
        raise ControlError("workflow runs response is invalid")
    for run in runs["workflow_runs"]:
        if isinstance(run, Mapping) and run.get("display_title") == title:
            return run
    return None


def locate_correlated_run(api: Api, title: str, attempts: int = 60, delay_seconds: float = 2.0) -> Mapping[str, Any]:
    """Locate the exact workflow_dispatch run by deterministic display title."""
    if attempts <= 0 or delay_seconds < 0:
        raise ControlError("invalid public run lookup bounds")
    path = f"/actions/workflows/{PUBLIC_WORKFLOW}/runs?event=workflow_dispatch&per_page=100"
    for index in range(attempts):
        run = _named_run(api.get(path), title)
        if run is not None:
            run_id = _positive_int(run.get("id"), "workflow run ID")
            _require_run_url(run.get("html_url"), SOURCE_REPOSITORY, run_id)
            return run
        if index + 1 < attempts:
            time.sleep(delay_seconds)
    raise ControlError(f"unable to locate correlated public Pi Staging run: {title}")


def record_from_run(binding: PrBinding, requester: str, run: Mapping[str, Any], state: str = "queued") -> Record:
    """Build a validated durable record from a discovered public workflow run."""
    run_id = _positive_int(run.get("id"), "workflow run ID")
    attempt = _positive_int(run.get("run_attempt", 1), "workflow run attempt")
    url = _require_run_url(run.get("html_url"), SOURCE_REPOSITORY, run_id)
    record = Record(binding.pr_number, binding.head_sha, requester, run_id, attempt, url, state)
    _validate_record(record)
    return record


def _get_pr(api: Api, pr_number: int, label: str) -> Mapping[str, Any]:
    return _mapping(api.get(f"/pulls/{pr_number}"), label)


def handle_command(api: Api, payload: Mapping[str, Any]) -> str:
    """Authorize, exact-bind, deduplicate, dispatch, correlate, and publish a command."""
    parsed = command_event(payload)
    if parsed is None:
        return "ignored"
    pr_number, requester, correlation = parsed
    binding = binding_from_pr(_get_pr(api, pr_number, "PR response"))
    if binding.pr_number != pr_number:
        raise ControlError("PR number mismatch")
    pending = find_pending_run(api, binding.pr_number, binding.head_sha)
    if pending is not None:
        publish_record(api, record_from_run(binding, requester, pending, "in_progress"))
        return "deduplicated"
    require_same_binding(_get_pr(api, pr_number, "PR revalidation response"), binding)
    api.post(f"/actions/workflows/{PUBLIC_WORKFLOW}/dispatches", dispatch_payload(binding, correlation, requester))
    run = locate_correlated_run(api, expected_run_title(binding, correlation))
    if run.get("status") != "completed":
        queued_states = {"queued", "requested", "waiting", "pending"}
        state = "queued" if run.get("status") in queued_states else "in_progress"
        publish_record(api, record_from_run(binding, requester, run, state))
    return "dispatched"


def parse_record_args(args: argparse.Namespace) -> Record:
    """Parse and validate workflow-provided publication arguments."""
    pr_number = _positive_int(args.pr_number, "PR number")
    head_sha = _validate_sha(args.sha, "record SHA")
    requester = validate_requester(args.requester)
    public_run_id = _positive_int(args.public_run_id, "public run ID")
    public_attempt = _positive_int(args.public_attempt, "public attempt")
    public_url = _require_run_url(args.public_run_url, SOURCE_REPOSITORY, public_run_id)
    private_id = None
    private_url = None
    if args.private_run_id or args.private_run_url:
        private_id = _positive_int(args.private_run_id, "private run ID")
        private_url = _require_run_url(args.private_run_url, STAGING_REPOSITORY, private_id)
    evidence = validate_evidence_identity(args.evidence_identity) if args.evidence_identity else None
    record = Record(
        pr_number=pr_number,
        head_sha=head_sha,
        requester=requester,
        public_run_id=public_run_id,
        public_attempt=public_attempt,
        public_run_url=public_url,
        state=args.state,
        private_run_id=private_id,
        private_run_url=private_url,
        conclusion=args.conclusion or None,
        cleanup=args.cleanup or None,
        evidence_identity=evidence,
    )
    _validate_record(record)
    return record


def build_parser() -> argparse.ArgumentParser:
    """Build the command-line interface used by trusted default-branch workflows."""
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
    return parser


def _api_from_environment() -> GitHubApi:
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    if repository != SOURCE_REPOSITORY:
        raise ControlError(f"unexpected repository: {repository!r}")
    return GitHubApi(repository, os.environ.get("GITHUB_TOKEN", ""))


def _run_command_mode(api: Api) -> int:
    event_path = os.environ.get("GITHUB_EVENT_PATH", "")
    if not event_path:
        raise ControlError("GITHUB_EVENT_PATH is required")
    try:
        payload = json.loads(Path(event_path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ControlError("unable to read a valid GitHub issue_comment event") from exc
    if not isinstance(payload, Mapping):
        raise ControlError("GitHub event payload is invalid")
    result = handle_command(api, payload)
    print(f"Pi staging command result: {result}")
    return 0


def _run_publish_mode(api: Api, args: argparse.Namespace) -> int:
    record = parse_record_args(args)
    if args.require_current_binding:
        head_ref = _validate_head_ref(args.head_ref)
        expected = PrBinding(SOURCE_REPOSITORY, record.pr_number, SOURCE_REPOSITORY, head_ref, record.head_sha)
        require_same_binding(_get_pr(api, record.pr_number, "PR response during publication"), expected)
    comment_id = publish_record(api, record)
    print(f"Pi staging record comment ID: {comment_id}")
    return 0


def main(argv: list[str] | None = None) -> int:
    """Run trusted staging command or publication mode."""
    args = build_parser().parse_args(argv)
    api = _api_from_environment()
    return _run_command_mode(api) if args.mode == "command" else _run_publish_mode(api, args)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ControlError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        raise SystemExit(1)
