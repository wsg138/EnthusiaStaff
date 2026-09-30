#!/usr/bin/env python3
from pathlib import Path
import unittest


class PiStagingCommandPermissionTests(unittest.TestCase):
    def test_command_can_publish_canonical_pr_record(self):
        workflow = (Path(__file__).parents[2] / ".github/workflows/pi-staging-command.yml").read_text(encoding="utf-8")
        self.assertIn("issues: write", workflow)
        self.assertIn("pull-requests: write", workflow)
        self.assertIn("statuses: write", workflow)
        self.assertIn("pi_staging_control.py command", workflow)

    def test_supersession_skips_draft_synchronize_but_keeps_closed_cleanup(self):
        workflow = (Path(__file__).parents[2] / ".github/workflows/pi-staging-supersede.yml").read_text(encoding="utf-8")
        expected_guard = (
            "github.event.pull_request.head.repo.full_name == github.repository && "
            "(github.event.action == 'closed' || github.event.pull_request.draft == false)"
        )
        self.assertIn(expected_guard, workflow)


if __name__ == "__main__":
    unittest.main(verbosity=2)
