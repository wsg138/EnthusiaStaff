#!/usr/bin/env python3
"""Fail-closed manifest gate for real Java/Bedrock punishment GUI staging evidence.

This audits evidence *records*, not the authenticity of client sessions.
Missing recordings, untested rows, and fake/synthetic-client declarations
can never produce an acceptance PASS. No network or server access.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import sys

SCENARIOS = tuple(f"A{i:02d}" for i in range(1, 21))
CLIENTS = ("java", "bedrock")
EXPECTED = frozenset(
    (scenario, client)
    for scenario in SCENARIOS
    for client in CLIENTS
    if not (scenario == "A19" and client == "java")
)
MEDIA_EXTENSIONS = frozenset((".png", ".jpg", ".jpeg", ".webp", ".mp4", ".webm"))
DIGEST = re.compile(r"[a-f0-9]{64}\Z")
COMMIT = re.compile(r"[a-f0-9]{40}\Z")
STATUSES = frozenset(("PASS", "FAIL", "BLOCKED"))


class EvidenceInvalid(ValueError):
    """Malformed, unsafe or contradictory acceptance evidence."""


def check_digest(value: object, label: str) -> str:
    if not isinstance(value, str) or not DIGEST.fullmatch(value):
        raise EvidenceInvalid(f"{label} must be a lowercase SHA-256 digest")
    return value


def required_text(value: object, label: str, length: int = 1) -> str:
    if not isinstance(value, str) or len(value.strip()) < length:
        raise EvidenceInvalid(f"{label} must be nonempty text (minimum {length} characters)")
    return value.strip()


def evidence_file(root: Path, relative: object, digest: str, extensions: frozenset[str]) -> None:
    raw = required_text(relative, "media/artifact path")
    path = Path(raw)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise EvidenceInvalid("evidence paths must be relative and cannot traverse directories")
    if path.suffix.lower() not in extensions:
        raise EvidenceInvalid(f"unexpected evidence file extension: {raw}")
    selected = root / path
    for ancestor in (selected, *selected.parents):
        if ancestor == root.parent:
            break
        if ancestor.is_symlink():
            raise EvidenceInvalid(f"symlink evidence is not allowed: {raw}")
    if not selected.is_file():
        raise EvidenceInvalid(f"evidence is missing: {raw}")
    actual = hashlib.sha256(selected.read_bytes()).hexdigest()
    if actual != digest:
        raise EvidenceInvalid(f"evidence checksum mismatch: {raw}")
    if selected.stat().st_size < 1:
        raise EvidenceInvalid(f"evidence file is empty: {raw}")


def verify_header(manifest: dict, root: Path) -> None:
    commit = manifest.get("candidate_commit")
    if not isinstance(commit, str) or not COMMIT.fullmatch(commit):
        raise EvidenceInvalid("candidate_commit must be the exact lowercase 40-character Git SHA")
    artifact = manifest.get("artifact")
    if not isinstance(artifact, dict):
        raise EvidenceInvalid("artifact record missing")
    digest = check_digest(artifact.get("sha256"), "artifact.sha256")
    evidence_file(root, artifact.get("path"), digest, frozenset((".jar",)))

    server = manifest.get("server")
    if not isinstance(server, dict) or server.get("isolated_staging") is not True:
        raise EvidenceInvalid("only explicitly isolated staging evidence is admissible")
    if server.get("production") is not False:
        raise EvidenceInvalid("production must explicitly be false")
    required_text(server.get("paper_version"), "server.paper_version")

    clients = manifest.get("client_versions")
    if not isinstance(clients, dict):
        raise EvidenceInvalid("client_versions is required")
    for name in ("java", "bedrock", "geyser", "floodgate"):
        required_text(clients.get(name), f"client_versions.{name}")


def verify_case(record: dict, root: Path) -> tuple[tuple[str, str], str]:
    scenario, client = record.get("id"), record.get("client")
    key = (scenario, client)
    if key not in EXPECTED:
        raise EvidenceInvalid(f"unexpected or inapplicable scenario/client: {key}")
    status = record.get("status")
    if status not in STATUSES:
        raise EvidenceInvalid(f"{key}: status must be PASS, FAIL, or BLOCKED")
    required_text(record.get("notes"), f"{key}.notes", 12)
    if status != "PASS":
        return key, status

    if record.get("actual_client_attested") is not True:
        raise EvidenceInvalid(f"{key}: PASS requires actual-client attestation")
    required_text(record.get("reviewer"), f"{key}.reviewer")
    instant = required_text(record.get("tested_at"), f"{key}.tested_at")
    try:
        parsed = datetime.fromisoformat(instant)
    except ValueError as exc:
        raise EvidenceInvalid(f"{key}: invalid tested_at ISO timestamp") from exc
    if parsed.tzinfo is None:
        raise EvidenceInvalid(f"{key}: tested_at must have a timezone")
    media_digest = check_digest(record.get("media_sha256"), f"{key}.media_sha256")
    evidence_file(root, record.get("media"), media_digest, MEDIA_EXTENSIONS)
    return key, status


def evaluate(manifest: dict, root: Path) -> tuple[int, list[str]]:
    if not isinstance(manifest, dict):
        raise EvidenceInvalid("manifest must be a JSON object")
    verify_header(manifest, root)
    cases = manifest.get("cases")
    if not isinstance(cases, list):
        raise EvidenceInvalid("cases must be a list")
    seen: dict[tuple[str, str], str] = {}
    for case in cases:
        if not isinstance(case, dict):
            raise EvidenceInvalid("every case must be a JSON object")
        key, status = verify_case(case, root)
        if key in seen:
            raise EvidenceInvalid(f"duplicate acceptance row: {key}")
        seen[key] = status
    missing = sorted(EXPECTED.difference(seen))
    if missing:
        raise EvidenceInvalid(f"missing {len(missing)} required acceptance rows: {missing}")
    incomplete = [f"{case}/{client}: {state}" for (case, client), state in
                  sorted(seen.items()) if state != "PASS"]
    return len(seen) - len(incomplete), incomplete


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="JSON session manifest beside artifact/media files")
    args = parser.parse_args(argv)
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        passed, incomplete = evaluate(manifest, args.manifest.parent.resolve())
    except (EvidenceInvalid, OSError, json.JSONDecodeError) as error:
        print(f"INVALID EVIDENCE: {error}", file=sys.stderr)
        return 2
    if incomplete:
        print(f"NO-GO: {passed}/{len(EXPECTED)} verified client rows")
        for detail in incomplete:
            print(f"  {detail}")
        return 1
    print(f"RECORDS COMPLETE: {passed}/{len(EXPECTED)} client rows have checked media and attestation.")
    print("IMPORTANT: Independent manual review must still verify the actual gameplay evidence.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
