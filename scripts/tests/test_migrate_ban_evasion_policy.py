"""Offline fail-closed tests; no network, server or production configuration access."""
from __future__ import annotations

import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from scripts import migrate_ban_evasion_policy as migration

ROOT = Path(__file__).resolve().parents[2]
CANDIDATE = ROOT / "paper" / "src" / "main" / "resources" / "reason-policies.yml"


class PolicyMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.candidate = CANDIDATE.read_bytes()
        cls.installed = cls.candidate.replace(
            migration.CANDIDATE_VERSION, migration.LEGACY_VERSION, 1
        ).replace(migration.NEW_BLOCK, migration.OLD_BLOCK, 1)
        if cls.installed == cls.candidate:
            raise ValueError("generated legacy policy unexpectedly equals candidate")

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

    def test_backup_and_replacement_preserve_unix_permissions_and_owner(self):
        if migration.os.name == "nt":
            self.skipTest("POSIX ownership and mode assertions")
        self.installed_path.chmod(0o640)
        previous = self.installed_path.stat()
        status, _, error = self.run_cli(
            "--apply", "--expected-sha256", migration.digest(self.installed)
        )
        self.assertEqual(0, status, error)
        for saved in [self.installed_path, self.backups()[0]]:
            current = saved.stat()
            self.assertEqual((previous.st_uid, previous.st_gid),
                             (current.st_uid, current.st_gid))
            self.assertEqual(0o640, current.st_mode & 0o777)

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

    def test_failed_backup_flush_removes_incomplete_backup_and_keeps_policy(self):
        with mock.patch.object(migration.os, "fsync", side_effect=OSError("disk full")):
            status, _, error = self.run_cli(
                "--apply", "--expected-sha256", migration.digest(self.installed)
            )
        self.assertEqual(2, status)
        self.assertIn("disk full", error)
        self.assertEqual([], self.backups(), "Partial backup must not be reused")
        self.assertEqual(self.installed, self.installed_path.read_bytes())

    def test_failed_backup_directory_sync_blocks_policy_replacement(self):
        with mock.patch.object(migration, "sync_directory",
                               side_effect=OSError("directory sync unavailable")):
            status, _, error = self.run_cli(
                "--apply", "--expected-sha256", migration.digest(self.installed)
            )
        self.assertEqual(2, status)
        self.assertIn("directory sync unavailable", error)
        self.assertEqual([], self.backups())
        self.assertEqual(self.installed, self.installed_path.read_bytes())

    def test_failed_atomic_replace_retains_recovery_backup(self):
        with mock.patch.object(migration.os, "replace", side_effect=OSError("replace failed")):
            status, _, error = self.run_cli(
                "--apply", "--expected-sha256", migration.digest(self.installed)
            )
        self.assertEqual(2, status)
        self.assertIn("replace failed", error)
        self.assertEqual(self.installed, self.installed_path.read_bytes())
        self.assertEqual(1, len(self.backups()))
        self.assertEqual(self.installed, self.backups()[0].read_bytes())
        self.assertEqual([], list(Path(self.tmp.name).glob(".reason-policies-migrate-*")))

    def test_concurrent_change_after_backup_refuses_replacement(self):
        original_write_backup = migration.write_exclusive_backup
        def backup_then_external_edit(source, backup, before, mode):
            original_write_backup(source, backup, before, mode)
            self.installed_path.write_bytes(before + b"# external operator edit\\n")

        with mock.patch.object(migration, "write_exclusive_backup",
                               side_effect=backup_then_external_edit):
            status, _, error = self.run_cli(
                "--apply", "--expected-sha256", migration.digest(self.installed)
            )
        self.assertEqual(2, status)
        self.assertIn("changed during backup", error)
        self.assertTrue(self.installed_path.read_bytes().endswith(b"# external operator edit\\n"))
        self.assertEqual(self.installed, self.backups()[0].read_bytes())

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
