package net.enthusia.staff.paper.staff;

import java.util.UUID;

/** Safe labels for confirmed staff-session transitions; never includes player-entered text. */
final class StaffModeLifecycleAudit {
    private StaffModeLifecycleAudit() {
    }

    static String detail(UUID sessionId, String path, String reason) {
        return "session=" + sessionId + " path=" + path + " reason=" + reason;
    }

    static String exitReason(String operation) {
        if ("Staff mode exit".equals(operation)) {
            return "manual";
        }
        if ("Staff rank removal".equals(operation)) {
            return "rank-removed";
        }
        if ("Staff rank profile reconciliation failure".equals(operation)) {
            return "profile-reconciliation-failed";
        }
        return "recovery";
    }
}
