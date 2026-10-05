package net.enthusia.staff.paper.commandbridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRule;

public record PaperCommandBridgeConfiguration(
        String bindHost,
        int port,
        String credential,
        List<CommandBridgeRule> rules
) {
    public static final String ENABLED_ENV = "ENTHUSIA_STAFF_COMMAND_BRIDGE_ENABLED";
    public static final String BIND_HOST_ENV = "ENTHUSIA_STAFF_COMMAND_BRIDGE_BIND_HOST";
    public static final String PORT_ENV = "ENTHUSIA_STAFF_COMMAND_BRIDGE_PORT";
    public static final String CREDENTIAL_ENV = "ENTHUSIA_STAFF_COMMAND_BRIDGE_SECRET";
    public static final String RULES_ENV = "ENTHUSIA_STAFF_COMMAND_BRIDGE_RULES";
    private static final int DEFAULT_PORT = 8772;
    private static final int MAX_RULES = 64;
    private static final int MIN_SECRET_LENGTH = 32;

    public PaperCommandBridgeConfiguration {
        rules = List.copyOf(rules);
        if (!("127.0.0.1".equals(bindHost) || "0.0.0.0".equals(bindHost))
                || port < 1 || port > 65_535
                || credential == null || credential.length() < MIN_SECRET_LENGTH
                || rules.isEmpty() || rules.size() > MAX_RULES) {
            throw new IllegalArgumentException("Paper command bridge configuration is invalid");
        }
    }

    public static Optional<PaperCommandBridgeConfiguration> fromEnvironment(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("Paper command bridge environment is required");
        }
        boolean any = List.of(ENABLED_ENV, BIND_HOST_ENV, PORT_ENV, CREDENTIAL_ENV, RULES_ENV).stream()
                .anyMatch(name -> present(values.get(name)));
        if (!any) {
            return Optional.empty();
        }
        if (!enabled(values.get(ENABLED_ENV))) {
            throw new IllegalArgumentException("command bridge settings require explicit enabled=true");
        }
        return Optional.of(new PaperCommandBridgeConfiguration(
                defaulted(values.get(BIND_HOST_ENV), "127.0.0.1"),
                port(values.get(PORT_ENV)),
                required(values.get(CREDENTIAL_ENV), CREDENTIAL_ENV),
                rules(required(values.get(RULES_ENV), RULES_ENV))
        ));
    }

    private static List<CommandBridgeRule> rules(String raw) {
        List<CommandBridgeRule> parsed = new ArrayList<>();
        for (String entry : raw.split(";", -1)) {
            String[] fields = entry.trim().split("\\|", -1);
            if (fields.length != 4 || parsed.size() >= MAX_RULES) {
                throw new IllegalArgumentException("command bridge rules must use command|rank|permission|maxArgs");
            }
            StaffRank rank = rank(fields[1]);
            parsed.add(new CommandBridgeRule(
                    fields[0].trim(),
                    rank,
                    fields[2].trim(),
                    boundedInteger(fields[3], 0, 32, "maximum arguments")
            ));
        }
        return List.copyOf(parsed);
    }

    private static StaffRank rank(String raw) {
        try {
            StaffRank rank = StaffRank.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (rank == StaffRank.SYSTEM) {
                throw new IllegalArgumentException("SYSTEM cannot execute Discord console commands");
            }
            return rank;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("command bridge rank is invalid", exception);
        }
    }

    private static boolean enabled(String raw) {
        if (!present(raw)) {
            return false;
        }
        if (!"true".equalsIgnoreCase(raw.trim()) && !"false".equalsIgnoreCase(raw.trim())) {
            throw new IllegalArgumentException("command bridge enabled flag must be true or false");
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private static int port(String raw) {
        return present(raw) ? boundedInteger(raw, 1, 65_535, "port") : DEFAULT_PORT;
    }

    private static int boundedInteger(String raw, int minimum, int maximum, String name) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " is outside its safe range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be numeric", exception);
        }
    }

    private static String required(String value, String name) {
        if (!present(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String defaulted(String value, String fallback) {
        return present(value) ? value.trim() : fallback;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "PaperCommandBridgeConfiguration[bindHost=" + bindHost + ", port=" + port
                + ", credential=<redacted>, ruleCount=" + rules.size() + "]";
    }
}
