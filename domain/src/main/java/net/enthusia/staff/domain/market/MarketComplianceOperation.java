package net.enthusia.staff.domain.market;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record MarketComplianceOperation(
        MarketComplianceRequest request,
        MarketComplianceState state,
        Optional<java.util.UUID> reviewedBy,
        Optional<String> snapshotChecksum,
        Optional<String> currentChecksum,
        long providerRevision,
        long journalRevision,
        String detail,
        Instant updatedAt,
        Optional<Instant> reviewAlertedAt
) {
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public MarketComplianceOperation {
        request = Objects.requireNonNull(request, "request");
        state = Objects.requireNonNull(state, "state");
        reviewedBy = Objects.requireNonNull(reviewedBy, "reviewedBy");
        snapshotChecksum = validateChecksum(snapshotChecksum, "snapshotChecksum");
        currentChecksum = validateChecksum(currentChecksum, "currentChecksum");
        if (providerRevision < 0L || journalRevision < 0L) {
            throw new IllegalArgumentException("market revisions cannot be negative");
        }
        if (detail == null || detail.isBlank() || detail.length() > 512) {
            throw new IllegalArgumentException("market detail must contain 1-512 characters");
        }
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        reviewAlertedAt = Objects.requireNonNull(reviewAlertedAt, "reviewAlertedAt");
    }

    public java.util.UUID operationId() {
        return request.operationId();
    }

    private static Optional<String> validateChecksum(Optional<String> value, String name) {
        Optional<String> checked = Objects.requireNonNull(value, name);
        checked.ifPresent(checksum -> {
            if (!CHECKSUM.matcher(checksum).matches()) {
                throw new IllegalArgumentException(name + " must be a lowercase SHA-256 checksum");
            }
        });
        return checked;
    }
}
