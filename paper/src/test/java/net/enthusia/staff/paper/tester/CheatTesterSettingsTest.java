package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void acceptsDocumentedSafeBoundaryValues() {
        assertDoesNotThrow(() -> new CheatTesterSettings(
                Duration.ofSeconds(1), 1, 1, 1.0D, 0.0D, 0.0D, 0.1D, 10
        ));
        assertDoesNotThrow(() -> new CheatTesterSettings(
                Duration.ofSeconds(15), 32, 32, 8.0D, 2.0D, 2.0D, 2.0D, 300
        ));
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

    @Test
    void rejectsNonFiniteProbeValues() {
        assertThrows(IllegalArgumentException.class, () -> new CheatTesterSettings(
                Duration.ofSeconds(4), 8, 2, Double.NaN, 0.75D, 0.30D, 0.70D, 60
        ));
        assertThrows(IllegalArgumentException.class, () -> new CheatTesterSettings(
                Duration.ofSeconds(4), 8, 2, 3.0D, Double.POSITIVE_INFINITY, 0.30D, 0.70D, 60
        ));
        assertThrows(IllegalArgumentException.class, () -> new CheatTesterSettings(
                Duration.ofSeconds(4), 8, 2, 3.0D, 0.75D, Double.NEGATIVE_INFINITY, 0.70D, 60
        ));
        assertThrows(IllegalArgumentException.class, () -> new CheatTesterSettings(
                Duration.ofSeconds(4), 8, 2, 3.0D, 0.75D, 0.30D, Double.NaN, 60
        ));
    }
}
