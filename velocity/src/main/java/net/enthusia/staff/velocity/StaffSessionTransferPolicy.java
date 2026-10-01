package net.enthusia.staff.velocity;

import net.enthusia.staff.domain.staff.StaffSessionState;

final class StaffSessionTransferPolicy {
    private StaffSessionTransferPolicy() {
    }

    static boolean activeHandoffAllowed(
            String ownerServerId,
            StaffSessionState state,
            String currentServerId,
            String requestedServerId
    ) {
        return validEndpoints(ownerServerId, currentServerId, requestedServerId)
                && state == StaffSessionState.ACTIVE
                && ownerServerId.equalsIgnoreCase(currentServerId)
                && !currentServerId.equalsIgnoreCase(requestedServerId);
    }

    static boolean recoveryReturnAllowed(
            String ownerServerId,
            StaffSessionState state,
            String currentServerId,
            String requestedServerId
    ) {
        return validEndpoints(ownerServerId, currentServerId, requestedServerId)
                && recoveryState(state)
                && !ownerServerId.equalsIgnoreCase(currentServerId)
                && ownerServerId.equalsIgnoreCase(requestedServerId);
    }

    private static boolean validEndpoints(String ownerServerId, String currentServerId, String requestedServerId) {
        return ownerServerId != null && !ownerServerId.isBlank()
                && currentServerId != null && !currentServerId.isBlank()
                && requestedServerId != null && !requestedServerId.isBlank();
    }

    private static boolean recoveryState(StaffSessionState state) {
        return state == StaffSessionState.RECOVERY_REQUIRED || state == StaffSessionState.EXITING;
    }
}
