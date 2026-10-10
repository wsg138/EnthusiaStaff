package net.enthusia.staff.discordbot;

import java.util.ArrayList;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.modals.Modal;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.sanction.SanctionType;

/** JDA adapter for D06 reads and D07 confirmed Discord-only moderation actions. */
final class JdaStaffModerationListener extends ListenerAdapter {
    private static final System.Logger LOGGER = System.getLogger(JdaStaffModerationListener.class.getName());
    private static final long NO_GUILD = 0L;
    private static final int REQUIRED_SELECTION = 1;
    private static final String USER_OPTION = "user";
    private static final String USER_ID_OPTION = "user-id";
    private static final String PLAYER_OPTION = "player";
    private static final String CASE_ID_OPTION = "id";
    private static final String REASON_OPTION = "reason";
    private static final String DURATION_OPTION = "duration";
    private static final String EXPLANATION_OPTION = "explanation";
    private static final String DELETE_SECONDS_OPTION = "delete-seconds";
    private static final String SCOPE_KIND_OPTION = "scope-kind";
    private static final String SCOPE_ID_OPTION = "scope-id";
    private static final String MODE_OPTION = "mode";
    private static final String SERVER_OPTION = "server";
    private static final String COMMAND_OPTION = "command";
    private static final String TYPE_OPTION = "type";
    private static final String CONSOLE = "console";
    private static final String REVIEW_REQUEST = "review-request";
    private static final String REVIEW_QUEUE = "review-queue";
    private static final String REVIEW_MODAL_NOTE = "review-note";
    private static final String REVIEW_ID = "request-id";
    private static final String REVIEW_DECISION = "decision";
    private static final String REVIEW_NOTE = "note";
    private static final String NOTIFICATION_TEST = "notification-test";
    private static final String MODERATE = "moderate";
    private static final String PUNISH = "punish";
    private static final String MODERATE_MINECRAFT = "moderate-minecraft";
    private static final String LINKED = "linked";
    private static final String HISTORY = "history";
    private static final String NOTES = "notes";
    private static final String CASE = "case";
    private static final String WARN = "warn";
    private static final String MUTE = "mute";
    private static final String UNMUTE = "unmute";
    private static final String KICK = "kick";
    private static final String BAN = "ban";
    private static final String UNBAN = "unban";
    private static final String RESTRICT = "restrict";
    private static final String UNRESTRICT = "unrestrict";
    private static final String MODERATE_USER = "Moderate User";
    private static final String MODERATE_MESSAGE = "Moderate Message";
    private static final String MODAL_REASON = "reason";
    private static final Set<String> PUNISHMENT_COMMANDS = Set.of(
            WARN, MUTE, UNMUTE, KICK, BAN, UNBAN, RESTRICT, UNRESTRICT
    );

    @FunctionalInterface
    private interface DiscordTargetRead {
        StaffModerationController.Response apply(long actorId, String actorName, long targetId);
    }

    private final long guildId;
    private final StaffBotWorkerPool workers;
    private final InteractionReplayGuard interactions;
    private final StaffModerationController controller;
    private final LinkedStaffActorResolver actors;
    private final Optional<ModerationPreviewHostedLaunchIssuer> webIssuer;
    private final Optional<StaffModerationRuntime> webModeration;
    private volatile ModerationReadRequestAuthorizer webAuthorizer;
    private final Optional<DiscordPunishmentCommandController> punishments;
    private final Optional<DiscordCommandBridgeCoordinator> commandBridge;
    private final HttpStaffAuthorityClient reviewAuthority;
    private final StaffModerationRuntime moderationRuntime;
    private final SignedComponentCodec reviewComponents;
    private final java.util.concurrent.atomic.AtomicBoolean enabled = new java.util.concurrent.atomic.AtomicBoolean();

    JdaStaffModerationListener(
            long guildId,
            StaffBotWorkerPool workers,
            InteractionReplayGuard interactions,
            StaffModerationRuntime moderation
    ) {
        this(guildId, workers, interactions, moderation, Optional.empty());
    }

    JdaStaffModerationListener(
            long guildId, StaffBotWorkerPool workers, InteractionReplayGuard interactions,
            StaffModerationRuntime moderation, Optional<URI> moderationWebUri, String discordToken
    ) {
        this(guildId, workers, interactions, moderation,
                moderationWebUri.map(uri -> new ModerationPreviewHostedLaunchIssuer(uri, discordToken, "production")));
    }

    private JdaStaffModerationListener(
            long guildId, StaffBotWorkerPool workers, InteractionReplayGuard interactions,
            StaffModerationRuntime moderation, Optional<ModerationPreviewHostedLaunchIssuer> webIssuer
    ) {
        this.guildId = guildId;
        this.workers = workers;
        this.interactions = interactions;
        this.punishments = moderation.punishmentService().map(DiscordPunishmentCommandController::new);
        this.actors = moderation.actors();
        this.commandBridge = moderation.commandBridge();
        this.reviewAuthority = moderation.authority();
        this.moderationRuntime = moderation;
        this.reviewComponents = moderation.components();
        this.webIssuer = webIssuer;
        this.webModeration = webIssuer.isPresent() ? Optional.of(moderation) : Optional.empty();
        this.controller = new StaffModerationController(
                moderation.reads(),
                actors,
                moderation.authorization(),
                moderation.components()
        );
    }

