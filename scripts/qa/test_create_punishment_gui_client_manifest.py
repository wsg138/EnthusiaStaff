"""Template generation must remain safe, incomplete and non-destructive."""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from scripts.qa import create_punishment_gui_client_manifest as generator
from scripts.qa import punishment_gui_client_evidence_gate as gate


class ManifestTemplateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.target = Path(self.temp.name) / "acceptance.json"

    def test_template_has_all_applicable_rows_blocked(self):
        manifest = generator.make_template()
        self.assertEqual(39, len(manifest["cases"]))
        self.assertEqual(39, len({
            (r["id"], r["client"]) for r in manifest["cases"]
        }))
        self.assertEqual("BLOCKED", manifest["cases"][0]["status"])
        self.assertTrue(all(row["status"] == "BLOCKED" for row in manifest["cases"]))
        self.assertFalse(manifest["server"]["isolated_staging"])
        self.assertEqual("", manifest["candidate_commit"])

    def test_template_remains_rejected_until_real_metadata_is_recorded(self):
        with self.assertRaises(gate.EvidenceInvalid):
            gate.evaluate(generator.make_template(), self.target.parent)

    def test_generate_cli_creates_file_once_without_overwriting(self):
        self.assertEqual(0, generator.main([str(self.target)]))
        saved = self.target.read_bytes()
        self.assertEqual(39, len(json.loads(saved)["cases"]))
        with self.assertRaises(SystemExit):
            generator.main([str(self.target)])
        self.assertEqual(saved, self.target.read_bytes())

    def test_template_never_claims_real_client_proof(self):
        manifest = generator.make_template()
        self.assertTrue(all("actual_client_attested" not in case
                            and "media_sha256" not in case
                            for case in manifest["cases"]))


if __name__ == "__main__":
    unittest.main()
