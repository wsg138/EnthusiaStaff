#!/usr/bin/env python3
"""Safely replace the Staff Bot JAR over SFTP without printing secrets.

Credentials are read from environment variables only:
  STAFFBOT_SFTP_HOST
  STAFFBOT_SFTP_PORT (default 22)
  STAFFBOT_SFTP_USER
  STAFFBOT_SFTP_PASSWORD
  STAFFBOT_SFTP_HOST_KEY_SHA256

The host-key fingerprint must be the trusted SHA256 fingerprint for the Bloom SFTP host.
The script never reads secret values aloud; it only validates the allowlisted key set in `m`.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import os
import posixpath
import sys
import tempfile
import time
from pathlib import Path

try:
    import paramiko
except ImportError as exc:  # pragma: no cover - deployment host dependency
    raise SystemExit("paramiko is required: python -m pip install paramiko") from exc

EXPECTED_CONFIG_KEYS = {
    "db.jdbc-url",
    "db.username",
    "db.password",
    "authority.url",
    "authority.secret",
    "component.secret",
}
MAX_CONFIG_BYTES = 64 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--remote-root", default=".")
    parser.add_argument("--jar-name", default="EnthusiaStaff-StaffBot.jar")
    parser.add_argument("--token-name", default="t")
    parser.add_argument("--config-name", default="m")
    return parser.parse_args()


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"missing required environment variable: {name}")
    return value


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def server_fingerprint(key: paramiko.PKey) -> str:
    digest = hashlib.sha256(key.asbytes()).digest()
    return "SHA256:" + base64.b64encode(digest).decode("ascii").rstrip("=")


def connect() -> tuple[paramiko.Transport, paramiko.SFTPClient]:
    host = required_env("STAFFBOT_SFTP_HOST")
    user = required_env("STAFFBOT_SFTP_USER")
    password = required_env("STAFFBOT_SFTP_PASSWORD")
    trusted_fingerprint = required_env("STAFFBOT_SFTP_HOST_KEY_SHA256")
    port = int(os.environ.get("STAFFBOT_SFTP_PORT", "22"))

    transport = paramiko.Transport((host, port))
    transport.start_client(timeout=15)
    actual_fingerprint = server_fingerprint(transport.get_remote_server_key())
    if actual_fingerprint != trusted_fingerprint:
        transport.close()
        raise SystemExit("SFTP host-key fingerprint mismatch; refusing connection")
    transport.auth_password(user, password)
    if not transport.is_authenticated():
        transport.close()
        raise SystemExit("SFTP authentication failed")
    return transport, paramiko.SFTPClient.from_transport(transport)


def remote_exists(sftp: paramiko.SFTPClient, path: str) -> bool:
    try:
        sftp.stat(path)
        return True
    except FileNotFoundError:
        return False


def read_small_remote(sftp: paramiko.SFTPClient, path: str) -> bytes:
    stat = sftp.stat(path)
    if stat.st_size <= 0 or stat.st_size > MAX_CONFIG_BYTES:
        raise SystemExit(f"remote configuration file has invalid size: {posixpath.basename(path)}")
    with sftp.open(path, "rb") as handle:
        return handle.read(MAX_CONFIG_BYTES + 1)


def property_map(raw: bytes) -> dict[str, str]:
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise SystemExit("moderation config is not UTF-8") from exc
    result: dict[str, str] = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        delimiter = "=" if "=" in stripped else ":" if ":" in stripped else None
        if delimiter is None:
            continue
        key, value = stripped.split(delimiter, 1)
        result[key.strip()] = value.strip()
    return result


def validate_remote_runtime_files(
    sftp: paramiko.SFTPClient,
    root: str,
    token_name: str,
    config_name: str,
) -> None:
    token_path = posixpath.join(root, token_name)
    config_path = posixpath.join(root, config_name)
    token_stat = sftp.stat(token_path)
    if token_stat.st_size < 20 or token_stat.st_size > 2048:
        raise SystemExit("token file is missing, empty, or implausibly sized")

    values = property_map(read_small_remote(sftp, config_path))
    missing = sorted(EXPECTED_CONFIG_KEYS - values.keys())
    if missing:
        raise SystemExit("moderation config is missing required keys: " + ", ".join(missing))
    enforcement = values.get("discord-enforcement.enabled", "false").strip().lower()
    if enforcement not in {"false", ""}:
        raise SystemExit("discord-enforcement.enabled must remain false for initial staging")
    transport = values.get("authority.transport", "loopback").strip()
    if transport != "bloom-private-split":
        raise SystemExit("authority.transport must be bloom-private-split for this deployment")
    print("runtime_files=valid")
    print("discord_enforcement=false")
    print("authority_transport=bloom-private-split")


def verify_remote_sha(sftp: paramiko.SFTPClient, remote_path: str, expected: str) -> None:
    with tempfile.NamedTemporaryFile(delete=False) as temp:
        local_copy = Path(temp.name)
    try:
        sftp.get(remote_path, str(local_copy))
        actual = file_sha256(local_copy)
        if actual.lower() != expected.lower():
            raise SystemExit("uploaded JAR SHA-256 verification failed")
    finally:
        local_copy.unlink(missing_ok=True)


def deploy_jar(
    sftp: paramiko.SFTPClient,
    root: str,
    jar_name: str,
    local_jar: Path,
    expected_sha: str,
) -> str | None:
    timestamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    target = posixpath.join(root, jar_name)
    incoming = posixpath.join(root, f".{jar_name}.uploading-{timestamp}")
    backup = posixpath.join(root, f"{jar_name}.backup-{timestamp}")

    sftp.put(str(local_jar), incoming)
    verify_remote_sha(sftp, incoming, expected_sha)

    had_existing = remote_exists(sftp, target)
    if had_existing:
        sftp.rename(target, backup)
    try:
        sftp.rename(incoming, target)
    except Exception:
        if had_existing and remote_exists(sftp, backup) and not remote_exists(sftp, target):
            sftp.rename(backup, target)
        raise
    return backup if had_existing else None


def main() -> int:
    args = parse_args()
    if not args.jar.is_file():
        raise SystemExit("local JAR does not exist")
    expected = args.expected_sha256.strip().lower()
    if len(expected) != 64 or any(char not in "0123456789abcdef" for char in expected):
        raise SystemExit("expected SHA-256 must be 64 lowercase/uppercase hex characters")
    actual = file_sha256(args.jar)
    if actual.lower() != expected:
        raise SystemExit("local JAR SHA-256 does not match expected value")

    transport = None
    sftp = None
    try:
        transport, sftp = connect()
        validate_remote_runtime_files(sftp, args.remote_root, args.token_name, args.config_name)
        backup = deploy_jar(sftp, args.remote_root, args.jar_name, args.jar, expected)
        print("artifact_sha_ok=yes")
        print("jar_uploaded=yes")
        print("rollback_backup=" + (posixpath.basename(backup) if backup else "none"))
        print("next_panel_app_flags=--environment=staging --token-file=t --moderation-config-file=m")
        return 0
    finally:
        if sftp is not None:
            sftp.close()
        if transport is not None:
            transport.close()


if __name__ == "__main__":
    sys.exit(main())
