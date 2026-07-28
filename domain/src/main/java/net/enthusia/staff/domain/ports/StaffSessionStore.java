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

    boolean setVanish(UUID staffId, boolean vanished, Instant now);
}
