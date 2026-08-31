package net.enthusia.staff.discordbot;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/** Staging-only Discord launcher for the web-first moderation preview. */
final class JdaModerationUiPreviewListener extends ListenerAdapter implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(JdaModerationUiPreviewListener.class.getName());
    private static final String COMMAND = "moderate-preview";
    private static final long NO_GUILD = 0L;

    private final long guildId;
    private final InteractionReplayGuard interactions;
    private final ModerationPreviewLauncherPresentation presentation = new ModerationPreviewLauncherPresentation();
    private final Optional<ModerationPreviewWebRuntime> localWebRuntime;
    private final Optional<ModerationPreviewHostedLaunchIssuer> hostedLaunchIssuer;
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final JdaDiscordGateway.CallbackFence registrationCallbacks = new JdaDiscordGateway.CallbackFence();

    JdaModerationUiPreviewListener(
            long guildId,
            InteractionReplayGuard interactions,
            int sessionCapacity,
            ModerationPreviewWebConfig webConfig,
            String discordBotToken
    ) {
        this.guildId = guildId;
        this.interactions = interactions;
        if (webConfig.hostedExternally()) {
            this.localWebRuntime = Optional.empty();
            this.hostedLaunchIssuer = Optional.of(new ModerationPreviewHostedLaunchIssuer(
                    webConfig.publicBaseUri().orElseThrow(),
                    discordBotToken));
        } else {
            this.localWebRuntime = Optional.of(ModerationPreviewWebRuntime.fromConfig(webConfig, sessionCapacity));
            this.hostedLaunchIssuer = Optional.empty();
        }
    }

    void startWeb() {
        localWebRuntime.ifPresent(ModerationPreviewWebRuntime::start);
    }

    void enable(JDA jda) {
        long generation = registrationCallbacks.beginResolution();
        enabled.set(false);
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return;
        }
        guild.updateCommands().addCommands(command()).queue(
                registered -> registrationCallbacks.runIfCurrent(
                        generation,
                        () -> commandsRegistered(registered)),
                failure -> registrationCallbacks.runIfCurrent(
                        generation,
                        () -> commandRegistrationFailed(failure)));
    }

    void disable() {
        registrationCallbacks.invalidate();
        enabled.set(false);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) {
            return;
        }
        if (!accepted(guildId(event.getGuild())) || !claim(event)) {
            unavailable(event);
            return;
        }
        Optional<URI> launchUri = issueLaunchUri(event.getUser().getIdLong());
        ModerationPreviewLauncherPresentation.Rendered rendered = presentation.render(launchUri);
        event.replyEmbeds(rendered.embed()).addComponents(rendered.rows()).setEphemeral(true).queue();
    }

    private Optional<URI> issueLaunchUri(long actorId) {
        if (hostedLaunchIssuer.isPresent()) {
            return Optional.of(hostedLaunchIssuer.orElseThrow().issueLaunchUri(actorId, guildId));
        }
        return localWebRuntime.flatMap(runtime -> runtime.issueLaunchUri(actorId, guildId));
    }

    static CommandData command() {
        return Commands.slash(COMMAND, "Open the staging moderation web panel")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED);
    }

    private void commandsRegistered(List<Command> registered) {
        boolean complete = registered.size() == 1 && COMMAND.equals(registered.getFirst().getName());
        enabled.set(complete);
        if (!complete) {
            log("discord_ui_preview_command_registration_incomplete", null);
        }
    }

    private void commandRegistrationFailed(Throwable failure) {
        enabled.set(false);
        log("discord_ui_preview_command_registration_failed", failure);
    }

    private boolean claim(IReplyCallback event) {
        return interactions.claim(event.getIdLong()) == InteractionReplayGuard.ClaimResult.CLAIMED;
    }

    private boolean accepted(long eventGuildId) {
        return enabled.get() && eventGuildId == guildId;
    }

    private static long guildId(Guild guild) {
        return guild == null ? NO_GUILD : guild.getIdLong();
    }

    private static void unavailable(IReplyCallback event) {
        if (!event.isAcknowledged()) {
            event.reply("The staging moderation preview is unavailable in this context.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private static void log(String code, Throwable failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            String type = failure == null ? "unknown" : failure.getClass().getSimpleName();
            LOGGER.log(System.Logger.Level.WARNING, "{0} type={1}", code, type);
        }
    }

    @Override
    public void close() {
        disable();
        localWebRuntime.ifPresent(ModerationPreviewWebRuntime::close);
    }
}
