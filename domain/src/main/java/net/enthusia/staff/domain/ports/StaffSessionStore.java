package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionInspection;

public interface StaffSessionStore {
    StaffSessionSnapshot begin(
            UUID staffId,
            String serverId,
            int schemaVersion,
            String checksum,
            byte[] snapshot,
            Instant now
    );

    Optional<StaffSessionSnapshot> active(UUID staffId);

    /** Read-only diagnostic lookup; rejects missing, ambiguous or corrupt snapshot evidence. */
    default Optional<StaffSessionInspection> inspect(UUID staffId) {
        throw new UnsupportedOperationException("staff session inspection is not supported");
    }

    /**
     * Releases the backend-local saved-state lease after that exact state has been restored,
     * while keeping network Staff Mode intent active for reconnect/transfer.
     */
    default Optional<StaffSessionSnapshot> detach(
            UUID staffId,
            UUID expectedSessionId,
            long expectedRevision,
            String expectedServerId,
            String restoredChecksum,
            Instant now
    ) {
        throw new UnsupportedOperationException("backend Staff Mode detach is not supported");
    }

    Optional<StaffSessionSnapshot> beginExit(UUID staffId, Instant now);

    /**
     * Closes only the exact detached lease whose source backend already verified restoration.
     * No destination player snapshot is applied. Older adapters fail closed.
     */
    default boolean retireDetached(StaffSessionSnapshot expected, Instant now) {
        return false;
    }

    boolean completeExit(UUID sessionId, String restoredChecksum, Instant now);

    void recoveryRequired(UUID sessionId, String reason, Instant now);

    default int recoveryRequiredForServer(String serverId, String reason, Instant now) {
        throw new UnsupportedOperationException("server-wide staff-session recovery is not supported");
    }

    boolean setVanish(UUID staffId, boolean vanished, Instant now);
}
