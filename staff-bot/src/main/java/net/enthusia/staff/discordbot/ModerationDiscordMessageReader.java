package net.enthusia.staff.discordbot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/** Performs bounded, actor-visible Discord REST message reads without Gateway scraping. */
final class ModerationDiscordMessageReader {
    private static final int MAX_PAGE = 50;
    private static final int DEFAULT_PAGE = 25;
    private static final int MAX_RECENT_CHANNELS = 8;
    private static final int RECENT_PER_CHANNEL = 20;

    private final ModerationDiscordMessageMapper mapper = new ModerationDiscordMessageMapper();

    List<ModerationReadApiModel.ChannelDto> visibleChannels(ModerationReadContext context) {
        return context.guild().getChannels().stream()
                .filter(TextChannel.class::isInstance)
                .map(TextChannel.class::cast)
                .filter(channel -> hasReadAccess(context, channel))
                .map(ModerationDiscordMessageReader::channelDto)
                .toList();
    }

    ModerationReadApiModel.MessagePageDto initial(
            ModerationReadContext context,
            List<ModerationReadApiModel.ChannelDto> channels
    ) {
        if (context.readTarget() instanceof ModerationReadTarget.MessageContext messageTarget) {
            return surrounding(context, messageTarget, DEFAULT_PAGE);
        }
        OptionalLong boundChannel = initialChannel(context.readTarget());
        if (boundChannel.isPresent()) {
            return recentTargetInChannel(context, boundChannel.orElseThrow(), DEFAULT_PAGE);
        }
        return recentTarget(context, channels, emptyQuery(), MAX_PAGE);
    }

    ModerationReadApiModel.MessagePageDto query(
            ModerationReadContext context,
            ModerationReadApiModel.MessageQuery query
    ) {
        int limit = boundedLimit(query.limit());
        if (query.channelId().isEmpty()) {
            return recentTarget(context, visibleChannels(context), query, limit);
        }
        long channelId = ModerationReadRequestAuthorizer.snowflake(query.channelId().orElseThrow(), "channel");
        TextChannel channel = visibleChannel(context, channelId);
        return mapper.page(context, filterAndLimit(page(channel, query, limit), query, limit), limit);
    }

    static OptionalLong initialChannel(ModerationReadTarget target) {
        if (target instanceof ModerationReadTarget.DiscordUserContext) {
            return target.channelId();
        }
        return OptionalLong.empty();
    }

    private ModerationReadApiModel.MessagePageDto recentTargetInChannel(
            ModerationReadContext context,
            long channelId,
            int limit
    ) {
        TextChannel channel = visibleChannel(context, channelId);
        List<Message> targetMessages = channel.getHistory().retrievePast(MAX_PAGE).complete().stream()
                .filter(message -> message.getAuthor().getIdLong() == context.readTarget().userId())
                .limit(boundedLimit(limit))
                .toList();
        return mapper.page(context, targetMessages, boundedLimit(limit));
    }

    private ModerationReadApiModel.MessagePageDto surrounding(
            ModerationReadContext context,
            ModerationReadTarget.MessageContext target,
            int limit
    ) {
        TextChannel channel = visibleChannel(context, target.channelIdValue());
        Message exact = channel.retrieveMessageById(target.messageIdValue()).complete();
        if (exact.getAuthor().getIdLong() != target.userId()) {
            throw new IllegalArgumentException("signed message target author no longer matches Discord");
        }
        List<Message> messages = channel.getHistoryAround(target.messageIdValue(), boundedLimit(limit))
                .complete().getRetrievedHistory();
        return mapper.page(context, messages, boundedLimit(limit));
    }

    private ModerationReadApiModel.MessagePageDto recentTarget(
            ModerationReadContext context,
            List<ModerationReadApiModel.ChannelDto> channels,
            ModerationReadApiModel.MessageQuery query,
            int limit
    ) {
        List<Message> messages = new ArrayList<>();
        channels.stream().limit(MAX_RECENT_CHANNELS)
                .map(channel -> context.guild().getTextChannelById(channel.id()))
                .filter(java.util.Objects::nonNull)
                .forEach(channel -> collectTargetMessages(channel, context.readTarget().userId(), messages));
        messages.sort(Comparator.comparing(Message::getTimeCreated).reversed());
        return mapper.page(context, filterAndLimit(messages, query, limit), limit);
    }

    static List<Message> filterAndLimit(
            List<Message> messages,
            ModerationReadApiModel.MessageQuery query,
            int limit
    ) {
        return ModerationMessageFilter.apply(messages, query).stream().limit(limit).toList();
    }

    private static void collectTargetMessages(TextChannel channel, long targetId, List<Message> target) {
        channel.getHistory().retrievePast(RECENT_PER_CHANNEL).complete().stream()
                .filter(message -> message.getAuthor().getIdLong() == targetId)
                .forEach(target::add);
    }

    private static List<Message> page(
            TextChannel channel,
            ModerationReadApiModel.MessageQuery query,
            int limit
    ) {
        if (query.aroundMessageId().isPresent()) {
            return channel.getHistoryAround(query.aroundMessageId().orElseThrow(), limit)
                    .complete().getRetrievedHistory();
        }
        if (query.beforeMessageId().isPresent()) {
            return channel.getHistoryBefore(query.beforeMessageId().orElseThrow(), limit).complete().getRetrievedHistory();
        }
        if (query.afterMessageId().isPresent()) {
            return channel.getHistoryAfter(query.afterMessageId().orElseThrow(), limit).complete().getRetrievedHistory();
        }
        return channel.getHistory().retrievePast(limit).complete();
    }

    private static TextChannel visibleChannel(ModerationReadContext context, long channelId) {
        TextChannel channel = context.guild().getTextChannelById(channelId);
        if (channel == null || !hasReadAccess(context, channel)) {
            throw new StaffReadAuthorization.DeniedException();
        }
        return channel;
    }

    static boolean hasReadPermissions(
            boolean actorCanView,
            boolean actorCanReadHistory,
            boolean botCanView,
            boolean botCanReadHistory
    ) {
        return actorCanView && actorCanReadHistory && botCanView && botCanReadHistory;
    }

    private static boolean hasReadAccess(ModerationReadContext context, TextChannel channel) {
        Member botMember = context.guild().getSelfMember();
        return hasReadPermissions(
                context.actorMember().hasPermission(channel, Permission.VIEW_CHANNEL),
                context.actorMember().hasPermission(channel, Permission.MESSAGE_HISTORY),
                botMember.hasPermission(channel, Permission.VIEW_CHANNEL),
                botMember.hasPermission(channel, Permission.MESSAGE_HISTORY));
    }

    private static ModerationReadApiModel.ChannelDto channelDto(TextChannel channel) {
        Category category = channel.getParentCategory();
        return new ModerationReadApiModel.ChannelDto(
                channel.getId(), channel.getName(),
                category == null ? Optional.empty() : Optional.of(category.getId()),
                category == null ? Optional.empty() : Optional.of(category.getName()), true);
    }

    static int boundedLimit(int requested) {
        return requested <= 0 ? DEFAULT_PAGE : Math.min(requested, MAX_PAGE);
    }

    static ModerationReadApiModel.MessageQuery emptyQuery() {
        return new ModerationReadApiModel.MessageQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), DEFAULT_PAGE);
    }
}
