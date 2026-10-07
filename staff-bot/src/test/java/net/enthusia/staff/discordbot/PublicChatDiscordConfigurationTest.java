package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicChatDiscordConfigurationTest {
    @Test
    void disabledBridgeNeedsNoPublicIdentity() {
        assertTrue(PublicChatDiscordConfiguration.fromEnvironment(Map.of(), false).isEmpty());
    }

    @Test
    void disabledBridgeAllowsCompleteDormantIdentityButRejectsPartialConfig() {
        assertTrue(PublicChatDiscordConfiguration.fromEnvironment(
                Map.of(
                        PublicChatDiscordConfiguration.TOKEN_ENV, "not-used-yet",
                        PublicChatDiscordConfiguration.APPLICATION_ID_ENV, "123456789012345678"
                ),
                false
        ).isEmpty());

        assertThrows(IllegalArgumentException.class, () ->
                PublicChatDiscordConfiguration.fromEnvironment(
                        Map.of(PublicChatDiscordConfiguration.TOKEN_ENV, "not-used"),
                        false
                ));
    }

    @Test
    void enabledBridgeRequiresTokenAndApplicationId() {
        assertThrows(IllegalArgumentException.class, () ->
                PublicChatDiscordConfiguration.fromEnvironment(Map.of(), true));
        assertThrows(IllegalArgumentException.class, () ->
                PublicChatDiscordConfiguration.fromEnvironment(
                        Map.of(PublicChatDiscordConfiguration.TOKEN_ENV, "public-token"),
                        true
                ));
        assertThrows(IllegalArgumentException.class, () ->
                PublicChatDiscordConfiguration.fromEnvironment(
                        Map.of(
                                PublicChatDiscordConfiguration.TOKEN_ENV, "public-token",
                                PublicChatDiscordConfiguration.APPLICATION_ID_ENV, "not-a-snowflake"
                        ),
                        true
                ));
    }

    @Test
    void enabledBridgeParsesAndRedactsPublicIdentity() {
        PublicChatDiscordConfiguration configuration =
                PublicChatDiscordConfiguration.fromEnvironment(
                        Map.of(
                                PublicChatDiscordConfiguration.TOKEN_ENV, "public-token-secret",
                                PublicChatDiscordConfiguration.APPLICATION_ID_ENV, "123456789012345678"
                        ),
                        true
                ).orElseThrow();

        assertEquals("public-token-secret", configuration.token());
        assertEquals(123456789012345678L, configuration.applicationId());
        assertFalse(configuration.toString().contains("public-token-secret"));
        assertTrue(configuration.toString().contains("token=<redacted>"));
    }
}
