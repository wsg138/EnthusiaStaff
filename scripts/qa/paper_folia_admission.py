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

PAPER_JAR_DIR = Path("paper/build/libs")
PAPER_JAR_GLOB = "EnthusiaStaff-Paper-*.jar"


def packaged_jar(root: Path) -> Path:
    """Fail closed if the build produced zero or multiple runtime Paper JARs."""
    folder = root / PAPER_JAR_DIR
    candidates = sorted(
        path for path in folder.glob(PAPER_JAR_GLOB)
        if path.is_file()
        and not path.name.endswith(("-sources.jar", "-javadoc.jar", "-plain.jar"))
    )
    if len(candidates) != 1:
        raise AdmissionError(
            f"Expected one deployable Paper JAR in {folder}, found {len(candidates)}"
        )
    return candidates[0].relative_to(root)


SOURCE_ROOT = Path("paper/src/main/java")
FLAG = re.compile(r"^folia-supported\s*:\s*(.*?)\s*$")
LEGACY = re.compile(r"\.getScheduler\(\)\s*\.runTask(?:Later|Timer|Asynchronously|LaterAsynchronously|TimerAsynchronously)?\s*\(")
TELEPORT = re.compile(r"\bplayer\s*\.\s*teleport\s*\(")
QUIT_HANDLER = re.compile(
    r"public\s+void\s+onQuit\s*\(\s*(?:final\s+)?PlayerQuitEvent\s+"
    r"(?P<event_var>[A-Za-z_$][A-Za-z0-9_$]*)\s*\)\s*\{"
)
QUIT_RESTORE = re.compile(r"\brestoreSavedState\s*\(")
def quit_teleport_pattern(event_var: str) -> re.Pattern[str]:
    """Match a direct player or actual quit-event parameter teleport call."""
    owner = re.escape(event_var) + r"\s*\.\s*getPlayer\s*\(\s*\)"
    return re.compile(r"\b(?:player|" + owner + r")\s*\.\s*teleport(?:Async)?\s*\(")



class AdmissionError(ValueError):
    """Missing, malformed, or incomplete admission evidence."""


def _top_level_folia_flag(line: str) -> str | None:
    """Ignore nested/comment metadata but reject malformed top-level opt-in."""
    if line.lstrip() != line or line.lstrip().startswith("#"):
        return None
    if not line.startswith("folia-supported"):
        return None
    match = FLAG.fullmatch(line.split("#", 1)[0].rstrip())
    if match is None:
        raise AdmissionError("Invalid folia-supported declaration in packaged plugin.yml")
    return match.group(1).lower()


def metadata_opt_in(yaml: str) -> bool:
    flags = [
        value for line in yaml.splitlines()
        if (value := _top_level_folia_flag(line)) is not None
    ]
    if len(flags) > 1 or any(value not in ("true", "false") for value in flags):
        raise AdmissionError("Duplicate/invalid folia-supported metadata in packaged plugin.yml")
    return flags == ["true"]


def _source_matches(
    content: str, path: Path, root: Path, pattern: re.Pattern[str],
    identifier: str, reason: str, start: int = 0, end: int | None = None,
) -> list[dict]:
    """Emit path/line evidence without executing code or player operations."""
    stop = len(content) if end is None else end
    return [
        {
            "id": identifier,
            "path": path.relative_to(root).as_posix(),
            "line": content.count("\n", 0, match.start()) + 1,
            "reason": reason,
        }
        for match in pattern.finditer(content, start, stop)
    ]


def _quit_handler_bounds(content: str, handler: re.Match[str]) -> tuple[int, int]:
    opened = content.index("{", handler.start(), handler.end())
    depth = 1
    end = opened + 1
    while end < len(content) and depth > 0:
        if content[end] == "{":
            depth += 1
        elif content[end] == "}":
            depth -= 1
        end += 1
    if depth != 0:
        raise AdmissionError("Unbalanced StaffModeManager.onQuit handler")
    return opened + 1, end - 1


def _quit_source_blockers(content: str, path: Path, root: Path) -> list[dict]:
    blockers = []
    for handler in QUIT_HANDLER.finditer(content):
        start, end = _quit_handler_bounds(content, handler)
        blockers.extend(_source_matches(
            content, path, root, QUIT_RESTORE,
            "quit_event_synchronous_staff_restore",
            "Quit-event state restore assumes immediate completion before player save",
            start, end,
        ))
        # Both synchronous and asynchronous teleport during quit are unsafe.
        blockers.extend(_source_matches(
            content, path, root, quit_teleport_pattern(handler.group("event_var")),
            "quit_event_teleport",
            "Player teleport during quit is unsafe; restore prior to disconnect",
            start, end,
        ))
    return blockers


def known_source_blockers(root: Path) -> list[dict]:
    source = root / SOURCE_ROOT
    if not source.is_dir():
        raise AdmissionError(f"Missing Paper Java source tree: {source}")
    files = sorted(source.rglob("*.java"))
    if not files:
        raise AdmissionError("Paper Java source tree is empty")
    blockers = []
    for path in files:
        content = path.read_text(encoding="utf-8")
        blockers.extend(_source_matches(
            content, path, root, LEGACY, "legacy_bukkit_scheduler",
            "Legacy Bukkit scheduler is not valid for Folia's regionized runtime",
        ))
        if path.name == "StaffStateCodec.java":
            blockers.extend(_source_matches(
                content, path, root, TELEPORT, "synchronous_staff_state_teleport",
                "Synchronous staff-state teleport needs cross-region runtime review",
            ))
        if path.name == "StaffModeManager.java":
            blockers.extend(_quit_source_blockers(content, path, root))
    return blockers


def inspect(root: Path) -> dict:
    root = root.resolve()
    jar_relative = packaged_jar(root)
    jar = root / jar_relative
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
        "packaged_plugin_yml": jar_relative.as_posix() + "!/plugin.yml",
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
