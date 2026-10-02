package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class LiveInventoryRecoveryPolicyTest {
    private static final int MAX_ATTEMPTS = 5;

    @Test
    void onlineMetadataRecoveryRetriesBeforeTheLimit() {
        assertEquals(
                LiveInventoryRecoveryPolicy.RetryDecision.RETRY,
                LiveInventoryRecoveryPolicy.metadataRetry(true, 4, MAX_ATTEMPTS)
        );
    }

    @Test
    void offlineMetadataRecoveryStopsInsteadOfRescheduling() {
        assertEquals(
                LiveInventoryRecoveryPolicy.RetryDecision.STOP_OFFLINE,
                LiveInventoryRecoveryPolicy.metadataRetry(false, 1, MAX_ATTEMPTS)
        );
    }

    @Test
    void metadataRecoveryExhaustionFailsClosed() {
        assertEquals(
                LiveInventoryRecoveryPolicy.RetryDecision.EXHAUSTED,
                LiveInventoryRecoveryPolicy.metadataRetry(true, MAX_ATTEMPTS, MAX_ATTEMPTS)
        );
    }
}
