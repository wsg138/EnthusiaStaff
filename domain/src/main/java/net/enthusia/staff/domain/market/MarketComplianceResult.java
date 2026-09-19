package net.enthusia.staff.domain.market;

import java.util.Objects;
import java.util.Optional;

public record MarketComplianceResult(
        Status status,
        Optional<MarketComplianceOperation> operation,
        String detail
) {
    public MarketComplianceResult {
        status = Objects.requireNonNull(status, "status");
        operation = Objects.requireNonNull(operation, "operation");
        if (detail == null || detail.isBlank() || detail.length() > 512) {
            throw new IllegalArgumentException("market result detail must contain 1-512 characters");
        }
    }

    public enum Status {
        CREATED,
        UPDATED,
        REPLAYED,
        CONFLICT,
        STALE,
        NOT_FOUND
    }
}
