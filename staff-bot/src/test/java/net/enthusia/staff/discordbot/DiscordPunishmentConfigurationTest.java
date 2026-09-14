package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscordPunishmentConfigurationTest {
    @Test
    void enforcementIsOptInAndHasNoImplicitAuthorityCeilings() {
        assertTrue(DiscordPunishmentConfiguration.fromEnvironment(Map.of()).isEmpty());
        assertTrue(DiscordPunishmentConfiguration.fromEnvironment(Map.of(
                DiscordPunishmentConfiguration.ENABLED_ENV, "false"
        )).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> DiscordPunishmentConfiguration.fromEnvironment(Map.of(
                DiscordPunishmentConfiguration.ENABLED_ENV, "true"
        )));
    }

    @Test
    void parsesExplicitSafeConfiguration() {
        Map<String, String> values = validValues();
        DiscordPunishmentConfiguration configuration = DiscordPunishmentConfiguration
                .fromEnvironment(values).orElseThrow();

        assertEquals("123", configuration.muteRoleId());
        assertEquals(java.util.Set.of("456", "789"), configuration.supportScopeIds());
        assertEquals(Duration.ofMinutes(10), configuration.authorizationLimits().helperMaxMute());
        assertEquals(Duration.ofSeconds(60), configuration.reconciliationInterval());
        assertEquals(Duration.ofMillis(250), configuration.workerInterval());
    }

    @Test
    void rejectsInvalidSnowflakesAndAuthorityInversion() {
        Map<String, String> invalidRole = validValues();
        invalidRole.put(DiscordPunishmentConfiguration.MUTE_ROLE_ENV, "role-name");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordPunishmentConfiguration.fromEnvironment(invalidRole));

        Map<String, String> inverted = validValues();
        inverted.put(DiscordPunishmentConfiguration.HELPER_MAX_MUTE_ENV, "7200");
        inverted.put(DiscordPunishmentConfiguration.MOD_MAX_MUTE_ENV, "3600");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordPunishmentConfiguration.fromEnvironment(inverted));
    }

    private static Map<String, String> validValues() {
        Map<String, String> values = new HashMap<>();
        values.put(DiscordPunishmentConfiguration.ENABLED_ENV, "true");
        values.put(DiscordPunishmentConfiguration.MUTE_ROLE_ENV, "123");
        values.put(DiscordPunishmentConfiguration.SUPPORT_SCOPES_ENV, "456, 789");
        values.put(DiscordPunishmentConfiguration.SUPPORT_MESSAGE_ENV, "Use support for help.");
        values.put(DiscordPunishmentConfiguration.HELPER_MAX_MUTE_ENV, "600");
        values.put(DiscordPunishmentConfiguration.MOD_MAX_MUTE_ENV, "86400");
        values.put(DiscordPunishmentConfiguration.MOD_MAX_BAN_ENV, "604800");
        values.put(DiscordPunishmentConfiguration.MOD_MAX_RESTRICTION_ENV, "604800");
        values.put(DiscordPunishmentConfiguration.RECONCILE_SECONDS_ENV, "60");
        values.put(DiscordPunishmentConfiguration.WORKER_MILLIS_ENV, "250");
        return values;
    }
}
