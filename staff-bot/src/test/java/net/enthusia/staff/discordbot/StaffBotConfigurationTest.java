package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffBotConfigurationTest {
    private static final String STAGING = "staging";
    private static final String DUMMY_TOKEN = "token";

    @Test
    void stagingUsesFixedIdentityAndRedactsToken() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffBotConfiguration.ENVIRONMENT_KEY, STAGING);
        values.put(StaffBotConfiguration.TOKEN_KEY, "secret-token-value");
        values.put(StaffBotConfiguration.HEALTH_PORT_KEY, "0");

        StaffBotConfiguration configuration = StaffBotConfiguration.fromEnvironment(values);

        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertEquals(1541279616881397772L, configuration.environment().applicationId());
        assertEquals(1410303324745371709L, configuration.environment().guildId());
        assertEquals(0, configuration.healthAddress().getPort());
        assertFalse(configuration.uiPreviewEnabled());
        assertFalse(configuration.toString().contains("secret-token-value"));
        assertTrue(configuration.toString().contains("discordToken=<redacted>"));
    }

    @Test
    void previewDedicatedHostNeedsOnlyTokenEnvironmentAndFlag() {
        StaffBotConfiguration configuration = StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, "true"));

        assertTrue(configuration.uiPreviewEnabled());
        assertEquals(StaffBotEnvironment.STAGING, configuration.environment());
        assertEquals(8765, configuration.healthAddress().getPort());
    }

    @Test
    void previewRequiresExplicitStagingPair() {
        StaffBotConfiguration staging = StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, "true",
                StaffBotConfiguration.HEALTH_PORT_KEY, "0"));

        assertTrue(staging.uiPreviewEnabled());
        assertThrows(IllegalArgumentException.class, () -> StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, "production",
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, "true")));
        assertThrows(IllegalArgumentException.class, () -> StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                StaffBotConfiguration.UI_PREVIEW_KEY, "yes")));
    }

    @Test
    void productionRejectsEphemeralHealthPort() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffBotConfiguration.ENVIRONMENT_KEY, "production");
        values.put(StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN);
        values.put(StaffBotConfiguration.HEALTH_PORT_KEY, "0");

        assertThrows(IllegalArgumentException.class, () -> StaffBotConfiguration.fromEnvironment(values));
    }

    @Test
    void rejectsMissingSecretAndNonLoopbackHealthBinding() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING)));

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                        StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                        StaffBotConfiguration.HEALTH_HOST_KEY, "0.0.0.0")));
    }

    @Test
    void validatesNumericBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                        StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                        StaffBotConfiguration.WORKER_THREADS_KEY, "0")));
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotConfiguration.fromEnvironment(Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, STAGING,
                        StaffBotConfiguration.TOKEN_KEY, DUMMY_TOKEN,
                        StaffBotConfiguration.INTERACTION_TTL_SECONDS_KEY, "86401")));
    }
}
