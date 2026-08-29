package net.enthusia.staff.discordbot;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Orchestrates the fake moderation preview. No moderation or persistence service is reachable from this type. */
final class ModerationUiPreviewController {
    private static final String PREFIX = "pui";
    static final String OP_NAV = "nav";
    static final String OP_PUNISH = "punish";
    static final String OP_OFFENSE = "offense";
    static final String OP_APPLY = "apply";
    static final String OP_CUSTOM = "custom";
    static final String OP_ACTION = "action";
    static final String OP_SCOPE = "scope";
    static final String OP_DURATION = "duration";
    static final String OP_TOGGLE = "toggle";
    static final String OP_EXPLANATION = "explain";
    static final String OP_REVIEW = "review";
    static final String OP_CONFIRM = "confirm";
    static final String OP_SAMPLE = "sample";
    static final String OP_EDGE = "edge";
    static final String OP_BACK = "back";
    private static final String OP_MODAL = "modal";
    private static final int MAX_CUSTOM_OFFENSE = 180;
    private static final int MAX_CUSTOM_DURATION = 40;
    private static final int MAX_EXPLANATION = 300;
    private static final int MAX_COMPONENT_ID_LENGTH = 100;
    private static final int MIN_TOKEN_PARTS = 4;
    private static final int TOKEN_PARTS_WITH_ARGUMENT = 5;
    private static final Set<String> ENTRY_OPERATIONS = Set.of(
            OP_NAV, OP_PUNISH, OP_OFFENSE, OP_SAMPLE, OP_EDGE);
    private static final Set<String> SELECTION_OPERATIONS = Set.of(
            OP_APPLY, OP_CUSTOM, OP_ACTION, OP_SCOPE, OP_DURATION);

    enum ResultType {
        VIEW,
        MODAL,
        ERROR
    }

    record ModalSpec(String customId, String title, String label, String placeholder, int maxLength) {
    }

    record Result(
            ResultType type,
            ModerationUiPreviewModel.Snapshot snapshot,
            ModalSpec modal,
            String message
    ) {
        static Result view(ModerationUiPreviewModel.Snapshot snapshot) {
            return new Result(ResultType.VIEW, snapshot, null, "");
        }

        static Result modal(ModerationUiPreviewModel.Snapshot snapshot, ModalSpec modal) {
            return new Result(ResultType.MODAL, snapshot, modal, "");
        }

        static Result error(String message) {
            return new Result(ResultType.ERROR, null, null, message);
        }
    }

    private record Token(String sessionId, int revision, String operation, String argument) {
    }

    private final ModerationUiPreviewSessionStore sessions;
    private final ModerationUiPreviewPolicy policy;

    ModerationUiPreviewController(int capacity, Duration ttl) {
        this(new ModerationUiPreviewSessionStore(capacity, ttl, Clock.systemUTC(), new SecureRandom()));
    }

    ModerationUiPreviewController(ModerationUiPreviewSessionStore sessions) {
        this.sessions = sessions;
        this.policy = new ModerationUiPreviewPolicy();
    }

    Result start(long ownerId) {
        return sessions.create(ownerId)
                .map(Result::view)
                .orElseGet(() -> Result.error("The staging preview is busy. Try again shortly."));
    }

    Result interact(long ownerId, String customId, Optional<String> selectedValue) {
        Optional<Token> parsed = parse(customId);
        if (parsed.isEmpty()) {
            return malformed();
        }
        Token token = parsed.get();
        ModerationUiPreviewSessionStore.Access access = sessions.inspect(
                token.sessionId(), ownerId, token.revision());
        if (access.status() != ModerationUiPreviewSessionStore.AccessStatus.OK) {
            return accessError(access.status());
        }
        return route(ownerId, token, selectedValue, access.snapshot());
    }

    Result submitModal(long ownerId, String customId, String input) {
        Optional<Token> parsed = parse(customId);
        if (parsed.isEmpty() || !OP_MODAL.equals(parsed.get().operation())) {
            return malformed();
        }
        Token token = parsed.get();
        ModerationUiPreviewSessionStore.Access access = sessions.inspect(
                token.sessionId(), ownerId, token.revision());
        if (access.status() != ModerationUiPreviewSessionStore.AccessStatus.OK) {
            return accessError(access.status());
        }
        return applyModal(ownerId, token, input, access.snapshot());
    }

