#!/usr/bin/env python3
"""Create an intentionally incomplete, fail-closed 39-row client evidence manifest.

No server, network, gameplay or JAR access. Template placeholders and BLOCKED
rows must be replaced with facts collected on authorized isolated staging.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from scripts.qa.punishment_gui_client_evidence_gate import EXPECTED


def make_template() -> dict:
    return {
        "candidate_commit": "",
        "artifact": {"path": "", "sha256": ""},
        "server": {
            "isolated_staging": False,
            "production": False,
            "paper_version": "",
        },
        "client_versions": {
            "java": "",
            "bedrock": "",
            "geyser": "",
            "floodgate": "",
        },
        "cases": [
            {
                "id": scenario,
                "client": client,
                "status": "BLOCKED",
                "notes": "Not tested using a real client in isolated staging.",
            }
            for scenario, client in sorted(EXPECTED)
        ],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="new manifest path (existing files not overwritten)")
    args = parser.parse_args(argv)
    manifest = json.dumps(make_template(), indent=2) + "\n"
    try:
        with args.output.open("x", encoding="utf-8") as file:
            file.write(manifest)
    except OSError as exc:
        parser.error(f"cannot create template: {exc}")
    print(f"Created {len(EXPECTED)} BLOCKED client rows at {args.output}. No checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
