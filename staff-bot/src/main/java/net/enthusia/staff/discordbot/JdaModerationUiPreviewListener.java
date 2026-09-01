package net.enthusia.staff.discordbot;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/** Staging-only Discord launcher and private read-API owner for the Cloudflare moderation preview. */
final class JdaModerationUiPreviewListener extends ListenerAdapter implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(JdaModerationUiPreviewListener.class.getName());
    private static final String SLASH_COMMAND = "moderate-preview";
    private static final String USER_COMMAND = "Moderate Preview";
    private static final String MESSAGE_COMMAND = "Moderate Message Preview";
    private static final String TARGET_OPTION = "player";
    private static final long NO_GUILD = 0L;
    private static final int MIN_SESSION_CAPACITY = 1;
    private static final int EXPECTED_COMMAND_COUNT = 3;

    private final long guildId;
    private final InteractionReplayGuard interactions;
    private final ModerationPreviewLauncherPresentation presentation = new ModerationPreviewLauncherPresentation();
    private final Optional<ModerationPreviewHostedLaunchIssuer> hostedLaunchIssuer;
    private final Optional<StaffModerationRuntime> moderation;
    private final String discordBotToken;
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final JdaDiscordGateway.CallbackFence registrationCallbacks = new JdaDiscordGateway.CallbackFence();
    private Optional<ModerationReadApiServer> readApiServer = Optional.empty();

    JdaModerationUiPreviewListener(
            long guildId,
            InteractionReplayGuard interactions,
            int sessionCapacity,
            ModerationPreviewWebConfig webConfig,
            String discordBotToken,
            Optional<StaffModerationRuntime> moderation
    ) {
        if (sessionCapacity < MIN_SESSION_CAPACITY) {
            throw new IllegalArgumentException("UI preview requires positive bounded capacity");
        }
        this.guildId = guildId;
        this.interactions = interactions;
        this.discordBotToken = discordBotToken;
        this.moderation = moderation == null ? Optional.empty() : moderation;
        this.hostedLaunchIssuer = webConfig.hostedExternally()
                ? Optional.of(new ModerationPreviewHostedLaunchIssuer(
                        webConfig.publicBaseUri().orElseThrow(), discordBotToken))
                : Optional.empty();
    }

    void startWeb() {
        if (hostedLaunchIssuer.isEmpty()) {
            log("discord_ui_preview_hosted_origin_unavailable", null);
        }
        if (moderation.isEmpty()) {
            log("discord_ui_preview_real_data_unavailable", null);
        }
    }

    void enable(JDA jda) {
        long generation = registrationCallbacks.beginResolution();
        enabled.set(false);
        Guild guild = jda.getGuildById(guildId);
        if (guild == null || !messageContentEntitled(jda) || !startReadApi(jda)) {
            return;
        }
        guild.updateCommands().addCommands(commands()).queue(
                registered -> registrationCallbacks.runIfCurrent(
                        generation, () -> commandsRegistered(registered)),
                failure -> registrationCallbacks.runIfCurrent(
                        generation, () -> commandRegistrationFailed(failure)));
    }

    void disable() {
        registrationCallbacks.invalidate();
        enabled.set(false);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!SLASH_COMMAND.equals(event.getName())) {
            return;
        }
        if (!accepted(event) || !claim(event)) {
            unavailable(event);
            return;
        }
        var option = event.getOption(TARGET_OPTION);
        if (option == null) {
            unavailable(event);
            return;
        }
        reply(event, userLaunch(event.getUser().getIdLong(), option.getAsUser().getIdLong()));
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        if (!USER_COMMAND.equals(event.getName())) {
            return;
        }
        if (!accepted(event) || !claim(event)) {
            unavailable(event);
            return;
        }
        reply(event, userLaunch(event.getUser().getIdLong(), event.getTarget().getIdLong()));
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        if (!MESSAGE_COMMAND.equals(event.getName())) {
            return;
        }
        if (!accepted(event) || !claim(event)) {
            unavailable(event);
            return;
        }
        Message message = event.getTarget();
        reply(event, messageLaunch(event.getUser().getIdLong(), message));
    }

    private boolean startReadApi(JDA jda) {
    if (readApiServer.isPresent()) {
        return true;
    }
    if (moderation.isEmpty()) {
        return false;
    }
    try {
        readApiServer = Optional.of(createStartedReadApi(jda));
        return true;
    } catch (java.io.IOException | RuntimeException exception) {
        log("moderation_read_api_start_failed", exception);
        closeReadApi();
        return false;
    }
}

