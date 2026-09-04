package net.enthusia.staff.domain.market;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MarketComplianceUpdate(
        MarketComplianceState state,
        Optional<UUID> reviewedBy,
        Optional<String> snapshotChecksum,
        Optional<String> currentChecksum,
        long providerRevision,
        String detail,
        Instant updatedAt
) {
    private static final long MINIMUM_PROVIDER_REVISION = 0L;

    public MarketComplianceUpdate {
        state = Objects.requireNonNull(state, "state");
        reviewedBy = Objects.requireNonNull(reviewedBy, "reviewedBy");
        snapshotChecksum = Objects.requireNonNull(snapshotChecksum, "snapshotChecksum");
        currentChecksum = Objects.requireNonNull(currentChecksum, "currentChecksum");
        if (providerRevision < MINIMUM_PROVIDER_REVISION) {
            throw new IllegalArgumentException("providerRevision cannot be negative");
        }
        if (detail == null || detail.isBlank() || detail.length() > 512) {
            throw new IllegalArgumentException("market detail must contain 1-512 characters");
        }
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
