package net.enthusia.staff.domain.inventory;

import java.util.Objects;

public record InventoryFinalizeResult(Status status, long resultingRevision, String detail) {
    public InventoryFinalizeResult {
        Objects.requireNonNull(status, "status");
        if (resultingRevision < 0L) {
            throw new IllegalArgumentException("resultingRevision cannot be negative");
        }
        detail = Objects.requireNonNull(detail, "detail");
    }

    public enum Status {
        COMMITTED,
        REPLAYED,
        STALE,
        FENCE_LOST,
        NOT_FOUND
    }
}
