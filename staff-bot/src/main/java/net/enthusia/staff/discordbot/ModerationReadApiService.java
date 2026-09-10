package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;

/** Authorized orchestration for the private staging moderation read API. */
final class ModerationReadApiService {
    private final StaffModerationRuntime moderation;
    private final ModerationReadRequestAuthorizer authorizer;
    private final ModerationReadSnapshotMapper snapshots;
    private final ModerationDiscordMessageReader messages;

    ModerationReadApiService(long guildId, StaffModerationRuntime moderation, JDA jda) {
        if (guildId <= 0 || moderation == null || jda == null) {
            throw new IllegalArgumentException("read API dependencies must be present");
        }
        this.moderation = moderation;
        this.authorizer = new ModerationReadRequestAuthorizer(guildId, moderation, jda);
        this.snapshots = new ModerationReadSnapshotMapper(jda, moderation.minecraftProfiles());
        this.messages = new ModerationDiscordMessageReader();
    }

    ModerationReadApiModel.BootstrapResponse bootstrap(ModerationReadApiModel.ReadRequest request) {
        ModerationReadContext context = authorizer.authorize(request);
        List<ModerationReadApiModel.ChannelDto> channels = messages.visibleChannels(context);
        return context.target().isPresent()
                ? targetedBootstrap(context, channels)
                : channelBootstrap(context, channels);
    }

    ModerationReadApiModel.MessagePageDto messages(ModerationReadApiModel.ReadRequest request) {
        ModerationReadContext context = authorizer.authorize(request);
        ModerationReadApiModel.MessageQuery query = request.messages()
                .orElseGet(ModerationDiscordMessageReader::emptyQuery);
        return messages.query(context, query);
    }

    private ModerationReadApiModel.BootstrapResponse targetedBootstrap(
            ModerationReadContext context,
            List<ModerationReadApiModel.ChannelDto> channels
    ) {
        StaffModerationReadService.Snapshot snapshot = moderation.reads().snapshot(context.target().orElseThrow());
        List<ModerationReadApiModel.LinkedAccountDto> linkedAccounts = snapshots.linked(snapshot);
        return new ModerationReadApiModel.BootstrapResponse(
                snapshots.actor(context), context.readTarget().key(), true,
                Optional.of(snapshots.identity(context, snapshot, linkedAccounts)), linkedAccounts,
                snapshots.sanctions(snapshot), snapshots.history(snapshot), snapshot.totalHistoryCount(),
                snapshots.relevantHistoryCounts(snapshot), snapshots.cases(snapshot), snapshots.notes(snapshot),
                channels, messages.initial(context, channels), centeredMessage(context));
    }

    private ModerationReadApiModel.BootstrapResponse channelBootstrap(
            ModerationReadContext context,
            List<ModerationReadApiModel.ChannelDto> channels
    ) {
        return new ModerationReadApiModel.BootstrapResponse(
                snapshots.actor(context), context.readTarget().key(), false, Optional.empty(),
                List.of(), List.of(), List.of(), 0L, List.of(), List.of(), List.of(), channels,
                messages.initial(context, channels), Optional.empty());
    }

    private Optional<String> centeredMessage(ModerationReadContext context) {
        return context.readTarget().messageId().isPresent()
                ? Optional.of(Long.toUnsignedString(context.readTarget().messageId().orElseThrow()))
                : Optional.empty();
    }
}
