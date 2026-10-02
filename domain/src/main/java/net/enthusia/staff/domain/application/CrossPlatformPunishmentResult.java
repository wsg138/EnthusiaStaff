package net.enthusia.staff.domain.application;

import java.util.UUID;
import net.enthusia.staff.common.CaseId;

/** Public-safe identifiers for a committed cross-platform punishment intent. */
public record CrossPlatformPunishmentResult(
        CaseId caseId,
        UUID discordPunishmentId,
        boolean replayed
) {
    public CrossPlatformPunishmentResult {
        if (caseId == null || discordPunishmentId == null) {
            throw new IllegalArgumentException("cross-platform punishment result identifiers must be present");
        }
    }
}
