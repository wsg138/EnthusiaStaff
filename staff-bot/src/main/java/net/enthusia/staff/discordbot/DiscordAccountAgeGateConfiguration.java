package net.enthusia.staff.discordbot;

import java.util.Map;
import java.util.Objects;

/** Explicit opt-in: enable only after the bot has member-join intent and kick permission. */
record DiscordAccountAgeGateConfiguration(boolean enabled) {
    static final String ENABLED_KEY = "ENTHUSIA_STAFF_BOT_ACCOUNT_AGE_GATE_ENABLED";

    static DiscordAccountAgeGateConfiguration disabled() {
        return new DiscordAccountAgeGateConfiguration(false);
    }

    static DiscordAccountAgeGateConfiguration fromEnvironment(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        String raw = values.getOrDefault(ENABLED_KEY, "false").trim();
        if ("true".equalsIgnoreCase(raw)) {
            return new DiscordAccountAgeGateConfiguration(true);
        }
        if ("false".equalsIgnoreCase(raw)) {
            return disabled();
        }
        throw new IllegalArgumentException("account age gate enabled must be true or false");
    }
}
