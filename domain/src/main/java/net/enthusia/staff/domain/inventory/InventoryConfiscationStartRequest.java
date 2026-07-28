package net.enthusia.staff.domain.inventory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record InventoryConfiscationStartRequest(
        UUID operationId,
        String idempotencyKey,
        UUID playerId,
        String scopeId,
        String owningServerId,
        UUID actorId,
        CaseId caseId,
        String beforeChecksum,
        byte[] beforeSnapshot,
        Instant requestedAt
) {
    public InventoryConfiscationStartRequest {
        Objects.requireNonNull(operationId, "operationId");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:/-]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        Objects.requireNonNull(playerId, "playerId");
        scopeId = InventoryObservation.requireIdentifier(scopeId, "scopeId");
        owningServerId = InventoryObservation.requireIdentifier(owningServerId, "owningServerId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(caseId, "caseId");
        beforeChecksum = InventoryObservation.requireChecksum(beforeChecksum);
        beforeSnapshot = Objects.requireNonNull(beforeSnapshot, "beforeSnapshot").clone();
        if (beforeSnapshot.length == 0) {
            throw new IllegalArgumentException("beforeSnapshot cannot be empty");
        }
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    @Override
    public byte[] beforeSnapshot() {
        return beforeSnapshot.clone();
    }
}
