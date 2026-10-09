package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffRecoveryRetryRegistryTest {
    @Test
    void repeatedRecoverySchedulesOnlyOneCallbackUntilConsumed() {
        StaffRecoveryRetryRegistry retries = new StaffRecoveryRetryRegistry();
        UUID player = UUID.randomUUID();
        UUID ticket = retries.begin(player).orElseThrow();
        assertTrue(retries.begin(player).isEmpty());
        assertTrue(retries.consume(player, ticket));
        assertFalse(retries.consume(player, ticket));
        assertTrue(retries.begin(player).isPresent());
    }

    @Test
    void cancelledOldCallbackCannotRecoverOrConsumeReplacementRetry() {
        StaffRecoveryRetryRegistry retries = new StaffRecoveryRetryRegistry();
        UUID player = UUID.randomUUID();
        UUID old = retries.begin(player).orElseThrow();
        retries.clear(player);
        UUID current = retries.begin(player).orElseThrow();
        assertFalse(retries.consume(player, old));
        assertTrue(retries.begin(player).isEmpty());
        assertTrue(retries.consume(player, current));
    }

    @Test
    void rejectedScheduleReleasesOnlyItsOwnTicketAndOtherPlayersContinue() {
        StaffRecoveryRetryRegistry retries = new StaffRecoveryRetryRegistry();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID rejected = retries.begin(player).orElseThrow();
        UUID unaffected = retries.begin(other).orElseThrow();
        assertTrue(retries.consume(player, rejected));
        UUID replacement = retries.begin(player).orElseThrow();
        assertFalse(retries.consume(player, rejected));
        assertTrue(retries.consume(player, replacement));
        assertTrue(retries.consume(other, unaffected));
    }
}
