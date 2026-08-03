package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CaseCommandDurationTest {
    @Test
    void formatsWholeDaysForStaff() {
        assertEquals("7 days", CaseCommand.humanDuration(Duration.ofDays(7)));
    }

    @Test
    void formatsMixedDurationParts() {
        assertEquals(
                "1 day 2 hours 3 minutes 4 seconds",
                CaseCommand.humanDuration(
                        Duration.ofDays(1)
                                .plusHours(2)
                                .plusMinutes(3)
                                .plusSeconds(4)
                )
        );
    }

    @Test
    void formatsZeroAndRejectsNegativeDurations() {
        assertEquals("0 seconds", CaseCommand.humanDuration(Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> CaseCommand.humanDuration(Duration.ofSeconds(-1))
        );
    }
}
