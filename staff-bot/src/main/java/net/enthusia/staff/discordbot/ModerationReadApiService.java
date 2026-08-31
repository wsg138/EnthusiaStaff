package net.enthusia.staff.discordbot;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/** Authorized, bounded orchestration for the private staging moderation read API. */
final class ModerationReadApiService {
    private static final int MAX_PAGE = 50;
    private static final int DEFAULT_PAGE = 25;
    private static final int MAX_RECENT_CHANNELS = 8;
    private static final int RECENT_PER_CHANNEL = 20;

    private final long guildId;
    private final StaffModerationRuntime moderation;
    private final JDA jda;

    ModerationReadApiService(long guildId, StaffModerationRuntime moderation, JDA jda) {
        if (guildId <= 0 || moderation == null || jda == null) {
            throw new IllegalArgumentException("read API dependencies must be present");
        }
        this.guildId = guildId;
        this.moderation = moderation;
        this.jda = jda;
    }

    ModerationReadApiModel.BootstrapResponse bootstrap(ModerationReadApiModel.ReadRequest request) {
        RequestContext context = authorize(request);
        StaffModerationReadService.Snapshot snapshot = moderation.reads().snapshot(context.target());
        List<ModerationReadApiModel.ChannelDto> channels = visibleChannels(context.guild(), context.actorMember());
        ModerationReadApiModel.MessagePageDto messages = initialMessages(context, channels);
        return new ModerationReadApiModel.BootstrapResponse(
                actorDto(context),
                identityDto(context, snapshot),
                linkedDtos(snapshot),
                sanctionDtos(snapshot),
                historyDtos(snapshot),
                caseDtos(snapshot),
                noteDtos(snapshot),
                channels,
                messages,
                context.readTarget().messageId().isPresent()
                        ? Optional.of(Long.toUnsignedString(context.readTarget().messageId().orElseThrow()))
                        : Optional.empty()
        );
    }

    ModerationReadApiModel.MessagePageDto messages(ModerationReadApiModel.ReadRequest request) {
        RequestContext context = authorize(request);
        return queryMessages(context, request.messages().orElseGet(ModerationReadApiService::emptyQuery));
    }

    private RequestContext authorize(ModerationReadApiModel.ReadRequest request) {
        if (request == null || !Long.toUnsignedString(guildId).equals(request.guildId())) {
            throw new StaffReadAuthorization.DeniedException();
        }
        long actorId = snowflake(request.actorId(), "actor");
        ModerationReadTarget readTarget = ModerationReadTarget.parse(request.targetKey());
        Guild guild = requireGuild();
        Member actorMember = guild.retrieveMemberById(actorId).complete();
        Actor actor = moderation.actors().invoker(
                new DiscordUserId(Long.toUnsignedString(actorId)),
                actorMember.getEffectiveName()
        );
        StaffModerationReadService.Target target = moderation.reads().discordTarget(
                new DiscordUserId(Long.toUnsignedString(readTarget.userId()))
        );
        requireAllReads(actor, target);
        return new RequestContext(actorId, actorMember, guild, actor, target, readTarget);
    }

    private void requireAllReads(Actor actor, StaffModerationReadService.Target target) {
        Optional<Actor> targetStaff = moderation.actors().targetStaff(target);
        for (DiscordModerationOperation operation : List.of(
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                DiscordModerationOperation.VIEW_HISTORY,
                DiscordModerationOperation.VIEW_NOTES)) {
            moderation.authorization().require(actor, targetStaff, operation, ModerationPlatform.DISCORD);
        }
    }

    private ModerationReadApiModel.MessagePageDto initialMessages(
            RequestContext context,
            List<ModerationReadApiModel.ChannelDto> channels
    ) {
        if (context.readTarget() instanceof ModerationReadTarget.MessageContext messageTarget) {
            return surroundingMessages(context, messageTarget, DEFAULT_PAGE);
        }
        return recentTargetMessages(context, channels);
    }

