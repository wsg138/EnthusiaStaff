#!/usr/bin/env python3
"""Safely replace the Staff Bot JAR over SFTP without printing secrets."""

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
SAFE_NAME = re.compile(r"^[A-Za-z0-9._-]+$")


class DeploymentError(RuntimeError):
    """Expected deployment failure safe to surface to the operator."""


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
        raise DeploymentError(f"missing required environment variable: {name}")
    return value


def validate_filename(value: str, label: str) -> str:
    if not SAFE_NAME.fullmatch(value) or value in {".", ".."}:
        raise DeploymentError(f"{label} must be a simple filename")
    return value


def validate_remote_root(value: str) -> str:
    normalized = value.strip().replace("\\", "/")
    if normalized == ".":
        return "."
    if not normalized or posixpath.isabs(normalized):
        raise DeploymentError("remote root must be a relative directory")
    parts = normalized.split("/")
    if any(not SAFE_NAME.fullmatch(part) or part in {".", ".."} for part in parts):
        raise DeploymentError("remote root contains an unsafe path component")
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
    host = required_env("STAFFBOT_SFTP_HOST")
    user = required_env("STAFFBOT_SFTP_USER")
    password = required_env("STAFFBOT_SFTP_PASSWORD")
    trusted_fingerprint = required_env("STAFFBOT_SFTP_HOST_KEY_SHA256")
    try:
        port = int(os.environ.get("STAFFBOT_SFTP_PORT", "22"))
    except ValueError as exc:
        raise DeploymentError("STAFFBOT_SFTP_PORT must be an integer") from exc

    transport = paramiko.Transport((host, port))
    try:
        transport.start_client(timeout=15)
        actual_fingerprint = server_fingerprint(transport.get_remote_server_key())
        if actual_fingerprint != trusted_fingerprint:
            raise DeploymentError("SFTP host-key fingerprint mismatch; refusing connection")
        transport.auth_password(user, password)
        if not transport.is_authenticated():
            raise DeploymentError("SFTP authentication failed")
        return transport, paramiko.SFTPClient.from_transport(transport)
    except (OSError, paramiko.SSHException, DeploymentError):
        transport.close()
        raise


def remote_exists(sftp: paramiko.SFTPClient, path: str) -> bool:
    try:
        sftp.stat(path)
        return True
    except FileNotFoundError:
        return False


def read_small_remote(sftp: paramiko.SFTPClient, path: str) -> bytes:
    stat = sftp.stat(path)
    if stat.st_size <= 0 or stat.st_size > MAX_CONFIG_BYTES:
        raise DeploymentError(f"remote configuration file has invalid size: {posixpath.basename(path)}")
    with sftp.open(path, "rb") as handle:
        raw = handle.read(MAX_CONFIG_BYTES + 1)
    if len(raw) > MAX_CONFIG_BYTES:
        raise DeploymentError(f"remote configuration file is too large: {posixpath.basename(path)}")
    return raw


def property_map(raw: bytes) -> dict[str, str]:
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise DeploymentError("moderation config is not UTF-8") from exc
    result: dict[str, str] = {}
    for line in text.splitlines():
        parse_property_line(line, result)
    return result


def parse_property_line(line: str, result: dict[str, str]) -> None:
    stripped = line.strip()
    if not stripped or stripped.startswith(("#", "!")):
        return
    for delimiter in ("=", ":"):
        if delimiter in stripped:
            key, value = stripped.split(delimiter, 1)
            result[key.strip()] = value.strip()
            return


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
        raise DeploymentError("token file is missing, empty, or implausibly sized")

    values = property_map(read_small_remote(sftp, config_path))
    missing = sorted(EXPECTED_CONFIG_KEYS - values.keys())
    if missing:
        raise DeploymentError("moderation config is missing required keys: " + ", ".join(missing))
    enforcement = values.get("discord-enforcement.enabled", "false").strip().lower()
    if enforcement not in {"false", ""}:
        raise DeploymentError("discord-enforcement.enabled must remain false for initial staging")
    transport = values.get("authority.transport", "loopback").strip()
    if transport != "bloom-private-split":
        raise DeploymentError("authority.transport must be bloom-private-split for this deployment")
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
            raise DeploymentError("uploaded JAR SHA-256 verification failed")
    finally:
        local_copy.unlink(missing_ok=True)


def rollback_target(
    sftp: paramiko.SFTPClient,
    target: str,
    incoming: str,
    backup: str,
    had_existing: bool,
) -> None:
    if remote_exists(sftp, incoming):
        sftp.remove(incoming)
    if had_existing and remote_exists(sftp, backup) and not remote_exists(sftp, target):
        sftp.rename(backup, target)


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

    had_existing = remote_exists(sftp, target)
    try:
        sftp.put(str(local_jar), incoming)
        verify_remote_sha(sftp, incoming, expected_sha)
        if had_existing:
            sftp.rename(target, backup)
        sftp.rename(incoming, target)
    except (OSError, paramiko.SSHException, DeploymentError):
        try:
            rollback_target(sftp, target, incoming, backup, had_existing)
        except (OSError, paramiko.SSHException) as rollback_error:
            raise DeploymentError("Staff Bot deployment failed and rollback could not complete") from rollback_error
        raise
    return backup if had_existing else None


def validate_request(args: argparse.Namespace) -> tuple[str, str, str, str, str]:
    if not args.jar.is_file():
        raise DeploymentError("local JAR does not exist")
    expected = validate_sha256(args.expected_sha256)
    if file_sha256(args.jar).lower() != expected:
        raise DeploymentError("local JAR SHA-256 does not match expected value")
    root = validate_remote_root(args.remote_root)
    jar_name = validate_filename(args.jar_name, "JAR name")
    token_name = validate_filename(args.token_name, "token name")
    config_name = validate_filename(args.config_name, "config name")
    return expected, root, jar_name, token_name, config_name


def main() -> int:
    args = parse_args()
    expected, root, jar_name, token_name, config_name = validate_request(args)
    transport = None
    sftp = None
    try:
        transport, sftp = connect()
        validate_remote_runtime_files(sftp, root, token_name, config_name)
        backup = deploy_jar(sftp, root, jar_name, args.jar, expected)
        print("artifact_sha_ok=yes")
        print("jar_uploaded=yes")
        print("rollback_backup=" + (posixpath.basename(backup) if backup else "none"))
        print("next_panel_app_flags=--token-file=t --moderation-config-file=m")
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
