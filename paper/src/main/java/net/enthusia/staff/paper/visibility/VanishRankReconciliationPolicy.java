package net.enthusia.staff.paper.visibility;

import net.enthusia.staff.domain.auth.StaffRank;

final class VanishRankReconciliationPolicy {
    private VanishRankReconciliationPolicy() {
    }

    static ViewerAction viewerAction(StaffRank cachedRank, StaffRank liveRank) {
        if (!isPlayerRank(liveRank)) {
            return cachedRank == null ? ViewerAction.NONE : ViewerAction.REMOVE;
        }
        return cachedRank == liveRank ? ViewerAction.NONE : ViewerAction.UPDATE;
    }

    static VanishAction vanishAction(
            boolean vanished,
            StaffRank durableRank,
            StaffRank liveRank,
            boolean staffModeActive
    ) {
        if (!vanished) {
            return durableRank == null ? VanishAction.NONE : VanishAction.DISABLE;
        }
        if (!isPlayerRank(liveRank)) {
            return VanishAction.DISABLE;
        }
        if (requiresStaffMode(liveRank) && !staffModeActive && durableRank != liveRank) {
            return VanishAction.DISABLE;
        }
        return durableRank == liveRank ? VanishAction.NONE : VanishAction.UPDATE_RANK;
    }

    static boolean requiresStaffMode(StaffRank rank) {
        return rank == StaffRank.HELPER || rank == StaffRank.MOD || rank == StaffRank.DEVELOPER;
    }

    static boolean isPlayerRank(StaffRank rank) {
        return rank != null && rank != StaffRank.SYSTEM;
    }

    enum ViewerAction {
        NONE,
        UPDATE,
        REMOVE
    }

    enum VanishAction {
        NONE,
        UPDATE_RANK,
        DISABLE
    }
}
