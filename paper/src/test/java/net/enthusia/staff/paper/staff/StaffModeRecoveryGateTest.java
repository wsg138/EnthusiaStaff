package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class StaffModeRecoveryGateTest {
    private static final UUID PLAYER_ID = UUID.fromString("72000000-0000-0000-0000-000000000001");

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
}