    private ModerationReadApiModel.MessagePageDto queryMessages(
            RequestContext context,
            ModerationReadApiModel.MessageQuery query
    ) {
        int limit = boundedLimit(query.limit());
        if (query.channelId().isEmpty()) {
            return recentTargetMessages(context, visibleChannels(context.guild(), context.actorMember()));
        }
        TextChannel channel = visibleChannel(context, snowflake(query.channelId().orElseThrow(), "channel"));
        List<Message> raw = page(channel, query, limit);
        return pageDto(context, filter(raw, query), limit);
    }

    private ModerationReadApiModel.MessagePageDto surroundingMessages(
            RequestContext context,
            ModerationReadTarget.MessageContext target,
            int limit
    ) {
        TextChannel channel = visibleChannel(context, target.channelIdValue());
        Message exact = channel.retrieveMessageById(target.messageIdValue()).complete();
        if (exact.getAuthor().getIdLong() != target.userId()) {
            throw new IllegalArgumentException("signed message target author no longer matches Discord");
        }
        List<Message> messages = channel.getHistoryAround(target.messageIdValue(), boundedLimit(limit))
                .complete()
                .getRetrievedHistory();
        return pageDto(context, messages, limit);
    }

    private ModerationReadApiModel.MessagePageDto recentTargetMessages(
            RequestContext context,
            List<ModerationReadApiModel.ChannelDto> channelDtos
    ) {
        List<Message> messages = new ArrayList<>();
        channelDtos.stream()
                .limit(MAX_RECENT_CHANNELS)
                .map(dto -> context.guild().getTextChannelById(dto.id()))
                .filter(java.util.Objects::nonNull)
                .forEach(channel -> channel.getHistory().retrievePast(RECENT_PER_CHANNEL).complete().stream()
                        .filter(message -> message.getAuthor().getIdLong() == context.readTarget().userId())
                        .forEach(messages::add));
        messages.sort(Comparator.comparing(Message::getTimeCreated).reversed());
        return pageDto(context, messages.stream().limit(MAX_PAGE).toList(), MAX_PAGE);
    }

    private static List<Message> page(
            TextChannel channel,
            ModerationReadApiModel.MessageQuery query,
            int limit
    ) {
        if (query.beforeMessageId().isPresent()) {
            return channel.getHistoryBefore(query.beforeMessageId().orElseThrow(), limit)
                    .complete().getRetrievedHistory();
        }
        if (query.afterMessageId().isPresent()) {
            return channel.getHistoryAfter(query.afterMessageId().orElseThrow(), limit)
                    .complete().getRetrievedHistory();
        }
        return channel.getHistory().retrievePast(limit).complete();
    }

    private static List<Message> filter(List<Message> source, ModerationReadApiModel.MessageQuery query) {
        Predicate<Message> predicate = message -> true;
        if (query.authorId().isPresent()) {
            long author = snowflake(query.authorId().orElseThrow(), "author");
            predicate = predicate.and(message -> message.getAuthor().getIdLong() == author);
        }
        if (query.text().isPresent()) {
            String text = query.text().orElseThrow().strip().toLowerCase(java.util.Locale.ROOT);
            if (!text.isEmpty()) {
                predicate = predicate.and(message -> message.getContentRaw().toLowerCase(java.util.Locale.ROOT).contains(text));
            }
        }
        if (query.date().isPresent()) {
            LocalDate date = LocalDate.parse(query.date().orElseThrow());
            predicate = predicate.and(message -> message.getTimeCreated().toLocalDate().equals(date));
        }
        return source.stream().filter(predicate).toList();
    }

    private List<ModerationReadApiModel.ChannelDto> visibleChannels(Guild guild, Member actor) {
        return guild.getTextChannels().stream()
                .filter(channel -> actor.hasPermission(channel, Permission.VIEW_CHANNEL))
                .sorted(Comparator.comparing(TextChannel::getPosition).thenComparing(TextChannel::getName))
                .map(channel -> channelDto(channel, actor))
                .toList();
    }

    private static ModerationReadApiModel.ChannelDto channelDto(TextChannel channel, Member actor) {
        Category category = channel.getParentCategory();
        return new ModerationReadApiModel.ChannelDto(
                channel.getId(),
                channel.getName(),
                category == null ? Optional.empty() : Optional.of(category.getId()),
                category == null ? Optional.empty() : Optional.of(category.getName()),
                actor.hasPermission(channel, Permission.VIEW_CHANNEL)
        );
    }

