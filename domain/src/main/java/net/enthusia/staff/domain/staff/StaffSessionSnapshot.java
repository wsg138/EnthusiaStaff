package net.enthusia.staff.domain.staff;

import java.time.Instant;
import java.util.UUID;

public record StaffSessionSnapshot(
        UUID sessionId,
        UUID staffId,
        String serverId,
        StaffSessionState state,
        boolean vanishActive,
        int schemaVersion,
        String checksum,
        byte[] snapshot,
        Instant startedAt,
        long revision
) {
    public StaffSessionSnapshot {
        if (sessionId == null || staffId == null || serverId == null || serverId.isBlank() || state == null
                || schemaVersion < 1 || checksum == null || !checksum.matches("[0-9a-f]{64}")
                || snapshot == null || snapshot.length == 0 || startedAt == null || revision < 0) {
            throw new IllegalArgumentException("staff session snapshot fields are invalid");
        }
        snapshot = snapshot.clone();
    }

    @Override
    public byte[] snapshot() {
        return snapshot.clone();
    }
}
