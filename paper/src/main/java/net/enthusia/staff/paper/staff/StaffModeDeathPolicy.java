package net.enthusia.staff.paper.staff;

/** Decides how an exceptional death is contained within the current Staff Mode lifecycle. */
final class StaffModeDeathPolicy {
    enum Action {
        IGNORE,
        CONTAIN,
        CONTAIN_AND_EXIT
    }

    private StaffModeDeathPolicy() {
    }

    static Action decide(boolean protectedStaffState, boolean usableAuthority) {
        if (!protectedStaffState) {
            return Action.IGNORE;
        }
        return usableAuthority ? Action.CONTAIN_AND_EXIT : Action.CONTAIN;
    }
}
