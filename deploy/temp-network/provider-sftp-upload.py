#!/usr/bin/env python3
"""Safely stage one Paper provider JAR over SFTP with SHA verification and rollback backups."""

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


class DeploymentError(RuntimeError):
    """Expected deployment failure safe to surface to the operator."""


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
        raise DeploymentError(f"missing required environment variable: {name}")
    return value


def validate_filename(value: str, label: str) -> str:
    if not SAFE_NAME.fullmatch(value) or value in {".", ".."}:
        raise DeploymentError(f"{label} must be a simple filename/prefix")
    return value


def validate_remote_directory(value: str, label: str) -> str:
    normalized = value.strip().replace("\\", "/")
    if not normalized or posixpath.isabs(normalized):
        raise DeploymentError(f"{label} must be a relative remote directory")
    parts = normalized.split("/")
    if any(not SAFE_NAME.fullmatch(part) or part in {".", ".."} for part in parts):
        raise DeploymentError(f"{label} contains an unsafe path component")
    return posixpath.join(*parts)


def validate_sha256(value: str) -> str:
    normalized = value.strip().lower()
    if len(normalized) != 64 or any(char not in "0123456789abcdef" for char in normalized):
        raise DeploymentError("expected SHA-256 must be 64 hexadecimal characters")
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
    try:
        port = int(os.environ.get("PROVIDER_SFTP_PORT", "22"))
    except ValueError as exc:
        raise DeploymentError("PROVIDER_SFTP_PORT must be an integer") from exc

    transport = paramiko.Transport((host, port))
    try:
        transport.start_client(timeout=15)
        actual = server_fingerprint(transport.get_remote_server_key())
        if actual != trusted:
            raise DeploymentError("SFTP host-key fingerprint mismatch; refusing connection")
        transport.auth_password(user, password)
        if not transport.is_authenticated():
            raise DeploymentError("SFTP authentication failed")
        return transport, paramiko.SFTPClient.from_transport(transport)
    except (OSError, paramiko.SSHException, DeploymentError):
        transport.close()
        raise


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
            raise DeploymentError("uploaded provider JAR SHA-256 verification failed")
    finally:
        local_copy.unlink(missing_ok=True)


def backup_existing_plugins(
    sftp: paramiko.SFTPClient,
    plugins_dir: str,
    backup_dir: str,
    match_prefix: str,
) -> list[str]:
    existing = matching_plugin_names(sftp.listdir(plugins_dir), match_prefix)
    for name in existing:
        sftp.rename(posixpath.join(plugins_dir, name), posixpath.join(backup_dir, name))
    return existing


def rollback_provider(
    sftp: paramiko.SFTPClient,
    plugins_dir: str,
    backup_dir: str,
    incoming: str,
    existing: list[str],
) -> None:
    try:
        sftp.remove(incoming)
    except FileNotFoundError:
        pass
    for name in existing:
        backup = posixpath.join(backup_dir, name)
        original = posixpath.join(plugins_dir, name)
        sftp.rename(backup, original)


def install_candidate(
    sftp: paramiko.SFTPClient,
    incoming: str,
    target: str,
    local_jar: Path,
    expected_sha: str,
) -> None:
    sftp.put(str(local_jar), incoming)
    verify_remote_sha(sftp, incoming, expected_sha)
    sftp.rename(incoming, target)


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

    existing = backup_existing_plugins(sftp, plugins_dir, backup_dir, match_prefix)
    target = posixpath.join(plugins_dir, remote_name)
    incoming = posixpath.join(plugins_dir, f".{remote_name}.uploading-{timestamp}")
    try:
        install_candidate(sftp, incoming, target, local_jar, expected_sha)
    except (OSError, paramiko.SSHException, DeploymentError):
        try:
            rollback_provider(sftp, plugins_dir, backup_dir, incoming, existing)
        except (OSError, paramiko.SSHException) as rollback_error:
            raise DeploymentError("provider deployment failed and rollback could not complete") from rollback_error
        raise
    return existing, backup_dir


def validate_request(args: argparse.Namespace) -> tuple[str, str, str, str]:
    if not args.jar.is_file():
        raise DeploymentError("local provider JAR does not exist")
    expected = validate_sha256(args.expected_sha256)
    if file_sha256(args.jar) != expected:
        raise DeploymentError("local provider JAR SHA-256 does not match expected value")
    remote_name = validate_filename(args.remote_name, "remote name")
    if not remote_name.casefold().endswith(".jar"):
        raise DeploymentError("remote name must end in .jar")
    match_prefix = validate_filename(args.match_prefix, "match prefix")
    plugins_dir = validate_remote_directory(args.plugins_dir, "plugins directory")
    return expected, remote_name, match_prefix, plugins_dir


def main() -> int:
    args = parse_args()
    expected, remote_name, match_prefix, plugins_dir = validate_request(args)
    transport = None
    sftp = None
    try:
        transport, sftp = connect()
        sftp.stat(plugins_dir)
        replaced, backup_dir = stage_provider(
            sftp, plugins_dir, args.jar, expected, remote_name, match_prefix
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


def cli() -> int:
    try:
        return main()
    except DeploymentError as exc:
        raise SystemExit(str(exc)) from exc


if __name__ == "__main__":
    sys.exit(cli())
