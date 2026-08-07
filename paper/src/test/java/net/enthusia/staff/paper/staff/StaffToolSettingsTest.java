package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaffToolSettingsTest {
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
}
