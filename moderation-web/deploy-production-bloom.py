"""Install only the production Cloudflare Tunnel connector token on the Bloom Staff Bot split."""

from __future__ import annotations

import hashlib
import http.client
import socket
import ssl
import importlib
import io
import json
import re
import sys
import tomllib
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


paramiko: Any = importlib.import_module("paramiko")

TUNNEL_NAME = "enthusia-moderation-read-production"
REMOTE_NAME = "prod-tunnel"
REMOTE_JAR = "EnthusiaStaff-StaffBot.jar"
PREVIOUS_JAR_SHA256 = "9a12cefd06b5158829ec8df4c4ed28c2d07bb119b2f1adbe546bc3fd31a7ce01"
FIRST_WEB_JAR_SHA256 = "0e4a9c7c3cb4bceddd550843578d9b74e184f94c6fd132c3f35a7f9e1bc88ba8"
PRODUCTION_WEB_JAR_SHA256 = "8cd85417ce26c66a851054fcb8ea2a4df29871917fd9bf86019271317d56ed4c"
ACTION_WEB_JAR_SHA256 = "4702525c31861cdf0692288e38583fd32a0adc586b93c318e302745bcb585caf"
HISTORY_WEB_JAR_SHA256 = "8d682c8edc8c7c88bae5441719d19dd10060c3b74b619d1073d35d36186a3708"
KNOWN_JAR_SHA256 = {
    PREVIOUS_JAR_SHA256,
    FIRST_WEB_JAR_SHA256,
    PRODUCTION_WEB_JAR_SHA256,
    ACTION_WEB_JAR_SHA256,
    HISTORY_WEB_JAR_SHA256,
}
LOCAL_JAR = Path(__file__).resolve().parent.parent / "staff-bot/build/libs/EnthusiaStaff-StaffBot-0.1.0-SNAPSHOT.jar"
DETAILS_FILE = Path.home() / "OneDrive/Desktop/SFTP Details- ENTHUSIA NETWORK.md"
HOST_KEYS_FILE = Path.home() / ".ssh/known_hosts_sentinel_bloom"
WRANGLER_CREDENTIALS = Path.home() / ".wrangler/config/default.toml"
CLOUDFLARE_HOST = "api.cloudflare.com"
CLOUDFLARE_PREFIX = "/client/v4/"


def bloom_connection() -> tuple[str, int, str, str]:
    lines = DETAILS_FILE.read_text(encoding="utf-8-sig").splitlines()
    password = bloom_password(lines)
    username = lines[79].strip()
    host, port = bloom_endpoint(lines[77].strip(), username)
    return host, port, username, password


def bloom_password(lines: list[str]) -> str:
    label, separator, value = lines[0].partition(":")
    if label.strip() != "All passwords" or not separator or not value.strip():
        raise RuntimeError("Bloom SFTP details are unavailable")
    return value.strip()


def bloom_endpoint(raw_url: str, username: str) -> tuple[str, int]:
    url = urlparse(raw_url)
    if url.scheme != "sftp":
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    if url.hostname is None or url.port is None:
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    if url.netloc.rsplit(":", 1)[0].lower() != url.hostname.lower():
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    if not re.fullmatch(r"[A-Za-z0-9._-]{3,120}", username):
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    return url.hostname, url.port


def wrangler_oauth_token() -> str:
    with WRANGLER_CREDENTIALS.open("rb") as stream:
        oauth_token = tomllib.load(stream).get("oauth_token")
    if (not isinstance(oauth_token, str) or not oauth_token or not oauth_token.isascii()
            or "\r" in oauth_token or "\n" in oauth_token):
        raise RuntimeError("Cloudflare authorization is unavailable")
    return oauth_token


def cloudflare_get(oauth_token: str, path: str) -> dict[str, Any]:
    validate_cloudflare_path(path)
    payload = cloudflare_tls_get(oauth_token, path)
    if not isinstance(payload, dict) or not payload.get("success"):
        raise RuntimeError("Cloudflare API rejected the tunnel lookup")
    return payload


def validate_cloudflare_path(path: str) -> None:
    if (not path.startswith(CLOUDFLARE_PREFIX) or "://" in path or not path.isascii()
            or "\r" in path or "\n" in path):
        raise RuntimeError("Cloudflare API path is invalid")


