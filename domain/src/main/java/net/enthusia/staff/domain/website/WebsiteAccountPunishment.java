package net.enthusia.staff.domain.website;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record WebsiteAccountPunishment(
        UUID punishmentId, CaseId caseId, String type, String reason, Instant issuedAt,
        UUID minecraftId, String minecraftName, boolean active, Instant expiresAt,
        Instant nextAppealAt, boolean appealable
) {
    public WebsiteAccountPunishment {
        if (punishmentId == null || caseId == null || type == null || reason == null
                || issuedAt == null || minecraftId == null || minecraftName == null) {
            throw new IllegalArgumentException("Owned punishment fields are required");
        }
    }
}
