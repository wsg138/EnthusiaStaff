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
        this.snapshots = new ModerationReadSnapshotMapper(jda);
        this.messages = new ModerationDiscordMessageReader();
    }

    ModerationReadApiModel.BootstrapResponse bootstrap(ModerationReadApiModel.ReadRequest request) {
        ModerationReadContext context = authorizer.authorize(request);
        StaffModerationReadService.Snapshot snapshot = moderation.reads().snapshot(context.target());
        List<ModerationReadApiModel.ChannelDto> channels = messages.visibleChannels(context);
        return new ModerationReadApiModel.BootstrapResponse(
                snapshots.actor(context),
                snapshots.identity(context, snapshot),
                snapshots.linked(snapshot),
                snapshots.sanctions(snapshot),
                snapshots.history(snapshot),
                snapshot.totalHistoryCount(),
                snapshots.relevantHistoryCounts(snapshot),
                snapshots.cases(snapshot),
                snapshots.notes(snapshot),
                channels,
                messages.initial(context, channels),
                centeredMessage(context));
    }

    ModerationReadApiModel.MessagePageDto messages(ModerationReadApiModel.ReadRequest request) {
        ModerationReadContext context = authorizer.authorize(request);
        ModerationReadApiModel.MessageQuery query = request.messages()
                .orElseGet(ModerationDiscordMessageReader::emptyQuery);
        return messages.query(context, query);
    }

    private Optional<String> centeredMessage(ModerationReadContext context) {
        return context.readTarget().messageId().isPresent()
                ? Optional.of(Long.toUnsignedString(context.readTarget().messageId().orElseThrow()))
                : Optional.empty();
    }
}
