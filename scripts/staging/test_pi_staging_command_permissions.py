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


if __name__ == "__main__":
    unittest.main(verbosity=2)