    private static TextChannel visibleChannel(RequestContext context, long channelId) {
        TextChannel channel = context.guild().getTextChannelById(channelId);
        if (channel == null || !context.actorMember().hasPermission(channel, Permission.VIEW_CHANNEL)) {
            throw new StaffReadAuthorization.DeniedException();
        }
        return channel;
    }

    private ModerationReadApiModel.ActorDto actorDto(RequestContext context) {
        return new ModerationReadApiModel.ActorDto(
                Long.toUnsignedString(context.actorId()),
                context.actorMember().getEffectiveName()
        );
    }

    private ModerationReadApiModel.IdentityDto identityDto(
            RequestContext context,
            StaffModerationReadService.Snapshot snapshot
    ) {
        User user = jda.retrieveUserById(context.readTarget().userId()).complete();
        Member member = retrieveMemberIfPresent(context.guild(), context.readTarget().userId());
        Optional<String> main = snapshot.linkedMinecraft().stream()
                .filter(StaffModerationReadService.LinkedMinecraft::main)
                .map(account -> account.username().orElse(account.playerId().toString()))
                .findFirst();
        String status = snapshot.activeMinecraftSanctions().isEmpty() ? "No active sanctions" : "Active moderation";
        return new ModerationReadApiModel.IdentityDto(
                user.getId(),
                user.getName(),
                Optional.ofNullable(user.getGlobalName()),
                Optional.ofNullable(member == null ? null : member.getEffectiveName()),
                member == null ? displayName(user) : member.getEffectiveName(),
                user.getEffectiveAvatarUrl(),
                snapshot.target().subject().isPresent() ? "Linked" : "Unlinked",
                main,
                status
        );
    }

