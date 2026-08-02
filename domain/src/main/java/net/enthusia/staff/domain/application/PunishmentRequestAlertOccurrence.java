package net.enthusia.staff.domain.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable identity for one lifecycle-event occurrence.
 *
 * <p>Revision-scoped occurrences identify transitions that can happen only once for a request
 * revision. Lease-fence occurrences identify repeatable claim transitions without relying on a
 * timestamp or random value.</p>
 */
public record PunishmentRequestAlertOccurrence(
        String key,
        UUID actorId
) {
    private static final int MAX_KEY_LENGTH = 160;

    public PunishmentRequestAlertOccurrence {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("alert occurrence key must be present and at most 160 characters");
        }
        key = key.trim();
    }

    public static PunishmentRequestAlertOccurrence forRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("request revision must not be negative");
        }
        return new PunishmentRequestAlertOccurrence("request-revision:" + revision, null);
    }

    public static PunishmentRequestAlertOccurrence forRevision(long revision, UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (revision < 0) {
            throw new IllegalArgumentException("request revision must not be negative");
        }
        return new PunishmentRequestAlertOccurrence("request-revision:" + revision, actorId);
    }

    public static PunishmentRequestAlertOccurrence forClaim(long fenceToken, UUID reviewerId) {
        Objects.requireNonNull(reviewerId, "reviewerId");
        if (fenceToken < 1) {
            throw new IllegalArgumentException("claim fence token must be positive");
        }
        return new PunishmentRequestAlertOccurrence("operation-lease-fence:" + fenceToken, reviewerId);
    }
}
