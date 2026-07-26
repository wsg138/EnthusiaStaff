package net.enthusia.staff.domain.inventory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InventoryPrepareRequest(
        UUID operationId,
        String idempotencyKey,
        UUID playerId,
        String scopeId,
        String owningServerId,
        UUID actorId,
        Optional<String> caseId,
        String operationType,
        long expectedRevision,
        String expectedChecksum,
        byte[] beforeSnapshot,
        String replacementChecksum,
        byte[] replacementSnapshot,
        List<Integer> changedSlots,
        boolean requireNetworkOffline
) {
    public InventoryPrepareRequest {
        Objects.requireNonNull(operationId, "operationId");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:/-]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        Objects.requireNonNull(playerId, "playerId");
        scopeId = InventoryObservation.requireIdentifier(scopeId, "scopeId");
        owningServerId = InventoryObservation.requireIdentifier(owningServerId, "owningServerId");
        Objects.requireNonNull(actorId, "actorId");
        caseId = Objects.requireNonNull(caseId, "caseId").map(String::trim);
        if (caseId.isPresent() && (caseId.orElseThrow().isEmpty() || caseId.orElseThrow().length() > 16)) {
            throw new IllegalArgumentException("caseId must contain 1-16 characters when present");
        }
        if (operationType == null || !operationType.matches("[A-Z0-9_]{1,48}")) {
            throw new IllegalArgumentException("operationType is invalid");
        }
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("expectedRevision cannot be negative");
        }
        expectedChecksum = InventoryObservation.requireChecksum(expectedChecksum);
        beforeSnapshot = Objects.requireNonNull(beforeSnapshot, "beforeSnapshot").clone();
        replacementChecksum = InventoryObservation.requireChecksum(replacementChecksum);
        replacementSnapshot = Objects.requireNonNull(replacementSnapshot, "replacementSnapshot").clone();
        changedSlots = List.copyOf(Objects.requireNonNull(changedSlots, "changedSlots"));
        if (changedSlots.isEmpty() || changedSlots.stream().anyMatch(slot -> slot == null || slot < 0 || slot > 127)) {
            throw new IllegalArgumentException("changedSlots must contain valid slots");
        }
    }

    @Override
    public byte[] beforeSnapshot() {
        return beforeSnapshot.clone();
    }

    @Override
    public byte[] replacementSnapshot() {
        return replacementSnapshot.clone();
    }
}
