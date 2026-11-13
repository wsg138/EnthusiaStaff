#!/usr/bin/env python3
"""Fail-closed evaluator of disposable Paper punishment-GUI smoke evidence.

This is a *protocol smoke*, not any human Java/Bedrock A01–A20 acceptance
record. It never contacts a Minecraft server or mutates a file.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import uuid
import sys

SERVER_STAGES = (
    "HARNESS_READY=true",
    "CLIENT_JOIN=true",
    "LUCKPERMS_OWNER_IDENTITY_HANDLED=true",
    "OFF_DUTY_PICKER_BLOCKED=true",
    "STAFF_COMMAND_ACCEPTED=true",
    "PUNISH_COMMAND_ACCEPTED=true",
    "REFRESH_CLICK_OBSERVED=true",
    "PICKER_REFRESH_OPEN=true",
    "CLOSE_CLICK_OBSERVED=true",
    "PICKER_CLOSED_AFTER_CONTROL=true",
    "PICKER_CLOSED_AT_CHECKPOINT=true",
    "DEDICATED_TARGET_ONLINE=true",
    "TARGET_PICKER_REOPEN_HANDLED=true",
    "TARGET_HEAD_CLICK_OBSERVED=true",
    "TARGET_CATEGORIES_OPEN=true",
)
CLIENT_STAGES = (
    "GUI_DISPOSABLE_TARGET_SPAWN=true",
    "GUI_CLIENT_REFRESH_CLICK=true",
    "GUI_CLIENT_REFRESHED_PICKER=true",
    "GUI_CLIENT_CLOSE_CLICK=true",
    "GUI_CLIENT_REFRESH_AND_CLOSE=true",
    "GUI_CLIENT_TARGET_PICKER_REOPEN=true",
    "GUI_CLIENT_TARGET_HEAD_CLICK=true",
    "GUI_CLIENT_TARGET_CATEGORIES_OPEN=true",
    "GUI_CLIENT_END|completed=true",
)


class EvidenceFailure(ValueError):
    pass


def check_sequence(lines: list[str], stages: tuple[str, ...], label: str) -> None:
    previous = -1
    for stage in stages:
        positions = [i for i, line in enumerate(lines) if line == stage]
        if len(positions) != 1:
            raise EvidenceFailure(
                f"{label}: expected one {stage!r}; found {len(positions)}"
            )
        if positions[0] <= previous:
            raise EvidenceFailure(f"{label}: out-of-order stage {stage!r}")
        previous = positions[0]


def reject_failure_markers(server: list[str], client: list[str]) -> None:
    if any("FAIL=" in line for line in server + client):
        raise EvidenceFailure("explicit server or client FAIL marker found")
    if any("GUI_CLIENT_ERROR=" in line or "GUI_CLIENT_KICKED=" in line for line in client):
        raise EvidenceFailure("client error or kick found")


def authoritative_disposable_uuid(server: list[str]) -> str:
    target_ids = [line.removeprefix("DISPOSABLE_TARGET_UUID=")
                  for line in server if line.startswith("DISPOSABLE_TARGET_UUID=")]
    if len(target_ids) != 1:
        raise EvidenceFailure("expected exactly one authoritative disposable target UUID")
    try:
        target_id = str(uuid.UUID(target_ids[0]))
    except ValueError as exc:
        raise EvidenceFailure("invalid disposable target UUID") from exc
    if target_ids[0] != target_id:
        raise EvidenceFailure("noncanonical disposable target UUID")
    return target_id


def stage_position(lines: list[str], stage: str) -> int:
    # check_sequence already ensures the marker occurs exactly once.
    return lines.index(stage)


def actual_category_title(server: list[str], target_id: str) -> str:
    possible_titles = ("Punish · GuiTarget", "Punish · " + target_id)
    prefix = "INVENTORY_OPEN|title="
    suffix = "|size=54|holder=PunishmentGuiHolder"
    records = [
        (index, line[len(prefix):-len(suffix)])
        for index, line in enumerate(server)
        if line.startswith(prefix) and line.endswith(suffix)
    ]
    if len(records) != 1 or records[0][1] not in possible_titles:
        raise EvidenceFailure("real Paper category title does not match disposable target")
    position, title = records[0]
    if not (stage_position(server, "TARGET_HEAD_CLICK_OBSERVED=true")
            < position < stage_position(server, "TARGET_CATEGORIES_OPEN=true")):
        raise EvidenceFailure("Paper category inventory was outside target navigation sequence")
    return title


def normalized_client_title(raw: str) -> str:
    """Mineflayer may serialize Adventure titles as JSON, not plain strings."""
    if not raw.startswith("{"):
        return raw
    try:
        component = json.loads(raw)
    except (TypeError, ValueError) as exc:
        raise EvidenceFailure("malformed Java protocol window title JSON") from exc
    if not isinstance(component, dict) or not isinstance(component.get("text"), str):
        raise EvidenceFailure("unsupported Java protocol window title component")
    # A title with additional components may render extra text after .text.
    if component.get("extra") or component.get("translate"):
        raise EvidenceFailure("Java protocol title includes unsupported extra components")
    nested = component.get("json")
    if nested is not None:
        if (not isinstance(nested, dict)
                or nested.get("text") != component["text"]
                or nested.get("extra") or nested.get("translate")):
            raise EvidenceFailure("conflicting Java protocol window title components")
    return component["text"]


def verify_client_category_packet(client: list[str], expected_title: str) -> None:
    prefix = "GUI_PACKET_OPEN|inventoryStart=54|slotCount=90|title="
    records = [
        (index, line[len(prefix):])
        for index, line in enumerate(client)
        if line.startswith(prefix)
    ]
    # Only category packets are relevant: preserve the earlier target-picker
    # windows, but reject any ambiguous or mismatched category title.
    matching = [(index, normalized_client_title(raw))
                for index, raw in records
                if "Select Player" not in raw and "Punish · " in raw]
    if len(matching) != 1 or matching[0][1] != expected_title:
        raise EvidenceFailure("Java protocol category window does not match Paper title")
    position = matching[0][0]
    if not (stage_position(client, "GUI_CLIENT_TARGET_HEAD_CLICK=true")
            < position < stage_position(client, "GUI_CLIENT_TARGET_CATEGORIES_OPEN=true")):
        raise EvidenceFailure("Java protocol category packet was outside target navigation sequence")


def validate(server_text: str, client_text: str) -> None:
    server = server_text.splitlines()
    client = client_text.splitlines()
    reject_failure_markers(server, client)
    check_sequence(server, SERVER_STAGES, "Paper")
    check_sequence(client, CLIENT_STAGES, "Java protocol")
    if server.count("PICKER_OPEN=true") != 3:
        raise EvidenceFailure("expected exactly three Paper target picker openings")
    target_id = authoritative_disposable_uuid(server)
    title = actual_category_title(server, target_id)
    verify_client_category_packet(client, title)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paper_proof", type=Path)
    parser.add_argument("client_log", type=Path)
    args = parser.parse_args(argv)
    try:
        validate(args.paper_proof.read_text(encoding="utf-8"),
                 args.client_log.read_text(encoding="utf-8"))
    except (EvidenceFailure, OSError) as exc:
        print(f"GUI_SMOKE_NO_GO: {exc}", file=sys.stderr)
        return 1
    print("GUI_SMOKE_EVIDENCE_VERIFIED=true")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
