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


PRODUCTION_CHAT = "ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_APPLICATION_ID=1650000000000000009\n"


VELOCITY = """channel.enabled=true
channel.backend.STAFFBOT.secret-environment=ES_CHANNEL_STAFFBOT_SECRET
channel.backend.TEST.secret-environment=ES_CHANNEL_TEST_SECRET
channel.backend.STAFFBOT_TEST.secret-environment=ES_CHANNEL_STAFFBOT_TEST_SECRET
"""


class PreflightTest(unittest.TestCase):
    def checks(self, paper=PAPER, legacy=LEGACY, live=LIVE, chat=CHAT, environment="production"):
        return dict(CHECK.evaluate(paper, legacy, live, chat, SOURCE_JAVA, environment))

    def isolated_checks(self, velocity=VELOCITY, environment="staging",
                        production_chat=PRODUCTION_CHAT):
        return dict(CHECK.evaluate(
            PAPER, LEGACY, LIVE, CHAT, SOURCE_JAVA, environment,
            velocity_config=velocity, isolated_test=True,
            production_chat=production_chat,
        ))

    def test_isolated_peer_only_passes_when_explicitly_configured(self):
        self.assertTrue(all(self.isolated_checks().values()))
        missing = VELOCITY.replace(
            "channel.backend.STAFFBOT_TEST.secret-environment=ES_CHANNEL_STAFFBOT_TEST_SECRET\n", ""
        )
        self.assertFalse(self.isolated_checks(velocity=missing)[
            "Velocity distinct STAFFBOT_TEST peer secret identifier configured"
        ])

    def test_isolated_peer_key_identifier_must_differ_from_existing_peers(self):
        same_staffbot = VELOCITY.replace(
            "ES_CHANNEL_STAFFBOT_TEST_SECRET", "ES_CHANNEL_STAFFBOT_SECRET"
        )
        self.assertFalse(self.isolated_checks(velocity=same_staffbot)[
            "Velocity distinct STAFFBOT_TEST peer secret identifier configured"
        ])
        same_paper = VELOCITY.replace(
            "ES_CHANNEL_STAFFBOT_TEST_SECRET", "ES_CHANNEL_TEST_SECRET"
        )
        self.assertFalse(self.isolated_checks(velocity=same_paper)[
            "Velocity distinct STAFFBOT_TEST peer secret identifier configured"
        ])

    def test_isolated_peer_identifier_cannot_match_another_authenticated_backend(self):
        extra_backend = VELOCITY + (
            "channel.backend.HUB.secret-environment=ES_CHANNEL_STAFFBOT_TEST_SECRET\n"
        )
        self.assertFalse(self.isolated_checks(velocity=extra_backend)[
            "Velocity distinct STAFFBOT_TEST peer secret identifier configured"
        ])

    def test_isolated_peer_requires_auth_channel_and_main_peers(self):
        disabled = VELOCITY.replace("channel.enabled=true", "channel.enabled=false")
        self.assertFalse(self.isolated_checks(velocity=disabled)[
            "Velocity authenticated channel enabled"
        ])
        without_primary = VELOCITY.replace(
            "channel.backend.STAFFBOT.secret-environment=ES_CHANNEL_STAFFBOT_SECRET\n", ""
        )
        self.assertFalse(self.isolated_checks(velocity=without_primary)[
            "Velocity retains STAFFBOT and TEST peer mappings"
        ])

    def test_isolated_peer_missing_test_backend_mapping_fails(self):
        without_test = VELOCITY.replace(
            "channel.backend.TEST.secret-environment=ES_CHANNEL_TEST_SECRET\n", ""
        )
        self.assertFalse(self.isolated_checks(velocity=without_test)[
            "Velocity retains STAFFBOT and TEST peer mappings"
        ])

    def test_commented_isolated_peer_does_not_count_as_configured(self):
        commented = VELOCITY.replace(
            "channel.backend.STAFFBOT_TEST.secret-environment=",
            "# channel.backend.STAFFBOT_TEST.secret-environment=",
        )
        self.assertFalse(self.isolated_checks(velocity=commented)[
            "Velocity distinct STAFFBOT_TEST peer secret identifier configured"
        ])

    def test_duplicate_active_velocity_key_fails_closed(self):
        duplicate = VELOCITY + (
            "channel.backend.STAFFBOT_TEST.secret-environment=ES_OTHER_TEST_SECRET\n"
        )
        with self.assertRaises(ValueError):
            self.isolated_checks(velocity=duplicate)

    def test_isolated_peer_identifier_must_be_valid_environment_name(self):
        malformed = VELOCITY.replace(
            "ES_CHANNEL_STAFFBOT_TEST_SECRET", "not-a-valid-env-name"
        )
        self.assertFalse(self.isolated_checks(velocity=malformed)[
            "Velocity distinct STAFFBOT_TEST peer secret identifier configured"
        ])

    def test_isolated_chat_app_differs_from_production_chat_app(self):
        check = "Isolated chat application differs from current production public-chat application"
        self.assertTrue(self.isolated_checks()[check])
        same_app = PRODUCTION_CHAT.replace("1650000000000000009", "1650000000000000008")
        self.assertFalse(self.isolated_checks(production_chat=same_app)[check])
        self.assertFalse(self.isolated_checks(production_chat="")[check])
        self.assertFalse(self.isolated_checks(production_chat=
            "ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_APPLICATION_ID=invalid\\n")[check])

    def test_duplicate_production_chat_app_property_fails_closed(self):
        with self.assertRaises(ValueError):
            self.isolated_checks(production_chat=PRODUCTION_CHAT + PRODUCTION_CHAT)

    def test_isolated_mode_requires_read_only_production_chat_input(self):
        with self.assertRaises(ValueError):
            CHECK.evaluate(PAPER, LEGACY, LIVE, CHAT, SOURCE_JAVA,
                           "staging", velocity_config=VELOCITY, isolated_test=True)

    def test_isolated_mode_cannot_use_production_environment_or_missing_proxy(self):
        with self.assertRaises(ValueError):
            self.isolated_checks(environment="production")
        with self.assertRaises(ValueError):
            CHECK.evaluate(PAPER, LEGACY, LIVE, CHAT, SOURCE_JAVA,
                           "staging", isolated_test=True)

    def test_existing_production_preflight_does_not_require_isolated_peer(self):
        self.assertTrue(all(self.checks().values()))
        self.assertEqual(14, len(self.checks()))

    def test_valid_private_staging_configuration(self):
        self.assertTrue(all(self.checks().values()))

    def test_production_shadow_needs_exact_acknowledgement(self):
        field = (
            "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MIGRATION_ACK="
            "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION\n"
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
            "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION\n",
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

    def test_unrelated_backend_id_does_not_pass_network_check(self):
        paper = PAPER.replace(
            "network:\n  server-id: TEST\n",
            "some-other-section:\n  server-id: TEST\n",
        )
        checks = self.checks(paper=paper)
        self.assertFalse(checks["Paper reports a non-empty backend ID"])
        self.assertFalse(checks["StaffBot outbound routes match only this test backend"])

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
