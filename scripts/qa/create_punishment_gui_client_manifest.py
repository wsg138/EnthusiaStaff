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

# Derived from docs/implementation-plans/staff-punishment-gui-ux-acceptance-20261015.md.
# Descriptions are prompts only: they do NOT count as evidence or test results.
SCENARIO_CHECKS = {
    "A01": "/punish without target: picker, heads select target, Refresh/Close, pagination",
    "A02": "Guided OFF: category grid, distinct materials, bold headings, exact-reason browsing",
    "A03": "Guided ON: six categories open interviews; Search still reaches exact reasons",
    "A04": "First guided question: readable prompt/options; Back to categories only before answers",
    "A05": "After first answer: Back to first question; changing answer clears dependent answers",
    "A06": "Remaining guided branches: relevant steps and warning outcomes; no mandatory evidence",
    "A07": "Supported result: read-only incident and factors; selectable strength, Calculate opens review",
    "A08": "Unsupported/sensitive result: review-only, no Calculate; Expert only when authorized",
    "A09": "Expert fallback: authorized filtered reasons, or visibly unavailable",
    "A10": "Search/browser: case insensitive, cancel, paging, select, Back/Close, inert decoration",
    "A11": "Normal review: distinguish proposed vs applied, show exact type/duration and authority",
    "A12": "Adjusted review: requires rationale before confirmation; blocked gray submission otherwise",
    "A13": "Back/resume: session answers preserved; restart falls back to exact reasons; no stale action",
    "A14": "Confirmation/approval: recheck, rank gates, pending approval, deny unauthorized actor",
    "A15": "Approval queue: pagination/Refresh, readable frozen proposal and rank",
    "A16": "Approval review/denial: valid claim, exact sanction, denial presets/custom audit",
    "A17": "History: complete/empty/unavailable modes, pagination, private visibility",
    "A18": "Close/reopen/quit: draft resumable; no stale or cross-target session",
    "A19": "Bedrock touch: single tap, lore/scroll/colors/nonitalic bold, 54-slot layout",
    "A20": "Policy v2 shadow path: distinct category/offense/questions/gap/review/shadow controls",
}


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
                "check": SCENARIO_CHECKS[scenario],
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
