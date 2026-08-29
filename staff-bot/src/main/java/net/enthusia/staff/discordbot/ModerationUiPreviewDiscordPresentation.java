package net.enthusia.staff.discordbot;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.modals.Modal;

/** Constructs the public Discord representation of fake preview state. */
final class ModerationUiPreviewDiscordPresentation {
    private static final int PREVIEW_COLOR = 0x5865F2;
    private static final int WARNING_COLOR = 0xF0B232;
    private static final int FAILURE_COLOR = 0xED4245;
    private static final String OP_NAV = "nav";
    private static final String OP_ACTION = "action";
    private static final String OP_BACK = "back";
    private static final String FOOTER = "STAGING UI PREVIEW · Sample data · No moderation action is possible";
    private static final Set<ModerationUiPreviewModel.Screen> DATA_SCREENS = EnumSet.of(
            ModerationUiPreviewModel.Screen.OVERVIEW,
            ModerationUiPreviewModel.Screen.ACCOUNTS,
            ModerationUiPreviewModel.Screen.HISTORY,
            ModerationUiPreviewModel.Screen.NOTES,
            ModerationUiPreviewModel.Screen.CASES);
    private static final Set<ModerationUiPreviewModel.Screen> CHOICE_EMBED_SCREENS = EnumSet.of(
            ModerationUiPreviewModel.Screen.ACTION,
            ModerationUiPreviewModel.Screen.SCOPE,
            ModerationUiPreviewModel.Screen.REASON,
            ModerationUiPreviewModel.Screen.DURATION);
    private static final Set<ModerationUiPreviewModel.Screen> CHOICE_COMPONENT_SCREENS = EnumSet.of(
            ModerationUiPreviewModel.Screen.ACTION,
            ModerationUiPreviewModel.Screen.SCOPE,
            ModerationUiPreviewModel.Screen.REASON,
            ModerationUiPreviewModel.Screen.DURATION);
    private static final Set<ModerationUiPreviewModel.Screen> BACK_ONLY_SCREENS = EnumSet.of(
            ModerationUiPreviewModel.Screen.ACCOUNTS,
            ModerationUiPreviewModel.Screen.HISTORY,
            ModerationUiPreviewModel.Screen.NOTES,
            ModerationUiPreviewModel.Screen.CASES,
            ModerationUiPreviewModel.Screen.SCENARIO);
    private static final List<SelectOption> REASON_OPTIONS = Arrays.stream(ModerationUiPreviewModel.Reason.values())
            .map(reason -> option(reason.label(), reason.name()))
            .toList();
    private static final List<SelectOption> DURATION_OPTIONS = Arrays.stream(ModerationUiPreviewModel.DurationChoice.values())
            .map(duration -> option(duration.label(), duration.name()))
            .toList();
    private static final List<SelectOption> SCENARIO_OPTIONS = Arrays.stream(ModerationUiPreviewModel.Scenario.values())
            .map(scenario -> option(scenario.label(), scenario.name()))
            .toList();

    record Rendered(MessageEmbed embed, List<ActionRow> rows) {
    }

    Rendered render(ModerationUiPreviewModel.Snapshot snapshot) {
        return new Rendered(embed(snapshot), components(snapshot));
    }

    Modal modal(ModerationUiPreviewController.ModalSpec spec) {
        TextInput input = TextInput.create("value", TextInputStyle.PARAGRAPH)
                .setPlaceholder(spec.placeholder())
                .setRequired(true)
                .setMaxLength(spec.maxLength())
                .build();
        return Modal.create(spec.customId(), spec.title())
                .addComponents(Label.of(spec.label(), input))
                .build();
    }

    private static MessageEmbed embed(ModerationUiPreviewModel.Snapshot snapshot) {
        if (DATA_SCREENS.contains(snapshot.state().screen())) {
            return dataEmbed(snapshot.state().screen());
        }
        return workflowEmbed(snapshot);
    }

    private static MessageEmbed dataEmbed(ModerationUiPreviewModel.Screen screen) {
        return switch (screen) {
            case OVERVIEW -> overview();
            case ACCOUNTS -> accounts();
            case HISTORY -> history();
            case NOTES -> notes();
            case CASES -> cases();
            default -> throw new IllegalArgumentException("not a preview data screen: " + screen);
        };
    }

