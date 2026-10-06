package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

class DetachedStaffSessionRecoveryPolicyTest {
    @Test
    void activeDetachedSessionRebinds() {
        assertEquals(
                DetachedStaffSessionRecoveryPolicy.Action.REBIND,
                DetachedStaffSessionRecoveryPolicy.decide(StaffSessionState.ACTIVE)
        );
    }

    @Test
    void alreadyRestoredTerminalStatesRetireWithoutRestoringSnapshotAgain() {
        assertEquals(
                DetachedStaffSessionRecoveryPolicy.Action.RETIRE_RESTORED_LEASE,
                DetachedStaffSessionRecoveryPolicy.decide(StaffSessionState.EXITING)
        );
        assertEquals(
                DetachedStaffSessionRecoveryPolicy.Action.RETIRE_RESTORED_LEASE,
                DetachedStaffSessionRecoveryPolicy.decide(StaffSessionState.RECOVERY_REQUIRED)
        );
    }

    @Test
    void impossibleEnteringStateIsHeldForReview() {
        assertEquals(
                DetachedStaffSessionRecoveryPolicy.Action.HOLD_INVALID_TRANSITION,
                DetachedStaffSessionRecoveryPolicy.decide(StaffSessionState.ENTERING)
        );
    }
}