    static String componentId(ModerationUiPreviewModel.Snapshot snapshot, String operation, String argument) {
        String suffix = argument == null || argument.isBlank() ? "" : ":" + argument;
        String value = PREFIX + ":" + snapshot.sessionId() + ":" + snapshot.revision() + ":" + operation + suffix;
        if (value.length() > MAX_COMPONENT_ID_LENGTH) {
            throw new IllegalArgumentException("preview component id exceeds Discord limit");
        }
        return value;
    }

    private Result route(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (ENTRY_OPERATIONS.contains(token.operation())) {
            return routeEntry(ownerId, token, selectedValue, snapshot);
        }
        if (SELECTION_OPERATIONS.contains(token.operation())) {
            return routeSelection(ownerId, token, selectedValue, snapshot);
        }
        return routeCompletion(ownerId, token, snapshot);
    }

    private Result routeEntry(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        return switch (token.operation()) {
            case OP_NAV -> navigate(ownerId, token, snapshot);
            case OP_PUNISH -> punish(ownerId, token, snapshot);
            case OP_OFFENSE -> chooseOffense(ownerId, token, selectedValue, snapshot);
            case OP_SAMPLE -> sample(ownerId, token, selectedValue, snapshot);
            case OP_EDGE -> edge(ownerId, token, selectedValue, snapshot);
            default -> malformed();
        };
    }

    private Result routeSelection(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        return switch (token.operation()) {
            case OP_APPLY -> applyRecommendation(ownerId, token, snapshot);
            case OP_CUSTOM -> beginCustom(ownerId, token, snapshot);
            case OP_ACTION -> chooseAction(ownerId, token, snapshot);
            case OP_SCOPE -> chooseScope(ownerId, token, snapshot);
            case OP_DURATION -> chooseDuration(ownerId, token, selectedValue, snapshot);
            default -> malformed();
        };
    }

    private Result routeCompletion(
            long ownerId,
            Token token,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        return switch (token.operation()) {
            case OP_TOGGLE -> toggle(ownerId, token, snapshot);
            case OP_EXPLANATION -> editExplanation(snapshot);
            case OP_REVIEW -> review(ownerId, token, snapshot);
            case OP_CONFIRM -> confirm(ownerId, token, snapshot);
            case OP_BACK -> back(ownerId, token, snapshot);
            default -> malformed();
        };
    }

