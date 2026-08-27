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
                failure -> {
                    enabled.set(false);
                    log("discord_read_commands_registration_failed", failure);
                }
        );
    }

    void disable() {
        enabled.set(false);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!accepted(event.getGuild() == null ? 0L : event.getGuild().getIdLong())) {
            unavailable(event);
            return;
        }
        long actor = event.getUser().getIdLong();
        String actorName = event.getUser().getName();
        switch (event.getName()) {
            case "moderate" -> {
                User target = event.getOption("user").getAsUser();
                dispatch(event, () -> controller.moderateDiscord(actor, actorName, target.getIdLong()));
            }
            case "moderate-minecraft" -> {
                String target = event.getOption("player").getAsString();
                dispatch(event, () -> controller.moderateMinecraft(actor, actorName, target));
            }
            case "linked" -> {
                User target = event.getOption("user").getAsUser();
                dispatch(event, () -> controller.linkedDiscord(actor, actorName, target.getIdLong()));
            }
            case "history" -> {
                User target = event.getOption("user").getAsUser();
                dispatch(event, () -> controller.historyDiscord(actor, actorName, target.getIdLong()));
            }
            case "notes" -> {
                User target = event.getOption("user").getAsUser();
                dispatch(event, () -> controller.notesDiscord(actor, actorName, target.getIdLong()));
            }
            case "case" -> {
                String caseId = event.getOption("id").getAsString();
                dispatch(event, () -> controller.caseView(actor, actorName, caseId));
            }
            default -> unavailable(event);
        }
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        long eventGuildId = event.getGuild() == null ? 0L : event.getGuild().getIdLong();
        if (!"Moderate User".equals(event.getName()) || !accepted(eventGuildId)) {
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
        long eventGuildId = event.getGuild() == null ? 0L : event.getGuild().getIdLong();
        if (!"Moderate Message".equals(event.getName()) || !accepted(eventGuildId)) {
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
        if (!accepted(event.getGuild() == null ? 0L : event.getGuild().getIdLong())) {
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
        if (!accepted(event.getGuild() == null ? 0L : event.getGuild().getIdLong())) {
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
        event.deferReply(true).queue(hook -> {
            boolean scheduled = workers.tryExecute(() -> {
                try {
                    send(hook, work.get());
                } catch (RuntimeException exception) {
                    log("discord_read_interaction_failed", exception);
                    hook.sendMessage("The read-only moderation view is temporarily unavailable.").queue();
                }
            });
            if (!scheduled) {
                hook.sendMessage("The moderation read queue is busy. Try again shortly.").queue();
            }
        }, failure -> interactions.release(interactionId));
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
                    .setRequiredRange(1, 1);
            response.choices().forEach(choice -> builder.addOption(choice.label(), choice.value()));
            action = action.addComponents(ActionRow.of(builder.build()));
        }
        action.queue();
    }

    private boolean accepted(long eventGuildId) {
        return enabled.get() && eventGuildId == guildId;
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
                Commands.slash("moderate", "Open a read-only moderation profile for a Discord user")
                        .addOptions(new OptionData(OptionType.USER, "user", "Discord user", true))
                        .setDefaultPermissions(discovery),
                Commands.user("Moderate User").setDefaultPermissions(discovery),
                Commands.message("Moderate Message").setDefaultPermissions(discovery),
                Commands.slash("moderate-minecraft", "Open a read-only moderation profile for a Minecraft identity")
                        .addOptions(new OptionData(OptionType.STRING, "player", "Username or UUID", true))
                        .setDefaultPermissions(discovery),
                Commands.slash("linked", "View private linked-account information")
                        .addOptions(new OptionData(OptionType.USER, "user", "Discord user", true))
                        .setDefaultPermissions(discovery),
                Commands.slash("history", "View recent moderation history")
                        .addOptions(new OptionData(OptionType.USER, "user", "Discord user", true))
                        .setDefaultPermissions(discovery),
                Commands.slash("notes", "View recent private staff notes")
                        .addOptions(new OptionData(OptionType.USER, "user", "Discord user", true))
                        .setDefaultPermissions(discovery),
                Commands.slash("case", "View a moderation case by exact ID")
                        .addOptions(new OptionData(OptionType.STRING, "id", "16-character case ID", true))
                        .setDefaultPermissions(discovery)
        );
    }

    private static void log(String code, Throwable failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            String type = failure == null ? "unknown" : failure.getClass().getSimpleName();
            LOGGER.log(System.Logger.Level.WARNING, "{0} type={1}", code, type);
        }
    }
}
