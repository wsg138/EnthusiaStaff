"""Synthetic only: verify that incomplete or tampered client evidence fails closed."""
from __future__ import annotations

from contextlib import redirect_stdout, redirect_stderr
from copy import deepcopy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest

from scripts.qa import punishment_gui_client_evidence_gate as gate


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class GuiClientEvidenceGateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "candidate.jar").write_bytes(b"synthetic jar fixture")
        (self.root / "proof.png").write_bytes(b"synthetic capture fixture")
        self.manifest = {
            "candidate_commit": "a" * 40,
            "artifact": {
                "path": "candidate.jar",
                "sha256": sha(b"synthetic jar fixture"),
            },
            "server": {"isolated_staging": True, "production": False,
                       "paper_version": "Paper test"},
            "client_versions": {
                "java": "synthetic test client", "bedrock": "synthetic test client",
                "geyser": "test build", "floodgate": "test build",
            },
            "cases": [
                {
                    "id": scenario, "client": client, "status": "PASS",
                    "notes": "Synthetic test fixture only, not real gameplay proof",
                    "actual_client_attested": True, "reviewer": "fixture",
                    "tested_at": "2026-10-30T12:00:00-04:00",
                    "media": "proof.png",
                    "media_sha256": sha(b"synthetic capture fixture"),
                }
                for scenario, client in sorted(gate.EXPECTED)
            ],
        }

    def test_all_expected_rows_are_unique_and_java_a19_is_excluded(self):
        self.assertEqual(39, len(gate.EXPECTED))
        self.assertNotIn(("A19", "java"), gate.EXPECTED)
        self.assertIn(("A19", "bedrock"), gate.EXPECTED)

    def test_well_formed_fixture_records_are_complete_not_proof_of_authenticity(self):
        passed, incomplete = gate.evaluate(self.manifest, self.root)
        self.assertEqual(39, passed)
        self.assertEqual([], incomplete)

    def test_blocked_row_is_never_counted_as_pass(self):
        row = self.manifest["cases"][0]
        row["status"] = "BLOCKED"
        row["notes"] = "Client not present for this actual staging session"
        passed, incomplete = gate.evaluate(self.manifest, self.root)
        self.assertEqual(38, passed)
        self.assertEqual(1, len(incomplete))

    def test_failed_row_is_never_counted_as_pass(self):
        row = self.manifest["cases"][0]
        row["status"] = "FAIL"
        row["notes"] = "Wrong result for the expected test outcome"
        passed, incomplete = gate.evaluate(self.manifest, self.root)
        self.assertEqual(38, passed)
        self.assertEqual(1, len(incomplete))

    def test_missing_row_rejected(self):
        self.manifest["cases"].pop()
        with self.assertRaisesRegex(gate.EvidenceInvalid, "missing 1 required"):
            gate.evaluate(self.manifest, self.root)

    def test_duplicate_and_inapplicable_rows_rejected(self):
        self.manifest["cases"].append(deepcopy(self.manifest["cases"][0]))
        with self.assertRaisesRegex(gate.EvidenceInvalid, "duplicate"):
            gate.evaluate(self.manifest, self.root)
        self.manifest["cases"][-1]["id"] = "A19"
        self.manifest["cases"][-1]["client"] = "java"
        with self.assertRaisesRegex(gate.EvidenceInvalid, "inapplicable"):
            gate.evaluate(self.manifest, self.root)

    def test_missing_real_client_attestation_rejected(self):
        self.manifest["cases"][0]["actual_client_attested"] = False
        with self.assertRaisesRegex(gate.EvidenceInvalid, "attestation"):
            gate.evaluate(self.manifest, self.root)

    def test_missing_media_and_tampered_media_rejected(self):
        row = self.manifest["cases"][0]
        row["media"] = "missing.png"
        with self.assertRaisesRegex(gate.EvidenceInvalid, "missing"):
            gate.evaluate(self.manifest, self.root)
        row["media"] = "proof.png"
        (self.root / "proof.png").write_bytes(b"changed fixture")
        with self.assertRaisesRegex(gate.EvidenceInvalid, "checksum mismatch"):
            gate.evaluate(self.manifest, self.root)

    def test_traversal_and_symlink_media_rejected(self):
        self.manifest["cases"][0]["media"] = "../outside.png"
        with self.assertRaisesRegex(gate.EvidenceInvalid, "traverse"):
            gate.evaluate(self.manifest, self.root)
        link = self.root / "linked.png"
        try:
            link.symlink_to(self.root / "proof.png")
        except (OSError, NotImplementedError):
            self.skipTest("symlink unavailable")
        self.manifest["cases"][0]["media"] = "linked.png"
        with self.assertRaisesRegex(gate.EvidenceInvalid, "symlink"):
            gate.evaluate(self.manifest, self.root)

    def test_wrong_jar_hash_rejected(self):
        self.manifest["artifact"]["sha256"] = "f" * 64
        with self.assertRaisesRegex(gate.EvidenceInvalid, "checksum mismatch"):
            gate.evaluate(self.manifest, self.root)

    def test_production_or_unknown_server_rejected(self):
        self.manifest["server"]["production"] = True
        with self.assertRaisesRegex(gate.EvidenceInvalid, "production"):
            gate.evaluate(self.manifest, self.root)
        self.manifest["server"]["production"] = False
        self.manifest["server"]["isolated_staging"] = False
        with self.assertRaisesRegex(gate.EvidenceInvalid, "isolated"):
            gate.evaluate(self.manifest, self.root)

    def test_missing_bedrock_versions_rejected(self):
        del self.manifest["client_versions"]["geyser"]
        with self.assertRaisesRegex(gate.EvidenceInvalid, "geyser"):
            gate.evaluate(self.manifest, self.root)

    def test_bad_timestamp_rejected(self):
        self.manifest["cases"][0]["tested_at"] = "2026-10-30T12:00:00"
        with self.assertRaisesRegex(gate.EvidenceInvalid, "timezone"):
            gate.evaluate(self.manifest, self.root)

    def test_cli_returns_no_go_for_unverified_rows(self):
        self.manifest["cases"][0]["status"] = "BLOCKED"
        file = self.root / "manifest.json"
        file.write_text(json.dumps(self.manifest))
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = gate.main([str(file)])
        self.assertEqual(1, status)
        self.assertIn("NO-GO: 38/39", out.getvalue())
        self.assertEqual("", err.getvalue())


if __name__ == "__main__":
    unittest.main()
