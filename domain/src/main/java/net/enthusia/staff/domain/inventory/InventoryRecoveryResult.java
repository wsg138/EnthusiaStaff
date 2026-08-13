package net.enthusia.staff.domain.inventory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InventoryRecoveryResult(
        Status status,
        Optional<UUID> operationId,
        String detail
) {
    public InventoryRecoveryResult {
        Objects.requireNonNull(status, "status");
        operationId = Objects.requireNonNull(operationId, "operationId");
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (detail.isEmpty() || detail.length() > 512) {
            throw new IllegalArgumentException("recovery detail must contain 1-512 characters");
        }
        if ((status == Status.REQUEUED || status == Status.REPLAYED) && operationId.isEmpty()) {
            throw new IllegalArgumentException("successful recovery results require an operation ID");
        }
    }

    public enum Status {
        REQUEUED,
        REPLAYED,
        NOT_FOUND,
        AMBIGUOUS,
        UNAUTHORIZED,
        STORAGE_UNAVAILABLE
    }
}
