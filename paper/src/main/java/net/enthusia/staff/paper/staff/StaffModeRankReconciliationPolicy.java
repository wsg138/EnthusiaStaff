package net.enthusia.staff.paper.staff;

import net.enthusia.staff.domain.auth.StaffRank;

final class StaffModeRankReconciliationPolicy {
    enum Action {
        NONE,
        APPLY_PROFILE,
        EXIT_SESSION
    }

    private StaffModeRankReconciliationPolicy() {
    }

    static Action decide(StaffRank cachedRank, StaffRank liveRank) {
        if (liveRank == null || liveRank == StaffRank.SYSTEM) {
            return Action.EXIT_SESSION;
        }
        return liveRank == cachedRank ? Action.NONE : Action.APPLY_PROFILE;
    }
}
