package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;

public record PunishmentRequestAlertIntent(
        UUID alertId,
        String intentKey,
        UUID requestId,
        long requestRevision,
        PunishmentRequestLifecycleEventType eventType,
        PunishmentRequestAlertAudience audience,
        UUID recipientId,
        UUID excludedRecipientId,
        StaffRank minimumRank,
        int schemaVersion,
        Instant createdAt
) {
    public PunishmentRequestAlertIntent {
        Objects.requireNonNull(alertId, "alertId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(createdAt, "createdAt");
        if (intentKey == null || intentKey.isBlank() || intentKey.length() > 160) {
            throw new IllegalArgumentException("alert intent key must be present and at most 160 characters");
        }
        if (requestRevision < 0 || schemaVersion < 1) {
            throw new IllegalArgumentException("alert revision and schema version must be valid");
        }
        if (audience == PunishmentRequestAlertAudience.DIRECT_RECIPIENT && recipientId == null) {
            throw new IllegalArgumentException("direct alert requires a recipient");
        }
        if (audience != PunishmentRequestAlertAudience.DIRECT_RECIPIENT && recipientId != null) {
            throw new IllegalArgumentException("audience alert cannot contain a direct recipient");
        }
        if (audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS && minimumRank == null) {
            throw new IllegalArgumentException("reviewer alert requires a minimum rank");
        }
    }
}
