package net.enthusia.staff.domain.inventory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record InventoryConfiscationSession(
        UUID operationId,
        UUID profileId,
        UUID playerId,
        String scopeId,
        String owningServerId,
        UUID actorId,
        CaseId caseId,
        long expectedRevision,
        long fencingToken,
        String beforeChecksum,
        byte[] beforeSnapshot,
        Instant createdAt
) {
    public InventoryConfiscationSession {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(playerId, "playerId");
        scopeId = InventoryObservation.requireIdentifier(scopeId, "scopeId");
        owningServerId = InventoryObservation.requireIdentifier(owningServerId, "owningServerId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(caseId, "caseId");
        if (expectedRevision < 0L || fencingToken < 1L) {
            throw new IllegalArgumentException("confiscation revision or fencing token is invalid");
        }
        beforeChecksum = InventoryObservation.requireChecksum(beforeChecksum);
        beforeSnapshot = Objects.requireNonNull(beforeSnapshot, "beforeSnapshot").clone();
        if (beforeSnapshot.length == 0) {
            throw new IllegalArgumentException("beforeSnapshot cannot be empty");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    @Override
    public byte[] beforeSnapshot() {
        return beforeSnapshot.clone();
    }
}
