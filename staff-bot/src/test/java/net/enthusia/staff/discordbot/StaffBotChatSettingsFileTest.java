package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffBotChatSettingsFileTest {
    private static final String BRIDGE_MODE = StaffBotChatBridgeConfiguration.MODE_ENV;
    private static final String CHAT_TOKEN = PublicChatDiscordConfiguration.TOKEN_ENV;
    private static final String CHAT_APP_ID = PublicChatDiscordConfiguration.APPLICATION_ID_ENV;

    @TempDir
    Path tempDir;

    @Test
    void overlaysOnlyChatRelatedPropertiesWithoutChangingStaffIdentity() throws IOException {
        Path file = write("chat-bridge.properties",
                BRIDGE_MODE + "=SHADOW\n"
                + CHAT_TOKEN + "=dummy-chat-token\n"
                + CHAT_APP_ID + "=1405605074331373718\n");
        Map<String, String> values = StaffBotChatSettingsFile.overlay(file, Map.of(
                StaffBotConfiguration.TOKEN_KEY, "dummy-existing-staff-token",
                StaffBotConfiguration.ENVIRONMENT_KEY, "production"));

        assertEquals("production", values.get(StaffBotConfiguration.ENVIRONMENT_KEY));
        assertEquals("dummy-existing-staff-token", values.get(StaffBotConfiguration.TOKEN_KEY));
        assertEquals("SHADOW", values.get(BRIDGE_MODE));
        assertEquals("dummy-chat-token", values.get(CHAT_TOKEN));
        assertEquals("1405605074331373718", values.get(CHAT_APP_ID));
        assertThrows(UnsupportedOperationException.class,
                () -> values.put(BRIDGE_MODE, "AUTHORITATIVE"));
    }

    @Test
    void productionShadowParsesThroughExistingIdentityAndRouteGates() throws IOException {
        String testKey = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        Path file = write("private-chat-bridge.properties",
                BRIDGE_MODE + "=SHADOW\n"
                + StaffBotChatBridgeConfiguration.MIGRATION_ACK_ENV
                        + "=I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION\n"
                + StaffBotChatBridgeConfiguration.HOST_ENV + "=velocity.example.test\n"
                + StaffBotChatBridgeConfiguration.PORT_ENV + "=28765\n"
                + StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV + "=" + testKey + "\n"
                + StaffBotChatBridgeConfiguration.PROXY_HMAC_ENV + "=" + testKey + "\n"
                + StaffBotChatBridgeConfiguration.TRUST_STORE_ENV + "=local-trust.p12\n"
                + StaffBotChatBridgeConfiguration.TRUST_STORE_ACCESS_ENV + "=dummy-test-password\n"
                + StaffBotChatBridgeConfiguration.ROUTES_ENV
                        + "=SMP/global=1541286004298752091\n"
                + CHAT_TOKEN + "=dummy-separate-public-chat-token\n"
                + CHAT_APP_ID + "=1405605074331373718\n");
        Map<String, String> settings = StaffBotChatSettingsFile.overlay(file, Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, "production"));

        StaffBotChatBridgeConfiguration bridge = StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.PRODUCTION, settings).orElseThrow();
        PublicChatDiscordConfiguration publicChat =
                PublicChatDiscordConfiguration.fromEnvironment(settings, true).orElseThrow();
        assertEquals(StaffBotChatBridgeConfiguration.Mode.SHADOW, bridge.mode());
        assertTrue(bridge.ingressRoutes().isEmpty());
        assertEquals(1405605074331373718L, publicChat.applicationId());
    }

    @Test
    void rejectsUnsupportedKeysIncludingModerationToken() throws IOException {
        Path file = write("chat-bridge.properties",
                BRIDGE_MODE + "=SHADOW\n"
                + StaffBotConfiguration.TOKEN_KEY + "=malicious-moderator-token\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(file, Map.of()));
        assertFalse(error.getMessage().contains("malicious-moderator-token"));
    }

    @Test
    void rejectsDuplicateKeysInsteadOfUsingLastValue() throws IOException {
        Path file = write("duplicate.properties", BRIDGE_MODE + "=SHADOW\n"
                + BRIDGE_MODE + "=AUTHORITATIVE\n");
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(file, Map.of()));
    }

    @Test
    void rejectsAmbiguousEnvironmentOrFileValues() throws IOException {
        Path file = write("chat-bridge.properties", BRIDGE_MODE + "=SHADOW\n");
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(file, Map.of(BRIDGE_MODE, "DISABLED")));
    }

    @Test
    void rejectsAbsentModeEmptyValuesAndEmptyFiles() throws IOException {
        Path missingMode = write("no-mode.properties", CHAT_TOKEN + "=dummy\n");
        Path empty = write("empty.properties", "");
        Path blank = write("blank.properties", BRIDGE_MODE + "=\n");
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(missingMode, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(empty, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(blank, Map.of()));
    }

    @Test
    void missingAndOversizedFilesFailClosedWithoutIncludingPrivateValues() throws IOException {
        Path file = write("oversized.properties", BRIDGE_MODE + "=SHADOW\n" + "a".repeat(17_000));
        IllegalArgumentException tooLarge = assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(file, Map.of()));
        assertTrue(tooLarge.getMessage().contains("too large"));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBotChatSettingsFile.overlay(tempDir.resolve("does-not-exist"), Map.of()));
    }

    private Path write(String name, String value) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, value);
        return file;
    }
}