def cloudflare_tls_get(oauth_token: str, path: str) -> object:
    context = ssl.create_default_context()
    with socket.create_connection((CLOUDFLARE_HOST, 443), timeout=20) as raw_socket:
        with context.wrap_socket(raw_socket, server_hostname=CLOUDFLARE_HOST) as tls_socket:
            tls_socket.sendall(cloudflare_request(oauth_token, path))
            response = http.client.HTTPResponse(tls_socket)
            response.begin()
            try:
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError("Cloudflare API request failed")
                return json.load(response)
            finally:
                response.close()


def cloudflare_request(oauth_token: str, path: str) -> bytes:
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {CLOUDFLARE_HOST}\r\n"
        f"Authorization: Bearer {oauth_token}\r\n"
        "Connection: close\r\n"
        "\r\n"
    )
    return request.encode("ascii")


def cloudflare_id(value: object, label: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", value):
        raise RuntimeError(f"Cloudflare returned an invalid {label}")
    return value


def cloudflare_account_id(oauth_token: str) -> str:
    payload = cloudflare_get(oauth_token, f"{CLOUDFLARE_PREFIX}zones?name=enthusia.info&status=active")
    zones = payload.get("result")
    if not isinstance(zones, list) or len(zones) != 1:
        raise RuntimeError("Enthusia Cloudflare zone is ambiguous")
    account = zones[0].get("account") if isinstance(zones[0], dict) else None
    account_id = account.get("id") if isinstance(account, dict) else None
    return cloudflare_id(account_id, "account id")


def cloudflare_tunnel_id(oauth_token: str, account_id: str) -> str:
    path = f"{CLOUDFLARE_PREFIX}accounts/{account_id}/cfd_tunnel?is_deleted=false"
    tunnels = cloudflare_get(oauth_token, path).get("result")
    if not isinstance(tunnels, list):
        raise RuntimeError("Production Cloudflare tunnel is ambiguous")
    matching = [item for item in tunnels if isinstance(item, dict) and item.get("name") == TUNNEL_NAME]
    if len(matching) != 1 or matching[0].get("config_src") != "cloudflare":
        raise RuntimeError("Production Cloudflare tunnel is ambiguous")
    return cloudflare_id(matching[0].get("id"), "tunnel id")


def connector_token() -> str:
    oauth_token = wrangler_oauth_token()
    account_id = cloudflare_account_id(oauth_token)
    tunnel_id = cloudflare_tunnel_id(oauth_token, account_id)
    path = f"{CLOUDFLARE_PREFIX}accounts/{account_id}/cfd_tunnel/{tunnel_id}/token"
    token = cloudflare_get(oauth_token, path).get("result")
    if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9._=-]{100,8192}", token):
        kind = type(token).__name__
        length = len(token) if isinstance(token, str) else 0
        raise RuntimeError(f"Cloudflare returned no valid connector token (type={kind}, length={length})")
    return token


def file_digest(stream: Any) -> str:
    digest = hashlib.sha256()
    while chunk := stream.read(1024 * 1024):
        digest.update(chunk)
    return digest.hexdigest()


def upload_jar(sftp: Any) -> None:
    with LOCAL_JAR.open("rb") as local:
        expected = file_digest(local)
    with sftp.open(REMOTE_JAR, "rb") as current:
        current_digest = file_digest(current)
    if current_digest == expected:
        print(f"production_staff_jar_sha256={expected} (already installed)")
        return
    if current_digest not in KNOWN_JAR_SHA256:
        raise RuntimeError("Remote Staff Bot JAR differs from the verified previous deployment")

    temporary = ".EnthusiaStaff-StaffBot.jar.upload"
    backup = REMOTE_JAR + ".backup-web-" + str(int(datetime.now(timezone.utc).timestamp()))
    try:
        sftp.put(str(LOCAL_JAR), temporary, confirm=True)
        verify_uploaded_jar(sftp, temporary, expected)
        sftp.rename(REMOTE_JAR, backup)
        promote_uploaded_jar(sftp, temporary, backup)
    finally:
        remove_if_present(sftp, temporary)
    print(f"production_staff_jar_sha256={expected}")
    print(f"previous_jar_backup={backup}")


