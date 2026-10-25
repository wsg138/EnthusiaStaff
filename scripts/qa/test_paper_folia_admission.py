"""Regression tests for fail-closed Folia metadata and source admission."""
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("paper_folia_admission.py")
spec = importlib.util.spec_from_file_location("paper_folia_admission", SCRIPT)
audit = importlib.util.module_from_spec(spec)
if spec.loader is not None:
    spec.loader.exec_module(audit)


class FoliaAdmissionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / "paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar"
        self.jar.parent.mkdir(parents=True)
        self.src = self.root / "paper/src/main/java/net/enthusia/staff/paper"
        self.src.mkdir(parents=True)
        self.write_metadata("name: EnthusiaStaff\nmain: example.EnthusiaStaff\n")
        self.write_source("integration/RoseChatInboundBridgeIntegration.java",
                          "task -> plugin.getServer().getScheduler().runTask(plugin, task)")
        self.write_source("staff/StaffStateCodec.java",
                          "if (!player.teleport(decoded.location())) return false;")

    def write_metadata(self, yaml):
        with zipfile.ZipFile(self.jar, "w") as z:
            z.writestr("plugin.yml", yaml)

    def write_source(self, name, content):
        path = self.src / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def test_missing_opt_in_reports_blockers_without_claiming_runtime_readiness(self):
        result = audit.inspect(self.root)
        self.assertFalse(result["folia_metadata_opt_in"])
        self.assertFalse(result["folia_load_admission"])
        self.assertEqual("NO_GO", result["release_status"])
        self.assertFalse(result["folia_runtime_verified"])
        self.assertEqual(2, len(result["known_source_blockers"]))
        self.assertEqual({"legacy_bukkit_scheduler", "synchronous_staff_state_teleport"},
                         {v["id"] for v in result["known_source_blockers"]})

    def test_true_marker_cannot_erase_known_threading_blockers(self):
        self.write_metadata("name: EnthusiaStaff\nfolia-supported: true\n")
        result = audit.inspect(self.root)
        self.assertTrue(result["folia_metadata_opt_in"])
        self.assertTrue(result["unsafe_opt_in"])
        self.assertEqual("NO_GO", result["release_status"])
        self.assertFalse(result["folia_load_admission"])

    def test_folia_marker_alone_never_certifies_live_runtime_even_if_sources_clean(self):
        self.write_metadata("folia-supported: true\n")
        self.write_source("integration/RoseChatInboundBridgeIntegration.java", "safe global schedule")
        self.write_source("staff/StaffStateCodec.java", "player.teleportAsync(destination)")
        result = audit.inspect(self.root)
        self.assertTrue(result["folia_load_admission"])
        self.assertFalse(result["folia_runtime_verified"])
        self.assertEqual("NO_GO", result["release_status"])

    def test_duplicate_or_invalid_marker_fails_closed(self):
        for yaml in ("folia-supported: true\nfolia-supported: false\n",
                     "folia-supported: maybe\n"):
            self.write_metadata(yaml)
            with self.assertRaises(audit.AdmissionError):
                audit.inspect(self.root)

    def test_missing_plugin_metadata_fails_closed(self):
        with zipfile.ZipFile(self.jar, "w") as z:
            z.writestr("config.yml", "guided-punishment:\n  enabled: false")
        with self.assertRaises(audit.AdmissionError):
            audit.inspect(self.root)


if __name__ == "__main__":
    unittest.main()
