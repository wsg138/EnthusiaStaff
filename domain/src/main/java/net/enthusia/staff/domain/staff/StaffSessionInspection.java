package net.enthusia.staff.domain.staff;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Diagnostic metadata and a bounded opaque header; never a restorable snapshot. */
public record StaffSessionInspection(UUID sessionId, UUID staffId, String serverId, StaffSessionState state,
                                     long revision, Instant startedAt, int schemaVersion, long snapshotBytes,
                                     String checksum, byte[] header) {
    public static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_HEADER_BYTES = 128;

    public StaffSessionInspection {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(header, "header");
        if (revision < 0 || schemaVersion < 1 || snapshotBytes < 1 || snapshotBytes > MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("invalid inspection metadata");
        }
        if (!checksum.matches("[0-9a-f]{64}") || header.length == 0 || header.length > MAX_HEADER_BYTES) {
            throw new IllegalArgumentException("invalid inspection integrity metadata");
        }
        header = header.clone();
    }

    @Override
    public byte[] header() {
        return header.clone();
    }
}
