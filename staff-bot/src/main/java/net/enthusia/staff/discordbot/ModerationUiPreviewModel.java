package net.enthusia.staff.discordbot;

import java.util.Locale;

/** Allowlisted, fake-only state used by the staging moderation UI preview. */
final class ModerationUiPreviewModel {
    static final String SAMPLE_DISCORD = "RiverAsh (sample Discord user)";
    static final String SAMPLE_MINECRAFT = "RiverAshMC (sample main account)";
    static final String NOT_SELECTED = "Not selected";
    static final String NOT_APPLICABLE = "Not applicable";
    static final String PERMANENT = "Permanent";

    private ModerationUiPreviewModel() {
    }

    enum Screen {
        OVERVIEW,
        ACCOUNTS,
        HISTORY,
        NOTES,
        CASES,
        OFFENSE,
        RECOMMENDATION,
        CUSTOM_ACTION,
        CUSTOM_SCOPE,
        CUSTOM_DURATION,
        OPTIONS,
        CONFIRM,
        EDGE_STATE,
        COMPLETE
    }

    enum Action {
        WARN("Warning", false),
        MUTE("Mute", true),
        KICK("Kick", false),
        BAN("Ban", true),
        RESTRICT("Restrict", true);

        private final String label;
        private final boolean durationSupported;

        Action(String label, boolean durationSupported) {
            this.label = label;
            this.durationSupported = durationSupported;
        }

        String label() {
            return label;
        }

        boolean durationSupported() {
            return durationSupported;
        }

