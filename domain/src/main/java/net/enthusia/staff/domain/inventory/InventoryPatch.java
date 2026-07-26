package net.enthusia.staff.domain.inventory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InventoryPatch(
        UUID patchId,
        UUID operationId,
        UUID profileId,
        UUID playerId,
        String scopeId,
        String owningServerId,
        UUID actorId,
        Optional<String> caseId,
        String operationType,
        InventoryOperationState state,
        long expectedRevision,
        long fencingToken,
        String expectedChecksum,
        String replacementChecksum,
        byte[] replacementSnapshot,
        List<Integer> changedSlots,
        Instant createdAt
) {
    public InventoryPatch {
        Objects.requireNonNull(patchId, "patchId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(profileId, "profileId");
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
        Objects.requireNonNull(state, "state");
        if (expectedRevision < 0L || fencingToken <= 0L) {
            throw new IllegalArgumentException("revision and fencing token are invalid");
        }
        expectedChecksum = InventoryObservation.requireChecksum(expectedChecksum);
        replacementChecksum = InventoryObservation.requireChecksum(replacementChecksum);
        replacementSnapshot = Objects.requireNonNull(replacementSnapshot, "replacementSnapshot").clone();
        changedSlots = List.copyOf(Objects.requireNonNull(changedSlots, "changedSlots"));
        if (changedSlots.stream().anyMatch(slot -> slot == null || slot < 0 || slot > 127)) {
            throw new IllegalArgumentException("changedSlots contains an invalid slot");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    @Override
    public byte[] replacementSnapshot() {
        return replacementSnapshot.clone();
    }
}
