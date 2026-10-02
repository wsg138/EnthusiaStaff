package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffModeDeathExitGateTest {
    private static final UUID PLAYER =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void duplicateDeathDoesNotStartAnotherRetryChain() {
        StaffModeDeathExitGate gate = new StaffModeDeathExitGate();

        assertTrue(gate.request(PLAYER));
        assertFalse(gate.request(PLAYER));
        assertTrue(gate.pending(PLAYER));
    }

    @Test
    void releasedChainCanResumePendingExitAfterReconnect() {
        StaffModeDeathExitGate gate = new StaffModeDeathExitGate();

        assertTrue(gate.request(PLAYER));
        gate.release(PLAYER);

        assertTrue(gate.resume(PLAYER));
        assertFalse(gate.resume(PLAYER));
        assertTrue(gate.pending(PLAYER));
    }

    @Test
    void completedExitClearsPendingIntentAndChain() {
        StaffModeDeathExitGate gate = new StaffModeDeathExitGate();

        assertTrue(gate.request(PLAYER));
        gate.complete(PLAYER);

        assertFalse(gate.pending(PLAYER));
        assertFalse(gate.resume(PLAYER));
    }
}
