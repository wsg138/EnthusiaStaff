package net.enthusia.staff.paper.visibility;

import net.enthusia.staff.domain.auth.StaffRank;

final class VanishRankReconciliationPolicy {
    private VanishRankReconciliationPolicy() {
    }

    static boolean shouldCheckRank(
            boolean fullScan,
            boolean staffModeActive,
            boolean onlineStaff,
            boolean durableVanish,
            boolean pendingExitCleanup
    ) {
        return fullScan || staffModeActive || onlineStaff || durableVanish || pendingExitCleanup;
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
            StaffModeState staffModeState
    ) {
        if (!vanished) {
            return durableRank == null ? VanishAction.NONE : VanishAction.DISABLE;
        }
        if (!isPlayerRank(liveRank)) {
            return VanishAction.DISABLE;
        }
        VanishAction sessionAction = requiresStaffMode(liveRank)
                ? staffModeAction(staffModeState)
                : null;
        return sessionAction != null
                ? sessionAction
                : durableRank == liveRank ? VanishAction.NONE : VanishAction.UPDATE_RANK;
    }

    private static VanishAction staffModeAction(StaffModeState staffModeState) {
        return switch (staffModeState) {
            case UNKNOWN -> VanishAction.VERIFY_SESSION;
            case INACTIVE, EXITED -> VanishAction.DISABLE;
            case ACTIVE -> null;
        };
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
        VERIFY_SESSION,
        DISABLE
    }

    enum StaffModeState {
        ACTIVE,
        INACTIVE,
        UNKNOWN,
        EXITED
    }
}
