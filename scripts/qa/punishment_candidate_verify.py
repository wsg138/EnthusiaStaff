#!/usr/bin/env python3
"""Execute a fresh, offline punishment build and bind its evidence to a clean source commit.

Local build artifacts are written by Gradle; no servers, databases or GitHub APIs
are contacted by this verifier. All output is NO_GO for release.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from punishment_offline_evidence import (
    EvidenceError,
    JAR,
    MODULES,
    build_record,
    _git_head,
    _git_worktree_clean,
)

GRADLE_TASKS = (
    ":domain:test",
    ":paper:test",
    ":staff-bot:test",
    ":velocity:test",
    ":integration-tests:compileTestJava",
    ":paper:shadowJar",
)
CLOCK_GRACE_SECONDS = 1.0


def gradle_command(root: Path) -> list[str]:
    """Use only the local, non-symlink Gradle wrapper and fixed offline tasks."""
    executable = root / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    if not executable.is_file() or executable.is_symlink():
        raise EvidenceError("Missing or symlinked Gradle wrapper; refusing candidate execution")
    return [str(executable), "--offline", "--no-daemon", "--rerun-tasks", *GRADLE_TASKS]


def assert_exact_source_identity(root: Path) -> str:
    sha = _git_head(root)
    if sha is None or _git_worktree_clean(root) is not True:
        raise EvidenceError(
            "Exact source provenance unavailable: require a clean Git worktree "
            "and a resolvable 40-character HEAD before building."
        )
    return sha


def verify_artifact_freshness(root: Path, build_started_epoch: float) -> dict:
    """Reject any old/absent JUnit report or shaded JAR, even if its contents pass."""
    artifacts: list[Path] = []
    for module in MODULES:
        report_root = root / module / "build/test-results/test"
        reports = sorted(report_root.glob("TEST-*.xml"))
        if not reports:
            raise EvidenceError(f"Missing JUnit results for {module} after build")
        artifacts.extend(reports)

    artifacts.append(root / JAR)
    threshold = build_started_epoch - CLOCK_GRACE_SECONDS
    for path in artifacts:
        try:
            timestamp = path.stat().st_mtime
        except OSError as exc:
            raise EvidenceError(f"Missing output from candidate build: {path}") from exc
        if timestamp < threshold:
            raise EvidenceError(
                f"Stale output predates verified build invocation: {path} "
                f"(mtime {timestamp:.3f}, build start {build_started_epoch:.3f})"
            )
    return {
        "fresh_for_invocation": True,
        "checked_files": len(artifacts),
        "build_started_utc": datetime.fromtimestamp(
            build_started_epoch, tz=timezone.utc
        ).isoformat(),
    }


def build_and_verify(root: Path, *, timeout_seconds: int = 1200) -> dict:
    """Run exactly six approved Gradle targets, then validate outputs and source."""
    root = root.resolve()
    source_sha = assert_exact_source_identity(root)
    command = gradle_command(root)
    started = time.time()
    try:
        # Intentional offline build execution, not a shell command: fixed
        # Gradle tasks, shell=False and a non-symlink wrapper from a clean tree.
        # TRUST BOUNDARY: only run this tool against source the operator trusts.
        # Security audit exception is scoped to this explicit build-runner call.
        result = subprocess.run(command, cwd=root, check=False, shell=False, timeout=timeout_seconds)  # nosec B603  # nosemgrep
    except (OSError, subprocess.SubprocessError) as exc:
        raise EvidenceError(f"Offline Gradle candidate build unavailable: {exc}") from exc
    if result.returncode != 0:
        raise EvidenceError(f"Offline Gradle candidate build failed (exit={result.returncode})")

    record = build_record(root)
    proof = verify_artifact_freshness(root, started)
    if (
        record["source_commit"] != source_sha
        or record["source_worktree_clean"] is not True
    ):
        raise EvidenceError("Source HEAD or working tree changed during offline build")
    record["offline_checks_pass"] = bool(record["offline_checks_pass"])
    record["build_provenance"] = {
        "exact_source_commit": source_sha,
        "tasks": list(GRADLE_TASKS),
        "gradle_flags": ["--offline", "--no-daemon", "--rerun-tasks"],
        **proof,
    }
    record["release_status"] = "NO_GO"
    return record


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(),
                        help="Clean source checkout (build writes only local outputs)")
    parser.add_argument("--output", type=Path, default=None,
                        help="Optional local JSON report destination")
    parser.add_argument("--timeout-seconds", type=int, default=1200)
    args = parser.parse_args()
    try:
        record = build_and_verify(args.root, timeout_seconds=args.timeout_seconds)
    except EvidenceError as exc:
        print(json.dumps({"offline_checks_pass": False, "release_status": "NO_GO",
                          "error": str(exc)}, indent=2), file=sys.stderr)
        return 2
    serialized = json.dumps(record, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding="utf-8")
        print(f"Local candidate evidence: {args.output} (release NO_GO)")
    else:
        print(serialized, end="")
    return 0 if record["offline_checks_pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