    void enable(JDA jda) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            enabled.set(false);
            return;
        }
        if (webIssuer.isPresent()) {
            webAuthorizer = new ModerationReadRequestAuthorizer(guildId, webModeration.orElseThrow(), jda);
        }
        List<CommandData> expected = commands(
                punishments.isPresent(), webIssuer.isPresent(), commandBridge.isPresent());
        guild.updateCommands().addCommands(expected).queue(
                registered -> commandsRegistered(registered, expected.size()),
                this::commandRegistrationFailed
        );
    }

    private void commandsRegistered(List<Command> registered, int expectedCount) {
        boolean complete = registered.size() == expectedCount;
        enabled.set(complete);
        if (!complete) {
            log("discord_staff_commands_registration_incomplete", null);
        }
    }

    private void commandRegistrationFailed(Throwable failure) {
        enabled.set(false);
        log("discord_staff_commands_registration_failed", failure);
    }

    void disable() {
        enabled.set(false);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        long actorId = event.getUser().getIdLong();
        String actorName = event.getUser().getName();
        if (REVIEW_QUEUE.equals(event.getName())) {
            dispatchReviewQueue(event, actorId, actorName);
            return;
        }
        if (REVIEW_REQUEST.equals(event.getName())) {
            dispatchMinecraftReview(event, actorId, actorName);
            return;
        }
        if (CONSOLE.equals(event.getName())) {
            dispatchConsole(event, actorId);
            return;
        }
        if (NOTIFICATION_TEST.equals(event.getName())) {
            dispatchNotificationTest(event, actorId, actorName);
            return;
        }
        if (PUNISHMENT_COMMANDS.contains(event.getName())) {
            dispatchQuickPunishment(event, actorId, actorName);
            return;
        }
        dispatchReadCommand(event, actorId, actorName);
    }

    /** Private interactive queue. Every button is HMAC-bound to the invoker and short-lived. */
    private void dispatchReviewQueue(SlashCommandInteractionEvent event, long actorId, String actorName) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(hook -> {
            boolean scheduled = workers.tryExecute(() -> {
                try {
                    var actor = actors.invoker(
                            new DiscordUserId(Long.toUnsignedString(actorId)), actorName);
                    if (!actor.rank().canApprovePunishmentRequests()) {
                        hook.sendMessage("Current Minecraft moderator rank is required.").queue();
                        return;
                    }
                    var pulse = moderationRuntime.reviewPulse();
                    var reviews = moderationRuntime.pendingReviews(4);
                    StringBuilder message = new StringBuilder("**Staff review center**\\n")
                            .append("Pending punishments: ").append(pulse.pendingPunishmentRequests())
                            .append(" | Open reports: ").append(pulse.openReports())
                            .append(" | Claimed reports: ").append(pulse.claimedReports())
                            .append(" | Alt alert events (24h): ").append(pulse.altSignalsInLastDay())
                            .append("\\n");
                    if (reviews.isEmpty()) {
                        hook.sendMessage(message.append("No pending Minecraft punishment requests.").toString())
                                .queue();
                        return;
                    }
                    java.util.List<ActionRow> rows = new ArrayList<>();
                    int number = 1;
                    for (var review : reviews) {
                        message.append(number).append(". **").append(review.reasonId())
                                .append("** · Target: `").append(review.targetId())
                                .append("` · Required: ").append(review.requiredRank())
                                .append(" · Request: `").append(review.requestId()).append("`\\n");
                        var target = SignedComponentCodec.TargetRef.request(review.requestId());
                        rows.add(ActionRow.of(
                                Button.success(reviewComponents.encode(
                                        SignedComponentCodec.Action.REVIEW_APPROVE, target, actorId),
                                        "Approve " + number),
                                Button.danger(reviewComponents.encode(
                                        SignedComponentCodec.Action.REVIEW_DENY, target, actorId),
                                        "Deny " + number)
                        ));
                        number++;
                    }
                    message.append("Review buttons expire shortly. All actions recheck live Minecraft authority.");
                    hook.sendMessage(message.toString()).addComponents(rows).queue();
                } catch (RuntimeException exception) {
                    log("discord_review_queue_failed", exception);
                    hook.sendMessage("The private review queue could not be loaded.").queue();
                }
            });
            if (!scheduled) {
                hook.sendMessage("The staff review worker is busy.").queue();
            }
        }, failure -> interactions.release(interactionId));
    }

    private static boolean isReviewComponent(String id) {
        return id != null && (id.startsWith("d6:u:r:") || id.startsWith("d6:v:r:"));
    }

    private boolean handleReviewButton(ButtonInteractionEvent event) {
        if (!isReviewComponent(event.getComponentId())) {
            return false;
        }
        long actorId = event.getUser().getIdLong();
        try {
            var decoded = reviewComponents.decodeAndClaim(event.getComponentId(), actorId);
            if (decoded.action() == SignedComponentCodec.Action.REVIEW_APPROVE) {
                submitReview(event, actorId, event.getUser().getName(),
                        decoded.target().requestId(), "approve", "");
            } else if (decoded.action() == SignedComponentCodec.Action.REVIEW_DENY) {
                String modalId = reviewComponents.encode(
                        SignedComponentCodec.Action.REVIEW_DENY_SUBMIT,
                        SignedComponentCodec.TargetRef.request(decoded.target().requestId()), actorId);
                TextInput note = TextInput.create(REVIEW_MODAL_NOTE, TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Why should this request be denied? (private staff audit)")
                        .setRequiredRange(1, 500).build();
                event.replyModal(Modal.create(modalId, "Deny Minecraft punishment request")
                        .addComponents(Label.of("Denial reason", note)).build()).queue();
            } else {
                event.reply("Invalid review action.").setEphemeral(true).queue();
            }
        } catch (RuntimeException exception) {
            event.reply("That review button is expired or belongs to another reviewer.").setEphemeral(true).queue();
        }
        return true;
    }

    private boolean handleReviewModal(ModalInteractionEvent event) {
        if (!accepted(guildId(event.getGuild()))) {
            return false;
        }
        String id = event.getModalId();
        if (id == null || !id.startsWith("d6:w:r:")) {
            return false;
        }
        try {
            var decoded = reviewComponents.decodeAndClaim(id, event.getUser().getIdLong());
            if (decoded.action() != SignedComponentCodec.Action.REVIEW_DENY_SUBMIT) {
                throw new IllegalArgumentException("invalid review denial modal");
            }
            String note = event.getValue(REVIEW_MODAL_NOTE) == null
                    ? "" : event.getValue(REVIEW_MODAL_NOTE).getAsString();
            if (note.isBlank() || note.length() > 500) {
                event.reply("A private denial reason is required.").setEphemeral(true).queue();
                return true;
            }
            submitReview(event, event.getUser().getIdLong(), event.getUser().getName(),
                    decoded.target().requestId(), "deny", note);
        } catch (RuntimeException exception) {
            event.reply("This review form expired or was already used. Refresh /review-queue.").setEphemeral(true)
                    .queue();
        }
        return true;
    }

    private void submitReview(IReplyCallback event, long actorId, String actorName,
            java.util.UUID requestId, String decision, String note) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(hook -> {
            boolean scheduled = workers.tryExecute(() -> {
                try {
                    String result = committedReview(actorId, actorName, requestId, decision, note);
                    hook.sendMessage(result).queue();
                } catch (RuntimeException exception) {
                    log("discord_review_button_commit_failed", exception);
                    hook.sendMessage("Review was not confirmed. Refresh the queue before trying again.").queue();
                }
            });
            if (!scheduled) {
                hook.sendMessage("Review workers are busy.").queue();
            }
        }, failure -> interactions.release(interactionId));
    }

    private String committedReview(long actorId, String actorName, java.util.UUID requestId,
            String decision, String note) {
        var actor = actors.invoker(new DiscordUserId(Long.toUnsignedString(actorId)), actorName);
        var response = reviewAuthority.review(decision, java.util.Map.of(
                "actorId", actor.id().toString(), "requestId", requestId.toString(), "note", note));
        return switch (response.path("state").asText()) {
            case "APPROVED" -> "Approved and saved; network enforcement queued. Case: "
                    + response.path("caseId").asText();
            case "DENIED" -> "Denied. No punishment applied.";
            default -> "The request outcome is unconfirmed. Reopen the review queue.";
        };
    }

    /** Discord decisions are never authoritative until Paper returns a committed case/request state. */
    private void dispatchMinecraftReview(SlashCommandInteractionEvent event, long actorId, String actorName) {
        if (event.getOption(REVIEW_ID) == null || event.getOption(REVIEW_DECISION) == null) {
            event.reply("A request ID and decision are required.").setEphemeral(true).queue();
            return;
        }
        String id = event.getOption(REVIEW_ID).getAsString();
        String decision = event.getOption(REVIEW_DECISION).getAsString().toLowerCase(java.util.Locale.ROOT);
        String note = event.getOption(REVIEW_NOTE) == null
                ? "" : event.getOption(REVIEW_NOTE).getAsString();
        if (!id.matches("[0-9a-fA-F-]{36}")
                || (!"approve".equals(decision) && !"deny".equals(decision))) {
            event.reply("Use an exact punishment request UUID and approve or deny.").setEphemeral(true).queue();
            return;
        }
        if ("deny".equals(decision) && note.isBlank()) {
            event.reply("A denial reason is required.").setEphemeral(true).queue();
            return;
        }
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(hook -> {
            boolean scheduled = workers.tryExecute(() -> {
                try {
                    var actor = actors.invoker(
                            new DiscordUserId(Long.toUnsignedString(actorId)), actorName);
                    var response = reviewAuthority.review(decision, java.util.Map.of(
                            "actorId", actor.id().toString(),
                            "requestId", java.util.UUID.fromString(id).toString(),
                            "note", note
                    ));
                    String state = response.path("state").asText();
                    if ("APPROVED".equals(state)) {
                        hook.sendMessage("Approved and saved. Network enforcement is queued. Case: "
                                + response.path("caseId").asText()).queue();
                    } else if ("DENIED".equals(state)) {
                        hook.sendMessage("Punishment request denied. No punishment was applied.").queue();
                    } else {
                        hook.sendMessage("Decision was not confirmed. Check its current state in-game.").queue();
                    }
                } catch (RuntimeException exception) {
                    log("discord_minecraft_review_failed", exception);
                    hook.sendMessage("Review was not confirmed. Check the current request in-game before retrying.")
                            .queue();
                }
            });
            if (!scheduled) {
                hook.sendMessage("The staff review worker is busy. Try again shortly.").queue();
            }
        }, failure -> interactions.release(interactionId));
    }

    private void dispatchConsole(SlashCommandInteractionEvent event, long actorId) {
        if (commandBridge.isEmpty()) {
            unavailable(event);
            return;
        }
        String server = event.getOption(SERVER_OPTION).getAsString();
        String command = event.getOption(COMMAND_OPTION).getAsString();
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(
                hook -> scheduleConsole(hook, actorId, server, command),
                failure -> interactions.release(interactionId)
        );
    }

    private void scheduleConsole(InteractionHook hook, long actorId, String server, String command) {
        boolean scheduled = workers.tryExecute(() -> {
            try {
                var result = commandBridge.orElseThrow().request(
                        new net.enthusia.staff.domain.moderation.DiscordUserId(
                                Long.toUnsignedString(actorId)),
                        server,
                        command
                );
                hook.sendMessage(DiscordCommandBridgeResponseFormatter.format(result)).queue();
            } catch (RuntimeException exception) {
                log("discord_console_request_failed", exception);
                hook.sendMessage("The console bridge could not safely process that request.").queue();
            }
        });
        if (!scheduled) {
            hook.sendMessage("The moderation action queue is busy. Try again shortly.").queue();
        }
    }

    private void dispatchNotificationTest(
            SlashCommandInteractionEvent event,
            long actorId,
            String actorName
    ) {
        if (punishments.isEmpty()) {
            unavailable(event);
            return;
        }
        String preview = option(event, TYPE_OPTION, "");
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(hook -> {
            boolean scheduled = workers.tryExecute(() -> executeNotificationTest(
                    hook, event.getJDA(), actorId, actorName, preview));
            if (!scheduled) {
                hook.sendMessage("The moderation action queue is busy. Try again shortly.").queue();
            }
        }, failure -> interactions.release(interactionId));
    }

    private void executeNotificationTest(
            InteractionHook hook,
            JDA jda,
            long actorId,
            String actorName,
            String preview
    ) {
        try {
            actors.invoker(new DiscordUserId(Long.toUnsignedString(actorId)), actorName);
            String userId = Long.toUnsignedString(actorId);
            DiscordDeliveryOutcome outcome = deliverNotificationPreview(jda, userId, preview, Instant.now());
            if (outcome == DiscordDeliveryOutcome.DELIVERED) {
                hook.sendMessage("Sent the real-format " + preview.replace('-', ' ')
                        + " notification preview to your DMs. No punishment or case was created.").queue();
            } else {
                hook.sendMessage("The preview was not delivered. No punishment or case was created.").queue();
            }
        } catch (RuntimeException exception) {
            log("discord_notification_preview_denied", exception);
            hook.sendMessage("Current linked staff authority could not be verified for the preview.").queue();
        }
    }

    private static DiscordDeliveryOutcome deliverNotificationPreview(
            JDA jda,
            String userId,
            String preview,
            Instant now
    ) {
        return switch (preview) {
            case "warning" -> JdaPunishmentNotifier.notifyPreview(
                    jda, userId, DiscordConsequenceType.WARNING, now);
            case "mute" -> JdaPunishmentNotifier.notifyPreview(
                    jda, userId, DiscordConsequenceType.MUTE, now);
            case "ban" -> JdaPunishmentNotifier.notifyPreview(
                    jda, userId, DiscordConsequenceType.BAN, now);
            case "minecraft-warning" -> JdaPunishmentNotifier.notifyMinecraftPreview(
                    jda, userId, SanctionType.WARNING, now);
            case "minecraft-mute" -> JdaPunishmentNotifier.notifyMinecraftPreview(
                    jda, userId, SanctionType.MUTE, now);
            case "minecraft-ban" -> JdaPunishmentNotifier.notifyMinecraftPreview(
                    jda, userId, SanctionType.BAN, now);
            default -> throw new IllegalArgumentException("unsupported notification preview type");
        };
    }

    private void dispatchReadCommand(SlashCommandInteractionEvent event, long actorId, String actorName) {
        switch (event.getName()) {
            case MODERATE, PUNISH -> dispatchModerate(event, actorId, actorName);
            case LINKED -> dispatchDiscordTarget(event, actorId, actorName, controller::linkedDiscord);
            case HISTORY -> dispatchDiscordTarget(event, actorId, actorName, controller::historyDiscord);
            case NOTES -> dispatchDiscordTarget(event, actorId, actorName, controller::notesDiscord);
            case MODERATE_MINECRAFT -> dispatchMinecraft(event, actorId, actorName);
            case CASE -> dispatchCase(event, actorId, actorName);
            default -> unavailable(event);
        }
    }

    private void dispatchModerate(SlashCommandInteractionEvent event, long actorId, String actorName) {
        if (webIssuer.isPresent()) {
            var selected = event.getOption(USER_OPTION);
            long channelId = event.getChannel().getIdLong();
            String targetKey = selected == null
                    ? "channel:" + Long.toUnsignedString(channelId)
                    : "discord-channel:" + Long.toUnsignedString(channelId) + ":"
                    + Long.toUnsignedString(selected.getAsUser().getIdLong());
            dispatchWeb(event, actorId, targetKey,
                    () -> selected == null
                            ? webIssuer.orElseThrow().issueChannelLaunchUri(actorId, guildId, channelId)
                            : webIssuer.orElseThrow().issueUserLaunchUri(
                                    actorId, guildId, channelId, selected.getAsUser().getIdLong()));
            return;
        }
        User target = event.getOption(USER_OPTION).getAsUser();
        dispatch(event, () -> withPunish(
                controller.moderateDiscord(actorId, actorName, target.getIdLong()),
                target.getIdLong()
        ));
    }

    private void dispatchDiscordTarget(
            SlashCommandInteractionEvent event,
            long actorId,
            String actorName,
            DiscordTargetRead read
    ) {
        User target = event.getOption(USER_OPTION).getAsUser();
        dispatch(event, () -> read.apply(actorId, actorName, target.getIdLong()));
    }

    private void dispatchMinecraft(SlashCommandInteractionEvent event, long actorId, String actorName) {
        String target = event.getOption(PLAYER_OPTION).getAsString();
        dispatch(event, () -> controller.moderateMinecraft(actorId, actorName, target));
    }

    private void dispatchCase(SlashCommandInteractionEvent event, long actorId, String actorName) {
        String caseId = event.getOption(CASE_ID_OPTION).getAsString();
        dispatch(event, () -> controller.caseView(actorId, actorName, caseId));
    }

    private void dispatchQuickPunishment(SlashCommandInteractionEvent event, long actorId, String actorName) {
        DiscordPunishmentCommandController punishment = punishments.orElse(null);
        if (punishment == null) {
            unavailable(event);
            return;
        }
        dispatchPunishment(event, quickWork(punishment, event, actorId, actorName));
    }

    private static Supplier<DiscordPunishmentCommandController.Prepared> quickWork(
            DiscordPunishmentCommandController punishment,
            SlashCommandInteractionEvent event,
            long actorId,
            String actorName
    ) {
        if (isRemovalCommand(event.getName())) {
            return quickRemovalWork(punishment, event, actorId, actorName);
        }
        User target = event.getOption(USER_OPTION).getAsUser();
        return quickIssueWork(punishment, event, actorId, actorName, target.getIdLong());
    }

    private static boolean isRemovalCommand(String command) {
        return UNMUTE.equals(command) || UNBAN.equals(command) || UNRESTRICT.equals(command);
    }

    private static Supplier<DiscordPunishmentCommandController.Prepared> quickRemovalWork(
            DiscordPunishmentCommandController punishment,
            SlashCommandInteractionEvent event,
            long actorId,
            String actorName
    ) {
        String targetId = option(event, USER_ID_OPTION, "");
        return switch (event.getName()) {
            case UNMUTE -> () -> punishment.remove(actorId, actorName, targetId, DiscordConsequenceType.MUTE);
            case UNBAN -> () -> punishment.remove(actorId, actorName, targetId, DiscordConsequenceType.BAN);
            case UNRESTRICT -> () -> punishment.unrestrict(
                    actorId, actorName, targetId, option(event, SCOPE_ID_OPTION, ""));
            default -> throw new IllegalArgumentException("not a Discord punishment removal command");
        };
    }

    private static Supplier<DiscordPunishmentCommandController.Prepared> quickIssueWork(
            DiscordPunishmentCommandController punishment,
            SlashCommandInteractionEvent event,
            long actorId,
            String actorName,
            long targetId
    ) {
        String reason = option(event, REASON_OPTION, "");
        String explanation = option(event, EXPLANATION_OPTION, "");
        return switch (event.getName()) {
            case WARN -> () -> punishment.warn(actorId, actorName, targetId, reason, explanation);
            case MUTE -> () -> punishment.mute(
                    actorId, actorName, targetId, option(event, DURATION_OPTION, ""), reason, explanation);
            case KICK -> () -> punishment.kick(actorId, actorName, targetId, reason, explanation);
            case BAN -> () -> punishment.ban(
                    actorId, actorName, targetId, option(event, DURATION_OPTION, ""),
                    reason, explanation, integerOption(event, DELETE_SECONDS_OPTION, 0));
            case RESTRICT -> () -> punishment.restrict(
                    actorId, actorName, targetId, option(event, DURATION_OPTION, ""), reason, explanation,
                    restrictionInput(event));
            default -> throw new IllegalArgumentException("not a Discord punishment issue command");
        };
    }

    private static DiscordPunishmentCommandController.RestrictionInput restrictionInput(
            SlashCommandInteractionEvent event
    ) {
        return new DiscordPunishmentCommandController.RestrictionInput(
                option(event, SCOPE_KIND_OPTION, ""),
                option(event, SCOPE_ID_OPTION, ""),
                option(event, MODE_OPTION, "")
        );
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        if (!MODERATE_USER.equals(event.getName()) || !accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        long actor = event.getUser().getIdLong();
        long target = event.getTarget().getIdLong();
        if (webIssuer.isPresent()) {
            dispatchWeb(event, actor, "discord-channel:" + event.getChannel().getId() + ":" + target,
                    () -> webIssuer.orElseThrow().issueUserLaunchUri(
                            actor, guildId, event.getChannel().getIdLong(), target));
            return;
        }
        dispatch(event, () -> withPunish(
                controller.moderateDiscord(actor, event.getUser().getName(), target), target));
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        if (!MODERATE_MESSAGE.equals(event.getName()) || !accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        long actor = event.getUser().getIdLong();
        long target = event.getTarget().getAuthor().getIdLong();
        if (webIssuer.isPresent()) {
            long channel = event.getTarget().getChannelIdLong();
            long message = event.getTarget().getIdLong();
            dispatchWeb(event, actor, "message:" + channel + ":" + message + ":" + target,
                    () -> webIssuer.orElseThrow().issueMessageLaunchUri(
                            actor, guildId, channel, message, target));
            return;
        }
        dispatch(event, () -> withPunish(
                controller.moderateDiscord(actor, event.getUser().getName(), target), target));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        if (handleReviewButton(event)) {
            return;
        }
        if (handlePunishmentButton(event)) {
            return;
        }
        long actor = event.getUser().getIdLong();
        dispatch(event, () -> controller.component(
                actor,
                event.getUser().getName(),
                event.getComponentId(),
                Optional.empty()
        ));
    }

    private boolean handlePunishmentButton(ButtonInteractionEvent event) {
        DiscordPunishmentCommandController punishment = punishments.orElse(null);
        if (punishment == null) {
            return false;
        }
        String customId = event.getComponentId();
        if (DiscordPunishmentCommandController.isConfirmation(customId)) {
            dispatchConfirmation(event, punishment);
            return true;
        }
        if (DiscordPunishmentCommandController.isPanelRoot(customId)) {
            showPunishmentPanel(event);
            return true;
        }
        if (DiscordPunishmentCommandController.isPanelAction(customId)) {
            showPunishmentModal(event);
            return true;
        }
        return false;
    }

    private void dispatchConfirmation(
            ButtonInteractionEvent event,
            DiscordPunishmentCommandController punishment
    ) {
        long actorId = event.getUser().getIdLong();
        String actorName = event.getUser().getName();
        dispatchCommitted(event, () -> punishment.confirm(
                actorId, actorName, event.getComponentId()
        ));
    }

    private void showPunishmentPanel(ButtonInteractionEvent event) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        long target = DiscordPunishmentCommandController.panelRootTarget(event.getComponentId());
        List<Button> buttons = DiscordPunishmentCommandController.panelButtons(target).stream()
                .map(value -> Button.secondary(value.customId(), value.label()))
                .toList();
        event.reply("Choose a Discord consequence. A reason and final confirmation are still required.")
                .setEphemeral(true)
                .addComponents(ActionRow.of(buttons))
                .queue(null, failure -> interactions.release(interactionId));
    }

    private void showPunishmentModal(ButtonInteractionEvent event) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        DiscordPunishmentCommandController.PanelDraft draft =
                DiscordPunishmentCommandController.panelDraft(event.getComponentId());
        TextInput reason = TextInput.create(MODAL_REASON, TextInputStyle.PARAGRAPH)
                .setPlaceholder("Public reason for this Discord moderation action")
                .setRequiredRange(1, 512)
                .build();
        Modal modal = Modal.create(
                        DiscordPunishmentCommandController.modalId(draft),
                        draft.action().label() + " Discord user"
                )
                .addComponents(Label.of("Reason", reason))
                .build();
        event.replyModal(modal).queue(null, failure -> interactions.release(interactionId));
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (handleReviewModal(event)) {
            return;
        }
        if (!accepted(guildId(event.getGuild()))
                || !DiscordPunishmentCommandController.isPanelModal(event.getModalId())
                || punishments.isEmpty()) {
            unavailable(event);
            return;
        }
        String reason = event.getValue(MODAL_REASON) == null
                ? ""
                : event.getValue(MODAL_REASON).getAsString();
        DiscordPunishmentCommandController.PanelDraft draft =
                DiscordPunishmentCommandController.modalDraft(event.getModalId());
        DiscordPunishmentCommandController punishment = punishments.orElseThrow();
        dispatchPunishment(event, () -> punishment.preparePanel(
                draft,
                event.getUser().getIdLong(),
                event.getUser().getName(),
                reason
        ));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        long actor = event.getUser().getIdLong();
        String selected = event.getValues().isEmpty() ? "" : event.getValues().getFirst();
        dispatch(event, () -> controller.component(
                actor,
                event.getUser().getName(),
                event.getComponentId(),
                Optional.of(selected)
        ));
    }

    private StaffModerationController.Response withPunish(
            StaffModerationController.Response response,
            long targetId
    ) {
        if (punishments.isEmpty()) {
            return response;
        }
        List<StaffModerationController.Button> buttons = new ArrayList<>();
        buttons.add(new StaffModerationController.Button(
                "Punish", DiscordPunishmentCommandController.panelRootId(targetId)
        ));
        response.buttons().stream()
                .filter(button -> !"Refresh".equals(button.label()))
                .limit(4)
                .forEach(buttons::add);
        return StaffModerationController.Response.text(response.content(), buttons);
    }

    private void dispatch(IReplyCallback event, Supplier<StaffModerationController.Response> work) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(
                hook -> scheduleRead(hook, work),
                failure -> interactions.release(interactionId)
        );
    }

    private void dispatchWeb(IReplyCallback event, long actorId, String targetKey, Supplier<URI> issue) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(hook -> {
            boolean scheduled = workers.tryExecute(() -> {
                try {
                    webAuthorizer.authorize(new ModerationReadApiModel.ReadRequest(
                            Long.toUnsignedString(actorId), Long.toUnsignedString(guildId),
                            targetKey, Optional.empty()));
                    URI link = issue.get();
                    hook.sendMessage("Open the private moderation workspace. This link expires in two minutes.")
                            .addComponents(ActionRow.of(Button.link(
                                    link.toString(), "Open Moderation Workspace"))).queue();
                } catch (RuntimeException exception) {
                    log("moderation_web_launch_denied", exception);
                    hook.sendMessage("Current staff authority could not be verified for this workspace.").queue();
                }
            });
            if (!scheduled) {
                hook.sendMessage("The moderation read queue is busy. Try again shortly.").queue();
            }
        }, failure -> interactions.release(interactionId));
    }

    private void dispatchPunishment(
            IReplyCallback event,
            Supplier<DiscordPunishmentCommandController.Prepared> work
    ) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(
                hook -> schedulePunishment(hook, work),
                failure -> interactions.release(interactionId)
        );
    }

    private void dispatchCommitted(
            IReplyCallback event,
            Supplier<DiscordPunishmentCommandController.Committed> work
    ) {
        long interactionId = event.getIdLong();
        if (!claim(interactionId, event)) {
            return;
        }
        event.deferReply(true).queue(
                hook -> scheduleCommitted(hook, work),
                failure -> interactions.release(interactionId)
        );
    }

    private boolean claim(long interactionId, IReplyCallback event) {
        InteractionReplayGuard.ClaimResult claim = interactions.claim(interactionId);
        if (claim == InteractionReplayGuard.ClaimResult.CLAIMED) {
            return true;
        }
        event.reply("That interaction was already handled or the moderation queue is saturated.")
                .setEphemeral(true)
                .queue();
        return false;
    }

    private void scheduleRead(InteractionHook hook, Supplier<StaffModerationController.Response> work) {
        boolean scheduled = workers.tryExecute(() -> executeRead(hook, work));
        if (!scheduled) {
            hook.sendMessage("The moderation read queue is busy. Try again shortly.").queue();
        }
    }

    private void schedulePunishment(
            InteractionHook hook,
            Supplier<DiscordPunishmentCommandController.Prepared> work
    ) {
        boolean scheduled = workers.tryExecute(() -> executePunishment(hook, work));
        if (!scheduled) {
            hook.sendMessage("The moderation action queue is busy. Try again shortly.").queue();
        }
    }

    private void scheduleCommitted(
            InteractionHook hook,
            Supplier<DiscordPunishmentCommandController.Committed> work
    ) {
        boolean scheduled = workers.tryExecute(() -> executeCommitted(hook, work));
        if (!scheduled) {
            hook.sendMessage("The moderation action queue is busy. Try again shortly.").queue();
        }
    }

    private void executePunishment(
            InteractionHook hook,
            Supplier<DiscordPunishmentCommandController.Prepared> work
    ) {
        try {
            DiscordPunishmentCommandController.Prepared prepared = work.get();
            hook.sendMessage(prepared.content())
                    .addComponents(ActionRow.of(Button.danger(prepared.confirmationCustomId(), "Confirm")))
                    .queue();
        } catch (RuntimeException exception) {
            actionFailure(hook, exception);
        }
    }

    private void executeCommitted(
            InteractionHook hook,
            Supplier<DiscordPunishmentCommandController.Committed> work
    ) {
        try {
            hook.sendMessage(work.get().content()).queue();
        } catch (RuntimeException exception) {
            actionFailure(hook, exception);
        }
    }

    private static void actionFailure(InteractionHook hook, RuntimeException exception) {
        log("discord_punishment_interaction_failed", exception);
        hook.sendMessage("The Discord moderation action was rejected or could not be prepared safely.").queue();
    }

    private void executeRead(InteractionHook hook, Supplier<StaffModerationController.Response> work) {
        try {
            send(hook, work.get());
        } catch (RuntimeException exception) {
            log("discord_read_interaction_failed", exception);
            hook.sendMessage("The moderation view is temporarily unavailable.").queue();
        }
    }

    private void send(InteractionHook hook, StaffModerationController.Response response) {
        var embed = StaffModerationDiscordPresentation.embed(response.content(), punishments.isPresent());
        var action = hook.sendMessageEmbeds(embed);
        if (!response.buttons().isEmpty()) {
            List<Button> buttons = response.buttons().stream()
                    .map(button -> StaffModerationDiscordPresentation.button(button, response.content()))
                    .toList();
            action = action.addComponents(ActionRow.of(buttons));
        }
        if (!response.choices().isEmpty()) {
            StringSelectMenu.Builder builder = StringSelectMenu.create(response.selectCustomId().orElseThrow())
                    .setPlaceholder("Choose the exact Minecraft identity")
                    .setRequiredRange(REQUIRED_SELECTION, REQUIRED_SELECTION);
            response.choices().forEach(choice -> builder.addOption(choice.label(), choice.value()));
            action = action.addComponents(ActionRow.of(builder.build()));
        }
        action.queue();
    }

    private boolean accepted(long eventGuildId) {
        return enabled.get() && eventGuildId == guildId;
    }

    private static long guildId(Guild guild) {
        return guild == null ? NO_GUILD : guild.getIdLong();
    }

    private static void unavailable(IReplyCallback event) {
        if (!event.isAcknowledged()) {
            event.reply("The Enthusia staff moderation surface is unavailable in this context.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    static List<CommandData> commands() {
        return commands(false);
    }

    static List<CommandData> commands(boolean includePunishments) {
        return commands(includePunishments, false);
    }

    static List<CommandData> commands(boolean includePunishments, boolean webEnabled) {
        return commands(includePunishments, webEnabled, false);
    }

    static List<CommandData> commands(boolean includePunishments, boolean webEnabled, boolean includeConsole) {
        DefaultMemberPermissions discovery = DefaultMemberPermissions.DISABLED;
        List<CommandData> commands = new ArrayList<>(List.of(
                moderateSlash(MODERATE, webEnabled, discovery),
                moderateSlash(PUNISH, webEnabled, discovery),
                Commands.user(MODERATE_USER).setDefaultPermissions(discovery),
                Commands.message(MODERATE_MESSAGE).setDefaultPermissions(discovery),
                stringSlash(
                        MODERATE_MINECRAFT,
                        "Open a read-only moderation profile for a Minecraft identity",
                        PLAYER_OPTION,
                        "Username or UUID",
                        discovery
                ),
                userSlash(LINKED, "View private linked-account information", discovery),
                userSlash(HISTORY, "View recent moderation history", discovery),
                userSlash(NOTES, "View recent private staff notes", discovery),
                stringSlash(CASE, "View a moderation case by exact ID", CASE_ID_OPTION, "16-character case ID", discovery),
                Commands.slash(REVIEW_QUEUE, "View Minecraft punishment requests with Approve/Deny buttons")
                        .setDefaultPermissions(discovery),
                Commands.slash(REVIEW_REQUEST, "Approve or deny a Minecraft punishment request")
                        .addOption(OptionType.STRING, REVIEW_ID, "Exact request UUID", true)
                        .addOptions(new OptionData(OptionType.STRING, REVIEW_DECISION, "Review decision", true)
                                .addChoice("Approve", "approve").addChoice("Deny", "deny"))
                        .addOption(OptionType.STRING, REVIEW_NOTE, "Required for denial", false)
                        .setDefaultPermissions(discovery)
        ));
        if (includePunishments) {
            commands.addAll(punishmentCommands(discovery));
        }
        if (includeConsole) {
            commands.add(Commands.slash(CONSOLE, "Run an allowlisted Minecraft console command")
                    .addOption(OptionType.STRING, SERVER_OPTION, "Configured server ID", true)
                    .addOption(OptionType.STRING, COMMAND_OPTION, "Allowlisted command and arguments", true)
                    .setDefaultPermissions(discovery));
        }
        return List.copyOf(commands);
    }

    private static CommandData moderateSlash(
            String name,
            boolean webEnabled,
            DefaultMemberPermissions discovery
    ) {
        if (webEnabled) {
            return Commands.slash(name, "Open the private moderation workspace")
                    .addOption(OptionType.USER, USER_OPTION, "Optional Discord user to inspect", false)
                    .setDefaultPermissions(discovery);
        }
        return userSlash(name, "Open a moderation profile for a Discord user", discovery);
    }

    private static List<CommandData> punishmentCommands(DefaultMemberPermissions discovery) {
        return List.of(
                punishmentSlash(WARN, "Warn a Discord user", false, discovery),
                punishmentSlash(MUTE, "Mute a Discord user", true, discovery),
                removalSlash(UNMUTE, "End the active Discord mute", discovery),
                punishmentSlash(KICK, "Kick a Discord user", false, discovery),
                banSlash(discovery),
                removalSlash(UNBAN, "End the active Discord ban", discovery),
                restrictSlash(discovery),
                restrictionRemovalSlash(discovery),
                notificationTestSlash(discovery)
        );
    }

    private static CommandData notificationTestSlash(DefaultMemberPermissions discovery) {
        OptionData type = new OptionData(
                OptionType.STRING,
                TYPE_OPTION,
                "Punishment notification format to DM to yourself",
                true
        )
                .addChoice("Discord Warning", "warning")
                .addChoice("Discord Mute", "mute")
                .addChoice("Discord Ban", "ban")
                .addChoice("Minecraft Warning", "minecraft-warning")
                .addChoice("Minecraft Mute", "minecraft-mute")
                .addChoice("Minecraft Ban", "minecraft-ban");
        return Commands.slash(NOTIFICATION_TEST, "DM yourself a safe punishment-notification preview")
                .addOptions(type)
                .setDefaultPermissions(discovery);
    }

    private static CommandData punishmentSlash(
            String name,
            String description,
            boolean duration,
            DefaultMemberPermissions discovery
    ) {
        List<OptionData> options = new ArrayList<>();
        options.add(userOption());
        if (duration) {
            options.add(stringOption(DURATION_OPTION, "Preset/custom duration or permanent", true));
        }
        options.add(stringOption(REASON_OPTION, "Public punishment reason", true));
        options.add(stringOption(EXPLANATION_OPTION, "Private staff explanation", false));
        return Commands.slash(name, description).addOptions(options).setDefaultPermissions(discovery);
    }

    private static CommandData banSlash(DefaultMemberPermissions discovery) {
        return Commands.slash(BAN, "Ban a Discord user")
                .addOptions(
                        userOption(),
                        stringOption(DURATION_OPTION, "Preset/custom duration or permanent", true),
                        stringOption(REASON_OPTION, "Public punishment reason", true),
                        stringOption(EXPLANATION_OPTION, "Private staff explanation", false),
                        new OptionData(OptionType.INTEGER, DELETE_SECONDS_OPTION, "Prior message deletion seconds", false)
                )
                .setDefaultPermissions(discovery);
    }

    private static CommandData restrictSlash(DefaultMemberPermissions discovery) {
        return Commands.slash(RESTRICT, "Restrict a Discord user in a channel or category")
                .addOptions(
                        userOption(),
                        stringOption(DURATION_OPTION, "Preset/custom duration or permanent", true),
                        stringOption(SCOPE_KIND_OPTION, "channel or category", true),
                        stringOption(SCOPE_ID_OPTION, "Discord channel/category ID", true),
                        stringOption(MODE_OPTION, "read-only or no-access", true),
                        stringOption(REASON_OPTION, "Public punishment reason", true),
                        stringOption(EXPLANATION_OPTION, "Private staff explanation", false)
                )
                .setDefaultPermissions(discovery);
    }

    private static CommandData restrictionRemovalSlash(DefaultMemberPermissions discovery) {
        return Commands.slash(UNRESTRICT, "End a Discord restriction on one exact scope")
                .addOptions(
                        stringOption(USER_ID_OPTION, "Exact Discord user ID", true),
                        stringOption(SCOPE_ID_OPTION, "Exact Discord channel/category ID", true)
                )
                .setDefaultPermissions(discovery);
    }

    private static CommandData removalSlash(
            String name,
            String description,
            DefaultMemberPermissions discovery
    ) {
        return Commands.slash(name, description)
                .addOptions(stringOption(USER_ID_OPTION, "Exact Discord user ID", true))
                .setDefaultPermissions(discovery);
    }

    private static OptionData userOption() {
        return new OptionData(OptionType.USER, USER_OPTION, "Discord user", true);
    }

    private static OptionData stringOption(String name, String description, boolean required) {
        return new OptionData(OptionType.STRING, name, description, required);
    }

    private static CommandData userSlash(String name, String description, DefaultMemberPermissions discovery) {
        return Commands.slash(name, description)
                .addOptions(userOption())
                .setDefaultPermissions(discovery);
    }

    private static CommandData stringSlash(
            String name,
            String description,
            String optionName,
            String optionDescription,
            DefaultMemberPermissions discovery
    ) {
        return Commands.slash(name, description)
                .addOptions(stringOption(optionName, optionDescription, true))
                .setDefaultPermissions(discovery);
    }

    private static String option(SlashCommandInteractionEvent event, String name, String fallback) {
        var mapping = event.getOption(name);
        return mapping == null ? fallback : mapping.getAsString();
    }

    private static int integerOption(SlashCommandInteractionEvent event, String name, int fallback) {
        var mapping = event.getOption(name);
        return mapping == null ? fallback : mapping.getAsInt();
    }

    private static void log(String code, Throwable failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            String type = failure == null ? "unknown" : failure.getClass().getSimpleName();
            LOGGER.log(System.Logger.Level.WARNING, "{0} type={1}", code, type);
        }
    }
}
