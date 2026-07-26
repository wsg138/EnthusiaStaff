package net.enthusia.staff.domain.website;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import net.enthusia.staff.common.CaseId;

public record PublicPunishment(
        String player,
        String punishmentType,
        String broadReason,
        String publicReason,
        Instant issuedAt,
        Optional<Instant> expiresAt,
        OptionalLong remainingSeconds,
        PublicPunishmentState state,
        CaseId caseId,
        boolean appealAvailable
) {
    public PublicPunishment {
        if (player == null || !player.matches("[A-Za-z0-9_]{3,16}")
                || punishmentType == null || punishmentType.isBlank()
                || broadReason == null || broadReason.isBlank()
                || publicReason == null || publicReason.isBlank()
                || issuedAt == null || expiresAt == null || remainingSeconds == null
                || state == null || caseId == null) {
            throw new IllegalArgumentException("Public punishment fields are invalid");
        }
        if (remainingSeconds.isPresent() && remainingSeconds.getAsLong() < 0) {
            throw new IllegalArgumentException("Remaining duration cannot be negative");
        }
    }
}
