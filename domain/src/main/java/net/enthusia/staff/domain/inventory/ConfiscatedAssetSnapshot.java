package net.enthusia.staff.domain.inventory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record ConfiscatedAssetSnapshot(
        UUID snapshotId,
        CaseId caseId,
        UUID inventoryOperationId,
        String checksum,
        byte[] assets,
        Instant createdAt,
        Instant expiresAt,
        Optional<UUID> restorationOperationId,
        Optional<Instant> restoredAt
) {
    public ConfiscatedAssetSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(inventoryOperationId, "inventoryOperationId");
        checksum = InventoryObservation.requireChecksum(checksum);
        assets = Objects.requireNonNull(assets, "assets").clone();
        if (assets.length == 0) {
            throw new IllegalArgumentException("confiscated asset snapshot cannot be empty");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("confiscated asset expiration must follow creation");
        }
        restorationOperationId = Objects.requireNonNull(
                restorationOperationId,
                "restorationOperationId"
        );
        restoredAt = Objects.requireNonNull(restoredAt, "restoredAt");
    }

    @Override
    public byte[] assets() {
        return assets.clone();
    }
}
