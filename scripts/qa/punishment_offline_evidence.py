#!/usr/bin/env python3
"""Read-only offline evidence for Enthusia punishment draft candidates.

Does not approve deployment or infer MariaDB, Paper/Folia or Bedrock acceptance.
Uses only Python's standard library. Optional --output writes a local report.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from xml.parsers import expat
import zipfile
from datetime import datetime, timezone
from pathlib import Path

MODULES = ("domain", "paper", "staff-bot", "velocity")
MAX_JUNIT_REPORT_BYTES = 16 * 1024 * 1024
MAX_XML_DEPTH = 256
JAR = Path("paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar")
NOT_PROVEN = (
    "MariaDB-backed integration execution",
    "Linux / Paper / Folia entity-thread runtime acceptance",
    "Java and Bedrock / Geyser client GUI acceptance",
    "Live staff permission and protected-target rank-change acceptance",
    "Customized production reason-policies.yml comparison",
    "Owner severity calibration approval (issue #616)",
    "Isolated rollback and recovery rehearsal",
)


class EvidenceError(ValueError):
    """Input is missing, unreadable or ambiguous: evidence cannot be trusted."""


def _suite_counts(path: Path) -> dict[str, int]:
    """Read bounded JUnit attributes without DTDs, external entities or expansion."""
    try:
        if path.stat().st_size > MAX_JUNIT_REPORT_BYTES:
            raise EvidenceError(f"JUnit report exceeds safe size bound: {path}")
        payload = path.read_bytes()
        if len(payload) > MAX_JUNIT_REPORT_BYTES:
            raise EvidenceError(f"JUnit report changed beyond safe size bound: {path}")

        parser = expat.ParserCreate()
        parser.SetParamEntityParsing(expat.XML_PARAM_ENTITY_PARSING_NEVER)
        root_attributes: dict[str, str] | None = None
        depth = 0

        def no_dtd(*_args: object) -> None:
            raise EvidenceError(f"DOCTYPE and entity declarations are forbidden in JUnit reports: {path}")

        def no_external_entity(*_args: object) -> int:
            raise EvidenceError(f"External XML entities are forbidden in JUnit reports: {path}")

        def on_start(name: str, attributes: dict[str, str]) -> None:
            nonlocal root_attributes, depth
            if depth == 0:
                if name != "testsuite":
                    raise EvidenceError(f"Unexpected JUnit root element in {path}")
                root_attributes = attributes
            depth += 1
            if depth > MAX_XML_DEPTH:
                raise EvidenceError(f"JUnit XML nesting exceeds safe depth bound: {path}")

        def on_end(_name: str) -> None:
            nonlocal depth
            depth -= 1

        parser.StartDoctypeDeclHandler = no_dtd
        parser.EntityDeclHandler = no_dtd
        parser.ExternalEntityRefHandler = no_external_entity
        parser.StartElementHandler = on_start
        parser.EndElementHandler = on_end
        parser.Parse(payload, True)
        if root_attributes is None:
            raise EvidenceError(f"Missing JUnit testsuite root in {path}")
        counts = {key: int(root_attributes.get(key, "0")) for key in
                  ("tests", "skipped", "failures", "errors")}
    except (expat.ExpatError, OSError, ValueError) as exc:
        raise EvidenceError(f"Cannot read JUnit report {path}: {exc}") from exc
    if any(v < 0 for v in counts.values()) or (
            counts["skipped"] + counts["failures"] + counts["errors"] > counts["tests"]):
        raise EvidenceError(f"Invalid JUnit totals in {path}: {counts}")
    return counts


def module_results(root: Path) -> tuple[dict, dict]:
    modules = {}
    for module in MODULES:
        folder = root / module / "build/test-results/test"
        reports = sorted(folder.glob("TEST-*.xml"))
        if not reports:
            raise EvidenceError(f"No JUnit TEST-*.xml for {module} at {folder}")
        counts = {key: 0 for key in ("tests", "skipped", "failures", "errors")}
        for report in reports:
            for key, value in _suite_counts(report).items():
                counts[key] += value
        counts["passed"] = counts["tests"] - counts["skipped"] - counts["failures"] - counts["errors"]
        counts["failed"] = counts["failures"] + counts["errors"]
        counts["reports"] = len(reports)
        modules[module] = counts

    totals = {key: sum(v[key] for v in modules.values())
              for key in ("tests", "passed", "skipped", "failures", "errors", "failed", "reports")}
    return modules, totals


def _guided_default_enabled(yaml: str) -> bool:
    """Fail closed on missing, multiple or non-boolean enabled declarations."""
    in_block = False
    candidates: list[str] = []
    for line in yaml.splitlines():
        raw = line.split("#", 1)[0].rstrip()
        if not raw.strip():
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        if "\t" in raw[:indent]:
            raise EvidenceError("Tab indentation in bundled guided configuration")
        if indent == 0:
            in_block = raw == "guided-punishment:"
            continue
        if in_block and indent == 2 and raw.strip().startswith("enabled:"):
            candidates.append(raw.strip().split(":", 1)[1].strip().strip("\"'").lower())

    if len(candidates) != 1 or candidates[0] not in ("true", "false"):
        raise EvidenceError("Bundled guided-punishment.enabled is missing, ambiguous or invalid")
    return candidates[0] == "true"


def jar_evidence(root: Path) -> dict:
    path = root / JAR
    try:
        data = path.read_bytes()
        with zipfile.ZipFile(path) as jar:
            yaml = jar.read("config.yml").decode("utf-8")
    except (OSError, KeyError, UnicodeError, zipfile.BadZipFile) as exc:
        raise EvidenceError(f"Missing/invalid packaged Paper JAR config at {path}: {exc}") from exc

    return {
        "path": JAR.as_posix(),
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "guided_default_enabled": _guided_default_enabled(yaml),
    }


def _git_head(root: Path) -> str | None:
    try:
        proc = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True,
                              text=True, check=False, timeout=10)
        sha = proc.stdout.strip()
        return sha if proc.returncode == 0 and len(sha) == 40 else None
    except (OSError, subprocess.SubprocessError):
        return None


def _git_worktree_clean(root: Path) -> bool | None:
    try:
        proc = subprocess.run(["git", "status", "--porcelain"], cwd=root,
                              capture_output=True, text=True, check=False, timeout=10)
        return not bool(proc.stdout.strip()) if proc.returncode == 0 else None
    except (OSError, subprocess.SubprocessError):
        return None


def build_record(root: Path) -> dict:
    root = root.resolve()
    modules, totals = module_results(root)
    jar = jar_evidence(root)
    return {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "source_commit": _git_head(root),
        "source_worktree_clean": _git_worktree_clean(root),
        "modules": modules,
        "totals": totals,
        "jar": jar,
        "offline_checks_pass": totals["failed"] == 0 and not jar["guided_default_enabled"],
        "release_status": "NO_GO",
        "not_proven": list(NOT_PROVEN),
        "limitations": "Offline JUnit XML and JAR inspection only. Exact-commit evidence "
                       "requires a clean git worktree and a fresh --rerun-tasks build. "
                       "Integration test execution, hosted CI, production policy and runtime "
                       "acceptance are not established. Never deployment authorization.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(),
                        help="Checkout root containing build/test-results and shaded Paper JAR")
    parser.add_argument("--output", type=Path, default=None,
                        help="Optional JSON destination; omitted means print only")
    args = parser.parse_args()
    try:
        record = build_record(args.root)
    except EvidenceError as exc:
        print(json.dumps({"offline_checks_pass": False, "release_status": "NO_GO",
                          "error": str(exc)}, indent=2), file=sys.stderr)
        return 2
    payload = json.dumps(record, indent=2, sort_keys=True) + "\n"
    if args.output is None:
        print(payload, end="")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
        print(f"Offline evidence written to {args.output}; release remains NO_GO")
    return 0 if record["offline_checks_pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
