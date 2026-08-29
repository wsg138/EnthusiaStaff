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
    private static final String FOOTER = "STAGING UI PREVIEW · Sample policy/data · No moderation action is possible";
    private static final Set<ModerationUiPreviewModel.Screen> DATA_SCREENS = EnumSet.of(
            ModerationUiPreviewModel.Screen.OVERVIEW,
            ModerationUiPreviewModel.Screen.ACCOUNTS,
            ModerationUiPreviewModel.Screen.HISTORY,
            ModerationUiPreviewModel.Screen.NOTES,
            ModerationUiPreviewModel.Screen.CASES);
    private static final List<SelectOption> OFFENSE_OPTIONS = Arrays.stream(ModerationUiPreviewModel.Offense.values())
            .map(offense -> option(offense.label(), offense.name()))
            .toList();
    private static final List<SelectOption> DURATION_OPTIONS =
            Arrays.stream(ModerationUiPreviewModel.DurationChoice.values())
                    .map(duration -> option(duration.label(), duration.name()))
                    .toList();
    private static final List<SelectOption> SAMPLE_OPTIONS =
            Arrays.stream(ModerationUiPreviewModel.SampleScenario.values())
                    .map(scenario -> option(scenario.label(), scenario.name()))
                    .toList();
    private static final List<SelectOption> EDGE_OPTIONS =
            Arrays.stream(ModerationUiPreviewModel.EdgeState.values())
                    .map(state -> option(state.label(), state.name()))
                    .toList();
    private static final ModerationUiPreviewPolicy POLICY = new ModerationUiPreviewPolicy();

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
        ModerationUiPreviewModel.State state = snapshot.state();
        if (DATA_SCREENS.contains(state.screen())) {
            return dataEmbed(state);
        }
        return workflowEmbed(state);
    }

    private static MessageEmbed dataEmbed(ModerationUiPreviewModel.State state) {
        ModerationUiPreviewPolicy.Profile profile = POLICY.profile(state.sampleScenario());
        return switch (state.screen()) {
            case OVERVIEW -> overview(state, profile);
            case ACCOUNTS -> accounts(profile);
            case HISTORY -> history(profile);
            case NOTES -> notes(profile);
            case CASES -> cases(profile);
            default -> throw new IllegalArgumentException("not a preview data screen: " + state.screen());
        };
    }

    private static MessageEmbed workflowEmbed(ModerationUiPreviewModel.State state) {
        if (state.screen() == ModerationUiPreviewModel.Screen.OFFENSE) {
            return offense();
        }
        if (EnumSet.of(
                ModerationUiPreviewModel.Screen.RECOMMENDATION,
                ModerationUiPreviewModel.Screen.CUSTOM_ACTION,
                ModerationUiPreviewModel.Screen.CUSTOM_SCOPE,
                ModerationUiPreviewModel.Screen.CUSTOM_DURATION).contains(state.screen())) {
            return selectionEmbed(state);
        }
        return outcomeEmbed(state);
    }

    private static MessageEmbed selectionEmbed(ModerationUiPreviewModel.State state) {
        return switch (state.screen()) {
            case RECOMMENDATION -> recommendation(state);
            case CUSTOM_ACTION -> customAction(state);
            case CUSTOM_SCOPE -> customScope(state);
            case CUSTOM_DURATION -> customDuration(state);
            default -> throw new IllegalArgumentException("not a preview selection screen: " + state.screen());
        };
    }

    private static MessageEmbed outcomeEmbed(ModerationUiPreviewModel.State state) {
        return switch (state.screen()) {
            case OPTIONS -> options(state);
            case CONFIRM -> confirmation(state);
            case EDGE_STATE -> edgeState(state.edgeState());
            case COMPLETE -> complete();
            default -> throw new IllegalArgumentException("not a preview outcome screen: " + state.screen());
        };
    }

    private static MessageEmbed overview(
            ModerationUiPreviewModel.State state,
            ModerationUiPreviewPolicy.Profile profile
    ) {
        return base("Moderation profile · UI preview")
                .setDescription("History first. Choose an offense only after reviewing the player's sample profile.\n"
                        + "**Sample:** " + state.sampleScenario().label() + " — " + state.sampleScenario().hint())
                .addField("Target", ModerationUiPreviewModel.SAMPLE_DISCORD
                        + "\nStatus: member · account age: 2y", true)
                .addField("Minecraft", ModerationUiPreviewModel.SAMPLE_MINECRAFT
                        + "\n2 linked accounts in most samples", true)
                .addField("Active punishments", profile.activePunishments(), false)
                .addField("Recent moderation", profile.recentHistory(), false)
                .addField("History context", profile.totalHistory()
                        + " total moderation record(s). Ladder relevance is calculated after offense selection.", false)
                .build();
    }

    private static MessageEmbed accounts(ModerationUiPreviewPolicy.Profile profile) {
        return base("Linked accounts · sample")
                .setDescription(profile.accounts())
                .addField("Relationship", "Confirmed sample links; historical relationships remain staff context.", false)
                .build();
    }

    private static MessageEmbed history(ModerationUiPreviewPolicy.Profile profile) {
        EmbedBuilder builder = base("Moderation history · sample")
                .setDescription("All records stay visible. Only the selected offense's relevant subset advances its ladder.");
        for (ModerationUiPreviewPolicy.HistoryEntry entry : profile.history()) {
            builder.addField(entry.when(), entry.punishment() + " · " + entry.offense().label()
                    + " · " + entry.scope().label()
                    + (entry.ladderRelevant() ? "" : " · excluded from ladder"), false);
        }
        return builder.build();
    }

    private static MessageEmbed notes(ModerationUiPreviewPolicy.Profile profile) {
        return base("Staff notes · sample")
                .addField("Current sample", profile.notes(), false)
                .addField("Visibility", "Staff-only sample content. Preview mode loads no production notes.", false)
                .build();
    }

    private static MessageEmbed cases(ModerationUiPreviewPolicy.Profile profile) {
        return base("Cases · sample")
                .addField("Current sample", profile.cases(), false)
                .addField("Boundary", "Case rows are deterministic display data only.", false)
                .build();
    }

    private static MessageEmbed offense() {
        return base("Choose offense / reason")
                .setDescription("The offense comes first. The preview then evaluates only relevant sample history "
                        + "and recommends the policy-consistent consequence.")
                .build();
    }

    private static MessageEmbed recommendation(ModerationUiPreviewModel.State state) {
        ModerationUiPreviewModel.Recommendation recommendation = state.recommendation();
        return base("Punishment ladder recommendation")
                .setDescription("This is the normal path. Staff should usually follow the recommendation instead "
                        + "of manually rebuilding the punishment.")
                .addField("Selected offense", state.offenseLabel(), false)
                .addField("History", "Total moderation history: " + recommendation.totalHistory()
                        + "\nRelevant previous offenses: " + recommendation.relevantHistory(), true)
                .addField("Ladder step", Integer.toString(recommendation.ladderStep()), true)
                .addField("Recommended punishment", recommendation.punishmentSummary(), false)
                .addField("Why this punishment?", recommendation.explanation(), false)
                .addField("Approval", recommendation.approvalRequirement(), false)
                .build();
    }

    private static MessageEmbed customAction(ModerationUiPreviewModel.State state) {
        return customBase(state, "Custom punishment · choose action",
                "You deliberately left the ladder recommendation. Manual controls are now available.")
                .build();
    }

    private static MessageEmbed customScope(ModerationUiPreviewModel.State state) {
        return customBase(state, "Custom punishment · choose scope",
                "Choose the future enforcement scope for this explicit override.")
                .addField("Custom action", state.actualAction().label(), false)
                .build();
    }

    private static MessageEmbed customDuration(ModerationUiPreviewModel.State state) {
        return customBase(state, "Custom punishment · choose duration",
                "Choose a preset, custom duration, or permanent state where applicable.")
                .addField("Custom action / scope", state.actualAction().label() + " · " + state.actualScope().label(), false)
                .build();
    }

    private static EmbedBuilder customBase(
            ModerationUiPreviewModel.State state,
            String title,
            String description
    ) {
        return base(title)
                .setDescription(description)
                .addField("Selected offense", state.offenseLabel(), false)
                .addField("Policy recommendation", state.recommendation().punishmentSummary(), false)
                .addField("Override status", "Custom punishment selected — recommendation is not being followed.", false);
    }

    private static MessageEmbed options(ModerationUiPreviewModel.State state) {
        String title = state.overridden() ? "Custom override options" : "Recommendation options";
        String path = state.overridden()
                ? "Custom override — differs from the default policy path."
                : "Recommendation selected — action, scope, and duration carried forward automatically.";
        return base(title)
                .setDescription(path)
                .addField("Selected punishment", state.selectedPunishmentSummary(), false)
                .addField("Policy recommendation", state.recommendation().punishmentSummary(), false)
                .addField("DM user", onOff(state.dmUser()), true)
                .addField("Delete triggering message", onOff(state.deleteMessage()), true)
                .addField("Explanation / context", state.explanation(), false)
                .addField("Approval", state.approvalSummary(), false)
                .build();
    }

    private static MessageEmbed confirmation(ModerationUiPreviewModel.State state) {
        ModerationUiPreviewModel.Recommendation recommendation = state.recommendation();
        String decision = state.followedRecommendation()
                ? "Followed recommendation"
                : "Overridden — custom punishment";
        return base("Final confirmation · preview only")
                .setDescription("A real system would immediately reauthorize current policy/authority here. "
                        + "This preview can only change in-memory sample state.")
                .addField("Target", ModerationUiPreviewModel.SAMPLE_DISCORD, false)
                .addField("Selected offense", state.offenseLabel(), false)
                .addField("Relevant history / ladder", recommendation.relevantHistory() + " relevant of "
                        + recommendation.totalHistory() + " total · step " + recommendation.ladderStep(), false)
                .addField("Recommended punishment", recommendation.punishmentSummary(), false)
                .addField("Actual selected punishment", state.selectedPunishmentSummary(), false)
                .addField("Recommendation status", decision, false)
                .addField("Duration", state.actualDuration(), true)
                .addField("Platform / scope", state.actualScope().label(), true)
                .addField("Options", "DM user: " + onOff(state.dmUser())
                        + "\nDelete message: " + onOff(state.deleteMessage()), false)
                .addField("Approval requirement", state.approvalSummary(), false)
                .addField("Explanation / context", state.explanation(), false)
                .build();
    }

    private static MessageEmbed edgeState(ModerationUiPreviewModel.EdgeState state) {
        return state == null
                ? simple("Preview state unavailable", "Choose a representative state from the profile.")
                : edgeStateSelected(state);
    }

    private static MessageEmbed edgeStateSelected(ModerationUiPreviewModel.EdgeState state) {
        return switch (state) {
            case INSUFFICIENT -> edgeEmbed(
                    "Insufficient authority", "Recommendation/custom selection gate",
                    "Blocked before final confirmation because current linked staff authority cannot authorize "
                            + "the selected consequence.", FAILURE_COLOR);
            case PROTECTED -> edgeEmbed(
                    "Protected target", "Recommendation/custom selection gate",
                    "Blocked because the target is equal/higher staff or otherwise protected by hierarchy.",
                    FAILURE_COLOR);
            case APPROVAL -> edgeEmbed(
                    "Approval required", "Recommendation / final confirmation",
                    "A permanent or elevated consequence remains pending until an authorized Admin+ decision.",
                    WARNING_COLOR);
            case STALE -> edgeEmbed(
                    "Stale confirmation", "Final reauthorization",
                    "History/policy changed after the preview was built. Refresh and recalculate the ladder before commit.",
                    WARNING_COLOR);
            case DISCORD_FAILURE -> edgeEmbed(
                    "Discord failure", "Future enforcement result",
                    "Authoritative intent would remain truthful while the Discord side effect is retried/reconciled.",
                    FAILURE_COLOR);
            case PARTIAL -> edgeEmbed(
                    "Partial result", "Future cross-platform result",
                    "Each platform result is shown separately; the system must not claim global success.",
                    WARNING_COLOR);
        };
    }

    private static MessageEmbed edgeEmbed(String title, String stage, String detail, int color) {
        return base(title)
                .setColor(color)
                .addField("Correct workflow stage", stage, false)
                .addField("What staff sees", detail, false)
                .build();
    }

    private static MessageEmbed complete() {
        return base("Preview complete")
                .setDescription("**Preview complete — no moderation action was applied.**")
                .addField("Result", "No Discord moderation REST action, database write, Minecraft action, "
                        + "LiteBans call, persistence mutation, or authority call was made.", false)
                .build();
    }

    private static MessageEmbed simple(String title, String description) {
        return base(title).setDescription(description).build();
    }

    private static EmbedBuilder base(String title) {
        return new EmbedBuilder().setTitle(title).setColor(PREVIEW_COLOR).setFooter(FOOTER);
    }

    private List<ActionRow> components(ModerationUiPreviewModel.Snapshot snapshot) {
        ModerationUiPreviewModel.Screen screen = snapshot.state().screen();
        if (screen == ModerationUiPreviewModel.Screen.OVERVIEW) {
            return overviewComponents(snapshot);
        }
        if (DATA_SCREENS.contains(screen) || screen == ModerationUiPreviewModel.Screen.EDGE_STATE) {
            return List.of(backRow(snapshot));
        }
        return workflowComponents(snapshot);
    }

    private List<ActionRow> workflowComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        ModerationUiPreviewModel.Screen screen = snapshot.state().screen();
        if (screen == ModerationUiPreviewModel.Screen.OFFENSE) {
            return offenseComponents(snapshot);
        }
        if (EnumSet.of(
                ModerationUiPreviewModel.Screen.RECOMMENDATION,
                ModerationUiPreviewModel.Screen.CUSTOM_ACTION,
                ModerationUiPreviewModel.Screen.CUSTOM_SCOPE,
                ModerationUiPreviewModel.Screen.CUSTOM_DURATION).contains(screen)) {
            return selectionComponents(snapshot);
        }
        return outcomeComponents(snapshot);
    }

    private static List<ActionRow> selectionComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return switch (snapshot.state().screen()) {
            case RECOMMENDATION -> recommendationComponents(snapshot);
            case CUSTOM_ACTION -> actionComponents(snapshot);
            case CUSTOM_SCOPE -> scopeComponents(snapshot);
            case CUSTOM_DURATION -> durationComponents(snapshot);
            default -> throw new IllegalArgumentException(
                    "not a preview selection component screen: " + snapshot.state().screen());
        };
    }

    private static List<ActionRow> outcomeComponents(ModerationUiPreviewModel.Snapshot snapshot) {
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
                        Button.danger(id(snapshot, ModerationUiPreviewController.OP_PUNISH, ""), "Punish"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_NAV, "history"), "History"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_NAV, "accounts"), "Accounts"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_NAV, "notes"), "Notes"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_NAV, "cases"), "Cases")
                ),
                ActionRow.of(sampleMenu(snapshot)),
                ActionRow.of(edgeMenu(snapshot))
        );
    }

    private static List<ActionRow> offenseComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        StringSelectMenu menu = StringSelectMenu.create(id(snapshot, ModerationUiPreviewController.OP_OFFENSE, ""))
                .setPlaceholder("Choose offense / reason")
                .setRequiredRange(1, 1)
                .addOptions(OFFENSE_OPTIONS)
                .build();
        return List.of(ActionRow.of(menu), backRow(snapshot));
    }

    private static List<ActionRow> recommendationComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(ActionRow.of(
                Button.success(id(snapshot, ModerationUiPreviewController.OP_APPLY, ""), "Apply Recommendation"),
                Button.secondary(id(snapshot, ModerationUiPreviewController.OP_CUSTOM, ""), "Custom Punishment"),
                Button.secondary(id(snapshot, ModerationUiPreviewController.OP_BACK, ""), "Back")
        ));
    }

    private static List<ActionRow> actionComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(
                ActionRow.of(
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_ACTION, "warn"), "Warning"),
                        Button.primary(id(snapshot, ModerationUiPreviewController.OP_ACTION, "mute"), "Mute"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_ACTION, "kick"), "Kick"),
                        Button.danger(id(snapshot, ModerationUiPreviewController.OP_ACTION, "ban"), "Ban"),
                        Button.primary(id(snapshot, ModerationUiPreviewController.OP_ACTION, "restrict"), "Restrict")
                ),
                backRow(snapshot)
        );
    }

    private static List<ActionRow> scopeComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(
                ActionRow.of(
                        Button.primary(id(snapshot, ModerationUiPreviewController.OP_SCOPE, "discord"), "Discord"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_SCOPE, "minecraft"), "Minecraft"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_SCOPE, "both"), "Both")
                ),
                backRow(snapshot)
        );
    }

    private static List<ActionRow> durationComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        StringSelectMenu menu = StringSelectMenu.create(id(snapshot, ModerationUiPreviewController.OP_DURATION, ""))
                .setPlaceholder("Choose custom punishment duration")
                .setRequiredRange(1, 1)
                .addOptions(DURATION_OPTIONS)
                .build();
        return List.of(ActionRow.of(menu), backRow(snapshot));
    }

    private static List<ActionRow> optionComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        ModerationUiPreviewModel.State state = snapshot.state();
        return List.of(
                ActionRow.of(
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_TOGGLE, "dm"),
                                "DM user: " + onOff(state.dmUser())),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_TOGGLE, "delete"),
                                "Delete message: " + onOff(state.deleteMessage())),
                        Button.success(id(snapshot, ModerationUiPreviewController.OP_REVIEW, ""), "Review")
                ),
                ActionRow.of(
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_EXPLANATION, ""),
                                "Edit explanation"),
                        Button.secondary(id(snapshot, ModerationUiPreviewController.OP_BACK, ""), "Back")
                )
        );
    }

    private static List<ActionRow> confirmComponents(ModerationUiPreviewModel.Snapshot snapshot) {
        return List.of(ActionRow.of(
                Button.danger(id(snapshot, ModerationUiPreviewController.OP_CONFIRM, ""), "Confirm preview"),
                Button.secondary(id(snapshot, ModerationUiPreviewController.OP_BACK, ""), "Back")
        ));
    }

    private static ActionRow backRow(ModerationUiPreviewModel.Snapshot snapshot) {
        return ActionRow.of(Button.secondary(id(snapshot, ModerationUiPreviewController.OP_BACK, ""), "Back"));
    }

    private static StringSelectMenu sampleMenu(ModerationUiPreviewModel.Snapshot snapshot) {
        return StringSelectMenu.create(id(snapshot, ModerationUiPreviewController.OP_SAMPLE, ""))
                .setPlaceholder("Choose deterministic ladder sample")
                .setRequiredRange(1, 1)
                .addOptions(SAMPLE_OPTIONS)
                .build();
    }

    private static StringSelectMenu edgeMenu(ModerationUiPreviewModel.Snapshot snapshot) {
        return StringSelectMenu.create(id(snapshot, ModerationUiPreviewController.OP_EDGE, ""))
                .setPlaceholder("Preview authority / failure states")
                .setRequiredRange(1, 1)
                .addOptions(EDGE_OPTIONS)
                .build();
    }

    private static SelectOption option(String label, String value) {
        return SelectOption.of(label, value.toLowerCase(Locale.ROOT));
    }

    private static String id(ModerationUiPreviewModel.Snapshot snapshot, String operation, String argument) {
        return ModerationUiPreviewController.componentId(snapshot, operation, argument);
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }
}
