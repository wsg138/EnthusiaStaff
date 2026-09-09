package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

final class ModerationDiscordMessageMapper {
    ModerationReadApiModel.MessagePageDto page(
            ModerationReadContext context,
            List<Message> source,
            int limit
    ) {
        List<Message> limited = source.stream().limit(limit).toList();
        List<ModerationReadApiModel.MessageDto> messages = limited.stream()
                .map(message -> message(context, message)).toList();
        Optional<String> older = cursor(limited, false);
        Optional<String> newer = cursor(limited, true);
        boolean contentAvailable = messages.stream().anyMatch(item -> item.content().isPresent());
        Optional<String> warning = messages.isEmpty() || contentAvailable
                ? Optional.empty()
                : Optional.of("Discord returned no textual message content for this page.");
        return new ModerationReadApiModel.MessagePageDto(messages, older, newer, contentAvailable, warning);
    }

    private ModerationReadApiModel.MessageDto message(ModerationReadContext context, Message message) {
        TextChannel channel = (TextChannel) message.getChannel();
        Category category = channel.getParentCategory();
        String display = message.getContentDisplay();
        return new ModerationReadApiModel.MessageDto(
                message.getId(), context.guild().getId(), channel.getId(), channel.getName(),
                category == null ? Optional.empty() : Optional.of(category.getName()),
                author(context.guild(), message.getAuthor()), message.getTimeCreated().toInstant(),
                Optional.ofNullable(message.getTimeEdited()).map(value -> value.toInstant()),
                display.isEmpty() ? Optional.empty() : Optional.of(display),
                Optional.ofNullable(message.getMessageReference()).map(reference -> reference.getMessageId()),
                message.getAttachments().stream().map(attachment -> new ModerationReadApiModel.AttachmentDto(
                        attachment.getId(), attachment.getFileName(), Optional.ofNullable(attachment.getContentType()),
                        attachment.getSize(), attachment.getUrl())).toList(),
                isTargetAuthor(context, message), false);
    }

    private static boolean isTargetAuthor(ModerationReadContext context, Message message) {
        OptionalLong targetUser = context.readTarget().userId();
        return targetUser.isPresent() && message.getAuthor().getIdLong() == targetUser.orElseThrow();
    }

    private ModerationReadApiModel.AuthorDto author(Guild guild, User user) {
        Member member = memberIfPresent(guild, user.getIdLong());
        return new ModerationReadApiModel.AuthorDto(
                user.getId(), user.getName(), Optional.ofNullable(user.getGlobalName()),
                Optional.ofNullable(member == null ? null : member.getEffectiveName()),
                member == null ? displayName(user) : member.getEffectiveName(), user.getEffectiveAvatarUrl());
    }

    static Member memberIfPresent(Guild guild, long userId) {
        try {
            return guild.retrieveMemberById(userId).complete();
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                return null;
            }
            throw exception;
        }
    }

    static String displayName(User user) {
        return user.getGlobalName() == null ? user.getName() : user.getGlobalName();
    }

    private static Optional<String> cursor(List<Message> messages, boolean newest) {
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((newest ? messages.getFirst() : messages.getLast()).getId());
    }
}
