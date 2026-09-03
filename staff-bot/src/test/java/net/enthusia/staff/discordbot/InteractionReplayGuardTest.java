package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InteractionReplayGuardTest {
    @Test
    void rejectsDuplicateUntilTtlExpires() {
        InteractionReplayGuard guard = new InteractionReplayGuard(16, Duration.ofSeconds(10));
        Instant start = Instant.parse("2026-08-24T00:00:00Z");

        assertEquals(InteractionReplayGuard.ClaimResult.CLAIMED, guard.claim(100L, start));
        assertEquals(InteractionReplayGuard.ClaimResult.DUPLICATE, guard.claim(100L, start.plusSeconds(9)));
        assertEquals(InteractionReplayGuard.ClaimResult.CLAIMED, guard.claim(100L, start.plusSeconds(10)));
    }

    @Test
    void failsClosedAtCapacityAndAllowsExplicitSafeRelease() {
        InteractionReplayGuard guard = new InteractionReplayGuard(1, Duration.ofMinutes(1));
        Instant start = Instant.parse("2026-08-24T00:00:00Z");

        assertEquals(InteractionReplayGuard.ClaimResult.CLAIMED, guard.claim(1L, start));
        assertEquals(InteractionReplayGuard.ClaimResult.SATURATED, guard.claim(2L, start));
        assertTrue(guard.release(1L));
        assertFalse(guard.release(1L));
        assertEquals(InteractionReplayGuard.ClaimResult.CLAIMED, guard.claim(2L, start));
    }

    @Test
    void prunesExpiredEntriesWithoutUnboundedGrowth() {
        InteractionReplayGuard guard = new InteractionReplayGuard(2, Duration.ofSeconds(1));
        Instant start = Instant.parse("2026-08-24T00:00:00Z");
        guard.claim(1L, start);
        guard.claim(2L, start);

        assertEquals(0, guard.size(start.plusSeconds(1)));
    }
}
