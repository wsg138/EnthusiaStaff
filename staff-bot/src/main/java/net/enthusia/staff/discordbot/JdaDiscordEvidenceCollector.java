package net.enthusia.staff.discordbot;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Message;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** JDA-only bounded evidence acquisition. Persistence and authorization live outside this adapter. */
final class JdaDiscordEvidenceCollector {
    private static final int INITIAL_HISTORY_LIMIT = 11;
    private static final int EXPANDED_HISTORY_LIMIT = 21;

    record Initial(
            InvestigationEvidence.Message focus,
            List<InvestigationEvidence.Message> before,
            List<InvestigationEvidence.Message> after
    ) {
    }

    Initial initial(Message focus) {
        requireMessage(focus);
        List<Message> around = focus.getChannel()
                .getHistoryAround(focus.getId(), INITIAL_HISTORY_LIMIT)
                .complete()
                .getRetrievedHistory();
        return new Initial(
                toDomain(focus),
                side(around, focus, false, 0, InvestigationEvidence.MAX_INITIAL_BEFORE),
                side(around, focus, true, 0, InvestigationEvidence.MAX_INITIAL_AFTER)
        );
    }

    List<InvestigationEvidence.Message> additional(Message focus) {
        requireMessage(focus);
        List<Message> around = focus.getChannel()
                .getHistoryAround(focus.getId(), EXPANDED_HISTORY_LIMIT)
                .complete()
                .getRetrievedHistory();
        List<InvestigationEvidence.Message> result = new ArrayList<>();
        result.addAll(side(around, focus, false, InvestigationEvidence.MAX_INITIAL_BEFORE, 5));
        result.addAll(side(around, focus, true, InvestigationEvidence.MAX_INITIAL_AFTER, 5));
        return List.copyOf(result);
    }

    InvestigationEvidence.Message toDomain(Message message) {
        requireMessage(message);
        List<InvestigationEvidence.Attachment> attachments = message.getAttachments().stream()
                .limit(InvestigationEvidence.MAX_ATTACHMENTS)
                .map(attachment -> new InvestigationEvidence.Attachment(
                        attachment.getId(),
                        attachment.getFileName(),
                        attachment.getSize(),
                        Optional.ofNullable(attachment.getContentType()),
                        attachment.getUrl()
                ))
                .toList();
        return new InvestigationEvidence.Message(
                message.getGuild().getId(),
                message.getChannel().getId(),
                message.getId(),
                new DiscordUserId(message.getAuthor().getId()),
                message.getTimeCreated().toInstant(),
                Optional.ofNullable(message.getTimeEdited()).map(OffsetDateTime::toInstant),
                message.getJumpUrl(),
                message.getContentRaw(),
                attachments
        );
    }

    private List<InvestigationEvidence.Message> side(
            List<Message> around,
            Message focus,
            boolean newer,
            int skip,
            int limit
    ) {
        Comparator<Message> order = Comparator.comparingLong(Message::getIdLong);
        if (!newer) {
            order = order.reversed();
        }
        long focusId = focus.getIdLong();
        return around.stream()
                .filter(message -> message.getIdLong() != focusId)
                .filter(message -> newer
                        ? Long.compareUnsigned(message.getIdLong(), focusId) > 0
                        : Long.compareUnsigned(message.getIdLong(), focusId) < 0)
                .sorted(order)
                .skip(skip)
                .limit(limit)
                .map(this::toDomain)
                .toList();
    }

    private static void requireMessage(Message message) {
        if (message == null || !message.isFromGuild()) {
            throw new IllegalArgumentException("Discord evidence requires a guild message");
        }
    }
}
