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
    private static final int MAX_CUSTOM_REASON = 300;
    private static final int MAX_CUSTOM_DURATION = 40;
    private static final Set<String> SELECTION_OPERATIONS = Set.of(
            "nav", "punish", "action", "scope", "reason", "duration");

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

    ModerationUiPreviewController(int capacity, Duration ttl) {
        this(new ModerationUiPreviewSessionStore(capacity, ttl, Clock.systemUTC(), new SecureRandom()));
    }

    ModerationUiPreviewController(ModerationUiPreviewSessionStore sessions) {
        this.sessions = sessions;
    }

    Result start(long ownerId) {
        return sessions.create(ownerId)
                .map(Result::view)
                .orElseGet(() -> Result.error("The staging preview is busy. Try again shortly."));
    }

    Result interact(long ownerId, String customId, Optional<String> selectedValue) {
        Optional<Token> parsed = parse(customId);
        if (parsed.isEmpty()) {
            return Result.error("That preview control is malformed or no longer supported.");
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
        if (parsed.isEmpty() || !"modal".equals(parsed.get().operation())) {
            return Result.error("That preview form is malformed or no longer supported.");
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
        if (value.length() > 100) {
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
        if (SELECTION_OPERATIONS.contains(token.operation())) {
            return routeSelection(ownerId, token, selectedValue, snapshot);
        }
        return routeCompletion(ownerId, token, selectedValue, snapshot);
    }

    private Result routeSelection(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        return switch (token.operation()) {
            case "nav" -> navigate(ownerId, token, snapshot);
            case "punish" -> transition(ownerId, token, state -> state.withScreen(ModerationUiPreviewModel.Screen.ACTION));
            case "action" -> chooseAction(ownerId, token, snapshot);
            case "scope" -> chooseScope(ownerId, token, snapshot);
            case "reason" -> chooseReason(ownerId, token, selectedValue, snapshot);
            case "duration" -> chooseDuration(ownerId, token, selectedValue, snapshot);
            default -> malformed();
        };
    }

    private Result routeCompletion(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        return switch (token.operation()) {
            case "toggle" -> toggle(ownerId, token, snapshot);
            case "review" -> review(ownerId, token, snapshot);
            case "confirm" -> confirm(ownerId, token, snapshot);
            case "scenario" -> scenario(ownerId, token, selectedValue, snapshot);
            case "back" -> back(ownerId, token, snapshot);
            default -> malformed();
        };
    }

    private Result navigate(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OVERVIEW) {
            return staleFlow();
        }
        Optional<ModerationUiPreviewModel.Screen> destination = navigationScreen(token.argument());
        if (destination.isEmpty()) {
            return malformed();
        }
        return transition(ownerId, token, state -> state.withScreen(destination.get()));
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

    private Result chooseAction(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.ACTION) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.Action action = ModerationUiPreviewModel.Action.parse(token.argument());
            return transition(ownerId, token, state -> state.withAction(action));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result chooseScope(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.SCOPE) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.Scope scope = ModerationUiPreviewModel.Scope.parse(token.argument());
            return transition(ownerId, token, state -> state.withScope(scope));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result chooseReason(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.REASON || selectedValue.isEmpty()) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.Reason reason = ModerationUiPreviewModel.Reason.parse(selectedValue.get());
            if (reason == ModerationUiPreviewModel.Reason.CUSTOM) {
                return customModal(snapshot, "reason", "Custom reason", "Reason",
                        "Describe the moderation reason", MAX_CUSTOM_REASON);
            }
            return transition(ownerId, token, state -> state.withReason(reason.label()));
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
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.DURATION || selectedValue.isEmpty()) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.DurationChoice duration =
                    ModerationUiPreviewModel.DurationChoice.parse(selectedValue.get());
            if (duration == ModerationUiPreviewModel.DurationChoice.CUSTOM) {
                return customModal(snapshot, "duration", "Custom duration", "Duration",
                        "Example: 5d 12h", MAX_CUSTOM_DURATION);
            }
            return transition(ownerId, token, state -> state.withDuration(duration.label()));
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

    private Result scenario(
            long ownerId,
            Token token,
            Optional<String> selectedValue,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.OVERVIEW || selectedValue.isEmpty()) {
            return staleFlow();
        }
        try {
            ModerationUiPreviewModel.Scenario scenario = ModerationUiPreviewModel.Scenario.parse(selectedValue.get());
            return transition(ownerId, token, state -> state.withScenario(scenario));
        } catch (IllegalArgumentException exception) {
            return malformed();
        }
    }

    private Result back(long ownerId, Token token, ModerationUiPreviewModel.Snapshot snapshot) {
        Optional<ModerationUiPreviewModel.Screen> destination = backDestination(snapshot.state());
        if (destination.isEmpty()) {
            return staleFlow();
        }
        return transition(ownerId, token, state -> state.withScreen(destination.get()));
    }

    private static Optional<ModerationUiPreviewModel.Screen> backDestination(ModerationUiPreviewModel.State state) {
        if (state.screen() == ModerationUiPreviewModel.Screen.OPTIONS) {
            ModerationUiPreviewModel.Screen destination = state.action() != null && state.action().durationSupported()
                    ? ModerationUiPreviewModel.Screen.DURATION
                    : ModerationUiPreviewModel.Screen.REASON;
            return Optional.of(destination);
        }
        return switch (state.screen()) {
            case ACCOUNTS, HISTORY, NOTES, CASES, SCENARIO, ACTION ->
                    Optional.of(ModerationUiPreviewModel.Screen.OVERVIEW);
            case SCOPE -> Optional.of(ModerationUiPreviewModel.Screen.ACTION);
            case REASON -> Optional.of(ModerationUiPreviewModel.Screen.SCOPE);
            case DURATION -> Optional.of(ModerationUiPreviewModel.Screen.REASON);
            case CONFIRM -> Optional.of(ModerationUiPreviewModel.Screen.OPTIONS);
            default -> Optional.empty();
        };
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
            case "reason" -> applyCustomReason(ownerId, token, normalized, snapshot);
            case "duration" -> applyCustomDuration(ownerId, token, normalized, snapshot);
            default -> malformed();
        };
    }

    private Result applyCustomReason(
            long ownerId,
            Token token,
            String value,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.REASON || value.length() > MAX_CUSTOM_REASON) {
            return staleFlow();
        }
        return transition(ownerId, token, state -> state.withReason("Custom — " + value));
    }

    private Result applyCustomDuration(
            long ownerId,
            Token token,
            String value,
            ModerationUiPreviewModel.Snapshot snapshot
    ) {
        if (snapshot.state().screen() != ModerationUiPreviewModel.Screen.DURATION || value.length() > MAX_CUSTOM_DURATION) {
            return staleFlow();
        }
        return transition(ownerId, token, state -> state.withDuration("Custom — " + value));
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
        String id = componentId(snapshot, "modal", kind);
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
        if (customId == null || customId.length() > 100) {
            return Optional.empty();
        }
        String[] parts = customId.split(":", 5);
        if (parts.length < 4 || !PREFIX.equals(parts[0]) || parts[1].isBlank()) {
            return Optional.empty();
        }
        try {
            int revision = Integer.parseInt(parts[2]);
            if (revision < 0) {
                return Optional.empty();
            }
            String operation = parts[3].toLowerCase(Locale.ROOT);
            String argument = parts.length == 5 ? parts[4].toLowerCase(Locale.ROOT) : "";
            return Optional.of(new Token(parts[1], revision, operation, argument));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Result malformed() {
        return Result.error("That preview control is malformed or no longer supported.");
    }

    private static Result staleFlow() {
        return Result.error("That control does not match the current preview step. Use the newest preview screen.");
    }
}
