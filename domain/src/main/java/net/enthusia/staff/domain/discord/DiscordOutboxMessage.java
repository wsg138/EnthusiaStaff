package net.enthusia.staff.domain.discord;

import java.time.Instant;
import java.util.UUID;

public record DiscordOutboxMessage(
        UUID messageId,
        String destination,
        String eventType,
        String payloadJson,
        int attemptCount,
        Instant createdAt
) {
    public DiscordOutboxMessage {
        if (messageId == null || destination == null || destination.isBlank()
                || eventType == null || eventType.isBlank() || payloadJson == null || payloadJson.isBlank()
                || attemptCount < 0 || createdAt == null) {
            throw new IllegalArgumentException("Discord outbox message fields must be present");
        }
    }
}