        static Action parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    enum Scope {
        DISCORD("Discord"),
        MINECRAFT("Minecraft"),
        BOTH("Both");

        private final String label;

        Scope(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Scope parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    enum Offense {
        SPAM("Spam / flooding"),
        HARASSMENT("Harassment"),
        HATE_SLURS("Hate / slurs"),
        ADVERTISING("Advertising / unwanted invites"),
        CHEATING("Cheating"),
        OTHER_CUSTOM("Other / custom");

        private final String label;

        Offense(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Offense parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    enum DurationChoice {
        MIN_30("30m"),
        HOUR_2("2h"),
        DAY_1("1d"),
        DAY_3("3d"),
        DAY_7("7d"),
        WEEK_2("2w"),
        MONTH_1("1mo"),
        PERMANENT(ModerationUiPreviewModel.PERMANENT),
        CUSTOM("Custom");

        private final String label;

        DurationChoice(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static DurationChoice parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    enum SampleScenario {
        FIRST_MINOR("First / minor offense", "Choose Spam / flooding to see a light first-step recommendation."),
        REPEAT("Repeat offense", "Choose Spam / flooding to see two relevant priors escalate the ladder."),
        SEVERE("Severe offense", "Choose Hate / slurs to see severity raise the starting ladder step."),
        ADMIN_ESCALATION("Admin-level escalation", "Choose Hate / slurs to preview a permanent recommendation and approval."),
        CUSTOM_OVERRIDE("Custom override", "Choose Harassment, then deliberately use Custom Punishment."),
        UNRELATED_HISTORY("Unrelated history", "Choose Spam / flooding to see unrelated records excluded from progression.");

        private final String label;
        private final String hint;

        SampleScenario(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        String label() {
            return label;
        }

        String hint() {
            return hint;
        }

        static SampleScenario parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    enum EdgeState {
        INSUFFICIENT("Insufficient authority"),
        PROTECTED("Protected target"),
        APPROVAL("Approval required"),
        STALE("Stale confirmation"),
        DISCORD_FAILURE("Discord failure"),
        PARTIAL("Partial result");

        private final String label;

        EdgeState(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static EdgeState parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    record Recommendation(
            Action action,
            Scope scope,
            String duration,
            int totalHistory,
            int relevantHistory,
            int ladderStep,
            String explanation,
            String approvalRequirement
    ) {
        String punishmentSummary() {
            String durationSuffix = NOT_APPLICABLE.equals(duration) ? "" : " — " + duration;
            return action.label() + durationSuffix + " · " + scope.label();
        }
    }

    record State(
            Screen screen,
            SampleScenario sampleScenario,
            Offense offense,
            String offenseLabel,
            Recommendation recommendation,
            Action actualAction,
            Scope actualScope,
            String actualDuration,
            String explanation,
            boolean dmUser,
            boolean deleteMessage,
            boolean overridden,
            EdgeState edgeState
    ) {
        static State initial() {
            return resetForScenario(SampleScenario.REPEAT);
        }

        private static State resetForScenario(SampleScenario scenario) {
            return new State(
                    Screen.OVERVIEW,
                    scenario,
                    null,
                    NOT_SELECTED,
                    null,
                    null,
                    null,
                    NOT_APPLICABLE,
                    "",
                    true,
                    false,
                    false,
                    null
            );
        }

        State withScreen(Screen next) {
            return new State(
                    next, sampleScenario, offense, offenseLabel, recommendation, actualAction, actualScope,
                    actualDuration, explanation, dmUser, deleteMessage, overridden, edgeState);
        }

        State withSampleScenario(SampleScenario next) {
            return resetForScenario(next);
        }

        State beginPunish() {
            State reset = resetForScenario(sampleScenario);
            return reset.withScreen(Screen.OFFENSE);
        }

        State withRecommendation(Offense nextOffense, String nextLabel, Recommendation nextRecommendation) {
            return new State(
                    Screen.RECOMMENDATION,
                    sampleScenario,
                    nextOffense,
                    nextLabel,
                    nextRecommendation,
                    null,
                    null,
                    NOT_APPLICABLE,
                    nextRecommendation.explanation(),
                    dmUser,
                    deleteMessage,
                    false,
                    null
            );
        }

        State applyRecommendation() {
            return new State(
                    Screen.OPTIONS,
                    sampleScenario,
                    offense,
                    offenseLabel,
                    recommendation,
                    recommendation.action(),
                    recommendation.scope(),
                    recommendation.duration(),
                    explanation,
                    dmUser,
                    deleteMessage,
                    false,
                    null
            );
        }

        State beginCustom() {
            return new State(
                    Screen.CUSTOM_ACTION,
                    sampleScenario,
                    offense,
                    offenseLabel,
                    recommendation,
                    null,
                    null,
                    NOT_APPLICABLE,
                    explanation,
                    dmUser,
                    deleteMessage,
                    true,
                    null
            );
        }

        State withCustomAction(Action next) {
            String duration = next.durationSupported() ? NOT_SELECTED : NOT_APPLICABLE;
            return new State(
                    Screen.CUSTOM_SCOPE, sampleScenario, offense, offenseLabel, recommendation, next, null,
                    duration, explanation, dmUser, deleteMessage, true, null);
        }

        State withCustomScope(Scope next) {
            Screen nextScreen = actualAction.durationSupported() ? Screen.CUSTOM_DURATION : Screen.OPTIONS;
            return new State(
                    nextScreen, sampleScenario, offense, offenseLabel, recommendation, actualAction, next,
                    actualDuration, explanation, dmUser, deleteMessage, true, null);
        }

        State withCustomDuration(String next) {
            return new State(
                    Screen.OPTIONS, sampleScenario, offense, offenseLabel, recommendation, actualAction, actualScope,
                    next, explanation, dmUser, deleteMessage, true, null);
        }

        State withExplanation(String next) {
            return new State(
                    screen, sampleScenario, offense, offenseLabel, recommendation, actualAction, actualScope,
                    actualDuration, next, dmUser, deleteMessage, overridden, edgeState);
        }

        State toggleDm() {
            return new State(
                    screen, sampleScenario, offense, offenseLabel, recommendation, actualAction, actualScope,
                    actualDuration, explanation, !dmUser, deleteMessage, overridden, edgeState);
        }

        State toggleDelete() {
            return new State(
                    screen, sampleScenario, offense, offenseLabel, recommendation, actualAction, actualScope,
                    actualDuration, explanation, dmUser, !deleteMessage, overridden, edgeState);
        }

        State withEdgeState(EdgeState next) {
            return new State(
                    Screen.EDGE_STATE, sampleScenario, offense, offenseLabel, recommendation, actualAction, actualScope,
                    actualDuration, explanation, dmUser, deleteMessage, overridden, next);
        }

        boolean followedRecommendation() {
            if (overridden || recommendation == null || actualAction == null || actualScope == null) {
                return false;
            }
            return actualAction == recommendation.action()
                    && actualScope == recommendation.scope()
                    && actualDuration.equals(recommendation.duration());
        }

        String approvalSummary() {
            if (PERMANENT.equals(actualDuration)
                    && (actualAction == Action.BAN || actualAction == Action.MUTE || actualAction == Action.RESTRICT)) {
                return "Required — permanent ban/mute/restriction requires Admin+ authority for the selected scope.";
            }
            if (actualScope == Scope.BOTH) {
                return "Separate authority checks — each platform would be reauthorized before commit.";
            }
            return "No higher approval shown for this sample selection.";
        }

        String selectedPunishmentSummary() {
            if (actualAction == null || actualScope == null) {
                return NOT_SELECTED;
            }
            String durationSuffix = NOT_APPLICABLE.equals(actualDuration) ? "" : " — " + actualDuration;
            return actualAction.label() + durationSuffix + " · " + actualScope.label();
        }
    }

    record Snapshot(String sessionId, int revision, State state) {
    }
}
