"""Runtime tests for the candidate's local offline-build provenance gate."""
import importlib.util
import os
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("punishment_candidate_verify.py")
spec = importlib.util.spec_from_file_location("punishment_candidate_verify", SCRIPT)
candidate = importlib.util.module_from_spec(spec)
if spec.loader is not None:
    spec.loader.exec_module(candidate)

class PunishmentCandidateVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.wrapper = self.root / ("gradlew.bat" if candidate.sys.platform == "win32" else "gradlew")
        self.wrapper.write_text("fake test-only Gradle wrapper")
        self.wrapper.chmod(0o755)
        self.reports = []
        for module in ("domain", "paper", "staff-bot", "velocity"):
            p = self.root / module / "build/test-results/test/TEST-Example.xml"
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('<testsuite tests="2" skipped="0" failures="0" errors="0"/>')
            self.reports.append(p)
        self.jar = self.root / "paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar"
        self.jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("config.yml", "guided-punishment:\n  enabled: false\n")
        self.anchor = 2_000_000_000.0
        for p in self.reports + [self.jar]:
            os.utime(p, (self.anchor + 3, self.anchor + 3))

    def test_valid_fresh_reports_and_jar(self):
        proof = candidate.verify_artifact_freshness(self.root, self.anchor)
        self.assertEqual(5, proof["checked_files"])
        self.assertTrue(proof["fresh_for_invocation"])

    def test_stale_jar_blocks_claim(self):
        os.utime(self.jar, (self.anchor - 60, self.anchor - 60))
        with self.assertRaises(candidate.EvidenceError):
            candidate.verify_artifact_freshness(self.root, self.anchor)

    def test_stale_single_junit_report_blocks_claim(self):
        os.utime(self.reports[2], (self.anchor - 60, self.anchor - 60))
        with self.assertRaises(candidate.EvidenceError):
            candidate.verify_artifact_freshness(self.root, self.anchor)

    def test_missing_module_blocks_claim(self):
        self.reports[0].unlink()
        with self.assertRaises(candidate.EvidenceError):
            candidate.verify_artifact_freshness(self.root, self.anchor)

    def test_unknown_source_provenance_does_not_claim_verified_head(self):
        # This fixture has no git metadata; it must not imply an exact candidate HEAD.
        with self.assertRaises(candidate.EvidenceError):
            candidate.assert_exact_source_identity(self.root)

    def test_missing_or_symlinked_wrapper_cannot_be_executed(self):
        self.wrapper.unlink()
        with self.assertRaises(candidate.EvidenceError):
            candidate.gradle_command(self.root)
        outside = self.root / "outside"
        outside.write_text("not a wrapper")
        try:
            self.wrapper.symlink_to(outside)
        except (OSError, NotImplementedError):
            self.skipTest("Symlinks are unavailable in the test environment")
        with self.assertRaises(candidate.EvidenceError):
            candidate.gradle_command(self.root)

    def test_run_invocation_uses_offline_no_daemon_rerun_tasks_and_never_integration_execution(self):
        args = candidate.gradle_command(self.root)
        self.assertIn("--offline", args)
        self.assertIn("--no-daemon", args)
        self.assertIn("--rerun-tasks", args)
        self.assertIn(":integration-tests:compileTestJava", args)
        self.assertNotIn(":integration-tests:test", args)
        self.assertTrue(args[0].lower().endswith(("gradlew", "gradlew.bat")))
        self.assertEqual(6, sum(a.startswith(":") for a in args))

if __name__ == "__main__":
    unittest.main()
