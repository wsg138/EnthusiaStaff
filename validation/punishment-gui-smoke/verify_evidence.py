#!/usr/bin/env python3
"""Fail-closed evaluator of disposable Paper punishment-GUI smoke evidence.

This is a *protocol smoke*, not any human Java/Bedrock A01–A20 acceptance
record. It never contacts a Minecraft server or mutates a file.
"""
from __future__ import annotations

import argparse
from pathlib import Path
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


def validate(server_text: str, client_text: str) -> None:
    server = server_text.splitlines()
    client = client_text.splitlines()
    if any("FAIL=" in line for line in server + client):
        raise EvidenceFailure("explicit server or client FAIL marker found")
    if any("GUI_CLIENT_ERROR=" in line or "GUI_CLIENT_KICKED=" in line for line in client):
        raise EvidenceFailure("client error or kick found")

    check_sequence(server, SERVER_STAGES, "Paper")
    check_sequence(client, CLIENT_STAGES, "Java protocol")
    if server.count("PICKER_OPEN=true") != 3:
        raise EvidenceFailure("expected exactly three Paper target picker openings")
    if not any(
        line.startswith("INVENTORY_OPEN|title=Punish · GuiTarget|size=54|holder=PunishmentGuiHolder")
        for line in server
    ):
        raise EvidenceFailure("real Paper disposable target category inventory not proven")
    if not any(
        "GUI_PACKET_OPEN|inventoryStart=54|" in line
        and "Punish · GuiTarget" in line
        for line in client
    ):
        raise EvidenceFailure("Java protocol category window not proven")


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
