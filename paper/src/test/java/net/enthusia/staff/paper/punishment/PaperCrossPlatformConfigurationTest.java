package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaperCrossPlatformConfigurationTest {
    @Test
    void disabledByDefault() {
        assertTrue(PaperCrossPlatformConfiguration.fromEnvironment(Map.of()).isEmpty());
    }

    @Test
    void canonicalDiscordLimitNamesEnablePaperD08() {
        Map<String, String> values = base();
        values.put(PaperCrossPlatformConfiguration.HELPER_MAX_MUTE_ENV, "600");
        values.put(PaperCrossPlatformConfiguration.MOD_MAX_MUTE_ENV, "86400");
        values.put(PaperCrossPlatformConfiguration.MOD_MAX_BAN_ENV, "604800");
        values.put(PaperCrossPlatformConfiguration.MOD_MAX_RESTRICTION_ENV, "604800");

        PaperCrossPlatformConfiguration configuration =
                PaperCrossPlatformConfiguration.fromEnvironment(values).orElseThrow();

        assertEquals("1410303324745371709", configuration.guildId().value());
        assertEquals(Duration.ofMinutes(10), configuration.authorizationLimits().helperMaxMute());
        assertEquals(Duration.ofDays(1), configuration.authorizationLimits().moderatorMaxMute());
    }

    @Test
    void legacyLimitNamesRemainAcceptedForExistingDeployments() {
        Map<String, String> values = base();
        values.put("ENTHUSIA_STAFF_BOT_HELPER_MAX_MUTE_SECONDS", "600");
        values.put("ENTHUSIA_STAFF_BOT_MOD_MAX_MUTE_SECONDS", "86400");
        values.put("ENTHUSIA_STAFF_BOT_MOD_MAX_BAN_SECONDS", "604800");
        values.put("ENTHUSIA_STAFF_BOT_MOD_MAX_RESTRICTION_SECONDS", "604800");

        assertTrue(PaperCrossPlatformConfiguration.fromEnvironment(values).isPresent());
    }

    @Test
    void enabledConfigurationFailsClosedWhenLimitsAreMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> PaperCrossPlatformConfiguration.fromEnvironment(base()));
    }

    private static Map<String, String> base() {
        Map<String, String> values = new HashMap<>();
        values.put(PaperCrossPlatformConfiguration.ENABLED_ENV, "true");
        values.put(PaperCrossPlatformConfiguration.GUILD_ID_ENV, "1410303324745371709");
        return values;
    }
}