private ModerationReadApiServer createStartedReadApi(JDA jda) throws java.io.IOException {
    ModerationReadApiService service = new ModerationReadApiService(guildId, moderation.orElseThrow(), jda);
    ModerationReadApiServer candidate = new ModerationReadApiServer(discordBotToken, service);
    try {
        candidate.start();
        return candidate;
    } catch (RuntimeException exception) {
        candidate.close();
        throw exception;
    }
}

    private static boolean messageContentEntitled(JDA jda) {
        try {
            Set<ApplicationInfo.Flag> flags = jda.retrieveApplicationInfo().complete().getFlags();
            boolean entitled = hasMessageContentEntitlement(flags);
            if (!entitled) {
                log("discord_ui_preview_message_content_intent_unavailable", null);
            }
            return entitled;
        } catch (RuntimeException exception) {
            log("discord_ui_preview_message_content_intent_check_failed", exception);
            return false;
        }
    }

    static boolean hasMessageContentEntitlement(Set<ApplicationInfo.Flag> flags) {
        return flags != null && (flags.contains(ApplicationInfo.Flag.GATEWAY_MESSAGE_CONTENT)
                || flags.contains(ApplicationInfo.Flag.GATEWAY_MESSAGE_CONTENT_LIMITED));
    }

    private Optional<URI> userLaunch(long actorId, long targetUserId) {
        return hostedLaunchIssuer.map(issuer -> issuer.issueUserLaunchUri(actorId, guildId, targetUserId));
    }

    private Optional<URI> messageLaunch(long actorId, Message message) {
        return hostedLaunchIssuer.map(issuer -> issuer.issueMessageLaunchUri(
                actorId, guildId, message.getChannelIdLong(), message.getIdLong(), message.getAuthor().getIdLong()));
    }

    private void reply(IReplyCallback event, Optional<URI> launchUri) {
        ModerationPreviewLauncherPresentation.Rendered rendered = presentation.render(launchUri);
        event.replyEmbeds(rendered.embed()).addComponents(rendered.rows()).setEphemeral(true).queue();
    }

    static List<CommandData> commands() {
        return List.of(
                Commands.slash(SLASH_COMMAND, "Open the staging moderation web panel for a Discord user")
                        .addOption(OptionType.USER, TARGET_OPTION, "Discord user to inspect", true)
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.user(USER_COMMAND).setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.message(MESSAGE_COMMAND).setDefaultPermissions(DefaultMemberPermissions.DISABLED)
        );
    }

    private void commandsRegistered(List<Command> registered) {
        boolean complete = registered.size() == EXPECTED_COMMAND_COUNT
                && registered.stream().map(Command::getName).collect(java.util.stream.Collectors.toSet())
                .equals(java.util.Set.of(SLASH_COMMAND, USER_COMMAND, MESSAGE_COMMAND));
        enabled.set(complete);
        if (!complete) {
            log("discord_ui_preview_command_registration_incomplete", null);
        }
    }

    private void commandRegistrationFailed(Throwable failure) {
        enabled.set(false);
        log("discord_ui_preview_command_registration_failed", failure);
    }

    private boolean accepted(IReplyCallback event) {
        return enabled.get() && guildId(event.getGuild()) == guildId;
    }

    private boolean claim(IReplyCallback event) {
        return interactions.claim(event.getIdLong()) == InteractionReplayGuard.ClaimResult.CLAIMED;
    }

    private static long guildId(Guild guild) {
        return guild == null ? NO_GUILD : guild.getIdLong();
    }

    private static void unavailable(IReplyCallback event) {
        if (!event.isAcknowledged()) {
            event.reply("The staging moderation preview is unavailable in this context.")
                    .setEphemeral(true).queue();
        }
    }

    private void closeReadApi() {
    readApiServer.ifPresent(ModerationReadApiServer::close);
    readApiServer = Optional.empty();
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
        closeReadApi();
    }
}
