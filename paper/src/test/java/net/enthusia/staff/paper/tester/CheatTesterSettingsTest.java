package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheatTesterSettingsTest {
    @Test
    void defaultsStayInsideReleaseSafetyBounds() {
        CheatTesterSettings settings = CheatTesterSettings.defaults();
        assertEquals(Duration.ofSeconds(4), settings.sessionTimeout());
        assertEquals(8, settings.maximumActiveGlobal());
        assertEquals(2, settings.maximumActivePerStaff());
        assertEquals(60, settings.probeTicks());
    }

    @Test
    void rejectsUnsafeTimeoutAndPerStaffLimit() {
        assertThrows(IllegalArgumentException.class, () -> new CheatTesterSettings(
                Duration.ofSeconds(30), 8, 2, 3.0, 0.75, 0.30, 0.70, 60
        ));
        assertThrows(IllegalArgumentException.class, () -> new CheatTesterSettings(
                Duration.ofSeconds(4), 2, 3, 3.0, 0.75, 0.30, 0.70, 60
        ));
    }
}
