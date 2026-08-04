package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;

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

    Optional<StaffSessionSnapshot> beginExit(UUID staffId, Instant now);

    boolean completeExit(UUID sessionId, String restoredChecksum, Instant now);

    void recoveryRequired(UUID sessionId, String reason, Instant now);

    default int recoveryRequiredForServer(String serverId, String reason, Instant now) {
        throw new UnsupportedOperationException("server-wide staff-session recovery is not supported");
    }

    boolean setVanish(UUID staffId, boolean vanished, Instant now);
}
