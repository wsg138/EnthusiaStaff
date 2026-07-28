package net.enthusia.staff.domain.economy;

import java.util.Objects;
import java.util.Optional;

public record EconomyPreparation(Status status, Optional<EconomyOperation> operation, String detail) {
    public EconomyPreparation {
        Objects.requireNonNull(status, "status");
        operation = Objects.requireNonNull(operation, "operation");
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == Status.PREPARED || status == Status.REPLAYED) != operation.isPresent()) {
            throw new IllegalArgumentException("prepared and replayed results require an operation");
        }
    }

    public enum Status {
        PREPARED,
        REPLAYED,
        LOCKED
    }
}
