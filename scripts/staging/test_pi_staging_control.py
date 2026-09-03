#!/usr/bin/env python3
from __future__ import annotations

import copy
import importlib.util
import secrets
import unittest
import sys
from pathlib import Path
from typing import Any, Mapping

MODULE_PATH = Path(__file__).with_name("pi_staging_control.py")
spec = importlib.util.spec_from_file_location("pi_staging_control", MODULE_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError("unable to load pi_staging_control module spec")
control = importlib.util.module_from_spec(spec)
sys.modules["pi_staging_control"] = control
spec.loader.exec_module(control)

SHA = "b231022b065b5843d2dd73811dfbf51acba6314b"
OTHER_SHA = "a" * 40


def pr(*, state="open", draft=False, repo=control.SOURCE_REPOSITORY, ref="package/es-d04-account-linking", sha=SHA, number=151):
    return {"number": number, "state": state, "draft": draft, "head": {"repo": {"full_name": repo}, "ref": ref, "sha": sha}}


def event(body=control.EXACT_COMMAND, *, pull=True, association="OWNER", requester="wsg138", comment_id=5397000001):
    return {
        "repository": {"full_name": control.SOURCE_REPOSITORY},
        "issue": {"number": 151, **({"pull_request": {"url": "x"}} if pull else {})},
        "comment": {"id": comment_id, "body": body, "author_association": association, "user": {"login": requester}},
    }


def status(context="Other", state="success", target_url="https://github.com/wsg138/EnthusiaStaff/actions/runs/1"):
    return {"context": context, "state": state, "target_url": target_url}


def public_run(run_id: int, *, pr_number: int = 151, sha: str = SHA, correlation: str = "comment-5397000001", state: str = "in_progress"):
    return {
        "id": run_id,
        "run_attempt": 1,
        "html_url": f"https://github.com/wsg138/EnthusiaStaff/actions/runs/{run_id}",
        "status": state,
        "display_title": f"Pi Staging PR #{pr_number} / {sha} / {correlation}",
    }


def marker_comment(comment_id: int, user_id: int, body: str | None = None):
    return {"id": comment_id, "body": body or control.marker(151, SHA), "user": {"id": user_id}}


class FakeApi:
    def __init__(self):
        self.gets: dict[str, Any] = {}
        self.posts: list[tuple[str, Mapping[str, Any]]] = []
        self.patches: list[tuple[str, Mapping[str, Any]]] = []
        self.pull_sequence: list[Any] = []

    def get(self, path: str):
        if path == "/pulls/151" and self.pull_sequence:
            return self.pull_sequence.pop(0)
        value = self.gets.get(path)
        if callable(value):
            return value()
        if value is None:
            if path.startswith("/issues/151/comments"):
                return []
            if path.startswith("/commits/") and "/statuses?per_page=100&page=" in path:
                return []
            raise AssertionError(f"unexpected GET {path}")
        return copy.deepcopy(value)

    def post(self, path: str, payload: Mapping[str, Any]):
        self.posts.append((path, copy.deepcopy(payload)))
        if path == "/issues/151/comments":
            return {"id": 9001, "user": {"id": control.PUBLISHER_USER_ID}}
        return None

    def patch(self, path: str, payload: Mapping[str, Any]):
        self.patches.append((path, copy.deepcopy(payload)))
        return {"id": int(path.rsplit("/", 1)[-1])}


class PiStagingControlTests(unittest.TestCase):
    def test_01_exact_command_accepted(self):
        self.assertEqual(control.command_event(event())[0], 151)

    def test_02_whitespace_only_normalization_accepted(self):
        self.assertEqual(control.command_event(event(" \n\t@enthusia-staging test\r\n "))[2], "comment-5397000001")

    def test_03_command_suffix_rejected(self):
        self.assertIsNone(control.command_event(event("@enthusia-staging test now")))

    def test_04_prefix_or_substring_rejected(self):
        self.assertIsNone(control.command_event(event("please @enthusia-staging test")))

    def test_05_ordinary_issue_rejected(self):
        with self.assertRaises(control.ControlError):
            control.command_event(event(pull=False))

    def test_06_closed_pr_rejected(self):
        with self.assertRaisesRegex(control.ControlError, "not open"):
            control.binding_from_pr(pr(state="closed"))

    def test_07_draft_pr_rejected(self):
        with self.assertRaisesRegex(control.ControlError, "draft"):
            control.binding_from_pr(pr(draft=True))

    def test_08_fork_pr_rejected(self):
        with self.assertRaisesRegex(control.ControlError, "fork"):
            control.binding_from_pr(pr(repo="someone/fork"))

    def test_09_unauthorized_author_rejected(self):
        with self.assertRaisesRegex(control.ControlError, "not authorized"):
            control.command_event(event(association="NONE"))

    def test_10_exact_pr_binding(self):
        binding = control.binding_from_pr(pr())
        self.assertEqual((binding.head_repository, binding.head_ref, binding.head_sha), (control.SOURCE_REPOSITORY, "package/es-d04-account-linking", SHA))

    def test_11_moved_head_detection_fails_closed(self):
        binding = control.binding_from_pr(pr())
        with self.assertRaisesRegex(control.ControlError, "moved"):
            control.require_same_binding(pr(sha=OTHER_SHA), binding)

    def test_12_exact_workflow_dispatch_payload(self):
        binding = control.binding_from_pr(pr())
        payload = control.dispatch_payload(binding, "comment-7", "wsg138")
        self.assertEqual(payload, {"ref": "main", "inputs": {"source_sha": SHA, "source_pr_number": "151", "source_pr_head_repository": control.SOURCE_REPOSITORY, "source_pr_head_ref": "package/es-d04-account-linking", "source_pr_head_sha": SHA, "run_pi_test": True, "request_correlation": "comment-7", "request_requester": "wsg138"}})

    def test_13_run_correlation(self):
        binding = control.binding_from_pr(pr())
        self.assertEqual(control.expected_run_title(binding, "comment-7"), f"Pi Staging PR #151 / {SHA} / comment-7")

    def test_14_pending_status_uses_exact_pr_head(self):
        api = FakeApi()
        record = control.Record(151, SHA, "wsg138", 123, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/123", "queued")
        control.publish_record(api, record)
        self.assertEqual(api.posts[0][0], f"/statuses/{SHA}")
        self.assertEqual(api.posts[0][1]["state"], "pending")
        self.assertEqual(api.posts[0][1]["context"], "Pi Staging")

    def test_15_success_mapping(self):
        record = control.Record(151, SHA, "wsg138", 1, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/1", "terminal", conclusion="success", cleanup="success")
        self.assertEqual(control.status_payload(record)["state"], "success")

    def test_16_failure_mapping(self):
        record = control.Record(151, SHA, "wsg138", 1, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/1", "terminal", conclusion="failure", cleanup="failure")
        self.assertEqual(control.status_payload(record)["state"], "failure")

    def test_17_stable_comment_marker_creation(self):
        api = FakeApi()
        record = control.Record(151, SHA, "wsg138", 1, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/1", "queued")
        comment_id = control.upsert_comment(api, record)
        self.assertEqual(comment_id, 9001)
        self.assertIn(control.marker(151, SHA), api.posts[0][1]["body"])

    def test_18_stable_comment_update_not_spam(self):
        api = FakeApi()
        api.gets["/issues/151/comments?per_page=100&page=1"] = [marker_comment(77, control.PUBLISHER_USER_ID, control.marker(151, SHA) + "\nold")]
        record = control.Record(151, SHA, "wsg138", 2, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/2", "in_progress")
        self.assertEqual(control.upsert_comment(api, record), 77)
        self.assertEqual(len(api.posts), 0)
        self.assertEqual(api.patches[0][0], "/issues/comments/77")

    def test_19_private_run_id_publication(self):
        record = control.Record(151, SHA, "wsg138", 2, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/2", "in_progress", private_run_id=456, private_run_url="https://github.com/wsg138/EnthusiaStaff-Staging/actions/runs/456")
        text = control.render_record(record)
        self.assertIn("Private run ID: `456`", text)
        self.assertIn("actions/runs/456", text)

    def test_20_duplicate_pending_command_does_not_dispatch(self):
        api = FakeApi()
        api.pull_sequence = [pr()]
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=1"] = [{"context": "Pi Staging", "state": "pending", "target_url": "https://github.com/wsg138/EnthusiaStaff/actions/runs/444"}]
        api.gets["/actions/runs/444"] = public_run(444)
        self.assertEqual(control.handle_command(api, event()), "deduplicated")
        self.assertFalse(any(path.endswith("/dispatches") for path, _ in api.posts))

    def test_21_private_staging_is_explicit_only(self):
        workflow = (Path(__file__).parents[2] / ".github/workflows/pi-staging-check.yml").read_text(encoding="utf-8")
        trigger_block = workflow.split("permissions:", 1)[0]
        self.assertIn("workflow_dispatch:", trigger_block)
        self.assertNotIn("pull_request_target:", trigger_block)
        self.assertNotIn("\n  push:\n", trigger_block)
        self.assertIn("Revalidate exact candidate and supersede stale staging", workflow)

    def test_22_public_private_provenance_behavior_unchanged(self):
        workflow = (Path(__file__).parents[2] / ".github/workflows/pi-staging-check.yml").read_text(encoding="utf-8")
        for needle in ("sha256sum -c SHA256SUMS", "manifest.json", "plugin-live-test.yml", "Locate dispatched private run", "source_pr_head_sha"):
            self.assertIn(needle, workflow)

    def test_23_cleanup_remains_required(self):
        workflow = (Path(__file__).parents[2] / ".github/workflows/pi-staging-check.yml").read_text(encoding="utf-8")
        self.assertIn("Remove transient public transfer", workflow)
        self.assertIn("Transient public transfer cleanup did not succeed", workflow)

    def test_24_command_cannot_bypass_private_resource_lane(self):
        command_workflow = (Path(__file__).parents[2] / ".github/workflows/pi-staging-command.yml").read_text(encoding="utf-8")
        self.assertNotIn("EnthusiaStaff-Staging", command_workflow)
        self.assertNotIn("plugin-live-test.yml", command_workflow)
        self.assertIn("pi_staging_control.py command", command_workflow)

    def test_25_head_move_between_fetches_prevents_dispatch(self):
        api = FakeApi()
        api.pull_sequence = [pr(), pr(sha=OTHER_SHA)]
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=1"] = []
        with self.assertRaisesRegex(control.ControlError, "moved"):
            control.handle_command(api, event())
        self.assertFalse(any(path.endswith("/dispatches") for path, _ in api.posts))

    def test_26_exact_command_dispatches_and_correlates(self):
        api = FakeApi()
        api.pull_sequence = [pr(), pr()]
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=1"] = []
        title = f"Pi Staging PR #151 / {SHA} / comment-5397000001"
        api.gets["/actions/workflows/pi-staging-check.yml/runs?event=workflow_dispatch&per_page=100"] = {"workflow_runs": [{"id": 555, "run_attempt": 1, "html_url": "https://github.com/wsg138/EnthusiaStaff/actions/runs/555", "display_title": title}]}
        self.assertEqual(control.handle_command(api, event()), "dispatched")
        dispatches = [(path, payload) for path, payload in api.posts if path.endswith("/dispatches")]
        self.assertEqual(len(dispatches), 1)
        self.assertEqual(dispatches[0][1]["inputs"]["source_pr_head_sha"], SHA)

    def test_27_api_client_rejects_non_repository_paths(self):
        client = control.GitHubApi(control.SOURCE_REPOSITORY, secrets.token_urlsafe(32))
        for path in ("https://evil.invalid/repos/x", "//evil.invalid/x", "relative"):
            with self.subTest(path=path), self.assertRaises(control.ControlError):
                client._request_path(path)

    def test_28_public_run_url_is_repository_scoped(self):
        self.assertEqual(control._run_id_from_url("https://github.com/wsg138/EnthusiaStaff/actions/runs/123"), 123)
        self.assertIsNone(control._run_id_from_url("https://github.com/evil/repo/actions/runs/123"))
        self.assertIsNone(control._run_id_from_url("https://github.com/wsg138/EnthusiaStaff/actions/runs/123?x=1"))

    def test_29_private_run_url_must_match_private_run_id(self):
        record = control.Record(151, SHA, "wsg138", 2, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/2", "in_progress", private_run_id=456, private_run_url="https://github.com/wsg138/EnthusiaStaff-Staging/actions/runs/999")
        with self.assertRaisesRegex(control.ControlError, "does not match"):
            control.render_record(record)

    def test_30_api_requests_include_user_agent(self):
        client = control.GitHubApi(control.SOURCE_REPOSITORY, secrets.token_urlsafe(32))
        captured: dict[str, Any] = {}

        def perform(method: str, path: str, data: bytes | None, headers: Mapping[str, str]):
            captured["headers"] = dict(headers)
            return 200, b"{}"

        client._perform_request = perform
        client.get("/pulls/151")
        self.assertEqual(captured["headers"]["User-Agent"], control.USER_AGENT)

    def test_31_forged_marker_is_not_updated(self):
        api = FakeApi()
        api.gets["/issues/151/comments?per_page=100&page=1"] = [marker_comment(77, 999999)]
        record = control.Record(151, SHA, "wsg138", 1, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/1", "queued")
        self.assertEqual(control.upsert_comment(api, record), 9001)
        self.assertEqual(api.patches, [])
        self.assertEqual(api.posts[0][0], "/issues/151/comments")

    def test_32_duplicate_non_publisher_markers_are_ignored(self):
        api = FakeApi()
        api.gets["/issues/151/comments?per_page=100&page=1"] = [marker_comment(77, 999999), marker_comment(78, 999998)]
        record = control.Record(151, SHA, "wsg138", 1, 1, "https://github.com/wsg138/EnthusiaStaff/actions/runs/1", "queued")
        self.assertEqual(control.upsert_comment(api, record), 9001)
        self.assertEqual(api.patches, [])

    def test_33_page_two_pending_status_is_reused(self):
        api = FakeApi()
        api.pull_sequence = [pr()]
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=1"] = [status() for _ in range(100)]
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=2"] = [{"context": "Pi Staging", "state": "pending", "target_url": "https://github.com/wsg138/EnthusiaStaff/actions/runs/777"}]
        api.gets["/actions/runs/777"] = public_run(777)
        self.assertEqual(control.handle_command(api, event()), "deduplicated")
        self.assertFalse(any(path.endswith("/dispatches") for path, _ in api.posts))

    def test_34_pending_run_for_other_pr_is_not_reused(self):
        api = FakeApi()
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=1"] = [{"context": "Pi Staging", "state": "pending", "target_url": "https://github.com/wsg138/EnthusiaStaff/actions/runs/801"}]
        api.gets["/actions/runs/801"] = public_run(801, pr_number=152)
        self.assertIsNone(control.find_pending_run(api, 151, SHA))

    def test_35_pending_run_for_other_source_sha_is_not_reused(self):
        api = FakeApi()
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=1"] = [{"context": "Pi Staging", "state": "pending", "target_url": "https://github.com/wsg138/EnthusiaStaff/actions/runs/802"}]
        api.gets["/actions/runs/802"] = public_run(802, sha=OTHER_SHA)
        self.assertIsNone(control.find_pending_run(api, 151, SHA))

    def test_36_pending_run_with_malformed_title_is_not_reused(self):
        api = FakeApi()
        api.gets[f"/commits/{SHA}/statuses?per_page=100&page=1"] = [{"context": "Pi Staging", "state": "pending", "target_url": "https://github.com/wsg138/EnthusiaStaff/actions/runs/803"}]
        run = public_run(803)
        run["display_title"] = f"Pi Staging PR #151 / {SHA} / invalid correlation with spaces"
        api.gets["/actions/runs/803"] = run
        self.assertIsNone(control.find_pending_run(api, 151, SHA))

    def test_37_public_workflow_run_name_preserves_exact_correlation(self):
        workflow = (Path(__file__).parents[2] / ".github/workflows/pi-staging-check.yml").read_text(encoding="utf-8")
        expected = 'run-name: "Pi Staging PR #${{ inputs.source_pr_number || \'main\' }} / ${{ inputs.source_pr_head_sha || inputs.source_sha || github.sha }} / ${{ inputs.request_correlation || \'manual\' }}"'
        self.assertIn(expected, workflow)


if __name__ == "__main__":
    unittest.main(verbosity=2)
