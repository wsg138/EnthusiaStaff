package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Explicit allowlisted DTOs for the private moderation read API. */
final class ModerationReadApiModel {
    private ModerationReadApiModel() {
    }

    record ReadRequest(String actorId, String guildId, String targetKey, Optional<MessageQuery> messages) {
        ReadRequest {
            messages = messages == null ? Optional.empty() : messages;
        }
    }

    record MessageQuery(
            Optional<String> channelId,
            Optional<String> beforeMessageId,
            Optional<String> afterMessageId,
            Optional<String> text,
            Optional<String> authorId,
            Optional<String> date,
            int limit
    ) {
        MessageQuery {
            channelId = safe(channelId);
            beforeMessageId = safe(beforeMessageId);
            afterMessageId = safe(afterMessageId);
            text = safe(text);
            authorId = safe(authorId);
            date = safe(date);
        }

        private static Optional<String> safe(Optional<String> value) {
            return value == null ? Optional.empty() : value;
        }
    }

    record BootstrapResponse(
            ActorDto actor,
            IdentityDto identity,
            List<LinkedAccountDto> linkedAccounts,
            List<SanctionDto> activeSanctions,
            List<HistoryDto> history,
            List<CaseDto> cases,
            List<NoteDto> notes,
            List<ChannelDto> channels,
            MessagePageDto messages,
            Optional<String> centeredMessageId
    ) {
        BootstrapResponse {
            linkedAccounts = List.copyOf(linkedAccounts);
            activeSanctions = List.copyOf(activeSanctions);
            history = List.copyOf(history);
            cases = List.copyOf(cases);
            notes = List.copyOf(notes);
            channels = List.copyOf(channels);
            centeredMessageId = centeredMessageId == null ? Optional.empty() : centeredMessageId;
        }
    }

    record ActorDto(String discordId, String displayName) {
    }

    record IdentityDto(
            String discordId,
            String username,
            Optional<String> globalName,
            Optional<String> serverName,
            String displayName,
            String avatarUrl,
            String linkState,
            Optional<String> minecraftMain,
            String targetStatus
    ) {
        IdentityDto {
            globalName = globalName == null ? Optional.empty() : globalName;
            serverName = serverName == null ? Optional.empty() : serverName;
            minecraftMain = minecraftMain == null ? Optional.empty() : minecraftMain;
        }
    }

    record LinkedAccountDto(String playerId, Optional<String> username, String platform, boolean main) {
        LinkedAccountDto {
            username = username == null ? Optional.empty() : username;
        }
    }

    record SanctionDto(
            String sanctionId,
            String caseId,
            String type,
            String reason,
            Instant issuedAt,
            Optional<Instant> expiresAt
    ) {
        SanctionDto {
            expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        }
    }

    record HistoryDto(
            String stableKey,
            String eventType,
            Instant occurredAt,
            Optional<String> caseId,
            Optional<String> punishmentType,
            String status,
            String reason,
            Optional<String> actorName,
            Optional<String> exactReasonId,
            Optional<String> sanctionFamily
    ) {
        HistoryDto {
            caseId = caseId == null ? Optional.empty() : caseId;
            punishmentType = punishmentType == null ? Optional.empty() : punishmentType;
            actorName = actorName == null ? Optional.empty() : actorName;
            exactReasonId = exactReasonId == null ? Optional.empty() : exactReasonId;
            sanctionFamily = sanctionFamily == null ? Optional.empty() : sanctionFamily;
        }
    }

    record CaseDto(
            String caseId,
            String reason,
            String exactReasonId,
            String sanctionFamily,
            String state,
            Instant issuedAt,
            String actorName
    ) {
    }

    record NoteDto(String noteId, String text, Instant createdAt, String actorId) {
    }

    record ChannelDto(
            String id,
            String name,
            Optional<String> categoryId,
            Optional<String> categoryName,
            boolean actorCanView
    ) {
        ChannelDto {
            categoryId = categoryId == null ? Optional.empty() : categoryId;
            categoryName = categoryName == null ? Optional.empty() : categoryName;
        }
    }

    record MessagePageDto(
            List<MessageDto> messages,
            Optional<String> olderCursor,
            Optional<String> newerCursor,
            boolean contentAvailable,
            Optional<String> warning
    ) {
        MessagePageDto {
            messages = List.copyOf(messages);
            olderCursor = olderCursor == null ? Optional.empty() : olderCursor;
            newerCursor = newerCursor == null ? Optional.empty() : newerCursor;
            warning = warning == null ? Optional.empty() : warning;
        }
    }

    record MessageDto(
            String id,
            String guildId,
            String channelId,
            String channelName,
            Optional<String> categoryName,
            AuthorDto author,
            Instant createdAt,
            Optional<Instant> editedAt,
            Optional<String> content,
            Optional<String> replyToMessageId,
            List<AttachmentDto> attachments,
            boolean targetAuthor,
            boolean deletedKnown
    ) {
        MessageDto {
            categoryName = categoryName == null ? Optional.empty() : categoryName;
            editedAt = editedAt == null ? Optional.empty() : editedAt;
            content = content == null ? Optional.empty() : content;
            replyToMessageId = replyToMessageId == null ? Optional.empty() : replyToMessageId;
            attachments = List.copyOf(attachments);
        }
    }

    record AuthorDto(
            String discordId,
            String username,
            Optional<String> globalName,
            Optional<String> serverName,
            String displayName,
            String avatarUrl
    ) {
        AuthorDto {
            globalName = globalName == null ? Optional.empty() : globalName;
            serverName = serverName == null ? Optional.empty() : serverName;
        }
    }

    record AttachmentDto(String id, String fileName, Optional<String> contentType, long size, String url) {
        AttachmentDto {
            contentType = contentType == null ? Optional.empty() : contentType;
        }
    }

    record ErrorResponse(String code, String message) {
    }
}
