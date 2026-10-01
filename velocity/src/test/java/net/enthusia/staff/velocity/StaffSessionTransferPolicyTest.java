package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

class StaffSessionTransferPolicyTest {
    private static final String HUB = "HUB";
    private static final String SMP = "SMP";

    @Test
    void activeHandoffRequiresCurrentSnapshotOwnerAndDifferentDestination() {
        assertTrue(StaffSessionTransferPolicy.activeHandoffAllowed(SMP, StaffSessionState.ACTIVE, SMP, HUB));
        assertFalse(StaffSessionTransferPolicy.activeHandoffAllowed(SMP, StaffSessionState.ACTIVE, HUB, SMP));
        assertFalse(StaffSessionTransferPolicy.activeHandoffAllowed(SMP, StaffSessionState.ACTIVE, SMP, SMP));
        assertFalse(StaffSessionTransferPolicy.activeHandoffAllowed(
                SMP, StaffSessionState.RECOVERY_REQUIRED, SMP, HUB));
    }

    @Test
    void recoveryStatesMayReturnOnlyToTheirOriginalBackend() {
        for (var state : new StaffSessionState[]{StaffSessionState.RECOVERY_REQUIRED, StaffSessionState.EXITING}) {
            assertTrue(StaffSessionTransferPolicy.recoveryReturnAllowed(SMP, state, HUB, "smp"));
            assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed(SMP, state, HUB, "TEST"));
            assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed(SMP, state, SMP, HUB));
        }
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed(SMP, StaffSessionState.ACTIVE, HUB, SMP));
    }

    @Test
    void incompleteSessionsAndMissingOwnershipRemainBlocked() {
        for (var state : new StaffSessionState[]{StaffSessionState.ENTERING, StaffSessionState.CLOSED}) {
            assertFalse(StaffSessionTransferPolicy.activeHandoffAllowed(SMP, state, SMP, HUB));
            assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed(SMP, state, HUB, SMP));
        }
        assertFalse(StaffSessionTransferPolicy.activeHandoffAllowed(null, StaffSessionState.ACTIVE, SMP, HUB));
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("", StaffSessionState.EXITING, HUB, SMP));
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed(SMP, StaffSessionState.EXITING, null, SMP));
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed(SMP, StaffSessionState.EXITING, HUB, null));
    }
}
