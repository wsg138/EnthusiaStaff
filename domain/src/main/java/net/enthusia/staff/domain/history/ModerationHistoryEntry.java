package net.enthusia.staff.domain.history;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.Checks;

public record ModerationHistoryEntry(
        String stableKey,
        HistoryEventType eventType,
        Instant occurredAt,
        Optional<CaseId> caseId,
        Optional<UUID> sanctionId,
        Optional<UUID> punishmentRequestId,
        Optional<UUID> appealId,
        Optional<String> punishmentType,
        String status,
        String publicReason,
        Optional<Instant> originalExpiration,
        Optional<Instant> resultingExpiration,
        Optional<UUID> actorId,
        Optional<String> actorName,
        Optional<String> sensitiveReason
) {
    public ModerationHistoryEntry {
        stableKey = Checks.nonBlank(stableKey, "stableKey", 160);
        if (eventType == null || occurredAt == null || caseId == null || sanctionId == null
                || punishmentRequestId == null || appealId == null || punishmentType == null
                || originalExpiration == null || resultingExpiration == null || actorId == null
                || actorName == null || sensitiveReason == null) {
            throw new IllegalArgumentException("history entry fields must be present");
        }
        punishmentType = punishmentType.map(value -> Checks.nonBlank(value, "punishmentType", 96));
        status = Checks.nonBlank(status, "status", 64);
        if (publicReason == null) {
            throw new IllegalArgumentException("publicReason must be present");
        }
        publicReason = publicReason.trim();
        if (publicReason.length() > 512) {
            throw new IllegalArgumentException("publicReason is too long");
        }
        actorName = actorName.map(value -> Checks.nonBlank(value, "actorName", 64));
        sensitiveReason = sensitiveReason.map(value -> Checks.nonBlank(value, "sensitiveReason", 2_000));
    }
}
