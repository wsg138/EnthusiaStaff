package net.enthusia.staff.paper.staff;

import java.util.Objects;
import net.enthusia.staff.domain.staff.StaffSessionState;

final class DetachedStaffSessionRecoveryPolicy {
    enum Action {
        REBIND,
        RETIRE_RESTORED_LEASE,
        HOLD_INVALID_TRANSITION,
        CLEAR_CLOSED
    }

    private DetachedStaffSessionRecoveryPolicy() {
    }

    static Action decide(StaffSessionState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case ACTIVE -> Action.REBIND;
            case EXITING, RECOVERY_REQUIRED -> Action.RETIRE_RESTORED_LEASE;
            case ENTERING -> Action.HOLD_INVALID_TRANSITION;
            case CLOSED -> Action.CLEAR_CLOSED;
        };
    }
}
