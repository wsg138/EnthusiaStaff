"""Tests for read-only offline punishment release evidence reporting."""
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("punishment_offline_evidence.py")
spec = importlib.util.spec_from_file_location("punishment_offline_evidence", SCRIPT)
evidence = importlib.util.module_from_spec(spec)
if spec.loader is not None:
    spec.loader.exec_module(evidence)


class PunishmentOfflineEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for m in ("domain", "paper", "staff-bot", "velocity"):
            p = self.root / m / "build/test-results/test/TEST-example.xml"
            p.parent.mkdir(parents=True)
            p.write_text('<testsuite name="fixture" tests="4" skipped="1" failures="0" errors="0"/>')
        self.jar = self.root / "paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar"
        self.jar.parent.mkdir(parents=True)
        self.write_jar(False)

    def write_jar(self, flag):
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("config.yml", f"guided-punishment:\n  enabled: {str(flag).lower()}\n")

    def test_four_modules_count_and_no_go_not_misrepresented_as_runtime(self):
        record = evidence.build_record(self.root)
        self.assertTrue(record["offline_checks_pass"])
        self.assertEqual(12, record["totals"]["passed"])
        self.assertEqual(4, record["totals"]["skipped"])
        self.assertEqual(16, record["totals"]["tests"])
        self.assertEqual("NO_GO", record["release_status"])
        self.assertIn("source_worktree_clean", record)
        self.assertIn("MariaDB-backed integration execution", record["not_proven"])
        self.assertFalse(record["jar"]["guided_default_enabled"])

    def test_guided_enabled_in_shaded_jar_fails(self):
        self.write_jar(True)
        record = evidence.build_record(self.root)
        self.assertFalse(record["offline_checks_pass"])
        self.assertTrue(record["jar"]["guided_default_enabled"])

    def test_test_failure_is_not_hidden_by_skips(self):
        p = self.root / "paper/build/test-results/test/TEST-example.xml"
        p.write_text('<testsuite tests="4" skipped="1" failures="1" errors="0"/>')
        record = evidence.build_record(self.root)
        self.assertFalse(record["offline_checks_pass"])
        self.assertEqual(1, record["totals"]["failed"])
        self.assertEqual(11, record["totals"]["passed"])

    def test_junit_doctype_external_entity_is_rejected(self):
        path = self.root / "paper/build/test-results/test/TEST-example.xml"
        path.write_text(
            '<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>'
            '<testsuite tests="4" skipped="1" failures="0" errors="0">'
            '<testcase>&xxe;</testcase></testsuite>'
        )
        with self.assertRaises(evidence.EvidenceError):
            evidence.build_record(self.root)

    def test_junit_internal_entity_expansion_is_rejected(self):
        path = self.root / "paper/build/test-results/test/TEST-example.xml"
        path.write_text(
            '<!DOCTYPE testsuite [<!ENTITY expanded "X">]>'
            '<testsuite tests="1" skipped="0" failures="0" errors="0">'
            '<testcase>&expanded;</testcase></testsuite>'
        )
        with self.assertRaises(evidence.EvidenceError):
            evidence.build_record(self.root)

    def test_junit_oversize_report_is_rejected(self):
        path = self.root / "paper/build/test-results/test/TEST-example.xml"
        with path.open("wb") as target:
            target.truncate(evidence.MAX_JUNIT_REPORT_BYTES + 1)
        with self.assertRaises(evidence.EvidenceError):
            evidence.build_record(self.root)

    def test_junit_malformed_and_wrong_root_are_rejected(self):
        path = self.root / "paper/build/test-results/test/TEST-example.xml"
        for xml in ('<testsuite tests="1"><testcase></testsuite>',
                    '<testsuites tests="4" skipped="1" failures="0" errors="0"/>'):
            path.write_text(xml)
            with self.assertRaises(evidence.EvidenceError):
                evidence.build_record(self.root)

    def test_valid_junit_nested_testcases_keep_expected_counts(self):
        path = self.root / "paper/build/test-results/test/TEST-example.xml"
        path.write_text(
            '<?xml version="1.0"?>'
            '<testsuite tests="3" skipped="1" failures="1" errors="0">'
            '<testcase name="first"/>'
            '<testcase name="second"><failure message="failed"/></testcase>'
            '<testcase name="third"><skipped/></testcase></testsuite>'
        )
        record = evidence.build_record(self.root)
        self.assertFalse(record["offline_checks_pass"])
        self.assertEqual(15, record["totals"]["tests"])
        self.assertEqual(1, record["totals"]["failures"])

    def test_missing_module_report_fails_closed(self):
        (self.root / "velocity/build/test-results/test/TEST-example.xml").unlink()
        with self.assertRaises(evidence.EvidenceError):
            evidence.build_record(self.root)

    def test_missing_config_in_jar_fails_closed(self):
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("plugin.yml", "name: EnthusiaStaff\n")
        with self.assertRaises(evidence.EvidenceError):
            evidence.build_record(self.root)

    def test_duplicate_ambiguous_default_flag_fails_closed(self):
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("config.yml", "guided-punishment:\n  enabled: false\n  enabled: true\n")
        with self.assertRaises(evidence.EvidenceError):
            evidence.build_record(self.root)


if __name__ == "__main__":
    unittest.main()