    private Result navigate(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OVERVIEW) {
            return staleFlow();
        }
        Optional<ModerationUiPreviewModel.Screen> destination = navigationScreen(token.argument());
        return destination.isEmpty()
                ? malformed()
                : transition(ownerId, token, state -> state.withScreen(destination.get()));
    }

    private static Optional<ModerationUiPreviewModel.Screen> navigationScreen(String argument) {
        return switch (argument) {
            case "accounts" -> Optional.of(ModerationUiPreviewModel.Screen.ACCOUNTS);
            case "history" -> Optional.of(ModerationUiPreviewModel.Screen.HISTORY);
            case "notes" -> Optional.of(ModerationUiPreviewModel.Screen.NOTES);
            case "cases" -> Optional.of(ModerationUiPreviewModel.Screen.CASES);
            default -> Optional.empty();
        };
    }

    private Result punish(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OVERVIEW) {
            return staleFlow();
        }
        return transition(ownerId, token, ModerationUiPreviewModel.State::beginPunish);
    }

    private Result chooseOffense(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OFFENSE || selectedValue.isEmpty()) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.Offense offense = ModerationUiPreviewModel.Offense.parse(selectedValue.get());
            if (offense == ModerationUiPreviewModel.Offense.OTHER_CUSTOM) {
                return customModal(snapshot, OP_OFFENSE, "Custom offense", "Offense / reason",
                        "Describe the offense for this preview", MAX_CUSTOM_OFFENSE);
            }
            return recommend(ownerId, token, offense, offense.label(), snapshot.state().sampleScenario());
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result recommend(
            long ownerId,
            Token token,
            ModerationUiPreviewModel.Offense offense,
            String label,
            ModerationUiPreviewModel.SampleScenario scenario
    ) {
        ModerationUiPreviewModel.Recommendation recommendation = policy.evaluate(scenario, offense);
        return transition(ownerId, token, state -> state.withRecommendation(offense, label, recommendation));
    }

    private Result applyRecommendation(
            long ownerId,
            Token token,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.RECOMMENDATION) {
            return staleFlow();
        }
        return transition(ownerId, token, ModerationUiPreviewModel.State::applyRecommendation);
    }

    private Result beginCustom(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.RECOMMENDATION) {
            return staleFlow();
        }
        return transition(ownerId, token, ModerationUiPreviewModel.State::beginCustom);
    }

    private Result chooseAction(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.CUSTOM_ACTION) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.Action action = ModerationUiPreviewModel.Action.parse(token.argument());
            return transition(ownerId, token, state -> state.withCustomAction(action));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result chooseScope(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.CUSTOM_SCOPE) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.Scope scope = ModerationUiPreviewModel.Scope.parse(token.argument());
            return transition(ownerId, token, state -> state.withCustomScope(scope));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result chooseDuration(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.CUSTOM_DURATION || selectedValue.isEmpty()) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.DurationChoice duration =
                    ModerationUiPreviewModel.DurationChoice.parse(selectedValue.get());
            if (duration == ModerationUiPreviewModel.DurationChoice.CUSTOM) {
                return customModal(snapshot, OP_DURATION, "Custom duration", "Duration",
                        "Example: 5d 12h", MAX_CUSTOM_DURATION);
            }
            return transition(ownerId, token, state -> state.withCustomDuration(duration.label()));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result toggle(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OPTIONS) {
            return staleFlow();
        }
        Optional<UnaryOperator<ModerationUiPreviewModel.State>> mutation = toggleMutation(token.argument());
        return mutation.isEmpty() ? malformed() : transition(ownerId, token, mutation.get());
    }

    private static Optional<UnaryOperator<ModerationUiPreviewModel.State>> toggleMutation(String argument) {
        return switch (argument) {
            case "dm" -> Optional.of(ModerationUiPreviewModel.State::toggleDm);
            case "delete" -> Optional.of(ModerationUiPreviewModel.State::toggleDelete);
            default -> Optional.empty();
        };
    }

    private Result editExplanation(ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OPTIONS) {
            return staleFlow();
        }
        return customModal(snapshot, OP_EXPLANATION, "Edit explanation", "Explanation / context",
                "Optional staff-facing context for this preview", MAX_EXPLANATION);
    }

    private Result review(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OPTIONS) {
            return staleFlow();
        }
        return transition(ownerId, token, state -> state.withScreen(ModerationUiPreviewModel.Screen.CONFIRM));
    }

    private Result confirm(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.CONFIRM) {
            return staleFlow();
        }
        return transition(ownerId, token, state -> state.withScreen(ModerationUiPreviewModel.Screen.COMPLETE));
    }

    private Result sample(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OVERVIEW || selectedValue.isEmpty()) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.SampleScenario scenario =
                    ModerationUiPreviewModel.SampleScenario.parse(selectedValue.get());
            return transition(ownerId, token, state -> state.withSampleScenario(scenario));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result edge(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OVERVIEW || selectedValue.isEmpty()) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.EdgeState edgeState =
                    ModerationUiPreviewModel.EdgeState.parse(selectedValue.get());
            return transition(ownerId, token, state -> state.withEdgeState(edgeState));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result back(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        Optional<ModerationUiPreviewModel.Screen> destination = backDestination(snapshot.state());
        return destination.isEmpty()
                ? staleFlow()
                : transition(ownerId, token, state -> state.withScreen(destination.get()));
    }

    private static Optional<ModerationUiPreviewModel.Screen> backDestination(ModerationUiPreviewModel.State state) {
        if (state.screen() == ModerationUiPreviewModel.Screen.OPTIONS) {
            return Optional.of(optionsBackDestination(state));
        }
        return fixedBackDestination(state.screen());
    }

    private static Optional<ModerationUiPreviewModel.Screen> fixedBackDestination(
            ModerationUiPreviewModel.Screen screen
    ) {
        return switch (screen) {
            case ACCOUNTS, HISTORY, NOTES, CASES, EDGE_STATE, OFFENSE ->
                    Optional.of(ModerationUiPreviewModel.Screen.OVERVIEW);
            case RECOMMENDATION -> Optional.of(ModerationUiPreviewModel.Screen.OFFENSE);
            case CUSTOM_ACTION -> Optional.of(ModerationUiPreviewModel.Screen.RECOMMENDATION);
            case CUSTOM_SCOPE -> Optional.of(ModerationUiPreviewModel.Screen.CUSTOM_ACTION);
            case CUSTOM_DURATION -> Optional.of(ModerationUiPreviewModel.Screen.CUSTOM_SCOPE);
            case CONFIRM -> Optional.of(ModerationUiPreviewModel.Screen.OPTIONS);
            default -> Optional.empty();
        };
    }

    private static ModerationUiPreviewModel.Screen optionsBackDestination(ModerationUiPreviewModel.State state) {
        if (!state.overridden()) {
            return ModerationUiPreviewModel.Screen.RECOMMENDATION;
        }
        return state.actualAction() != null && state.actualAction().durationSupported()
                ? ModerationUiPreviewModel.Screen.CUSTOM_DURATION
                : ModerationUiPreviewModel.Screen.CUSTOM_SCOPE;
    }

    private Result applyModal(
            long ownerId,
            Token token,
            String input,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isEmpty()) {
            return Result.error("Enter a value before submitting the preview form.");
        }
        return switch (token.argument()) {
            case OP_OFFENSE -> applyCustomOffense(ownerId, token, normalized, snapshot);
            case OP_DURATION -> applyCustomDuration(ownerId, token, normalized, snapshot);
            case OP_EXPLANATION -> applyExplanation(ownerId, token, normalized, snapshot);
            default -> malformed();
        };
    }

    private Result applyCustomOffense(
            long ownerId,
            Token token,
            String value,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OFFENSE
                || value.length() > MAX_CUSTOM_OFFENSE) {
            return staleFlow();
        }
        return recommend(
                ownerId,
                token,
                ModerationUiPreviewModel.Offense.OTHER_CUSTOM,
                "Other / custom — " + value,
                snapshot.state().sampleScenario()
        );
    }

    private Result applyCustomDuration(
            long ownerId,
            Token token,
            String value,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.CUSTOM_DURATION
                || value.length() > MAX_CUSTOM_DURATION) {
            return staleFlow();
        }
        return transition(ownerId, token, state -> state.withCustomDuration("Custom — " + value));
    }

    private Result applyExplanation(
            long ownerId,
            Token token,
            String value,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OPTIONS || value.length() > MAX_EXPLANATION) {
            return staleFlow();
        }
        return transition(ownerId, token, state -> state.withExplanation(value));
    }

    private Result transition(long ownerId, Token token, UnaryOperator<ModerationUiPreviewModel.State> mutation) {
        ModerationUiPreviewSessionStore.Access access = sessions.update(
                token.sessionId(), ownerId, token.revision(), mutation);
        return access.status() == ModerationUiPreviewSessionStore.AccessStatus.OK
                ? Result.view(access.snapshot())
                : accessError(access.status());
    }

    private static Result customModal(
            ModerationUiPreviewModel.Snapshot snapshot,
            String kind,
            String title,
            String label,
            String placeholder,
            int maxLength
    ) {
        String id = componentId(snapshot, OP_MODAL, kind);
        return Result.modal(snapshot, new ModalSpec(id, title, label, placeholder, maxLength));
    }

    private static Result accessError(ModerationUiPreviewSessionStore.AccessStatus status) {
        return switch (status) {
            case WRONG_OWNER -> Result.error("This preview belongs to another staff member.");
            case EXPIRED, MISSING -> Result.error("This preview expired. Run /moderate-preview to start a fresh session.");
            case STALE -> Result.error("This preview control is stale. Use the newest preview screen.");
            case COMPLETE -> Result.error("This preview is already complete. No moderation action was applied.");
            case OK -> throw new IllegalStateException("OK is not an error status");
        };
    }

    private static Optional<Token> parse(String customId) {
        if (!validComponentId(customId)) {
            return Optional.empty();
        }
        String[] parts = customId.split(":", TOKEN_PARTS_WITH_ARGUMENT);
        if (!validTokenParts(parts)) {
            return Optional.empty();
        }
        return parseTokenParts(parts);
    }

    private static boolean validComponentId(String customId) {
        return customId != null && customId.length() <= MAX_COMPONENT_ID_LENGTH;
    }

    private static boolean validTokenParts(String[] parts) {
        return parts.length >= MIN_TOKEN_PARTS && PREFIX.equals(parts[0]) && !parts[1].isBlank();
    }

    private static Optional<Token> parseTokenParts(String[] parts) {
        try {
            int revision = Integer.parseInt(parts[2]);
            if (revision < 0) {
                return Optional.empty();
            }
            String operation = parts[3].toLowerCase(Locale.ROOT);
            return Optional.of(new Token(parts[1], revision, operation, tokenArgument(parts)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String tokenArgument(String[] parts) {
        return parts.length == TOKEN_PARTS_WITH_ARGUMENT ? parts[4].toLowerCase(Locale.ROOT) : "";
    }

    private static Result malformed() {
        return Result.error("That preview control is malformed or no longer supported.");
    }

    private static Result staleFlow() {
        return Result.error("That control does not match the current preview step. Use the newest preview screen.");
    }
}
