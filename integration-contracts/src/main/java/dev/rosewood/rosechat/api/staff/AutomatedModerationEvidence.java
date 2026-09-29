package dev.rosewood.rosechat.api.staff;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AutomatedModerationEvidence(
        UUID moderationEventId,
        Instant occurredAt,
        String message,
        String category,
        double confidence,
        int severity
) {
    public AutomatedModerationEvidence {
        Objects.requireNonNull(moderationEventId, "moderationEventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        message = bounded(message, "message", 1_024, false);
        category = bounded(category, "category", 96, true);
        if (!Double.isFinite(confidence) || confidence < 0.0D || confidence > 1.0D) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        if (severity < 0 || severity > 100) {
            throw new IllegalArgumentException("severity must be in [0, 100]");
        }
    }

    private static String bounded(String value, String field, int maximum, boolean trim) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        if (value.chars().anyMatch(character -> character == '\r' || character == '\n' || character == '\0')) {
            throw new IllegalArgumentException(field + " contains unsupported control characters");
        }
        return trim ? value.trim() : value;
    }
}
