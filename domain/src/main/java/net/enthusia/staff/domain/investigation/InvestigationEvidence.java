package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Durable private Discord message evidence; every capture is bounded before persistence. */
public final class InvestigationEvidence {
    public static final int MAX_INITIAL_BEFORE = 5;
    public static final int MAX_INITIAL_AFTER = 5;
    public static final int MAX_CONTEXT_BATCH = 10;
    public static final int MAX_CONTENT_LENGTH = 16_000;
    public static final int MAX_ATTACHMENTS = 10;

    private InvestigationEvidence() {
    }

    public record Attachment(
            String attachmentId,
            String fileName,
            long sizeBytes,
            Optional<String> contentType,
            String sourceUrl
    ) {
        public Attachment {
            if (blank(attachmentId) || blank(fileName) || sizeBytes < 0 || contentType == null
                    || blank(sourceUrl) || sourceUrl.length() > 2_048) {
                throw new IllegalArgumentException("evidence attachment metadata is invalid");
            }
        }
    }

    public record Message(
            String guildId,
            String channelId,
            String messageId,
            DiscordUserId authorUserId,
            Instant createdAt,
            Optional<Instant> editedAt,
            String jumpUrl,
            String content,
            List<Attachment> attachments
    ) {
        public Message {
            if (blank(guildId) || blank(channelId) || blank(messageId) || authorUserId == null
                    || createdAt == null || editedAt == null || blank(jumpUrl) || content == null
                    || content.length() > MAX_CONTENT_LENGTH || attachments == null
                    || attachments.size() > MAX_ATTACHMENTS) {
                throw new IllegalArgumentException("evidence message fields are invalid");
            }
            attachments = List.copyOf(attachments);
            editedAt.ifPresent(value -> {
                if (value.isBefore(createdAt)) {
                    throw new IllegalArgumentException("message edit cannot predate creation");
                }
            });
        }
    }

    public record Capture(
            UUID evidenceId,
            String operationKey,
            ModerationSubjectId subjectId,
            UUID caseId,
            Message focus,
            List<Message> before,
            List<Message> after,
            Instant capturedAt
    ) {
        public Capture {
            if (evidenceId == null || blank(operationKey) || operationKey.length() > 128
                    || subjectId == null || caseId == null || focus == null || before == null
                    || after == null || capturedAt == null || before.size() > MAX_INITIAL_BEFORE
                    || after.size() > MAX_INITIAL_AFTER) {
                throw new IllegalArgumentException("evidence capture fields are invalid or unbounded");
            }
            before = List.copyOf(before);
            after = List.copyOf(after);
            requireSameLocation(focus, before);
            requireSameLocation(focus, after);
            requireUniqueMessages(focus, before, after);
        }
    }

    public record Edit(
            UUID evidenceId,
            String operationKey,
            Message message,
            Instant recordedAt
    ) {
        public Edit {
            if (evidenceId == null || blank(operationKey) || operationKey.length() > 128
                    || message == null || recordedAt == null) {
                throw new IllegalArgumentException("evidence edit fields are invalid");
            }
        }
    }

    public record ContextBatch(
            UUID evidenceId,
            String operationKey,
            List<Message> messages,
            Instant capturedAt
    ) {
        public ContextBatch {
            if (evidenceId == null || blank(operationKey) || operationKey.length() > 128
                    || messages == null || messages.isEmpty() || messages.size() > MAX_CONTEXT_BATCH
                    || capturedAt == null) {
                throw new IllegalArgumentException("additional evidence context must be bounded");
            }
            messages = List.copyOf(messages);
            requireUniqueMessages(null, messages, List.of());
        }
    }

    public record Stored(
            UUID evidenceId,
            UUID caseId,
            ModerationSubjectId subjectId,
            String messageId,
            Instant capturedAt,
            Instant lastObservedAt,
            long revision,
            boolean replayed
    ) {
        public Stored {
            if (evidenceId == null || caseId == null || subjectId == null || blank(messageId)
                    || capturedAt == null || lastObservedAt == null || lastObservedAt.isBefore(capturedAt)
                    || revision < 0) {
                throw new IllegalArgumentException("stored evidence fields are invalid");
            }
        }
    }

    private static void requireSameLocation(Message focus, List<Message> messages) {
        for (Message message : messages) {
            if (!focus.guildId().equals(message.guildId()) || !focus.channelId().equals(message.channelId())) {
                throw new IllegalArgumentException("evidence context must come from the focus channel");
            }
        }
    }

    @SafeVarargs
    private static void requireUniqueMessages(Message focus, List<Message>... groups) {
        Set<String> ids = new HashSet<>();
        if (focus != null) {
            ids.add(focus.messageId());
        }
        for (List<Message> group : groups) {
            for (Message message : group) {
                if (!ids.add(message.messageId())) {
                    throw new IllegalArgumentException("evidence context cannot repeat message ids");
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
