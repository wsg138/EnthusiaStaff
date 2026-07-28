package net.enthusia.staff.domain.network;

import java.time.Instant;
import java.util.UUID;

public record NetworkOutboxMessage(
        UUID messageId,
        String idempotencyKey,
        String destination,
        String messageType,
        int protocolVersion,
        String payloadJson,
        int attemptCount,
        Instant createdAt
) {
    public NetworkOutboxMessage {
        if (messageId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || destination == null || destination.isBlank() || messageType == null || messageType.isBlank()
                || protocolVersion < 1 || payloadJson == null || payloadJson.isBlank() || attemptCount < 0
                || createdAt == null) {
            throw new IllegalArgumentException("network outbox fields must be present");
        }
    }
}
