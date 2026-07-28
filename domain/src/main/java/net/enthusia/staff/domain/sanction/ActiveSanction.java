package net.enthusia.staff.domain.sanction;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record ActiveSanction(
        UUID sanctionId,
        CaseId caseId,
        UUID targetId,
        SanctionType type,
        String publicReason,
        Instant issuedAt,
        Optional<Instant> expiresAt,
        Optional<UUID> inheritedFrom
) {
    public ActiveSanction {
        if (sanctionId == null || caseId == null || targetId == null || type == null
                || publicReason == null || publicReason.isBlank() || issuedAt == null
                || expiresAt == null || inheritedFrom == null) {
            throw new IllegalArgumentException("active sanction fields must be present");
        }
    }

    public boolean permanent() {
        return expiresAt.isEmpty();
    }
}
