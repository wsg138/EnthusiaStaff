package net.enthusia.staff.discordbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private static final String MODERATE = "moderate";
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

    @FunctionalInterface
    private interface DiscordTargetRead {
        StaffModerationController.Response apply(long actorId, String actorName, long targetId);
    }

    private final long guildId;
    private final StaffBotWorkerPool workers;
    private final InteractionReplayGuard interactions;
    private final StaffModerationController controller;
    private final Optional<DiscordPunishmentCommandController> punishments;
    private final java.util.concurrent.atomic.AtomicBoolean enabled = new java.util.concurrent.atomic.AtomicBoolean();

    JdaStaffModerationListener(
            long guildId,
            StaffBotWorkerPool workers,
            InteractionReplayGuard interactions,
            StaffModerationRuntime moderation
    ) {
        this.guildId = guildId;
        this.workers = workers;
        this.interactions = interactions;
        this.punishments = moderation.punishmentService().map(DiscordPunishmentCommandController::new);
        this.controller = new StaffModerationController(
                moderation.reads(),
                moderation.actors(),
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
        List<CommandData> expected = commands(punishments.isPresent());
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
        switch (event.getName()) {
            case MODERATE -> dispatchModerate(event, actorId, actorName);
            case LINKED -> dispatchDiscordTarget(event, actorId, actorName, controller::linkedDiscord);
            case HISTORY -> dispatchDiscordTarget(event, actorId, actorName, controller::historyDiscord);
            case NOTES -> dispatchDiscordTarget(event, actorId, actorName, controller::notesDiscord);
            case MODERATE_MINECRAFT -> dispatchMinecraft(event, actorId, actorName);
            case CASE -> dispatchCase(event, actorId, actorName);
            case WARN, MUTE, UNMUTE, KICK, BAN, UNBAN, RESTRICT, UNRESTRICT ->
                    dispatchQuickPunishment(event, actorId, actorName);
            default -> unavailable(event);
        }
    }

    private void dispatchModerate(SlashCommandInteractionEvent event, long actorId, String actorName) {
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
                    option(event, SCOPE_KIND_OPTION, ""), option(event, SCOPE_ID_OPTION, ""),
                    option(event, MODE_OPTION, ""));
            default -> throw new IllegalArgumentException("not a Discord punishment issue command");
        };
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        if (!MODERATE_USER.equals(event.getName()) || !accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        long actor = event.getUser().getIdLong();
        long target = event.getTarget().getIdLong();
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
        dispatch(event, () -> withPunish(
                controller.moderateDiscord(actor, event.getUser().getName(), target), target));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!accepted(guildId(event.getGuild()))) {
            unavailable(event);
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
        DefaultMemberPermissions discovery = DefaultMemberPermissions.DISABLED;
        List<CommandData> commands = new ArrayList<>(List.of(
                userSlash(MODERATE, "Open a moderation profile for a Discord user", discovery),
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
                stringSlash(CASE, "View a moderation case by exact ID", CASE_ID_OPTION, "16-character case ID", discovery)
        ));
        if (includePunishments) {
            commands.addAll(punishmentCommands(discovery));
        }
        return List.copyOf(commands);
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
                restrictionRemovalSlash(discovery)
        );
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
