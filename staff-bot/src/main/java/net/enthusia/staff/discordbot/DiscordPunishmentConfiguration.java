package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;

/** Explicit opt-in D07 runtime configuration. No punishment duration ceilings are hard-coded. */
record DiscordPunishmentConfiguration(
        DiscordAuthorizationLimits authorizationLimits,
        String muteRoleId,
        Set<String> supportScopeIds,
        String supportMessage,
        Duration reconciliationInterval,
        Duration workerInterval
) {
    static final String ENABLED_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED";
    static final String MUTE_ROLE_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_MUTE_ROLE_ID";
    static final String SUPPORT_SCOPES_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_SUPPORT_SCOPE_IDS";
    static final String SUPPORT_MESSAGE_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_SUPPORT_MESSAGE";
    static final String HELPER_MAX_MUTE_ENV = "ENTHUSIA_STAFF_BOT_HELPER_MAX_MUTE_SECONDS";
    static final String MOD_MAX_MUTE_ENV = "ENTHUSIA_STAFF_BOT_MOD_MAX_MUTE_SECONDS";
    static final String MOD_MAX_BAN_ENV = "ENTHUSIA_STAFF_BOT_MOD_MAX_BAN_SECONDS";
    static final String MOD_MAX_RESTRICTION_ENV = "ENTHUSIA_STAFF_BOT_MOD_MAX_RESTRICTION_SECONDS";
    static final String RECONCILE_SECONDS_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_RECONCILE_SECONDS";
    static final String WORKER_MILLIS_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_WORKER_MILLIS";

    private static final Pattern SNOWFLAKE = Pattern.compile("[1-9][0-9]{0,19}");
    private static final int MAX_SUPPORT_MESSAGE = 512;
    private static final long DEFAULT_RECONCILE_SECONDS = 60;
    private static final long DEFAULT_WORKER_MILLIS = 1_000;

    DiscordPunishmentConfiguration {
        if (authorizationLimits == null || !snowflake(muteRoleId) || supportScopeIds == null
                || supportScopeIds.isEmpty() || supportScopeIds.stream().anyMatch(id -> !snowflake(id))
                || supportMessage == null || supportMessage.isBlank() || supportMessage.length() > MAX_SUPPORT_MESSAGE
                || reconciliationInterval == null || reconciliationInterval.isZero() || reconciliationInterval.isNegative()
                || workerInterval == null || workerInterval.toMillis() < 1) {
            throw new IllegalArgumentException("Discord punishment configuration is invalid");
        }
        supportScopeIds = Set.copyOf(supportScopeIds);
        supportMessage = supportMessage.trim();
    }

    static Optional<DiscordPunishmentConfiguration> fromEnvironment(Map<String, String> values) {
        String enabled = values.get(ENABLED_ENV);
        if (enabled == null || enabled.isBlank() || enabled.trim().equalsIgnoreCase("false")) {
            return Optional.empty();
        }
        if (!enabled.trim().equalsIgnoreCase("true")) {
            throw new IllegalArgumentException(ENABLED_ENV + " must be true or false");
        }
        DiscordAuthorizationLimits limits = new DiscordAuthorizationLimits(
                seconds(values, HELPER_MAX_MUTE_ENV),
                seconds(values, MOD_MAX_MUTE_ENV),
                seconds(values, MOD_MAX_BAN_ENV),
                seconds(values, MOD_MAX_RESTRICTION_ENV)
        );
        return Optional.of(new DiscordPunishmentConfiguration(
                limits,
                required(values, MUTE_ROLE_ENV),
                scopes(required(values, SUPPORT_SCOPES_ENV)),
                required(values, SUPPORT_MESSAGE_ENV),
                Duration.ofSeconds(optionalPositive(values, RECONCILE_SECONDS_ENV, DEFAULT_RECONCILE_SECONDS)),
                Duration.ofMillis(optionalPositive(values, WORKER_MILLIS_ENV, DEFAULT_WORKER_MILLIS))
        ));
    }

    private static Duration seconds(Map<String, String> values, String name) {
        return Duration.ofSeconds(optionalPositive(values, name, -1));
    }

    private static long optionalPositive(Map<String, String> values, String name, long fallback) {
        String raw = values.get(name);
        if (raw == null || raw.isBlank()) {
            if (fallback > 0) {
                return fallback;
            }
            throw new IllegalArgumentException(name + " is required when Discord enforcement is enabled");
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static Set<String> scopes(String raw) {
        Set<String> result = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (result.isEmpty()) {
            throw new IllegalArgumentException(SUPPORT_SCOPES_ENV + " must contain at least one Discord scope");
        }
        return result;
    }

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required when Discord enforcement is enabled");
        }
        return value.trim();
    }

    private static boolean snowflake(String value) {
        return value != null && SNOWFLAKE.matcher(value).matches();
    }
}
