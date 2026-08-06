package net.enthusia.staff.domain.website;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public record WebsiteAppealView(
        UUID appealId,
        UUID punishmentId,
        CaseId caseId,
        String punishmentType,
        String playerUsername,
        String reason,
        String state,
        long version,
        String decision,
        String decisionNote,
        Instant createdAt,
        Instant updatedAt
) {
    public WebsiteAppealView {
        if (appealId == null || punishmentId == null || caseId == null
                || punishmentType == null || punishmentType.isBlank()
                || playerUsername == null || !playerUsername.matches("[A-Za-z0-9_]{1,16}")
                || reason == null || reason.isBlank() || state == null || state.isBlank()
                || version < 1 || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Website appeal fields are invalid");
        }
    }
}
