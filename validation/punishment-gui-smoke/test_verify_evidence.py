"""Regression tests: even one bad marker must never be reported as a pass."""
from __future__ import annotations

import unittest

import verify_evidence as gate


def valid_server() -> str:
    stages = list(gate.SERVER_STAGES)
    stages.insert(stages.index("REFRESH_CLICK_OBSERVED=true"), "PICKER_OPEN=true")
    stages.insert(stages.index("PICKER_REFRESH_OPEN=true"), "PICKER_OPEN=true")
    stages.insert(stages.index("TARGET_HEAD_CLICK_OBSERVED=true"), "PICKER_OPEN=true")
    stages.append("INVENTORY_OPEN|title=Punish · GuiTarget|size=54|holder=PunishmentGuiHolder")
    return "\n".join(stages)


def valid_client() -> str:
    stages = list(gate.CLIENT_STAGES)
    stages.append(
        "GUI_PACKET_OPEN|inventoryStart=54|slotCount=90|title=Punish · GuiTarget"
    )
    return "\n".join(stages)


class SmokeEvidenceTests(unittest.TestCase):
    def test_complete_disposable_protocol_fixture(self):
        gate.validate(valid_server(), valid_client())

    def test_server_fail_marker_must_override_every_positive_marker(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "FAIL marker"):
            gate.validate(valid_server() + "\nFAIL=expected_target_picker_not_open",
                          valid_client())

    def test_client_fail_marker_must_override_every_positive_marker(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "FAIL marker"):
            gate.validate(valid_server(), valid_client() + "\nGUI_PACKET_FAIL=timeout")

    def test_client_kick_is_never_acceptance(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "error or kick"):
            gate.validate(valid_server(), valid_client() + "\nGUI_CLIENT_KICKED=denied")

    def test_missing_disposable_target_category_fails(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "category inventory"):
            gate.validate(valid_server().replace(
                "INVENTORY_OPEN|title=Punish · GuiTarget|size=54|holder=PunishmentGuiHolder",
                "INVENTORY_OPEN|title=Unrelated|size=54|holder=PunishmentGuiHolder"),
                valid_client())

    def test_missing_client_category_packet_fails(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "category window"):
            gate.validate(valid_server(), valid_client().replace(
                "GUI_PACKET_OPEN|inventoryStart=54|slotCount=90|title=Punish · GuiTarget",
                "GUI_PACKET_OPEN|inventoryStart=27|slotCount=63|title=Unrelated"))

    def test_out_of_order_refresh_cannot_be_accepted(self):
        lines = valid_server().splitlines()
        a = lines.index("REFRESH_CLICK_OBSERVED=true")
        b = lines.index("PICKER_REFRESH_OPEN=true")
        lines[a], lines[b] = lines[b], lines[a]
        with self.assertRaisesRegex(gate.EvidenceFailure, "out-of-order"):
            gate.validate("\n".join(lines), valid_client())

    def test_extra_picker_opening_fails(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "exactly three"):
            gate.validate(valid_server() + "\nPICKER_OPEN=true", valid_client())

    def test_duplicate_success_marker_fails(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "expected one"):
            gate.validate(valid_server() + "\nTARGET_CATEGORIES_OPEN=true", valid_client())

    def test_missing_close_checkpoint_fails(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "expected one"):
            gate.validate(valid_server().replace(
                "PICKER_CLOSED_AT_CHECKPOINT=true\n", ""),
                valid_client())


if __name__ == "__main__":
    unittest.main()
