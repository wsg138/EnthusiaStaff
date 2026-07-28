package net.enthusia.staff.domain.inventory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InventoryObservation(
        UUID profileId,
        UUID playerId,
        String scopeId,
        String owningServerId,
        long revision,
        String checksum,
        byte[] snapshot,
        Instant observedAt
) {
    public InventoryObservation {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(playerId, "playerId");
        scopeId = requireIdentifier(scopeId, "scopeId");
        owningServerId = requireIdentifier(owningServerId, "owningServerId");
        if (revision < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        checksum = requireChecksum(checksum);
        snapshot = Objects.requireNonNull(snapshot, "snapshot").clone();
        Objects.requireNonNull(observedAt, "observedAt");
    }

    @Override
    public byte[] snapshot() {
        return snapshot.clone();
    }

    static String requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(field + " must contain 1-64 characters");
        }
        return value;
    }

    static String requireChecksum(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksum must be a lowercase SHA-256 value");
        }
        return value;
    }
}
