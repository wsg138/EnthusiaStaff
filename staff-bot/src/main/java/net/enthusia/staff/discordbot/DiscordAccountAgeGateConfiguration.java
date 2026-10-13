package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Enabled by default; an explicit false setting disables the age gate. */
record DiscordAccountAgeGateConfiguration(boolean enabled, Path bypassFile) {
    static final String BYPASS_FILE_KEY = "ENTHUSIA_STAFF_BOT_ACCOUNT_AGE_BYPASS_FILE";
    static final String DEFAULT_BYPASS_FILE = "account-age-bypass-ids.txt";
    static final String ENABLED_KEY = "ENTHUSIA_STAFF_BOT_ACCOUNT_AGE_GATE_ENABLED";

    static DiscordAccountAgeGateConfiguration disabled() {
        return new DiscordAccountAgeGateConfiguration(false, Path.of(DEFAULT_BYPASS_FILE));
    }

    static DiscordAccountAgeGateConfiguration fromEnvironment(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        String raw = values.getOrDefault(ENABLED_KEY, "true").trim();
        if ("true".equalsIgnoreCase(raw)) {
            return new DiscordAccountAgeGateConfiguration(true, bypassFile(values));
        }
        if ("false".equalsIgnoreCase(raw)) {
            return disabled();
        }
        throw new IllegalArgumentException("account age gate enabled must be true or false");
    }

    private static Path bypassFile(Map<String, String> values) {
        String value = values.getOrDefault(BYPASS_FILE_KEY, DEFAULT_BYPASS_FILE).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("account-age bypass file path must not be empty");
        }
        return Path.of(value);
    }
}
