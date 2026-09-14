package net.enthusia.staff.discordbot;

import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated, explicit allowlist for one-way Minecraft group to Discord role projection. */
record DiscordRoleSyncConfiguration(
        Mode mode,
        Map<String, String> groupToRole,
        Set<String> protectedRoleIds,
        Duration interval,
        int batchSize
) {
    static final String MAPPINGS_ENV = "ENTHUSIA_STAFF_BOT_ROLE_SYNC_MAPPINGS";
    static final String PROTECTED_ROLES_ENV = "ENTHUSIA_STAFF_BOT_ROLE_SYNC_PROTECTED_ROLE_IDS";
    static final String MODE_ENV = "ENTHUSIA_STAFF_BOT_ROLE_SYNC_MODE";
    static final String INTERVAL_SECONDS_ENV = "ENTHUSIA_STAFF_BOT_ROLE_SYNC_INTERVAL_SECONDS";
    static final String BATCH_SIZE_ENV = "ENTHUSIA_STAFF_BOT_ROLE_SYNC_BATCH_SIZE";

    private static final int DEFAULT_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final int MAX_RULES = 64;
    private static final int MAX_PROTECTED_ROLES = 64;
    private static final Pattern GROUP = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern SNOWFLAKE = Pattern.compile("[0-9]{1,20}");
    private static final BigInteger MAX_SNOWFLAKE = new BigInteger("18446744073709551615");

    enum Mode {
        SHADOW,
        ENFORCE
    }

    DiscordRoleSyncConfiguration {
        if (mode == null || groupToRole == null || protectedRoleIds == null || interval == null) {
            throw new IllegalArgumentException("role-sync configuration fields must be present");
        }
        groupToRole = Map.copyOf(groupToRole);
        protectedRoleIds = Set.copyOf(protectedRoleIds);
        if (groupToRole.isEmpty() || groupToRole.size() > MAX_RULES) {
            throw new IllegalArgumentException("role-sync mappings must contain between 1 and 64 entries");
        }
        if (protectedRoleIds.size() > MAX_PROTECTED_ROLES) {
            throw new IllegalArgumentException("too many protected Discord roles");
        }
        if (interval.compareTo(Duration.ofSeconds(30)) < 0 || interval.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("role-sync interval must be between 30 seconds and 1 hour");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("role-sync batch size must be between 1 and 100");
        }
        Set<String> managed = Set.copyOf(groupToRole.values());
        if (!java.util.Collections.disjoint(managed, protectedRoleIds)) {
            throw new IllegalArgumentException("protected Discord roles cannot also be managed role-sync outputs");
        }
    }

    static Optional<DiscordRoleSyncConfiguration> fromEnvironment(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("role-sync configuration values must be present");
        }
        String rawMappings = values.get(MAPPINGS_ENV);
        boolean anySetting = present(rawMappings)
                || present(values.get(PROTECTED_ROLES_ENV))
                || present(values.get(MODE_ENV))
                || present(values.get(INTERVAL_SECONDS_ENV))
                || present(values.get(BATCH_SIZE_ENV));
        if (!anySetting) {
            return Optional.empty();
        }
        if (!present(rawMappings)) {
            throw new IllegalArgumentException("role-sync mappings are required when role sync is configured");
        }
        return Optional.of(new DiscordRoleSyncConfiguration(
                mode(values.get(MODE_ENV)),
                mappings(rawMappings),
                roleSet(values.get(PROTECTED_ROLES_ENV)),
                Duration.ofSeconds(integer(values, INTERVAL_SECONDS_ENV, DEFAULT_INTERVAL_SECONDS, 30, 3600)),
                integer(values, BATCH_SIZE_ENV, DEFAULT_BATCH_SIZE, 1, 100)
        ));
    }

    Set<String> managedRoleIds() {
        return Set.copyOf(groupToRole.values());
    }

    Set<String> desiredRoles(Set<String> minecraftGroups) {
        if (minecraftGroups == null) {
            throw new IllegalArgumentException("Minecraft groups must be present");
        }
        Set<String> desired = new LinkedHashSet<>();
        for (String rawGroup : minecraftGroups) {
            String group = normalizeGroup(rawGroup);
            String role = groupToRole.get(group);
            if (role != null) {
                desired.add(role);
            }
        }
        return Set.copyOf(desired);
    }

    private static Map<String, String> mappings(String raw) {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (String entry : raw.split(";", -1)) {
            String trimmed = entry.trim();
            int separator = trimmed.indexOf('=');
            if (trimmed.isEmpty() || separator < 1 || separator != trimmed.lastIndexOf('=')) {
                throw new IllegalArgumentException("role-sync mappings must use group=roleId entries separated by semicolons");
            }
            String group = normalizeGroup(trimmed.substring(0, separator));
            String role = normalizeSnowflake(trimmed.substring(separator + 1), "role-sync role ID");
            if (mappings.putIfAbsent(group, role) != null) {
                throw new IllegalArgumentException("role-sync mappings contain a duplicate Minecraft group");
            }
        }
        return Map.copyOf(mappings);
    }

    private static Set<String> roleSet(String raw) {
        if (!present(raw)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String entry : raw.split(",", -1)) {
            roles.add(normalizeSnowflake(entry, "protected role ID"));
        }
        return Set.copyOf(roles);
    }

    private static Mode mode(String raw) {
        if (!present(raw)) {
            return Mode.SHADOW;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "shadow" -> Mode.SHADOW;
            case "enforce" -> Mode.ENFORCE;
            default -> throw new IllegalArgumentException("role-sync mode must be shadow or enforce");
        };
    }

    private static int integer(Map<String, String> values, String key, int fallback, int minimum, int maximum) {
        String raw = values.get(key);
        if (!present(raw)) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(key + " is outside its safe range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static String normalizeGroup(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Minecraft group must be present");
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!GROUP.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Minecraft group name is invalid");
        }
        return normalized;
    }

    private static String normalizeSnowflake(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must be present");
        }
        String normalized = value.trim();
        if (!SNOWFLAKE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be a decimal Discord snowflake");
        }
        BigInteger numeric = new BigInteger(normalized);
        if (numeric.signum() <= 0 || numeric.compareTo(MAX_SNOWFLAKE) > 0) {
            throw new IllegalArgumentException(label + " is outside the Discord snowflake range");
        }
        return numeric.toString();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
