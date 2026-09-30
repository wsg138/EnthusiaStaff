#!/usr/bin/env python3
"""Safely stage one Paper provider JAR over SFTP with SHA verification and rollback backups.

SFTP credentials are read only from process environment:
  PROVIDER_SFTP_HOST
  PROVIDER_SFTP_PORT (default 22)
  PROVIDER_SFTP_USER
  PROVIDER_SFTP_PASSWORD
  PROVIDER_SFTP_HOST_KEY_SHA256

The script never starts/restarts a server. It moves matching old JARs out of the
Paper plugins directory, verifies the uploaded candidate byte-for-byte, and
prints only non-secret deployment state.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import os
import posixpath
import re
import sys
import tempfile
import time
from pathlib import Path

try:
    import paramiko
except ImportError as exc:  # pragma: no cover - deployment-host dependency
    raise SystemExit("paramiko is required: python -m pip install paramiko") from exc

SAFE_NAME = re.compile(r"^[A-Za-z0-9._-]+$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--remote-name", required=True)
    parser.add_argument("--match-prefix", required=True)
    parser.add_argument("--plugins-dir", default="plugins")
    return parser.parse_args()


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"missing required environment variable: {name}")
    return value


def validate_filename(value: str, label: str) -> str:
    if not SAFE_NAME.fullmatch(value) or value in {".", ".."}:
        raise SystemExit(f"{label} must be a simple filename/prefix")
    return value


def validate_sha256(value: str) -> str:
    normalized = value.strip().lower()
    if len(normalized) != 64 or any(char not in "0123456789abcdef" for char in normalized):
        raise SystemExit("expected SHA-256 must be 64 hexadecimal characters")
    return normalized


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
    host = required_env("PROVIDER_SFTP_HOST")
    user = required_env("PROVIDER_SFTP_USER")
    password = required_env("PROVIDER_SFTP_PASSWORD")
    trusted = required_env("PROVIDER_SFTP_HOST_KEY_SHA256")
    port = int(os.environ.get("PROVIDER_SFTP_PORT", "22"))

    transport = paramiko.Transport((host, port))
    transport.start_client(timeout=15)
    actual = server_fingerprint(transport.get_remote_server_key())
    if actual != trusted:
        transport.close()
        raise SystemExit("SFTP host-key fingerprint mismatch; refusing connection")
    transport.auth_password(user, password)
    if not transport.is_authenticated():
        transport.close()
        raise SystemExit("SFTP authentication failed")
    return transport, paramiko.SFTPClient.from_transport(transport)


def matching_plugin_names(names: list[str], prefix: str) -> list[str]:
    wanted = prefix.casefold()
    return sorted(
        name for name in names
        if name.casefold().startswith(wanted) and name.casefold().endswith(".jar")
    )


def ensure_directory(sftp: paramiko.SFTPClient, path: str) -> None:
    try:
        sftp.stat(path)
    except FileNotFoundError:
        sftp.mkdir(path)


def verify_remote_sha(sftp: paramiko.SFTPClient, remote_path: str, expected: str) -> None:
    with tempfile.NamedTemporaryFile(delete=False) as handle:
        local_copy = Path(handle.name)
    try:
        sftp.get(remote_path, str(local_copy))
        if file_sha256(local_copy) != expected:
            raise SystemExit("uploaded provider JAR SHA-256 verification failed")
    finally:
        local_copy.unlink(missing_ok=True)


def stage_provider(
    sftp: paramiko.SFTPClient,
    plugins_dir: str,
    local_jar: Path,
    expected_sha: str,
    remote_name: str,
    match_prefix: str,
) -> tuple[list[str], str]:
    timestamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    backup_root = posixpath.join(plugins_dir, ".enthusia-deploy-backups")
    backup_dir = posixpath.join(backup_root, timestamp)
    ensure_directory(sftp, backup_root)
    ensure_directory(sftp, backup_dir)

    existing = matching_plugin_names(sftp.listdir(plugins_dir), match_prefix)
    for name in existing:
        sftp.rename(posixpath.join(plugins_dir, name), posixpath.join(backup_dir, name))

    target = posixpath.join(plugins_dir, remote_name)
    incoming = posixpath.join(plugins_dir, f".{remote_name}.uploading-{timestamp}")
    try:
        sftp.put(str(local_jar), incoming)
        verify_remote_sha(sftp, incoming, expected_sha)
        sftp.rename(incoming, target)
    except (Exception, SystemExit):
        try:
            sftp.remove(incoming)
        except (FileNotFoundError, OSError):
            pass
        for name in existing:
            backup = posixpath.join(backup_dir, name)
            original = posixpath.join(plugins_dir, name)
            try:
                sftp.rename(backup, original)
            except (FileNotFoundError, OSError):
                pass
        raise
    return existing, backup_dir


def main() -> int:
    args = parse_args()
    if not args.jar.is_file():
        raise SystemExit("local provider JAR does not exist")
    expected = validate_sha256(args.expected_sha256)
    if file_sha256(args.jar) != expected:
        raise SystemExit("local provider JAR SHA-256 does not match expected value")
    remote_name = validate_filename(args.remote_name, "remote name")
    if not remote_name.casefold().endswith(".jar"):
        raise SystemExit("remote name must end in .jar")
    match_prefix = validate_filename(args.match_prefix, "match prefix")

    transport = None
    sftp = None
    try:
        transport, sftp = connect()
        sftp.stat(args.plugins_dir)
        replaced, backup_dir = stage_provider(
            sftp,
            args.plugins_dir,
            args.jar,
            expected,
            remote_name,
            match_prefix,
        )
        print("artifact_sha_ok=yes")
        print("provider_uploaded=yes")
        print(f"replaced_jar_count={len(replaced)}")
        print("rollback_backup_dir=" + backup_dir)
        print("restart_required=yes")
        return 0
    finally:
        if sftp is not None:
            sftp.close()
        if transport is not None:
            transport.close()


if __name__ == "__main__":
    sys.exit(main())
