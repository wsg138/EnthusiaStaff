#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).parents[2]
CHECK_WORKFLOW = ROOT / ".github/workflows/pi-staging-check.yml"
SUPERSEDE_WORKFLOW = ROOT / ".github/workflows/pi-staging-supersede.yml"
APP_ACTION = ROOT / ".github/actions/staging-app-token/action.yml"
LEGACY_SECRET = "secrets.ENTHUSIASTAFF_STAGING_TOKEN"
CLIENT_ID = "vars.ENTHUSIASTAFF_STAGING_APP_CLIENT_ID"
PRIVATE_KEY = "secrets.ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY"
PINNED_APP_TOKEN_ACTION = "actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1"


class StagingAppAuthTests(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_canonical_bridge_uses_short_lived_app_auth(self):
        workflow = self.read(CHECK_WORKFLOW)
        self.assertNotIn(LEGACY_SECRET, workflow)
        self.assertIn(CLIENT_ID, workflow)
        self.assertIn(PRIVATE_KEY, workflow)
        self.assertIn("./staging-controls/.github/actions/staging-app-token", workflow)
        self.assertIn("Verify private staging Actions access", workflow)
        self.assertLess(
            workflow.index("Verify private staging Actions access"),
            workflow.index("Publish bounded transient GitHub release asset"),
        )

    def test_supersession_uses_short_lived_app_auth(self):
        workflow = self.read(SUPERSEDE_WORKFLOW)
        self.assertNotIn(LEGACY_SECRET, workflow)
        self.assertIn(CLIENT_ID, workflow)
        self.assertIn(PRIVATE_KEY, workflow)
        self.assertIn("./.github/actions/staging-app-token", workflow)
        self.assertLess(
            workflow.index("Mint short-lived private staging token"),
            workflow.index("Supersede stale public and private staging"),
        )

    def test_app_token_is_pinned_and_least_privilege(self):
        action = self.read(APP_ACTION)
        self.assertIn(PINNED_APP_TOKEN_ACTION, action)
        self.assertIn("owner: wsg138", action)
        self.assertIn("repositories: EnthusiaStaff-Staging", action)
        self.assertIn("permission-actions: write", action)
        self.assertIn("STAGING_TOKEN=%s", action)
        self.assertIn("$GITHUB_ENV", action)


if __name__ == "__main__":
    unittest.main()
