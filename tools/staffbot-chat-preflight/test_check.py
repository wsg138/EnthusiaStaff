"""Offline regression checks for private-channel staging preflight.

All inputs are synthetic; no production Discord IDs or credentials are used.
"""
import importlib.util
import sys
import unittest
from pathlib import Path

SOURCE = Path(__file__).with_name("check.py")
SPEC = importlib.util.spec_from_file_location("staffbot_staging_check", SOURCE)
if SPEC is None or SPEC.loader is None:
    raise ImportError("Preflight checker module unavailable")
CHECK = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CHECK
SPEC.loader.exec_module(CHECK)

PIN = "1555555555555555555"
GLOBAL = "1650000000000000001"
STAFF = "1650000000000000002"
CONSOLE = "1650000000000000003"
SOURCE_JAVA = (
    'STAGING("staging", 1111111111111111111L, 3333333333333333333L, '
    + PIN + 'L), PRODUCTION("production", 2222222222222222222L, '
    '3333333333333333333L, null);'
)
PAPER = """operational-mode: BOOTSTRAP
network:
  server-id: TEST
discord-chat-bridge:
  mode: SHADOW
channel:
  enabled: true
"""
LEGACY = (
    f'Channels: {{"global": "{GLOBAL}", "staff": "{STAFF}"}}\n'
    'DiscordConsoleChannelId: ""\n'
    'DiscordChatChannelMinecraftToDiscord: false\n'
    'DiscordChatChannelDiscordToMinecraft: false\n'
    'DiscordChatChannelConsoleCommandEnabled: false\n'
)
LIVE = (
    f'Channels: {{"global": "{GLOBAL}", "staff": "{STAFF}"}}\n'
    f'DiscordConsoleChannelId: "{CONSOLE}"\n'
)
CHAT = (
    "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=SHADOW\n"
    "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MIGRATION_ACK=I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION\n"
    f"ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ROUTES=TEST/global={PIN}\n"
    "ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_APPLICATION_ID=1650000000000000008\n"
    "ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_TOKEN=SYNTHETIC_NONSECRET\n"
)


class PreflightTest(unittest.TestCase):
    def checks(self, paper=PAPER, legacy=LEGACY, live=LIVE, chat=CHAT, environment="production"):
        return dict(CHECK.evaluate(paper, legacy, live, chat, SOURCE_JAVA, environment))

    def test_valid_private_staging_configuration(self):
        self.assertTrue(all(self.checks().values()))

    def test_production_shadow_needs_exact_acknowledgement(self):
        field = (
            "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MIGRATION_ACK="
            "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION\\n"
        )
        missing = CHAT.replace(field, "")
        self.assertFalse(self.checks(chat=missing)[
            "Production SHADOW migration acknowledged when required"
        ])
        invalid = CHAT.replace(
            "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION",
            "I_ACKNOWLEDGE_DISCORDSRV_CHAT_CUTOVER",
        )
        self.assertFalse(self.checks(chat=invalid)[
            "Production SHADOW migration acknowledged when required"
        ])

    def test_staging_mode_does_not_require_production_ack(self):
        chat = CHAT.replace(
            "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MIGRATION_ACK="
            "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION\\n",
            "",
        )
        self.assertTrue(all(self.checks(chat=chat, environment="staging").values()))

    def test_environment_must_be_explicitly_recognized(self):
        with self.assertRaises(ValueError):
            self.checks(environment="")
        with self.assertRaises(ValueError):
            self.checks(environment="PRODUCTION")

    def test_actual_smp_route_cannot_serve_test_backend(self):
        mismatched = CHAT.replace("TEST/global", "SMP/global")
        self.assertFalse(self.checks(chat=mismatched)[
            "StaffBot outbound routes match only this test backend"
        ])

    def test_extra_production_route_fails(self):
        invalid = CHAT.replace(
            f"TEST/global={PIN}", f"TEST/global={PIN};SMP/global={PIN}"
        )
        self.assertFalse(self.checks(chat=invalid)[
            "StaffBot outbound routes match only this test backend"
        ])

    def test_private_channel_must_not_overlap_live_destinations(self):
        live = LIVE.replace(GLOBAL, PIN)
        self.assertFalse(self.checks(live=live)[
            "private channel distinct from legacy chat and console"
        ])

    def test_missing_legacy_channel_map_fails_closed(self):
        legacy = LEGACY.replace(f'Channels: {{"global": "{GLOBAL}", "staff": "{STAFF}"}}\n', '')
        self.assertFalse(self.checks(legacy=legacy)[
            'private channel distinct from legacy chat and console'
        ])

    def test_private_channel_must_not_overlap_test_console(self):
        legacy = LEGACY.replace('DiscordConsoleChannelId: ""',
                                f'DiscordConsoleChannelId: "{PIN}"')
        self.assertFalse(self.checks(legacy=legacy)[
            "private channel distinct from legacy chat and console"
        ])

    def test_legacy_console_requires_disabled_destination(self):
        legacy = LEGACY.replace('DiscordConsoleChannelId: ""',
                                f'DiscordConsoleChannelId: "{CONSOLE}"')
        self.assertFalse(self.checks(legacy=legacy)[
            "Test legacy console Discord destination absent"
        ])

    def test_legacy_chat_requires_both_directions_disabled(self):
        legacy = LEGACY.replace("DiscordChatChannelMinecraftToDiscord: false",
                                "DiscordChatChannelMinecraftToDiscord: true")
        self.assertFalse(self.checks(legacy=legacy)[
            "Test legacy Minecraft-to-Discord forwarding disabled"
        ])

    def test_paper_chat_defaults_off(self):
        paper = PAPER.replace("discord-chat-bridge:\n  mode: SHADOW\n", "")
        self.assertFalse(self.checks(paper=paper)[
            "Paper chat mode explicitly SHADOW"
        ])

    def test_no_ingress_route_during_outbound_only_stage(self):
        chat = CHAT + f"ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_INGRESS_ROUTES={PIN}=TEST/global\n"
        self.assertFalse(self.checks(chat=chat)[
            "StaffBot inbound chat remains disabled"
        ])

    def test_missing_or_duplicate_config_fails_closed(self):
        with self.assertRaises(ValueError):
            CHECK.properties(CHAT + "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=AUTHORITATIVE\n")
        with self.assertRaises(ValueError):
            CHECK.private_channel("enum StaffBotEnvironment { }")


if __name__ == "__main__":
    unittest.main()
