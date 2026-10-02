package net.enthusia.staff.domain.commandbridge;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Fail-closed target and command allowlist. This policy has no Discord-role input. */
public final class CommandBridgePolicy {
    private static final Pattern TARGET = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9][a-z0-9:_-]{0,63}");
    private static final int NO_SEPARATOR = -1;
    private static final char ARGUMENT_SEPARATOR = ' ';

    private final Set<String> targetServers;
    private final Map<String, CommandBridgeRule> rules;

    public CommandBridgePolicy(Set<String> targetServers, Collection<CommandBridgeRule> rules) {
        if (targetServers == null || rules == null || targetServers.stream().anyMatch(this::invalidTarget)) {
            throw new IllegalArgumentException("command bridge policy is invalid");
        }
        this.targetServers = Set.copyOf(targetServers);
        this.rules = index(rules);
    }

    public Decision evaluate(CommandBridgeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("command bridge request is required");
        }
        ParsedCommand parsed = parse(request.command());
        if (parsed == null) {
            return Decision.rejected(Status.MALFORMED_COMMAND, "invalid");
        }
        if (!targetServers.contains(request.targetServer())) {
            return Decision.rejected(Status.INVALID_SERVER, parsed.commandName());
        }
        CommandBridgeRule rule = rules.get(parsed.commandName());
        if (rule == null) {
            return Decision.rejected(Status.UNSUPPORTED_COMMAND, parsed.commandName());
        }
        if (parsed.argumentCount() > rule.maximumArguments()) {
            return Decision.rejected(Status.COMMAND_POLICY_REJECTED, parsed.commandName());
        }
        return Decision.accepted(parsed.commandName(), parsed.normalized(), rule);
    }

    private boolean invalidTarget(String target) {
        return target == null || !TARGET.matcher(target).matches();
    }

    private static Map<String, CommandBridgeRule> index(Collection<CommandBridgeRule> configured) {
        Map<String, CommandBridgeRule> indexed = new HashMap<>();
        for (CommandBridgeRule rule : configured) {
            if (rule == null || indexed.putIfAbsent(rule.commandName(), rule) != null) {
                throw new IllegalArgumentException("command bridge rules must be unique and non-null");
            }
        }
        return Map.copyOf(indexed);
    }

    private static ParsedCommand parse(String command) {
        if (command == null || command.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        normalized = normalized.replaceAll(" +", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        int separator = normalized.indexOf(ARGUMENT_SEPARATOR);
        String name = (separator == NO_SEPARATOR ? normalized : normalized.substring(0, separator)).toLowerCase(Locale.ROOT);
        if (!COMMAND_NAME.matcher(name).matches()) {
            return null;
        }
        return new ParsedCommand(name, normalized, argumentCount(normalized));
    }

    private static int argumentCount(String command) {
        int count = 0;
        for (int index = 0; index < command.length(); index++) {
            if (command.charAt(index) == ARGUMENT_SEPARATOR) {
                count++;
            }
        }
        return count;
    }

    private record ParsedCommand(String commandName, String normalized, int argumentCount) {
    }

    public record Decision(
            Status status,
            String commandName,
            String normalizedCommand,
            CommandBridgeRule rule
    ) {
        private static Decision accepted(String commandName, String normalizedCommand, CommandBridgeRule rule) {
            return new Decision(Status.ACCEPTED, commandName, normalizedCommand, rule);
        }

        private static Decision rejected(Status status, String commandName) {
            return new Decision(status, commandName, "", null);
        }

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    public enum Status {
        ACCEPTED,
        MALFORMED_COMMAND,
        UNSUPPORTED_COMMAND,
        COMMAND_POLICY_REJECTED,
        INVALID_SERVER
    }
}
