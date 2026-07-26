package net.enthusia.staff.domain.economy;

import java.util.Objects;
import java.util.Optional;

public record EconomyJournalResult(Status status, Optional<EconomyOperation> operation, String detail) {
    public EconomyJournalResult {
        Objects.requireNonNull(status, "status");
        operation = Objects.requireNonNull(operation, "operation");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean successful() {
        return status == Status.UPDATED || status == Status.REPLAYED;
    }

    public enum Status {
        UPDATED,
        REPLAYED,
        STALE,
        FENCE_LOST,
        INVALID_STATE,
        NOT_FOUND
    }
}
