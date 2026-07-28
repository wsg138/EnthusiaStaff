package net.enthusia.staff.domain.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;

public record CreateReportRequest(
        IdempotencyKey idempotencyKey,
        UUID reporterId,
        UUID targetId,
        String reasonId,
        String description,
        String serverId,
        Optional<String> worldId,
        Optional<String> reporterCoordinates,
        Optional<String> targetCoordinates,
        Instant createdAt,
        List<ChatContextMessage> publicChatContext,
        List<PrivateMessageContextMessage> privateMessageContext,
        Optional<ClientEvidenceSnapshot> targetClientEvidence
) {
    public CreateReportRequest {
        if (idempotencyKey == null || reporterId == null || targetId == null || reasonId == null
                || description == null || serverId == null || worldId == null || reporterCoordinates == null
                || targetCoordinates == null || createdAt == null || publicChatContext == null
                || privateMessageContext == null || targetClientEvidence == null) {
            throw new IllegalArgumentException("report fields must be present");
        }
        if (reporterId.equals(targetId)) {
            throw new IllegalArgumentException("players cannot report themselves");
        }
        if (reasonId.isBlank() || reasonId.length() > 96 || description.isBlank() || description.length() > 4_000
                || serverId.isBlank() || serverId.length() > 64
                || publicChatContext.size() > 2_000 || privateMessageContext.size() > 2_000) {
            throw new IllegalArgumentException("report text or context bounds are invalid");
        }
        publicChatContext = List.copyOf(publicChatContext);
        privateMessageContext = List.copyOf(privateMessageContext);
        if (targetClientEvidence.stream().anyMatch(snapshot ->
                !snapshot.playerId().equals(targetId) || snapshot.capturedAt().isAfter(createdAt))) {
            throw new IllegalArgumentException("target client evidence does not match the report");
        }
    }

    public record ChatContextMessage(UUID senderId, String senderName, String body, Instant sentAt) {
        public ChatContextMessage {
            if (senderId == null || senderName == null || senderName.isBlank() || senderName.length() > 32
                    || body == null || body.length() > 1_000 || sentAt == null) {
                throw new IllegalArgumentException("chat context message fields are invalid");
            }
        }
    }

    public record PrivateMessageContextMessage(
            UUID senderId,
            String senderName,
            UUID recipientId,
            String recipientName,
            String body,
            Instant sentAt
    ) {
        public PrivateMessageContextMessage {
            if (senderId == null || senderName == null || senderName.isBlank()
                    || senderName.length() > 32 || recipientId == null
                    || recipientName == null || recipientName.isBlank()
                    || recipientName.length() > 32 || body == null || body.length() > 1_000
                    || sentAt == null) {
                throw new IllegalArgumentException("private-message context fields are invalid");
            }
        }
    }
}
