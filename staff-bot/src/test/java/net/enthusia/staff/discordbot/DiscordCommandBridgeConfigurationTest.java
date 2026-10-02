package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscordCommandBridgeConfigurationTest {
    private static final String SECRET = "s".repeat(40);

    @Test
    void absentConfigurationKeepsBridgeDisabled() {
        assertTrue(DiscordCommandBridgeConfiguration.fromEnvironment(Map.of()).isEmpty());
    }

    @Test
    void completeConfigurationCreatesExplicitTargetAllowlistAndRedactsSecret() {
        Map<String, String> values = new HashMap<>();
        values.put(DiscordCommandBridgeConfiguration.ENDPOINTS_ENV,
                "SMP=http://10.0.0.5:8772/v1/discord-command;HUB=https://hub.internal/v1/discord-command");
        values.put(DiscordCommandBridgeConfiguration.CREDENTIAL_ENV, SECRET);
        values.put(DiscordCommandBridgeConfiguration.TIMEOUT_MILLIS_ENV, "2500");

        DiscordCommandBridgeConfiguration configuration =
                DiscordCommandBridgeConfiguration.fromEnvironment(values).orElseThrow();

        assertEquals(java.util.Set.of("SMP", "HUB"), configuration.endpoints().keySet());
        assertEquals(Duration.ofMillis(2500), configuration.timeout());
        assertFalse(configuration.toString().contains(SECRET));
    }

    @Test
    void partialOrUnsafeConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                DiscordCommandBridgeConfiguration.fromEnvironment(Map.of(
                        DiscordCommandBridgeConfiguration.ENDPOINTS_ENV,
                        "SMP=http://127.0.0.1:8772/v1/discord-command"
                )));
        assertThrows(IllegalArgumentException.class, () ->
                DiscordCommandBridgeConfiguration.fromEnvironment(Map.of(
                        DiscordCommandBridgeConfiguration.ENDPOINTS_ENV,
                        "SMP=https://example.invalid/wrong",
                        DiscordCommandBridgeConfiguration.CREDENTIAL_ENV,
                        SECRET
                )));
        assertThrows(IllegalArgumentException.class, () ->
                DiscordCommandBridgeConfiguration.fromEnvironment(Map.of(
                        DiscordCommandBridgeConfiguration.ENDPOINTS_ENV,
                        "SMP=http://127.0.0.1:8772/v1/discord-command",
                        DiscordCommandBridgeConfiguration.CREDENTIAL_ENV,
                        "weak"
                )));
    }
}
