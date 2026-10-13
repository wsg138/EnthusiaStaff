package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Enabled by default for production; explicitly disabled for staging/shared-guild safety. */
record DiscordAccountAgeGateConfiguration(boolean enabled, Path bypassFile) {
    static final String BYPASS_FILE_KEY = "ENTHUSIA_STAFF_BOT_ACCOUNT_AGE_BYPASS_FILE";
    static final String DEFAULT_BYPASS_FILE = "account-age-bypass-ids.txt";
    static final String ENABLED_KEY = "ENTHUSIA_STAFF_BOT_ACCOUNT_AGE_GATE_ENABLED";

    static DiscordAccountAgeGateConfiguration disabled() {
        return new DiscordAccountAgeGateConfiguration(false, Path.of(DEFAULT_BYPASS_FILE));
    }

    static DiscordAccountAgeGateConfiguration fromEnvironment(
            StaffBotEnvironment environment, Map<String, String> values) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(values, "values");
        // The staging and production identities currently share a guild. A staging
        // instance must never unexpectedly remove real members from that guild.
        boolean production = environment == StaffBotEnvironment.PRODUCTION;
        String raw = values.getOrDefault(ENABLED_KEY, Boolean.toString(production)).trim();
        if ("false".equalsIgnoreCase(raw)) {
            return disabled();
        }
        if (!"true".equalsIgnoreCase(raw)) {
            throw new IllegalArgumentException("account age gate enabled must be true or false");
        }
        if (!production) {
            throw new IllegalArgumentException("staging StaffBot may not enforce account-age kicks in the shared guild");
        }
        return new DiscordAccountAgeGateConfiguration(true, bypassFile(values));
    }

    private static Path bypassFile(Map<String, String> values) {
        String value = values.getOrDefault(BYPASS_FILE_KEY, DEFAULT_BYPASS_FILE).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("account-age bypass file path must not be empty");
        }
        return Path.of(value);
    }
}