    private static MessageEmbed workflowEmbed(ModerationUiPreviewModel.Snapshot snapshot) {
        ModerationUiPreviewModel.Screen screen = snapshot.state().screen();
        return CHOICE_EMBED_SCREENS.contains(screen)
                ? choiceEmbed(screen)
                : outcomeEmbed(snapshot);
    }

    private static MessageEmbed choiceEmbed(ModerationUiPreviewModel.Screen screen) {
        return switch (screen) {
            case ACTION -> simple("Choose punishment", "Select the action you want to prototype.");
            case SCOPE -> simple("Choose platform scope", "This is visual only; no platform action will run.");
            case REASON -> simple("Choose offense", "Pick a representative offense or enter a custom reason.");
            case DURATION -> simple("Choose duration", "Choose a preset or preview a custom duration.");
            default -> throw new IllegalArgumentException("not a preview choice screen: " + screen);
        };
    }

    private static MessageEmbed outcomeEmbed(ModerationUiPreviewModel.Snapshot snapshot) {
        return switch (snapshot.state().screen()) {
            case OPTIONS -> options(snapshot.state());
            case CONFIRM -> confirmation(snapshot.state());
            case SCENARIO -> scenario(snapshot.state().scenario());
            case COMPLETE -> complete();
            default -> throw new IllegalArgumentException("not a preview outcome screen: " + snapshot.state().screen());
        };
    }

    private static MessageEmbed overview() {
        return base("Moderation overview · UI preview")
                .setDescription("Proposed staff punishment workflow using deterministic sample moderation data.")
                .addField("Target", ModerationUiPreviewModel.SAMPLE_DISCORD + "\nStatus: member · account age: 2y", true)
                .addField("Minecraft", ModerationUiPreviewModel.SAMPLE_MINECRAFT + "\n2 linked accounts total", true)
                .addField("Active punishments", "Discord: mute · 1h 18m remaining\nMinecraft: none", false)
                .addField("Recent moderation", "Warn · Spam / flooding · 9d ago\nMute · Harassment · 24d ago", false)
                .build();
    }

    private static MessageEmbed accounts() {
        return base("Linked accounts · sample")
                .addField("Main", "RiverAshMC · Java · 412h active playtime", false)
                .addField("Linked alt", "AshRiverAlt · Bedrock · linked 83d ago", false)
                .addField("Relationship", "Confirmed current links · historical relationships retained for staff", false)
                .build();
    }

    private static MessageEmbed history() {
        return base("Moderation history · sample")
                .addField("9d ago", "Discord warning · Spam / flooding · completed", false)
                .addField("24d ago", "Discord mute · Harassment · 2h · expired", false)
                .addField("61d ago", "Minecraft warning · Chat abuse · completed", false)
                .build();
    }

    private static MessageEmbed notes() {
        return base("Staff notes · sample")
                .addField("Recent", "Cooperative during prior review; no current management-sensitive note.", false)
                .addField("Visibility", "Staff-only sample content. No production note data is loaded in preview mode.", false)
                .build();
    }

    private static MessageEmbed cases() {
        return base("Cases · sample")
                .addField("CASE-PREVIEW-1042", "Closed · harassment review · 24d ago", false)
                .addField("CASE-PREVIEW-1187", "Open · spam pattern review · updated 9d ago", false)
                .build();
    }

    private static MessageEmbed options(ModerationUiPreviewModel.State state) {
        return base("Punishment options")
                .addField("Selection", summary(state), false)
                .addField("DM user", state.dmUser() ? "On" : "Off", true)
                .addField("Delete offending message", state.deleteMessage() ? "On" : "Off", true)
                .setDescription("The delete toggle represents the message-context flow; this preview has no real message target.")
                .build();
    }

