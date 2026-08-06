package net.enthusia.staff.domain.website;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record WebsiteAppealCandidate(
        UUID punishmentId,
        CaseId caseId,
        String punishmentType,
        String publicReason,
        Instant issuedAt
) {
    public WebsiteAppealCandidate {
        if (punishmentId == null || caseId == null || punishmentType == null
                || punishmentType.isBlank() || publicReason == null
                || publicReason.isBlank() || issuedAt == null) {
            throw new IllegalArgumentException("Website appeal candidate fields are invalid");
        }
    }
}
