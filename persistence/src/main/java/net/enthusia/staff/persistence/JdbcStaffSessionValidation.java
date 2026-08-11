package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.UUID;

final class JdbcStaffSessionValidation {
    private static final int MINIMUM_SCHEMA_VERSION = 1;
    private static final int MAXIMUM_SNAPSHOT_BYTES = 8 * 1024 * 1024;

    private JdbcStaffSessionValidation() {
    }

    static void validateSnapshot(
            UUID staffId,
            String serverId,
            int schemaVersion,
            String checksum,
            byte[] snapshot,
            Instant now
    ) {
        if (!validSnapshotIdentity(staffId, serverId, schemaVersion, checksum, now)
                || !validSnapshotPayload(snapshot)) {
            throw new IllegalArgumentException("valid bounded staff snapshot fields are required");
        }
    }

    static void validateRecoveryRequest(
            UUID sessionId,
            String reason,
            Instant now
    ) {
        if (sessionId == null || reason == null || reason.isBlank()
                || reason.length() > 512 || now == null) {
            throw new IllegalArgumentException("valid staff recovery fields are required");
        }
    }

    static void validateServerRecovery(String serverId, String reason, Instant now) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}")
                || reason == null || reason.isBlank() || reason.length() > 512 || now == null) {
            throw new IllegalArgumentException("valid server staff-recovery fields are required");
        }
    }

    private static boolean validSnapshotIdentity(
            UUID staffId,
            String serverId,
            int schemaVersion,
            String checksum,
            Instant now
    ) {
        return staffId != null
                && serverId != null
                && serverId.matches("[A-Za-z0-9_-]{1,64}")
                && schemaVersion >= MINIMUM_SCHEMA_VERSION
                && checksum != null
                && checksum.matches("[0-9a-f]{64}")
                && now != null;
    }

    private static boolean validSnapshotPayload(byte[] snapshot) {
        return snapshot != null
                && snapshot.length > 0
                && snapshot.length <= MAXIMUM_SNAPSHOT_BYTES;
    }
}