    private static MessageEmbed confirmation(ModerationUiPreviewModel.State state) {
        return base("Confirm punishment · preview only")
                .setDescription("Review exactly what the future enforcement flow would reauthorize before commit.")
                .addField("Target", ModerationUiPreviewModel.SAMPLE_DISCORD, false)
                .addField("Action / platform", state.action().label() + " · " + state.scope().label(), true)
                .addField("Duration", state.duration(), true)
                .addField("Reason", state.reason(), false)
                .addField("Options", "DM user: " + onOff(state.dmUser())
                        + "\nDelete message: " + onOff(state.deleteMessage()), false)
                .addField("Higher approval", state.approvalSummary(), false)
                .build();
    }

    private static MessageEmbed scenario(ModerationUiPreviewModel.Scenario scenario) {
        if (scenario == null) {
            return simple("Preview state unavailable", "Choose a representative state from the overview.");
        }
        return switch (scenario) {
            case INSUFFICIENT -> scenarioEmbed(
                    "Insufficient authority", "Action blocked",
                    "Your linked staff authority does not allow this sanction.", FAILURE_COLOR);
            case PROTECTED -> scenarioEmbed(
                    "Protected target", "Action blocked",
                    "This target is equal/higher staff or otherwise protected by hierarchy.", FAILURE_COLOR);
            case APPROVAL -> scenarioEmbed(
                    "Approval required", "Pending higher approval",
                    "The requested permanent/elevated consequence needs Admin+ authorization before commit.", WARNING_COLOR);
            case STALE -> scenarioEmbed(
                    "Confirmation expired", "Refresh required",
                    "The confirmation is stale. Reopen the punishment flow and reauthorize current policy.", WARNING_COLOR);
            case DISCORD_FAILURE -> scenarioEmbed(
                    "Discord enforcement failed", "Retry pending",
                    "Authoritative intent would remain truthful while the Discord side effect is retried/reconciled.", FAILURE_COLOR);
            case PARTIAL -> scenarioEmbed(
                    "Partial result", "Discord succeeded · Minecraft pending",
                    "A cross-platform flow would report each platform result separately instead of claiming global success.", WARNING_COLOR);
        };
    }

    private static MessageEmbed scenarioEmbed(String title, String status, String detail, int color) {
        return base(title)
                .setColor(color)
                .addField("Status", status, false)
                .addField("What staff sees", detail, false)
                .build();
    }

    private static MessageEmbed complete() {
        return base("Preview complete")
                .setDescription("**Preview complete — no moderation action was applied.**")
                .addField("Result", "No Discord moderation REST action, database write, Minecraft action, or authority call was made.", false)
                .build();
    }

    private static MessageEmbed simple(String title, String description) {
        return base(title).setDescription(description).build();
    }

    private static EmbedBuilder base(String title) {
        return new EmbedBuilder().setTitle(title).setColor(PREVIEW_COLOR).setFooter(FOOTER);
    }

    private static String summary(ModerationUiPreviewModel.State state) {
        return state.action().label() + " · " + state.scope().label() + "\n"
                + state.reason() + " · " + state.duration();
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private List<ActionRow> components(ModerationUiPreviewModel.Snapshot snapshot) {
        ModerationUiPreviewModel.Screen screen = snapshot.state().screen();
        if (screen == ModerationUiPreviewModel.Screen.OVERVIEW) {
            return overviewComponents(snapshot);
        }
        if (BACK_ONLY_SCREENS.contains(screen)) {
            return List.of(backRow(snapshot));
        }
        return workflowComponents(snapshot);
    }

    private List<ActionRow> workflowComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        ModerationUiPreviewModel.Screen screen = snapshot.state().screen();
        return CHOICE_COMPONENT_SCREENS.contains(screen)
                ? choiceComponents(snapshot)
                : outcomeComponents(snapshot);
    }

