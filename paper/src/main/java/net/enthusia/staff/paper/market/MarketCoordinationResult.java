package net.enthusia.staff.paper.market;

import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.market.MarketComplianceOperation;

public record MarketCoordinationResult(
        Status status,
        Optional<MarketComplianceOperation> operation,
        String detail
) {
    public MarketCoordinationResult {
        status = Objects.requireNonNull(status, "status");
        operation = Objects.requireNonNull(operation, "operation");
        if (detail == null || detail.isBlank() || detail.length() > 512) {
            throw new IllegalArgumentException("market coordination detail must contain 1-512 characters");
        }
    }

    public enum Status {
        UPDATED,
        REPLAYED,
        REJECTED,
        CONFLICT,
        QUARANTINED,
        UNAVAILABLE
    }
}
