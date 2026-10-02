package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffModeHandoffTrackerTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSFER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String SMP = SMP;
    private static final String HUB = HUB;

    @Test
    void oneTransferOwnsThePlayerUntilCompletion() {
        StaffModeHandoffTracker tracker = new StaffModeHandoffTracker();

        assertTrue(tracker.begin(PLAYER, SMP, HUB, TRANSFER, NOW));
        assertFalse(tracker.begin(PLAYER, SMP, "TEST", OTHER, NOW));
        assertTrue(tracker.inProgress(PLAYER));
        assertFalse(tracker.completeReady(PLAYER, "TEST", "TEST"));
        assertTrue(tracker.completeReady(PLAYER, "hub", HUB));
        assertFalse(tracker.inProgress(PLAYER));
    }

    @Test
    void timeoutClaimIsTransferFencedAndOneShot() {
        StaffModeHandoffTracker tracker = new StaffModeHandoffTracker();
        tracker.begin(PLAYER, SMP, HUB, TRANSFER, NOW);

        assertTrue(tracker.claimTimedOut(PLAYER, OTHER, NOW.plusSeconds(13)).isEmpty());
        assertTrue(tracker.claimTimedOut(PLAYER, TRANSFER, NOW.plusSeconds(11)).isEmpty());
        assertTrue(tracker.claimTimedOut(PLAYER, TRANSFER, NOW.plusSeconds(12)).isPresent());
        assertTrue(tracker.claimTimedOut(PLAYER, TRANSFER, NOW.plusSeconds(13)).isEmpty());
    }

    @Test
    void retryKeepsIdentityAndAdvancesAttempt() {
        StaffModeHandoffTracker tracker = new StaffModeHandoffTracker();
        tracker.begin(PLAYER, SMP, HUB, TRANSFER, NOW);
        var first = tracker.claimTimedOut(PLAYER, TRANSFER, NOW.plusSeconds(12)).orElseThrow();

        tracker.retry(first, NOW.plusSeconds(12));
        var second = tracker.claimTimedOut(PLAYER, TRANSFER, NOW.plusSeconds(24)).orElseThrow();

        assertTrue(second.retryCount() == 1);
        assertTrue(second.transferId().equals(TRANSFER));
    }
}
