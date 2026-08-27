package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/** JDA adapter for the D06 read-only command/context/component surface. */
final class JdaStaffModerationListener extends ListenerAdapter {
    private static final System.Logger LOGGER = System.getLogger(JdaStaffModerationListener.class.getName());
    private static final long NO_GUILD = 0L;
    private static final int REQUIRED_SELECTION = 1;
    private static final String USER_OPTION = "user";
    private static final String PLAYER_OPTION = "player";
    private static final String CASE_ID_OPTION = "id";
    private static final String MODERATE = "moderate";
    private static final String MODERATE_MINECRAFT = "moderate-minecraft";
    private static final String LINKED = "linked";
    private static final String HISTORY = "history";
    private static final String NOTES = "notes";
    private static final String CASE = "case";
    private static final String MODERATE_USER = "Moderate User";
    private static final String MODERATE_MESSAGE = "Moderate Message";

    @FunctionalInterface
    private interface DiscordTargetRead {
        StaffModerationController.Response apply(long actorId, String actorName, long targetId);
    }

    private final long guildId;
    private final StaffBotWorkerPool workers;
    private final InteractionReplayGuard interactions;
    private final StaffModerationController controller;
    private final AtomicBoolean enabled = new AtomicBoolean();

    JdaStaffModerationListener(
            long guildId,
            StaffBotWorkerPool workers,
            InteractionReplayGuard interactions,
            StaffModerationRuntime moderation
    ) {
        this.guildId = guildId;
        this.workers = workers;
        this.interactions = interactions;
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
        guild.updateCommands().addCommands(commands()).queue(
                ignored -> enabled.set(true),
                failure -> commandRegistrationFailed(failure)
        );
    }

    private void commandRegistrationFailed(Throwable failure) {
        enabled.set(false);
        log("discord_read_commands_registration_failed", failure);
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
            case MODERATE -> dispatchDiscordTarget(event, actorId, actorName, controller::moderateDiscord);
            case LINKED -> dispatchDiscordTarget(event, actorId, actorName, controller::linkedDiscord);
            case HISTORY -> dispatchDiscordTarget(event, actorId, actorName, controller::historyDiscord);
            case NOTES -> dispatchDiscordTarget(event, actorId, actorName, controller::notesDiscord);
            case MODERATE_MINECRAFT -> dispatchMinecraft(event, actorId, actorName);
            case CASE -> dispatchCase(event, actorId, actorName);
            default -> unavailable(event);
        }
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

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        if (!MODERATE_USER.equals(event.getName()) || !accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        long actor = event.getUser().getIdLong();
        dispatch(event, () -> controller.moderateDiscord(
                actor,
                event.getUser().getName(),
                event.getTarget().getIdLong()
        ));
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        if (!MODERATE_MESSAGE.equals(event.getName()) || !accepted(guildId(event.getGuild()))) {
            unavailable(event);
            return;
        }
        long actor = event.getUser().getIdLong();
        dispatch(event, () -> controller.moderateDiscord(
                actor,
                event.getUser().getName(),
                event.getTarget().getAuthor().getIdLong()
        ));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!accepted(guildId(event.getGuild()))) {
            unavailable(event);
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

    private void dispatch(IReplyCallback event, Supplier<StaffModerationController.Response> work) {
        long interactionId = event.getIdLong();
        InteractionReplayGuard.ClaimResult claim = interactions.claim(interactionId);
        if (claim != InteractionReplayGuard.ClaimResult.CLAIMED) {
            event.reply("That interaction was already handled or the read queue is saturated.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.deferReply(true).queue(
                hook -> scheduleRead(hook, work),
                failure -> interactions.release(interactionId)
        );
    }

    private void scheduleRead(InteractionHook hook, Supplier<StaffModerationController.Response> work) {
        boolean scheduled = workers.tryExecute(() -> executeRead(hook, work));
        if (!scheduled) {
            hook.sendMessage("The moderation read queue is busy. Try again shortly.").queue();
        }
    }

    private static void executeRead(InteractionHook hook, Supplier<StaffModerationController.Response> work) {
        try {
            send(hook, work.get());
        } catch (RuntimeException exception) {
            log("discord_read_interaction_failed", exception);
            hook.sendMessage("The read-only moderation view is temporarily unavailable.").queue();
        }
    }

    private static void send(InteractionHook hook, StaffModerationController.Response response) {
        var action = hook.sendMessage(response.content());
        if (!response.buttons().isEmpty()) {
            List<Button> buttons = response.buttons().stream()
                    .map(button -> Button.secondary(button.customId(), button.label()))
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
        DefaultMemberPermissions discovery = DefaultMemberPermissions.DISABLED;
        return List.of(
                userSlash(MODERATE, "Open a read-only moderation profile for a Discord user", discovery),
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
        );
    }

    private static CommandData userSlash(String name, String description, DefaultMemberPermissions discovery) {
        return Commands.slash(name, description)
                .addOptions(new OptionData(OptionType.USER, USER_OPTION, "Discord user", true))
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
                .addOptions(new OptionData(OptionType.STRING, optionName, optionDescription, true))
                .setDefaultPermissions(discovery);
    }

    private static void log(String code, Throwable failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            String type = failure == null ? "unknown" : failure.getClass().getSimpleName();
            LOGGER.log(System.Logger.Level.WARNING, "{0} type={1}", code, type);
        }
    }
}