    private static Member retrieveMemberIfPresent(Guild guild, long userId) {
        try {
            return guild.retrieveMemberById(userId).complete();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String displayName(User user) {
        return user.getGlobalName() == null ? user.getName() : user.getGlobalName();
    }

    private static List<ModerationReadApiModel.LinkedAccountDto> linkedDtos(StaffModerationReadService.Snapshot snapshot) {
        return snapshot.linkedMinecraft().stream().map(account -> new ModerationReadApiModel.LinkedAccountDto(
                account.playerId().toString(), account.username(), account.platform().name(), account.main())).toList();
    }

    private static List<ModerationReadApiModel.SanctionDto> sanctionDtos(StaffModerationReadService.Snapshot snapshot) {
        return snapshot.activeMinecraftSanctions().stream().map(ModerationReadApiService::sanctionDto).toList();
    }

    private static ModerationReadApiModel.SanctionDto sanctionDto(ActiveSanction sanction) {
        return new ModerationReadApiModel.SanctionDto(
                sanction.sanctionId().toString(), sanction.caseId().toString(), sanction.type().name(),
                sanction.publicReason(), sanction.issuedAt(), sanction.expiresAt());
    }

    private static List<ModerationReadApiModel.HistoryDto> historyDtos(StaffModerationReadService.Snapshot snapshot) {
        Map<String, CaseReview> cases = new HashMap<>();
        snapshot.recentCases().forEach(review -> cases.put(review.caseId().toString(), review));
        return snapshot.recentHistory().stream().map(entry -> historyDto(entry, cases)).toList();
    }

    private static ModerationReadApiModel.HistoryDto historyDto(
            ModerationHistoryEntry entry,
            Map<String, CaseReview> cases
    ) {
        CaseReview review = entry.caseId().map(id -> cases.get(id.toString())).orElse(null);
        return new ModerationReadApiModel.HistoryDto(
                entry.stableKey(), entry.eventType().name(), entry.occurredAt(),
                entry.caseId().map(Object::toString), entry.punishmentType(), entry.status(), entry.publicReason(),
                entry.actorName(), Optional.ofNullable(review).map(CaseReview::exactReasonId),
                Optional.ofNullable(review).map(CaseReview::sanctionFamily));
    }

    private static List<ModerationReadApiModel.CaseDto> caseDtos(StaffModerationReadService.Snapshot snapshot) {
        return snapshot.recentCases().stream().map(review -> new ModerationReadApiModel.CaseDto(
                review.caseId().toString(), review.publicReason(), review.exactReasonId(), review.sanctionFamily(),
                review.state().name(), review.issuedAt(), review.actorName())).toList();
    }

    private static List<ModerationReadApiModel.NoteDto> noteDtos(StaffModerationReadService.Snapshot snapshot) {
        return snapshot.recentNotes().stream().map(ModerationReadApiService::noteDto).toList();
    }

    private static ModerationReadApiModel.NoteDto noteDto(StaffNote note) {
        return new ModerationReadApiModel.NoteDto(
                note.noteId().toString(), note.noteText(), note.createdAt(), note.actorId().toString());
    }

    private static ModerationReadApiModel.MessagePageDto pageDto(
            RequestContext context,
            List<Message> source,
            int limit
    ) {
        List<Message> limited = source.stream().limit(boundedLimit(limit)).toList();
        List<ModerationReadApiModel.MessageDto> messages = limited.stream()
                .map(message -> messageDto(context, message)).toList();
        Optional<String> older = limited.isEmpty() ? Optional.empty() : Optional.of(limited.getLast().getId());
        Optional<String> newer = limited.isEmpty() ? Optional.empty() : Optional.of(limited.getFirst().getId());
        boolean contentAvailable = messages.stream().anyMatch(message -> message.content().isPresent());
        Optional<String> warning = !messages.isEmpty() && !contentAvailable
                ? Optional.of("Discord returned no textual message content for this page.")
                : Optional.empty();
        return new ModerationReadApiModel.MessagePageDto(messages, older, newer, contentAvailable, warning);
    }

    private static ModerationReadApiModel.MessageDto messageDto(RequestContext context, Message message) {
        TextChannel channel = (TextChannel) message.getChannel();
        Category category = channel.getParentCategory();
        String raw = message.getContentRaw();
        return new ModerationReadApiModel.MessageDto(
                message.getId(), context.guild().getId(), channel.getId(), channel.getName(),
                category == null ? Optional.empty() : Optional.of(category.getName()),
                authorDto(context.guild(), message.getAuthor()), message.getTimeCreated().toInstant(),
                Optional.ofNullable(message.getTimeEdited()).map(value -> value.toInstant()),
                raw.isEmpty() ? Optional.empty() : Optional.of(raw),
                Optional.ofNullable(message.getMessageReference()).map(reference -> reference.getMessageId()),
                message.getAttachments().stream().map(attachment -> new ModerationReadApiModel.AttachmentDto(
                        attachment.getId(), attachment.getFileName(), Optional.ofNullable(attachment.getContentType()),
                        attachment.getSize(), attachment.getUrl())).toList(),
                message.getAuthor().getIdLong() == context.readTarget().userId(),
                false
        );
    }

    private static ModerationReadApiModel.AuthorDto authorDto(Guild guild, User user) {
        Member member = retrieveMemberIfPresent(guild, user.getIdLong());
        return new ModerationReadApiModel.AuthorDto(
                user.getId(), user.getName(), Optional.ofNullable(user.getGlobalName()),
                Optional.ofNullable(member == null ? null : member.getEffectiveName()),
                member == null ? displayName(user) : member.getEffectiveName(), user.getEffectiveAvatarUrl());
    }

    private Guild requireGuild() {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalStateException("staging guild unavailable");
        }
        return guild;
    }

    private static int boundedLimit(int requested) {
        return requested <= 0 ? DEFAULT_PAGE : Math.min(requested, MAX_PAGE);
    }

    private static long snowflake(String value, String label) {
        if (value == null || !value.matches("[1-9][0-9]{0,19}")) {
            throw new IllegalArgumentException(label + " ID is invalid");
        }
        return Long.parseUnsignedLong(value);
    }

    private static ModerationReadApiModel.MessageQuery emptyQuery() {
        return new ModerationReadApiModel.MessageQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), DEFAULT_PAGE);
    }

    private record RequestContext(
            long actorId,
            Member actorMember,
            Guild guild,
            Actor actor,
            StaffModerationReadService.Target target,
            ModerationReadTarget readTarget
    ) {
    }
}