    private List<ActionRow> choiceComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return switch (snapshot.state().screen()) {
            case ACTION -> actionComponents(snapshot);
            case SCOPE -> scopeComponents(snapshot);
            case REASON -> reasonComponents(snapshot);
            case DURATION -> durationComponents(snapshot);
            default -> throw new IllegalArgumentException(
                    "not a preview choice component screen: " + snapshot.state().screen());
        };
    }

    private List<ActionRow> outcomeComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return switch (snapshot.state().screen()) {
            case OPTIONS -> optionComponents(snapshot);
            case CONFIRM -> confirmComponents(snapshot);
            case COMPLETE -> List.of();
            default -> throw new IllegalArgumentException(
                    "not a preview outcome component screen: " + snapshot.state().screen());
        };
    }

    private static List<ActionRow> overviewComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(
                ActionRow.of(
                        Button.danger(id(snapshot, "punish", ""), "Punish"),
                        Button.secondary(id(snapshot, OP_NAV, "history"), "History"),
                        Button.secondary(id(snapshot, OP_NAV, "accounts"), "Accounts"),
                        Button.secondary(id(snapshot, OP_NAV, "notes"), "Notes"),
                        Button.secondary(id(snapshot, OP_NAV, "cases"), "Cases")
                ),
                ActionRow.of(scenarioMenu(snapshot))
        );
    }

    private static List<ActionRow> actionComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(
                ActionRow.of(
                        Button.secondary(id(snapshot, OP_ACTION, "warn"), "Warn"),
                        Button.primary(id(snapshot, OP_ACTION, "mute"), "Mute"),
                        Button.secondary(id(snapshot, OP_ACTION, "kick"), "Kick"),
                        Button.danger(id(snapshot, OP_ACTION, "ban"), "Ban"),
                        Button.primary(id(snapshot, OP_ACTION, "restrict"), "Restrict")
                ),
                backRow(snapshot)
        );
    }

    private static List<ActionRow> scopeComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(
                ActionRow.of(
                        Button.primary(id(snapshot, "scope", "discord"), "Discord"),
                        Button.secondary(id(snapshot, "scope", "minecraft"), "Minecraft"),
                        Button.secondary(id(snapshot, "scope", "both"), "Both")
                ),
                backRow(snapshot)
        );
    }

    private static List<ActionRow> reasonComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        StringSelectMenu menu = StringSelectMenu.create(id(snapshot, "reason", ""))
                .setPlaceholder("Choose offense / reason")
                .setRequiredRange(1, 1)
                .addOptions(REASON_OPTIONS)
                .build();
        return List.of(ActionRow.of(menu), backRow(snapshot));
    }

    private static List<ActionRow> durationComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        StringSelectMenu menu = StringSelectMenu.create(id(snapshot, "duration", ""))
                .setPlaceholder("Choose duration")
                .setRequiredRange(1, 1)
                .addOptions(DURATION_OPTIONS)
                .build();
        return List.of(ActionRow.of(menu), backRow(snapshot));
    }

    private static List<ActionRow> optionComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        ModerationUiPreviewModel.State state = snapshot.state();
        return List.of(
                ActionRow.of(
                        Button.secondary(id(snapshot, "toggle", "dm"), "DM user: " + onOff(state.dmUser())),
                        Button.secondary(id(snapshot, "toggle", "delete"), "Delete message: " + onOff(state.deleteMessage())),
                        Button.success(id(snapshot, "review", ""), "Review")
                ),
                backRow(snapshot)
        );
    }

    private static List<ActionRow> confirmComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(ActionRow.of(
                Button.danger(id(snapshot, "confirm", ""), "Confirm preview"),
                Button.secondary(id(snapshot, OP_BACK, ""), "Back")
        ));
    }

    private static ActionRow backRow(ModerationUiPreviewModel.Snapshot snapshot) {
        return ActionRow.of(Button.secondary(id(snapshot, OP_BACK, ""), "Back"));
    }

    private static StringSelectMenu scenarioMenu(ModerationUiPreviewModel.Snapshot snapshot) {
        return StringSelectMenu.create(id(snapshot, "scenario", ""))
                .setPlaceholder("Preview edge / failure states")
                .setRequiredRange(1, 1)
                .addOptions(SCENARIO_OPTIONS)
                .build();
    }

    private static SelectOption option(String label, String value) {
        return SelectOption.of(label, value.toLowerCase(Locale.ROOT));
    }

    private static String id(ModerationUiPreviewModel.Snapshot snapshot, String operation, String argument) {
        return ModerationUiPreviewController.componentId(snapshot, operation, argument);
    }
}
