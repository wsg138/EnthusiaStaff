#!/usr/bin/env python3
"""Read-only StaffBot private-channel staging configuration preflight.

This checks configuration safety boundaries, not live Discord permissions,
connected peers, or runtime readiness. It never prints raw input values.
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path


def yaml_scalar(content: str, name: str) -> str:
    matches = re.findall(r"(?m)^\s*" + re.escape(name) + r"\s*:\s*([^#\r\n]*)", content)
    if len(matches) > 1:
        raise ValueError("Duplicate YAML configuration key")
    return matches[0].strip().strip("'\"") if matches else ""


def yaml_section(content: str, name: str) -> str:
    lines = content.splitlines()
    sections = [i for i, line in enumerate(lines) if line == name + ":"]
    if len(sections) > 1:
        raise ValueError("Duplicate YAML section")
    for i in sections:
        block = []
        for next_line in lines[i + 1 :]:
            if next_line and not next_line[0].isspace() and not next_line.startswith("#"):
                break
            block.append(next_line)
        return "\n".join(block)
    return ""


def legacy_setting(content: str, name: str) -> str:
    matches = re.findall(r"(?m)^" + re.escape(name) + r":\s*([^#\r\n]*)", content)
    if len(matches) > 1:
        raise ValueError("Duplicate legacy routing key")
    return matches[0].strip() if matches else ""


def private_channel(source: str) -> str:
    match = re.search(
        r'STAGING\(\s*"staging"\s*,\s*\d+L\s*,\s*\d+L\s*,\s*(\d+)L',
        source, flags=re.S,
    )
    if not match:
        raise ValueError("Unable to identify the pinned STAGING channel in source")
    return match.group(1)


def properties(content: str) -> dict[str, str]:
    result = {}
    for line in content.splitlines():
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() in result:
            raise ValueError("Duplicate chat bridge property key")
        result[key.strip()] = value.strip()
    return result


def paper_shadow_enabled(paper: str) -> bool:
    section = yaml_section(paper, "discord-chat-bridge")
    # Legacy boolean-only fallback does not prove the explicit SHADOW mode.
    return yaml_scalar(section, "mode").upper() == "SHADOW"


def legacy_destinations(test: str, live: str) -> tuple[bool, list[str]]:
    ids: list[str] = []
    maps_present = True
    for config in (test, live):
        channels = re.findall(r"\b\d{16,22}\b", legacy_setting(config, "Channels"))
        maps_present = maps_present and bool(channels)
        ids.extend(channels)
        ids.extend(re.findall(
            r"\b\d{16,22}\b", legacy_setting(config, "DiscordConsoleChannelId")
        ))
    return maps_present, ids


def legacy_route_isolation(test: str, live: str) -> bool:
    """Block test chat/console destinations also used by production."""
    maps_present, _ = legacy_destinations(test, live)
    if not maps_present:
        return False
    test_values = legacy_destinations(test, "")[1]
    live_values = legacy_destinations("", live)[1]
    return bool(test_values) and bool(live_values) and set(test_values).isdisjoint(live_values)


def distinct_chat_app(prop: dict[str, str], java_source: str) -> bool:
    public_app = prop.get("ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_APPLICATION_ID", "")
    staff_apps = re.findall(
        r'(?:STAGING|PRODUCTION)\(\s*"(?:staging|production)"\s*,\s*(\d+)L',
        java_source,
    )
    return bool(public_app) and public_app.isdigit() and len(staff_apps) == 2 and public_app not in staff_apps


def evaluate(
    paper: str, legacy_test: str, legacy_live: str, chat: str, java_source: str
) -> list[tuple[str, bool]]:
    pin = private_channel(java_source)
    prop = properties(chat)
    server_id = yaml_scalar(yaml_section(paper, "network"), "server-id")
    routes_raw = prop.get("ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ROUTES", "")
    routes = [item.strip() for item in routes_raw.split(";") if item.strip()]
    maps_present, outbound_values = legacy_destinations(legacy_test, legacy_live)
    return [
        ("private channel distinct from legacy chat and console", maps_present and pin not in outbound_values),
        ("test legacy Discord destinations do not overlap production", legacy_route_isolation(legacy_test, legacy_live)),
        ("Paper reports a non-empty backend ID", bool(server_id)),
        ("Paper chat mode explicitly SHADOW", paper_shadow_enabled(paper)),
        ("Paper authenticated channel configured", yaml_scalar(yaml_section(paper, "channel"), "enabled").lower() == "true"),
        ("StaffBot chat mode SHADOW", prop.get("ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE", "").upper() == "SHADOW"),
        ("StaffBot outbound routes match only this test backend",
         bool(server_id) and routes == [f"{server_id}/global={pin}"]),
        ("StaffBot inbound chat remains disabled",
         not prop.get("ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_INGRESS_ROUTES", "").strip()),
        ("Chat-only application distinct from staff application",
         distinct_chat_app(prop, java_source)),
        ("Chat-only application credential configured",
         bool(prop.get("ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_TOKEN", "").strip())),
        ("Test legacy Minecraft-to-Discord forwarding disabled",
         legacy_setting(legacy_test, "DiscordChatChannelMinecraftToDiscord").lower() == "false"),
        ("Test legacy Discord-to-Minecraft forwarding disabled",
         legacy_setting(legacy_test, "DiscordChatChannelDiscordToMinecraft").lower() == "false"),
        ("Test legacy chat command execution disabled",
         legacy_setting(legacy_test, "DiscordChatChannelConsoleCommandEnabled").lower() == "false"),
        ("Test legacy console Discord destination absent",
         legacy_setting(legacy_test, "DiscordConsoleChannelId").strip("'\" ") in ("", "0")),
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper", required=True, type=Path)
    parser.add_argument("--discordsrv-test", required=True, type=Path)
    parser.add_argument("--discordsrv-live", required=True, type=Path)
    parser.add_argument("--chat", required=True, type=Path)
    parser.add_argument("--staffbot-environment-source", required=True, type=Path)
    args = parser.parse_args()
    try:
        results = evaluate(
            args.paper.read_text(encoding="utf-8"),
            args.discordsrv_test.read_text(encoding="utf-8"),
            args.discordsrv_live.read_text(encoding="utf-8"),
            args.chat.read_text(encoding="utf-8"),
            args.staffbot_environment_source.read_text(encoding="utf-8"),
        )
    except (OSError, UnicodeError, ValueError) as error:
        print(f"BLOCKED: configuration cannot be validated ({type(error).__name__})")
        return 2
    for name, passed in results:
        print(("PASS" if passed else "BLOCKED") + ": " + name)
    count = sum(not result for _, result in results)
    print(f"CONFIGURATION PREFLIGHT: {len(results)-count} PASS, {count} BLOCKED")
    print("Runtime identity, channel permissions, TLS peers, bot readiness, and rollback are NOT VERIFIED.")
    return 2 if count else 0


if __name__ == "__main__":
    raise SystemExit(main())
