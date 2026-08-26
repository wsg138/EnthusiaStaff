#!/usr/bin/env python3
from __future__ import annotations
import importlib.util
import sys
import unittest
from pathlib import Path
from typing import Any, Mapping

MODULE_PATH = Path(__file__).with_name('supersede_pi_staging.py')
spec = importlib.util.spec_from_file_location('supersede_pi_staging', MODULE_PATH)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
sys.modules['supersede_pi_staging'] = mod
spec.loader.exec_module(mod)

SHA = 'a' * 40
OLD = 'b' * 40

class FakeApi:
    def __init__(self):
        self.gets: dict[str, Any] = {}
        self.posts: list[tuple[str, Mapping[str, Any] | None]] = []
    def get(self, path: str):
        value = self.gets.get(path)
        if value is None:
            if '/comments?' in path:
                return []
            raise AssertionError(path)
        return value
    def post(self, path: str, payload=None):
        self.posts.append((path, payload))
        return None

def pub(run_id, sha, status='queued'):
    return {'id': run_id, 'status': status, 'display_title': f'Pi Staging PR #160 / {sha} / comment-123'}

def priv(run_id, sha, status='queued'):
    return {'id': run_id, 'status': status, 'path': mod.PRIVATE_WORKFLOW_PATH, 'event': 'workflow_dispatch', 'display_title': f'EnthusiaStaff bridge 99-1 / {sha}'}

class Tests(unittest.TestCase):
    def test_parse_public_title(self):
        self.assertEqual(mod.parse_public_title(f'Pi Staging PR #160 / {SHA} / comment-1'), (160, SHA))
        self.assertIsNone(mod.parse_public_title('bad'))

    def test_public_state_keeps_exact_and_cancels_stale(self):
        api = FakeApi()
        api.gets['/actions/workflows/pi-staging-check.yml/runs?per_page=100&page=1'] = {'workflow_runs': [pub(1, OLD), pub(2, SHA)]}
        exact, cancelled = mod.public_state(api, 160, SHA)
        self.assertTrue(exact)
        self.assertEqual(cancelled, [1])
        self.assertEqual(api.posts[0][0], '/actions/runs/1/cancel')

    def test_private_queued_cancel_safe(self):
        api = FakeApi()
        self.assertTrue(mod.private_cancel_is_safe(api, priv(7, OLD), OLD))

    def test_private_running_before_paper_cancel_safe(self):
        api = FakeApi()
        api.gets['/actions/runs/7/jobs?per_page=100'] = {'jobs': [{'steps': [{'name': mod.PAPER_STEP, 'status': 'pending'}]}]}
        self.assertTrue(mod.private_cancel_is_safe(api, priv(7, OLD, 'in_progress'), OLD))

    def test_private_running_paper_is_preserved(self):
        api = FakeApi()
        api.gets['/actions/runs/7/jobs?per_page=100'] = {'jobs': [{'steps': [{'name': mod.PAPER_STEP, 'status': 'in_progress'}]}]}
        self.assertFalse(mod.private_cancel_is_safe(api, priv(7, OLD, 'in_progress'), OLD))

    def test_private_title_must_match_stale_sha(self):
        api = FakeApi()
        with self.assertRaises(mod.SupersedeError):
            mod.private_cancel_is_safe(api, priv(7, SHA), OLD)

    def test_legacy_pull_request_target_binding_is_drained(self):
        run = {
            'id': 9,
            'run_attempt': 1,
            'status': 'queued',
            'event': 'pull_request_target',
            'display_title': '[ES-D05] Staff bot runtime foundation',
            'pull_requests': [{'number': 160, 'head': {'sha': OLD}}],
        }
        self.assertEqual(mod.public_binding(run), (160, OLD))

    def test_private_run_discovered_from_public_correlation_without_comment(self):
        api = FakeApi()
        api.gets['/actions/workflows/plugin-live-test.yml/runs?event=workflow_dispatch&per_page=100&page=1'] = {
            'workflow_runs': [priv(17, OLD)]
        }
        # Canonical private titles encode the originating public run/attempt.
        api.gets['/actions/workflows/plugin-live-test.yml/runs?event=workflow_dispatch&per_page=100&page=1']['workflow_runs'][0]['display_title'] = f'EnthusiaStaff bridge 99-1 / {OLD}'
        self.assertEqual(mod.private_runs_from_correlations(api, [(OLD, '99-1')]), [(OLD, 17)])

    def test_record_parser_ignores_current_head(self):
        api = FakeApi()
        body_old = f'<!-- enthusia-pi-staging pr=160 sha={OLD} -->\n- Private run ID: `7`'
        body_new = f'<!-- enthusia-pi-staging pr=160 sha={SHA} -->\n- Private run ID: `8`'
        api.gets['/issues/160/comments?per_page=100&page=1'] = [
            {'body': body_old, 'user': {'id': mod.PUBLISHER_USER_ID}},
            {'body': body_new, 'user': {'id': mod.PUBLISHER_USER_ID}},
        ]
        self.assertEqual(mod.private_records(api, 160, SHA), [(OLD, 7)])

if __name__ == '__main__':
    unittest.main(verbosity=2)
