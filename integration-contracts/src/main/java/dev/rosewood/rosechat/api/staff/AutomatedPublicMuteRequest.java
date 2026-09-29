package dev.rosewood.rosechat.api.staff;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AutomatedPublicMuteRequest(
        UUID targetId,
        String targetName,
        UUID moderationEventId,
        String category,
        int severity,
        int strikeCount,
        Duration muteDuration,
        String idempotencyKey,
        List<AutomatedModerationEvidence> evidence
) {
    public AutomatedPublicMuteRequest {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(moderationEventId, "moderationEventId");
        Objects.requireNonNull(muteDuration, "muteDuration");
        Objects.requireNonNull(evidence, "evidence");
        targetName = bounded(targetName, "targetName", 64);
        category = bounded(category, "category", 96);
        idempotencyKey = bounded(idempotencyKey, "idempotencyKey", 128);
        evidence = List.copyOf(evidence);
        if (severity < 0 || severity > 100 || strikeCount < 1) {
            throw new IllegalArgumentException("invalid automated moderation severity/strike count");
        }
        if (muteDuration.isZero() || muteDuration.isNegative()) {
            throw new IllegalArgumentException("muteDuration must be positive");
        }
        if (evidence.size() < strikeCount) {
            throw new IllegalArgumentException("evidence must contain every enforcement strike");
        }
    }

    private static String bounded(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
