package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;

public record PunishmentRequestAlertIntent(
        UUID alertId,
        String intentKey,
        UUID requestId,
        long requestRevision,
        PunishmentRequestLifecycleEventType eventType,
        PunishmentRequestAlertOccurrence occurrence,
        PunishmentRequestAlertAudience audience,
        UUID recipientId,
        UUID excludedRecipientId,
        StaffRank minimumRank,
        CaseVisibility visibility,
        int schemaVersion,
        Instant createdAt,
        Instant expiresAt
) {
    public PunishmentRequestAlertIntent {
        Objects.requireNonNull(alertId, "alertId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurrence, "occurrence");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (intentKey == null || intentKey.isBlank() || intentKey.length() > 160) {
            throw new IllegalArgumentException("alert intent key must be present and at most 160 characters");
        }
        if (requestRevision < 0 || schemaVersion < 1) {
            throw new IllegalArgumentException("alert revision and schema version must be valid");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("alert intent expiry must be after creation");
        }
        if (eventType == PunishmentRequestLifecycleEventType.REQUEST_CLAIMED
                && occurrence.actorId() == null) {
            throw new IllegalArgumentException("claim alerts require the immutable claiming reviewer identifier");
        }
        switch (audience) {
            case DIRECT_RECIPIENT -> {
                if (recipientId == null || minimumRank != null || excludedRecipientId != null) {
                    throw new IllegalArgumentException("direct alert requires only a direct recipient");
                }
            }
            case ELIGIBLE_REVIEWERS -> {
                if (recipientId != null || minimumRank == null || excludedRecipientId == null) {
                    throw new IllegalArgumentException("reviewer alert requires minimum rank and excluded requester");
                }
                if (minimumRank == StaffRank.DEVELOPER || minimumRank == StaffRank.SYSTEM) {
                    throw new IllegalArgumentException("reviewer alert requires a moderation rank");
                }
            }
            case OPERATIONAL_ADMINISTRATORS -> {
                if (recipientId != null || minimumRank != null || excludedRecipientId != null) {
                    throw new IllegalArgumentException("operational alert cannot contain recipient filters");
                }
            }
            default -> throw new IllegalStateException("unsupported alert audience: " + audience);
        }
    }

    /** Compatibility constructor for deterministic, non-repeatable revision events. */
    public PunishmentRequestAlertIntent(
            UUID alertId,
            String intentKey,
            UUID requestId,
            long requestRevision,
            PunishmentRequestLifecycleEventType eventType,
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            UUID excludedRecipientId,
            StaffRank minimumRank,
            CaseVisibility visibility,
            int schemaVersion,
            Instant createdAt,
            Instant expiresAt
    ) {
        this(
                alertId,
                intentKey,
                requestId,
                requestRevision,
                eventType,
                PunishmentRequestAlertOccurrence.forRevision(requestRevision),
                audience,
                recipientId,
                excludedRecipientId,
                minimumRank,
                visibility,
                schemaVersion,
                createdAt,
                expiresAt
        );
    }
}
