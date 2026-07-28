package net.enthusia.staff.domain.casefile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.domain.sanction.SanctionType;

public record SanctionReview(
        UUID sanctionId,
        SanctionType type,
        SanctionStatus status,
        Instant issuedAt,
        Optional<Instant> expirationAt,
        Optional<Instant> endedAt,
        long revision
) {
    public SanctionReview {
        if (sanctionId == null || type == null || status == null || issuedAt == null
                || expirationAt == null || endedAt == null || revision < 0) {
            throw new IllegalArgumentException("sanction review fields must be present");
        }
    }

    public boolean active() {
        return status == SanctionStatus.PENDING
                || status == SanctionStatus.ACTIVE
                || status == SanctionStatus.APPLIED;
    }
}
