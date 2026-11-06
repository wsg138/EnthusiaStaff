package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class StaffModeRecoveryGateTest {
    private static final UUID PLAYER_ID = UUID.fromString("72000000-0000-0000-0000-000000000001");

    @Test
    void retryableHoldIsDistinctFromAnOwnedInFlightAttempt() {
        StaffModeRecoveryGate gate = new StaffModeRecoveryGate(ConcurrentHashMap.newKeySet());
        Object first = gate.beginAttempt(PLAYER_ID);
        assertFalse(gate.retryable(PLAYER_ID));
        assertTrue(gate.retry(PLAYER_ID, first));
        assertTrue(gate.retryable(PLAYER_ID));
        Object retry = gate.beginAttempt(PLAYER_ID);
        assertNotNull(retry);
        assertFalse(gate.retryable(PLAYER_ID));
        assertFalse(gate.retry(PLAYER_ID, first));
        assertTrue(gate.owns(PLAYER_ID, retry));
    }

    @Test
    void unavailableStorageKeepsFenceAndAllowsOneLaterRetry() {
        Set<UUID> transitions = ConcurrentHashMap.newKeySet();
        StaffModeRecoveryGate gate = new StaffModeRecoveryGate(transitions);

        assertTrue(gate.begin(PLAYER_ID));
        gate.retry(PLAYER_ID);

        assertTrue(transitions.contains(PLAYER_ID));
        assertTrue(gate.begin(PLAYER_ID));
        assertFalse(gate.begin(PLAYER_ID));
        assertTrue(transitions.contains(PLAYER_ID));
    }

    @Test
    void clearRemovesFenceAndPendingRetry() {
        Set<UUID> transitions = ConcurrentHashMap.newKeySet();
        StaffModeRecoveryGate gate = new StaffModeRecoveryGate(transitions);

        assertTrue(gate.begin(PLAYER_ID));
        gate.retry(PLAYER_ID);
        gate.clear(PLAYER_ID);

        assertFalse(transitions.contains(PLAYER_ID));
        assertTrue(gate.begin(PLAYER_ID));
    }

    @Test
    void disconnectedAttemptCannotOwnCleanupAfterReconnect() {
        StaffModeRecoveryGate gate = new StaffModeRecoveryGate(ConcurrentHashMap.newKeySet());
        assertTrue(gate.begin(PLAYER_ID));
        Object old = gate.attempt(PLAYER_ID);
        gate.clear(PLAYER_ID);
        assertTrue(gate.begin(PLAYER_ID));

        assertFalse(gate.owns(PLAYER_ID, old));
        assertTrue(gate.owns(PLAYER_ID, gate.attempt(PLAYER_ID)));
    }

    @Test
    void retryInvalidatesOldCleanupUntilItsNewReadSucceeds() {
        StaffModeRecoveryGate gate = new StaffModeRecoveryGate(ConcurrentHashMap.newKeySet());
        assertTrue(gate.begin(PLAYER_ID));
        Object old = gate.attempt(PLAYER_ID);
        gate.retry(PLAYER_ID);

        assertFalse(gate.owns(PLAYER_ID, old));
        assertFalse(gate.owns(PLAYER_ID, null));
        assertTrue(gate.begin(PLAYER_ID));
        assertFalse(gate.owns(PLAYER_ID, old));
        assertTrue(gate.owns(PLAYER_ID, gate.attempt(PLAYER_ID)));
    }

    @Test
    void staleCompletionAndRetryCannotClearANewerAttempt() {
        StaffModeRecoveryGate gate = new StaffModeRecoveryGate(ConcurrentHashMap.newKeySet());
        Object old = gate.beginAttempt(PLAYER_ID);
        assertTrue(gate.retry(PLAYER_ID, old));
        Object current = gate.beginAttempt(PLAYER_ID);

        assertFalse(gate.complete(PLAYER_ID, old, () -> {
            throw new AssertionError("stale cleanup must not run");
        }));
        assertFalse(gate.retry(PLAYER_ID, old));
        assertTrue(gate.owns(PLAYER_ID, current));
    }

    @Test
    void cleanupAndFenceReleaseCannotInterleaveWithAReplacementAttempt() throws Exception {
        StaffModeRecoveryGate gate = new StaffModeRecoveryGate(ConcurrentHashMap.newKeySet());
        Object old = gate.beginAttempt(PLAYER_ID);
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        CountDownLatch replacementStarted = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var completed = workers.submit(() -> gate.complete(PLAYER_ID, old, () -> {
                cleanupStarted.countDown();
                try {
                    assertTrue(releaseCleanup.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return true;
            }));
            assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS));
            var replacement = workers.submit(() -> {
                replacementStarted.countDown();
                gate.clear(PLAYER_ID);
                return gate.beginAttempt(PLAYER_ID);
            });
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> replacement.get(100, TimeUnit.MILLISECONDS),
                    "a replacement attempt must wait until cleanup and fence release finish");

            releaseCleanup.countDown();
            assertTrue(completed.get(5, TimeUnit.SECONDS));
            Object current = replacement.get(5, TimeUnit.SECONDS);
            assertNotNull(current);
            assertTrue(gate.owns(PLAYER_ID, current));
            assertFalse(gate.retry(PLAYER_ID, old));
        } finally {
            releaseCleanup.countDown();
            workers.shutdownNow();
        }
    }
}
