"""Offline fail-closed tests; no network, server or production configuration access."""
from __future__ import annotations

import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "migrate_ban_evasion_policy.py"
CANDIDATE = ROOT / "paper" / "src" / "main" / "resources" / "reason-policies.yml"

spec = importlib.util.spec_from_file_location("ban_evasion_migration", SCRIPT)
migration = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(migration)


class PolicyMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.candidate = CANDIDATE.read_bytes()
        cls.installed = cls.candidate.replace(
            migration.CANDIDATE_VERSION, migration.LEGACY_VERSION, 1
        ).replace(migration.NEW_BLOCK, migration.OLD_BLOCK, 1)
        assert cls.installed != cls.candidate

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.installed_path = Path(self.tmp.name) / "reason-policies.yml"
        self.candidate_path = Path(self.tmp.name) / "candidate.yml"
        self.installed_path.write_bytes(self.installed)
        self.candidate_path.write_bytes(self.candidate)

    def run_cli(self, *arguments):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            status = migration.main([
                "--installed", str(self.installed_path),
                "--candidate", str(self.candidate_path),
                *arguments
            ])
        return status, out.getvalue(), err.getvalue()

    def backups(self):
        return list(Path(self.tmp.name).glob("reason-policies.yml.before-ban-evasion-*.bak"))

    def test_exact_approved_migration_matches_candidate_bytes(self):
        proposed, current = migration.prepare(self.installed, self.candidate)
        self.assertFalse(current)
        self.assertEqual(self.candidate, proposed)
        self.assertEqual(85, self.candidate.count(b"\n  - id: "))

    def test_dry_run_never_writes_policy_or_backup(self):
        status, output, error = self.run_cli()
        self.assertEqual(0, status, error)
        self.assertIn("DRY_RUN_OK", output)
        self.assertIn(migration.digest(self.installed), output)
        self.assertEqual(self.installed, self.installed_path.read_bytes())
        self.assertEqual([], self.backups())

    def test_apply_requires_the_exact_original_hash(self):
        status, _, error = self.run_cli("--apply")
        self.assertEqual(2, status)
        self.assertIn("requires --expected-sha256", error)
        status, _, error = self.run_cli("--apply", "--expected-sha256", "0" * 64)
        self.assertEqual(2, status)
        self.assertIn("expected SHA-256 did not match", error)
        self.assertEqual(self.installed, self.installed_path.read_bytes())
        self.assertEqual([], self.backups())

    def test_apply_writes_exact_candidate_and_exclusive_recovery_backup(self):
        status, output, error = self.run_cli(
            "--apply", "--expected-sha256", migration.digest(self.installed)
        )
        self.assertEqual(0, status, error)
        self.assertIn("MIGRATION_APPLIED", output)
        self.assertEqual(self.candidate, self.installed_path.read_bytes())
        self.assertEqual(1, len(self.backups()))
        self.assertEqual(self.installed, self.backups()[0].read_bytes())
        status, output, error = self.run_cli()
        self.assertEqual(0, status, error)
        self.assertIn("ALREADY_CURRENT", output)

    def test_existing_backup_causes_safe_rejection(self):
        backup = self.installed_path.with_name(
            self.installed_path.name + ".before-ban-evasion-"
            + migration.digest(self.installed)[:16] + ".bak"
        )
        backup.write_bytes(b"do not overwrite")
        status, _, error = self.run_cli(
            "--apply", "--expected-sha256", migration.digest(self.installed)
        )
        self.assertEqual(2, status)
        self.assertIn("backup already exists", error)
        self.assertEqual(b"do not overwrite", backup.read_bytes())
        self.assertEqual(self.installed, self.installed_path.read_bytes())

    def test_other_customization_is_rejected_without_writes(self):
        self.installed_path.write_bytes(
            self.installed.replace(b"severity: 10", b"severity: 11", 1)
        )
        status, _, error = self.run_cli()
        self.assertEqual(2, status)
        self.assertIn("other policy content differs", error)
        self.assertEqual([], self.backups())

    def test_partial_already_changed_ladder_is_rejected(self):
        partial = self.installed.replace(
            b'      - { label: "30 day ban", sanctions: [{ type: NETWORK_BAN, duration: 30d }] }\n',
            b"",
        )
        self.installed_path.write_bytes(partial)
        status, _, error = self.run_cli()
        self.assertEqual(2, status)
        self.assertIn("installed ban-evasion ladder does not match", error)
        self.assertEqual(partial, self.installed_path.read_bytes())
        self.assertEqual([], self.backups())

    def test_mismatched_candidate_version_is_rejected(self):
        self.candidate_path.write_bytes(
            self.candidate.replace(migration.CANDIDATE_VERSION, b'version: "other"\n', 1)
        )
        status, _, error = self.run_cli()
        self.assertEqual(2, status)
        self.assertIn("candidate version is not", error)

    def test_same_installed_and_candidate_path_is_rejected(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            status = migration.main([
                "--installed", str(self.installed_path),
                "--candidate", str(self.installed_path),
            ])
        self.assertEqual(2, status)
        self.assertIn("cannot refer to the same file", err.getvalue())

    def test_symlink_installed_is_rejected_if_supported(self):
        symlink = Path(self.tmp.name) / "symlink.yml"
        try:
            symlink.symlink_to(self.installed_path)
        except (OSError, NotImplementedError):
            self.skipTest("symlink creation unavailable")
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            status = migration.main([
                "--installed", str(symlink),
                "--candidate", str(self.candidate_path),
            ])
        self.assertEqual(2, status)
        self.assertIn("symlink", err.getvalue())


if __name__ == "__main__":
    unittest.main()
