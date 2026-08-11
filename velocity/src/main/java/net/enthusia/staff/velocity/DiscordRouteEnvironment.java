package net.enthusia.staff.velocity;

import java.util.Locale;

enum DiscordRouteEnvironment {
    STAGING,
    PRODUCTION;

    static DiscordRouteEnvironment parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("discord.route-environment must be configured");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("discord.route-environment must be STAGING or PRODUCTION", exception);
        }
    }
}
