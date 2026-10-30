#!/usr/bin/env python3
"""Safely migrate one installed EnthusiaStaff policy to the owner-approved ban-evasion rule.

Dry-run by default. Does not connect to a server, reload the plugin, or apply sanctions.
Python 3.10+; standard library only.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import stat
import sys
import tempfile

LEGACY_VERSION = b'version: "2026-09-26.1"\n'
CANDIDATE_VERSION = b'version: "2026-10-10.1"\n'
OLD_BLOCK = b'''  - id: evasion.ban
    family: evasion
    display-name: Ban evasion
    severity: 80
    ladder:
      - { label: "30 day ban", sanctions: [{ type: NETWORK_BAN, duration: 30d }] }
      - { label: "60 day ban", sanctions: [{ type: NETWORK_BAN, duration: 60d }] }
      - { label: "90 day ban", sanctions: [{ type: NETWORK_BAN, duration: 90d }] }
      - { label: "Permanent ban", sanctions: [{ type: NETWORK_BAN, duration: permanent }] }

'''
NEW_BLOCK = b'''  - id: evasion.ban
    family: evasion
    display-name: Ban evasion
    severity: 80
    ladder:
      - { label: "Permanent ban", sanctions: [{ type: NETWORK_BAN, duration: permanent }] }

'''
EXPECTED_REASONS = 85


class MigrationRejected(ValueError):
    """An unexpected input must never be written to the installed policy."""


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_candidate(candidate: bytes) -> None:
    """Require the exact approved candidate version, reason count and ban ladder."""
    if not candidate.startswith(CANDIDATE_VERSION):
        raise MigrationRejected("candidate version is not the pinned 2026-10-10.1")
    if candidate.count(NEW_BLOCK) != 1 or OLD_BLOCK in candidate:
        raise MigrationRejected("candidate ban-evasion ladder is not the approved permanent ban")
    if candidate.count(b"\n  - id: ") != EXPECTED_REASONS:
        raise MigrationRejected("candidate reason count differs from pinned 85")


def validate_legacy(installed: bytes) -> None:
    """Reject any source outside the reviewed 85-reason legacy structure."""
    if not installed.startswith(LEGACY_VERSION) or installed.count(LEGACY_VERSION) != 1:
        raise MigrationRejected("installed policy version is not the expected 2026-09-26.1")
    if installed.count(OLD_BLOCK) != 1 or NEW_BLOCK in installed:
        raise MigrationRejected("installed ban-evasion ladder does not match the legacy four steps")
    if installed.count(b"\n  - id: ") != EXPECTED_REASONS:
        raise MigrationRejected("installed policy does not contain exactly 85 reasons")


def prepare(installed: bytes, candidate: bytes) -> tuple[bytes, bool]:
    """Return (approved bytes, already_current); reject all unapproved differences."""
    validate_candidate(candidate)
    if installed == candidate:
        return installed, True
    validate_legacy(installed)
    proposed = installed.replace(LEGACY_VERSION, CANDIDATE_VERSION, 1).replace(
        OLD_BLOCK, NEW_BLOCK, 1
    )
    if proposed != candidate:
        raise MigrationRejected("other policy content differs; refusing to overwrite customization")
    return proposed, False


def safe_regular(path: Path) -> None:
    if not path.is_file() or path.is_symlink():
        raise MigrationRejected(f"policy path is missing, not a regular file, or a symlink: {path}")


def sync_directory(path: Path) -> None:
    """Persist file creation/replacement directory entries on POSIX systems."""
    if os.name == "nt":
        return  # Windows has different directory-handle and durability semantics.
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def require_same_owner(source: Path, generated: Path) -> None:
    """Never replace an installed policy with a file owned by another account."""
    if os.name == "nt":
        return  # Staging on Windows does not establish production ACL compatibility.
    original = source.stat()
    replacement = generated.stat()
    if (original.st_uid, original.st_gid) != (replacement.st_uid, replacement.st_gid):
        raise MigrationRejected(
            "owner/group mismatch; run the migration as the installed policy owner"
        )


def write_exclusive_backup(source: Path, backup: Path, original: bytes, mode: int) -> None:
    """Remove an incomplete *new* backup; never overwrite an existing one."""
    created = False
    try:
        with backup.open("xb") as output:
            created = True
            output.write(original)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(backup, mode)
        require_same_owner(source, backup)
        if backup.read_bytes() != original:
            raise MigrationRejected("backup verification failed before policy replacement")
        sync_directory(backup.parent)
    except (OSError, MigrationRejected):
        if created:
            backup.unlink(missing_ok=True)
        raise


def apply_migration(path: Path, expected_sha256: str, before: bytes, after: bytes) -> Path:
    """Require exact-byte compare-and-swap; keep an exclusive backup before atomic replace."""
    if not re.fullmatch(r"[a-fA-F0-9]{64}", expected_sha256):
        raise MigrationRejected("--expected-sha256 must be the 64-character dry-run SHA-256")
    if expected_sha256.lower() != digest(before):
        raise MigrationRejected("expected SHA-256 did not match the migration source")
    safe_regular(path)
    if path.read_bytes() != before:
        raise MigrationRejected("installed policy changed since dry-run; no write performed")

    original_mode = stat.S_IMODE(path.stat().st_mode)
    backup = path.with_name(path.name + ".before-ban-evasion-" + digest(before)[:16] + ".bak")
    if backup.exists() or backup.is_symlink():
        raise MigrationRejected(f"backup already exists; refusing to overwrite: {backup}")

    # Backup is verified and durably recorded before any installed-policy write.
    write_exclusive_backup(path, backup, before, original_mode)

    temp_name: str | None = None
    try:
        # Recheck immediately before replacement; plugin/runtime quiescence must be
        # established separately by the operator.
        if path.read_bytes() != before:
            raise MigrationRejected("installed policy changed during backup; original retained")
        fd, temp_name = tempfile.mkstemp(prefix=".reason-policies-migrate-", dir=path.parent)
        with os.fdopen(fd, "wb") as output:
            output.write(after)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temp_name, original_mode)
        require_same_owner(path, Path(temp_name))
        if path.read_bytes() != before:
            raise MigrationRejected("installed policy changed during staging; original retained")
        os.replace(temp_name, path)
        temp_name = None
        sync_directory(path.parent)
        if path.read_bytes() != after:
            raise MigrationRejected("post-replacement verification failed; restore from backup")
    finally:
        if temp_name is not None:
            Path(temp_name).unlink(missing_ok=True)
    return backup


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installed", required=True, type=Path, help="installed reason-policies.yml")
    parser.add_argument("--candidate", required=True, type=Path, help="pinned repository candidate YAML")
    parser.add_argument("--apply", action="store_true", help="write only after exact checksum verification")
    parser.add_argument("--expected-sha256", help="SHA-256 from the preceding dry run, required for --apply")
    args = parser.parse_args(argv)
    try:
        safe_regular(args.installed)
        safe_regular(args.candidate)
        if args.installed.resolve() == args.candidate.resolve():
            raise MigrationRejected("installed and candidate cannot refer to the same file")
        before = args.installed.read_bytes()
        after, already = prepare(before, args.candidate.read_bytes())
        print(f"installed_sha256={digest(before)}")
        print(f"candidate_sha256={digest(after)}")
        if already:
            print("ALREADY_CURRENT; no changes needed")
            return 0
        if not args.apply:
            print("DRY_RUN_OK; no files changed. Explicit --apply and --expected-sha256 required.")
            return 0
        if not args.expected_sha256:
            raise MigrationRejected("--apply requires --expected-sha256 from dry-run")
        backup = apply_migration(args.installed, args.expected_sha256, before, after)
        print(f"MIGRATION_APPLIED; backup={backup}")
        print("Restart/reload is NOT performed. Verify runtime policy separately.")
        return 0
    except (MigrationRejected, OSError) as error:
        print(f"REJECTED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