def verify_uploaded_jar(sftp: Any, temporary: str, expected: str) -> None:
    with sftp.open(temporary, "rb") as uploaded:
        if file_digest(uploaded) != expected:
            raise RuntimeError("Uploaded Staff Bot JAR checksum mismatch")


def promote_uploaded_jar(sftp: Any, temporary: str, backup: str) -> None:
    try:
        sftp.rename(temporary, REMOTE_JAR)
    except Exception:
        sftp.rename(backup, REMOTE_JAR)
        raise


def remove_if_present(sftp: Any, path: str) -> None:
    try:
        sftp.remove(path)
    except FileNotFoundError:
        pass


def command_mode(argv: list[str]) -> str:
    mode = argv[1] if len(argv) == 2 else "install-token"
    if mode not in ("install-token", "--audit", "--upload-jar"):
        raise RuntimeError("Unsupported command line argument")
    return mode


def connected_client(host: str, port: int, username: str, password: str) -> Any:
    host_keys = paramiko.HostKeys()
    host_keys.load(str(HOST_KEYS_FILE))
    host_label = f"[{host}]:{port}"
    if host_keys.lookup(host_label) is None:
        raise RuntimeError("Trusted Bloom host key is unavailable")
    client = paramiko.SSHClient()
    client.load_host_keys(str(HOST_KEYS_FILE))
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    client.connect(host, port=port, username=username, password=password,
                   look_for_keys=False, allow_agent=False, timeout=20)
    return client


def require_discord_enforcement_disabled(sftp: Any) -> None:
    with sftp.open("m", "r") as config:
        lines = config.read().decode("utf-8").splitlines()
    enforcement = [line.strip() for line in lines
                   if line.strip().startswith("discord-enforcement.enabled=")]
    if enforcement != ["discord-enforcement.enabled=false"]:
        raise RuntimeError("Discord enforcement must remain disabled")


def audit_remote(sftp: Any) -> None:
    with sftp.open(REMOTE_JAR, "rb") as current:
        digest = file_digest(current)
    print(f"remote_staff_jar_sha256={digest}")
    print("discord_enforcement_disabled=true")


def existing_token_matches(sftp: Any, token: bytes) -> bool:
    try:
        with sftp.open(REMOTE_NAME, "rb") as existing:
            installed = existing.read()
    except FileNotFoundError:
        return False
    if hashlib.sha256(installed).digest() != hashlib.sha256(token).digest():
        raise RuntimeError("Existing production tunnel token differs; refusing replacement")
    return True


def write_token(sftp: Any, token: bytes) -> None:
    temporary = f".{REMOTE_NAME}.upload"
    try:
        sftp.putfo(io.BytesIO(token), temporary)
        sftp.chmod(temporary, 0o600)
        sftp.rename(temporary, REMOTE_NAME)
    finally:
        remove_if_present(sftp, temporary)


def secure_installed_token(sftp: Any, token: bytes) -> None:
    attributes = sftp.stat(REMOTE_NAME)
    installed_mode = attributes.st_mode
    if installed_mode is None:
        raise RuntimeError("Production tunnel token mode is unavailable")
    if installed_mode & 0o077:
        sftp.chmod(REMOTE_NAME, 0o600)
    with sftp.open(REMOTE_NAME, "rb") as installed:
        installed_token = installed.read()
    if hashlib.sha256(installed_token).digest() != hashlib.sha256(token).digest():
        raise RuntimeError("Production tunnel token upload did not verify")


def install_connector_token(sftp: Any) -> None:
    token = connector_token().encode("ascii")
    if not existing_token_matches(sftp, token):
        write_token(sftp, token)
    secure_installed_token(sftp, token)


def execute_mode(sftp: Any, mode: str) -> None:
    require_discord_enforcement_disabled(sftp)
    if mode == "--audit":
        audit_remote(sftp)
    elif mode == "--upload-jar":
        upload_jar(sftp)
    else:
        install_connector_token(sftp)


def main() -> None:
    host, port, username, password = bloom_connection()
    mode = command_mode(sys.argv)
    client = connected_client(host, port, username, password)
    try:
        with client.open_sftp() as sftp:
            execute_mode(sftp, mode)
    finally:
        client.close()
    if mode == "install-token":
        print("production tunnel connector installed and verified")


if __name__ == "__main__":
    main()
