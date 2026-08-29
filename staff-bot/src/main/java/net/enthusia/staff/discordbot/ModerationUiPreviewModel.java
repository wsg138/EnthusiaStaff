package net.enthusia.staff.discordbot;

import java.util.Locale;

/** Allowlisted, fake-only state used by the staging moderation UI preview. */
final class ModerationUiPreviewModel {
    static final String SAMPLE_DISCORD = "RiverAsh (sample Discord user)";
    static final String SAMPLE_MINECRAFT = "RiverAshMC (sample main account)";
    private static final String NOT_SELECTED = "Not selected";
    private static final String NOT_APPLICABLE = "Not applicable";
    private static final String PERMANENT = "Permanent";

    private ModerationUiPreviewModel() {
    }

    enum Screen {
        OVERVIEW,
        ACCOUNTS,
        HISTORY,
        NOTES,
        CASES,
        ACTION,
        SCOPE,
        REASON,
        DURATION,
        OPTIONS,
        CONFIRM,
        SCENARIO,
        COMPLETE
    }

    enum Action {
        WARN("Warn", false),
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

    enum Reason {
        SPAM("Spam / flooding"),
        HARASSMENT("Harassment"),
        SEVERE_ABUSE("Hate / severe abuse"),
        MALICIOUS_LINK("Malicious link"),
        EVASION("Punishment evasion"),
        CUSTOM("Custom reason");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Reason parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    enum DurationChoice {
        MIN_30("30m"),
        HOUR_2("2h"),
        DAY_3("3d"),
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

    enum Scenario {
        INSUFFICIENT("Insufficient authority"),
        PROTECTED("Protected / equal-or-higher target"),
        APPROVAL("Approval required"),
        STALE("Expired / stale confirmation"),
        DISCORD_FAILURE("Discord API failure"),
        PARTIAL("Partial result");

        private final String label;

        Scenario(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Scenario parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    record State(
            Screen screen,
            Action action,
            Scope scope,
            String reason,
            String duration,
            boolean dmUser,
            boolean deleteMessage,
            Scenario scenario
    ) {
        static State initial() {
            return new State(Screen.OVERVIEW, null, null, NOT_SELECTED, NOT_APPLICABLE, true, false, null);
        }

        State withScreen(Screen next) {
            return new State(next, action, scope, reason, duration, dmUser, deleteMessage, scenario);
        }

        State withAction(Action next) {
            String nextDuration = next.durationSupported() ? NOT_SELECTED : NOT_APPLICABLE;
            return new State(Screen.SCOPE, next, null, NOT_SELECTED, nextDuration, dmUser, deleteMessage, null);
        }

        State withScope(Scope next) {
            return new State(Screen.REASON, action, next, NOT_SELECTED, duration, dmUser, deleteMessage, null);
        }

        State withReason(String next) {
            Screen nextScreen = action.durationSupported() ? Screen.DURATION : Screen.OPTIONS;
            return new State(nextScreen, action, scope, next, duration, dmUser, deleteMessage, null);
        }

        State withDuration(String next) {
            return new State(Screen.OPTIONS, action, scope, reason, next, dmUser, deleteMessage, null);
        }

        State toggleDm() {
            return new State(screen, action, scope, reason, duration, !dmUser, deleteMessage, scenario);
        }

        State toggleDelete() {
            return new State(screen, action, scope, reason, duration, dmUser, !deleteMessage, scenario);
        }

        State withScenario(Scenario next) {
            return new State(Screen.SCENARIO, action, scope, reason, duration, dmUser, deleteMessage, next);
        }

        String approvalSummary() {
            boolean permanentElevated = PERMANENT.equals(duration)
                    && (action == Action.BAN || action == Action.MUTE || action == Action.RESTRICT);
            if (permanentElevated) {
                return "Required — permanent ban/mute/restriction requires Admin+ authority for the selected platform scope.";
            }
            if (scope == Scope.BOTH) {
                return "Separate authority checks — each platform would be reauthorized before commit.";
            }
            return "No higher approval shown for this sample selection.";
        }
    }

    record Snapshot(String sessionId, int revision, State state) {
    }
}
