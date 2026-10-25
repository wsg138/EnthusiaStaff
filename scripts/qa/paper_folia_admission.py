#!/usr/bin/env python3
"""Read-only Folia startup admission and known-source-blocker audit.

A successful audit is NOT proof of runtime compatibility. No plugin is loaded,
no config is changed, and release_status always remains NO_GO.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

JAR_PATH = Path("paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar")
SOURCE_ROOT = Path("paper/src/main/java")
FLAG = re.compile(r"^folia-supported\s*:\s*(.*?)\s*$")
LEGACY = re.compile(r"\.getScheduler\(\)\s*\.runTask(?:Later|Timer|Asynchronously|LaterAsynchronously|TimerAsynchronously)?\s*\(")
TELEPORT = re.compile(r"\bplayer\s*\.\s*teleport\s*\(")


class AdmissionError(ValueError):
    """Missing, malformed, or incomplete admission evidence."""


def metadata_opt_in(yaml: str) -> bool:
    flags = []
    for line in yaml.splitlines():
        if line.lstrip() != line or line.lstrip().startswith("#"):
            continue
        if line.startswith("folia-supported"):
            match = FLAG.fullmatch(line.split("#", 1)[0].rstrip())
            if match is None:
                raise AdmissionError("Invalid folia-supported declaration in packaged plugin.yml")
            flags.append(match.group(1).lower())
    if len(flags) > 1 or any(v not in ("true", "false") for v in flags):
        raise AdmissionError("Duplicate/invalid folia-supported metadata in packaged plugin.yml")
    return flags == ["true"]


def known_source_blockers(root: Path) -> list[dict]:
    source = root / SOURCE_ROOT
    if not source.is_dir():
        raise AdmissionError(f"Missing Paper Java source tree: {source}")
    files = sorted(source.rglob("*.java"))
    if not files:
        raise AdmissionError("Paper Java source tree is empty")
    blockers = []
    for path in files:
        lines = path.read_text(encoding="utf-8").splitlines()
        for line_no, line in enumerate(lines, 1):
            if LEGACY.search(line):
                blockers.append({
                    "id": "legacy_bukkit_scheduler",
                    "path": path.relative_to(root).as_posix(),
                    "line": line_no,
                    "reason": "Legacy Bukkit scheduler is not valid for Folia's regionized runtime",
                })
            if path.name == "StaffStateCodec.java" and TELEPORT.search(line):
                blockers.append({
                    "id": "synchronous_staff_state_teleport",
                    "path": path.relative_to(root).as_posix(),
                    "line": line_no,
                    "reason": "Synchronous staff-state teleport needs cross-region runtime review",
                })
    return blockers


def inspect(root: Path) -> dict:
    root = root.resolve()
    jar = root / JAR_PATH
    try:
        with zipfile.ZipFile(jar) as z:
            matches = [v for v in z.infolist() if v.filename == "plugin.yml"]
            if len(matches) != 1:
                raise AdmissionError("Packaged JAR must have exactly one plugin.yml")
            yaml = z.read(matches[0]).decode("utf-8")
    except (OSError, zipfile.BadZipFile, UnicodeError) as exc:
        raise AdmissionError(f"Packaged Paper plugin metadata unavailable: {exc}") from exc
    opted_in = metadata_opt_in(yaml)
    blockers = known_source_blockers(root)
    return {
        "packaged_plugin_yml": JAR_PATH.as_posix() + "!/plugin.yml",
        "folia_metadata_opt_in": opted_in,
        "known_source_blockers": blockers,
        "unsafe_opt_in": opted_in and bool(blockers),
        "folia_load_admission": opted_in and not blockers,
        "folia_runtime_verified": False,
        "release_status": "NO_GO",
        "not_proven": [
            "Exhaustive threading audit and all dependent plugins' Folia compatibility",
            "Folia actual plugin loading, runtime enable/disable and state reset",
            "Player/region/global scheduler semantics on an isolated Folia instance",
            "Cross-world teleport, player inventory and permission paths",
            "Java/Bedrock/Geyser GUI acceptance, rollback and owner policy signoff",
        ],
        "limits": "Known-source heuristic only. Zero findings does not prove thread safety. "
                  "Never set folia-supported solely to satisfy this report.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--require-folia-load-admission", action="store_true")
    args = parser.parse_args()
    try:
        report = inspect(args.root)
    except AdmissionError as exc:
        print(json.dumps({"release_status": "NO_GO", "error": str(exc)}, indent=2),
              file=sys.stderr)
        return 2
    body = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output is None:
        print(body, end="")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(body, encoding="utf-8")
        print("Folia admission report recorded; release remains NO_GO")
    return 1 if args.require_folia_load_admission and not report["folia_load_admission"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
