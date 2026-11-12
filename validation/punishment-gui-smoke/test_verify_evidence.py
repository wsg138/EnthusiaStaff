"""Regression tests: no failed, mistargeted or out-of-order smoke may pass."""
from __future__ import annotations

import unittest

import verify_evidence as gate

TARGET_UUID = "1f4333cd-0425-35ab-b679-0cc1ba8393a3"
CATEGORY_LINE = ("INVENTORY_OPEN|title=Punish · " + TARGET_UUID
                 + "|size=54|holder=PunishmentGuiHolder")
CATEGORY_PACKET = ("GUI_PACKET_OPEN|inventoryStart=54|slotCount=90|title=Punish · "
                   + TARGET_UUID)


def valid_server() -> str:
    stages: list[str] = list(gate.SERVER_STAGES)
    stages.insert(stages.index("REFRESH_CLICK_OBSERVED=true"), "PICKER_OPEN=true")
    stages.insert(stages.index("PICKER_REFRESH_OPEN=true"), "PICKER_OPEN=true")
    stages.insert(stages.index("TARGET_HEAD_CLICK_OBSERVED=true"), "PICKER_OPEN=true")
    stages.insert(stages.index("TARGET_PICKER_REOPEN_HANDLED=true"),
                  "DISPOSABLE_TARGET_UUID=" + TARGET_UUID)
    stages.append(CATEGORY_LINE)
    return "\n".join(stages)


def valid_client() -> str:
    return "\n".join((*gate.CLIENT_STAGES, CATEGORY_PACKET))


class SmokeEvidenceTests(unittest.TestCase):
    def test_complete_uuid_fallback_is_valid_in_disposable_paper(self):
        gate.validate(valid_server(), valid_client())

    def test_name_is_valid_if_proxy_directory_supplies_it(self):
        gate.validate(valid_server().replace("Punish · " + TARGET_UUID,
                                            "Punish · GuiTarget"),
                      valid_client().replace("Punish · " + TARGET_UUID,
                                             "Punish · GuiTarget"))

    def test_server_fail_marker_overrides_all_positive_markers(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "FAIL marker"):
            gate.validate(valid_server() + "\nFAIL=expected_target_picker_not_open",
                          valid_client())

    def test_client_fail_marker_overrides_all_positive_markers(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "FAIL marker"):
            gate.validate(valid_server(), valid_client() + "\nGUI_PACKET_FAIL=timeout")

    def test_client_kick_is_never_acceptance(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "error or kick"):
            gate.validate(valid_server(), valid_client() + "\nGUI_CLIENT_KICKED=denied")

    def test_missing_disposable_target_category_fails(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "category title"):
            gate.validate(valid_server().replace(CATEGORY_LINE,
                          "INVENTORY_OPEN|title=Unrelated|size=54|holder=PunishmentGuiHolder"),
                          valid_client())

    def test_missing_client_category_packet_fails(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "category window"):
            gate.validate(valid_server(), valid_client().replace(CATEGORY_PACKET,
                          "GUI_PACKET_OPEN|inventoryStart=27|slotCount=63|title=Unrelated"))

    def test_wrong_uuid_is_not_accepted_even_if_both_logs_agree(self):
        wrong = "22222222-2222-4222-a222-222222222222"
        with self.assertRaisesRegex(gate.EvidenceFailure, "category title"):
            gate.validate(valid_server().replace("Punish · " + TARGET_UUID,
                                                 "Punish · " + wrong),
                          valid_client().replace("Punish · " + TARGET_UUID,
                                                 "Punish · " + wrong))

    def test_missing_authoritative_uuid_rejected(self):
        with self.assertRaisesRegex(gate.EvidenceFailure, "authoritative disposable target UUID"):
            gate.validate(valid_server().replace(
                "DISPOSABLE_TARGET_UUID=" + TARGET_UUID + "\n", ""),
                valid_client())

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
