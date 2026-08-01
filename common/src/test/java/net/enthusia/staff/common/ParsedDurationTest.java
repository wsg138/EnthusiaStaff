package net.enthusia.staff.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParsedDurationTest {
    @Test
    void permanentHasNoTemporaryValue() {
        ParsedDuration duration = ParsedDuration.permanent();

        assertTrue(duration.isPermanent());
        assertTrue(duration.temporary().isEmpty());
    }

    @Test
    void temporaryRetainsItsPositiveValue() {
        Duration value = Duration.ofMinutes(15);
        ParsedDuration duration = ParsedDuration.temporary(value);

        assertFalse(duration.isPermanent());
        assertEquals(Optional.of(value), duration.temporary());
    }

    @Test
    void recordRejectsANullOptional() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedDuration(null));
    }

    @Test
    void temporaryRejectsZeroAndNegativeDurations() {
        assertThrows(IllegalArgumentException.class, () -> ParsedDuration.temporary(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ParsedDuration.temporary(Duration.ofSeconds(-1)));
    }
}
