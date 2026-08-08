package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeBaseOperationTest {
    private static final Instant START = Instant.parse("2026-08-07T20:00:00Z");

    @Test
    void warningFiresOncePerExpiryWindowAndExtendResetsIt() {
        FakeBaseOperation operation = operation();

        assertFalse(operation.markWarningIfDue(START.plusSeconds(239), Duration.ofMinutes(1)));
        assertTrue(operation.markWarningIfDue(START.plusSeconds(240), Duration.ofMinutes(1)));
        assertFalse(operation.markWarningIfDue(START.plusSeconds(250), Duration.ofMinutes(1)));

        assertTrue(operation.extend(START.plusSeconds(250), Duration.ofMinutes(5)));
        assertEquals(300L, operation.remainingSeconds(START.plusSeconds(250)));
        assertFalse(operation.markWarningIfDue(START.plusSeconds(489), Duration.ofMinutes(1)));
        assertTrue(operation.markWarningIfDue(START.plusSeconds(490), Duration.ofMinutes(1)));
    }

    @Test
    void closeAndViewerCleanupStateAreIdempotent() {
        FakeBaseOperation operation = operation();
        UUID viewer = UUID.randomUUID();

        assertTrue(operation.addViewerIfOpen(viewer));
        assertTrue(operation.viewersSnapshot().contains(viewer));
        assertTrue(operation.close());
        assertFalse(operation.close());
        assertFalse(operation.addViewerIfOpen(UUID.randomUUID()));
    }

    @Test
    void expiryIsInclusiveAtDeadline() {
        FakeBaseOperation operation = operation();
        assertFalse(operation.expired(START.plusSeconds(299)));
        assertTrue(operation.expired(START.plusSeconds(300)));
        assertEquals(0L, operation.remainingSeconds(START.plusSeconds(300)));
    }

    private static FakeBaseOperation operation() {
        return new FakeBaseOperation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new FakeBasePlacementPlanner.Anchor(8, 64, 8),
                START,
                Duration.ofMinutes(5)
        );
    }
}
