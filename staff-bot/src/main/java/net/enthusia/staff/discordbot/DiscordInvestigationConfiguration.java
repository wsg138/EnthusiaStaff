package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Explicit opt-in configuration for private D09 investigation maintenance and alert delivery. */
record DiscordInvestigationConfiguration(
        String alertChannelId,
        String staffRoleId,
        Duration workerInterval,
        Duration retryBase,
        Duration retryMaximum
) {
    static final String ENABLED_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_INVESTIGATIONS_ENABLED";
    static final String ALERT_CHANNEL_ENV = "ENTHUSIA_STAFF_BOT_INVESTIGATION_ALERT_CHANNEL_ID";
    static final String STAFF_ROLE_ENV = "ENTHUSIA_STAFF_BOT_INVESTIGATION_STAFF_ROLE_ID";
    static final String WORKER_MILLIS_ENV = "ENTHUSIA_STAFF_BOT_INVESTIGATION_WORKER_MILLIS";
    static final String RETRY_SECONDS_ENV = "ENTHUSIA_STAFF_BOT_INVESTIGATION_RETRY_SECONDS";
    static final String RETRY_MAX_SECONDS_ENV = "ENTHUSIA_STAFF_BOT_INVESTIGATION_RETRY_MAX_SECONDS";

    static final String DEFAULT_STAFF_ROLE_ID = "1497476349244211311";
    private static final long DEFAULT_WORKER_MILLIS = 2_000;
    private static final long DEFAULT_RETRY_SECONDS = 30;
    private static final long DEFAULT_RETRY_MAX_SECONDS = 900;

    DiscordInvestigationConfiguration {
        if (!snowflake(alertChannelId) || !snowflake(staffRoleId)
                || workerInterval == null || workerInterval.toMillis() < 1
                || retryBase == null || retryBase.isZero() || retryBase.isNegative()
                || retryMaximum == null || retryMaximum.compareTo(retryBase) < 0) {
            throw new IllegalArgumentException("Discord investigation configuration is invalid");
        }
    }

    static Optional<DiscordInvestigationConfiguration> fromEnvironment(Map<String, String> values) {
        String enabled = values.get(ENABLED_ENV);
        if (enabled == null || enabled.isBlank() || enabled.trim().equalsIgnoreCase("false")) {
            return Optional.empty();
        }
        if (!enabled.trim().equalsIgnoreCase("true")) {
            throw new IllegalArgumentException(ENABLED_ENV + " must be true or false");
        }
        Duration retryBase = Duration.ofSeconds(positive(values, RETRY_SECONDS_ENV, DEFAULT_RETRY_SECONDS));
        Duration retryMaximum = Duration.ofSeconds(
                positive(values, RETRY_MAX_SECONDS_ENV, DEFAULT_RETRY_MAX_SECONDS)
        );
        return Optional.of(new DiscordInvestigationConfiguration(
                required(values, ALERT_CHANNEL_ENV),
                optional(values, STAFF_ROLE_ENV, DEFAULT_STAFF_ROLE_ID),
                Duration.ofMillis(positive(values, WORKER_MILLIS_ENV, DEFAULT_WORKER_MILLIS)),
                retryBase,
                retryMaximum
        ));
    }

    private static long positive(Map<String, String> values, String name, long fallback) {
        String raw = values.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
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

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required when D09 investigations are enabled");
        }
        return value.trim();
    }

    private static String optional(Map<String, String> values, String name, String fallback) {
        String value = values.get(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean snowflake(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return Long.parseUnsignedLong(value) != 0L;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
