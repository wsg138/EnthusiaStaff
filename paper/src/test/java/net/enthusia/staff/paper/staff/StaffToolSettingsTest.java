package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StaffToolSettingsTest {
    private static final String RANDOM_COOLDOWN = "staff-tools.cooldowns.random-teleport-millis";
    private static final String TARGET_COOLDOWN = "staff-tools.cooldowns.target-tool-millis";
    private static final String TOGGLE_COOLDOWN = "staff-tools.cooldowns.toggle-tool-millis";
    private static final String MENU_COOLDOWN = "staff-tools.cooldowns.menu-millis";

    @Test
    void disabledServersAndWorldsAreCaseInsensitive() {
        StaffToolSettings settings = new StaffToolSettings(
                Set.of(" HUB "),
                Set.of(" StaffWorld "),
                Duration.ofSeconds(2),
                Duration.ofMillis(750),
                Duration.ofMillis(500),
                Duration.ofMillis(500)
        );

        assertFalse(settings.randomTeleportEnabledOn("hub"));
        assertFalse(settings.worldEnabled("staffworld"));
        assertTrue(settings.randomTeleportEnabledOn("SMP"));
        assertTrue(settings.worldEnabled("world"));
    }

    @Test
    void cooldownClassesResolveToConfiguredDurations() {
        StaffToolSettings settings = new StaffToolSettings(
                Set.of(),
                Set.of(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofMillis(250)
        );

        assertEquals(Duration.ofSeconds(3), settings.cooldownFor(StaffToolDefinition.RANDOM_TELEPORT));
        assertEquals(Duration.ofSeconds(2), settings.cooldownFor(StaffToolDefinition.FREEZE));
        assertEquals(Duration.ofSeconds(1), settings.cooldownFor(StaffToolDefinition.VANISH));
        assertEquals(Duration.ofMillis(250), settings.cooldownFor(StaffToolDefinition.STAFF_TOOLS));
    }

    @Test
    void cooldownConfigurationAcceptsInclusiveBoundaries() {
        assertEquals(
                Duration.ZERO,
                StaffToolSettings.load(configuration(RANDOM_COOLDOWN, 0L))
                        .cooldownFor(StaffToolDefinition.RANDOM_TELEPORT)
        );
        assertEquals(
                Duration.ofMinutes(1),
                StaffToolSettings.load(configuration(RANDOM_COOLDOWN, 60_000L))
                        .cooldownFor(StaffToolDefinition.RANDOM_TELEPORT)
        );
        assertEquals(
                Duration.ZERO,
                StaffToolSettings.load(configuration(TARGET_COOLDOWN, 0L))
                        .cooldownFor(StaffToolDefinition.FREEZE)
        );
        assertEquals(
                Duration.ofMinutes(1),
                StaffToolSettings.load(configuration(TARGET_COOLDOWN, 60_000L))
                        .cooldownFor(StaffToolDefinition.FREEZE)
        );
        assertEquals(
                Duration.ZERO,
                StaffToolSettings.load(configuration(TOGGLE_COOLDOWN, 0L))
                        .cooldownFor(StaffToolDefinition.VANISH)
        );
        assertEquals(
                Duration.ofMinutes(1),
                StaffToolSettings.load(configuration(TOGGLE_COOLDOWN, 60_000L))
                        .cooldownFor(StaffToolDefinition.VANISH)
        );
        assertEquals(
                Duration.ZERO,
                StaffToolSettings.load(configuration(MENU_COOLDOWN, 0L))
                        .cooldownFor(StaffToolDefinition.STAFF_TOOLS)
        );
        assertEquals(
                Duration.ofMinutes(1),
                StaffToolSettings.load(configuration(MENU_COOLDOWN, 60_000L))
                        .cooldownFor(StaffToolDefinition.STAFF_TOOLS)
        );
    }

    @Test
    void cooldownConfigurationRejectsValuesOutsideInclusiveBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(RANDOM_COOLDOWN, -1L)));
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(RANDOM_COOLDOWN, 60_001L)));
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(TARGET_COOLDOWN, -1L)));
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(TARGET_COOLDOWN, 60_001L)));
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(TOGGLE_COOLDOWN, -1L)));
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(TOGGLE_COOLDOWN, 60_001L)));
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(MENU_COOLDOWN, -1L)));
        assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(configuration(MENU_COOLDOWN, 60_001L)));
    }

    private static YamlConfiguration configuration(String path, long value) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set(path, value);
        return configuration;
    }
}
