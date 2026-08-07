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
    private static final String[] COOLDOWN_PATHS = {
            "staff-tools.cooldowns.random-teleport-millis",
            "staff-tools.cooldowns.target-tool-millis",
            "staff-tools.cooldowns.toggle-tool-millis",
            "staff-tools.cooldowns.menu-millis"
    };

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
        for (String path : COOLDOWN_PATHS) {
            YamlConfiguration zero = new YamlConfiguration();
            zero.set(path, 0L);
            StaffToolSettings.load(zero);

            YamlConfiguration maximum = new YamlConfiguration();
            maximum.set(path, 60_000L);
            StaffToolSettings.load(maximum);
        }
    }

    @Test
    void cooldownConfigurationRejectsValuesOutsideInclusiveBoundaries() {
        for (String path : COOLDOWN_PATHS) {
            YamlConfiguration negative = new YamlConfiguration();
            negative.set(path, -1L);
            assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(negative));

            YamlConfiguration aboveMaximum = new YamlConfiguration();
            aboveMaximum.set(path, 60_001L);
            assertThrows(IllegalArgumentException.class, () -> StaffToolSettings.load(aboveMaximum));
        }
    }
}
