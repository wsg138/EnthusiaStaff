package net.enthusia.staff.paper.punishment;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.moderation.DiscordGuildId;

/** Explicit fail-closed Paper gate for Minecraft-origin Discord/Both punishment scopes. */
public record PaperCrossPlatformConfiguration(
        DiscordGuildId guildId,
        DiscordAuthorizationLimits authorizationLimits
) {
    static final String ENABLED_ENV = "ENTHUSIA_STAFF_CROSS_PLATFORM_ENABLED";
    static final String GUILD_ID_ENV = "ENTHUSIA_STAFF_CROSS_PLATFORM_DISCORD_GUILD_ID";
    static final String HELPER_MAX_MUTE_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_HELPER_MAX_MUTE_SECONDS";
    static final String MOD_MAX_MUTE_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_MOD_MAX_MUTE_SECONDS";
    static final String MOD_MAX_BAN_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_MOD_MAX_BAN_SECONDS";
    static final String MOD_MAX_RESTRICTION_ENV = "ENTHUSIA_STAFF_BOT_DISCORD_MOD_MAX_RESTRICTION_SECONDS";
    private static final String LEGACY_HELPER_MAX_MUTE_ENV = "ENTHUSIA_STAFF_BOT_HELPER_MAX_MUTE_SECONDS";
    private static final String LEGACY_MOD_MAX_MUTE_ENV = "ENTHUSIA_STAFF_BOT_MOD_MAX_MUTE_SECONDS";
    private static final String LEGACY_MOD_MAX_BAN_ENV = "ENTHUSIA_STAFF_BOT_MOD_MAX_BAN_SECONDS";
    private static final String LEGACY_MOD_MAX_RESTRICTION_ENV = "ENTHUSIA_STAFF_BOT_MOD_MAX_RESTRICTION_SECONDS";

    public PaperCrossPlatformConfiguration {
        if (guildId == null || authorizationLimits == null) {
            throw new IllegalArgumentException("Paper cross-platform configuration must be complete");
        }
    }

    public static Optional<PaperCrossPlatformConfiguration> fromSystemEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static Optional<PaperCrossPlatformConfiguration> fromEnvironment(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("environment values must be present");
        }
        String enabled = values.get(ENABLED_ENV);
        if (enabled == null || enabled.isBlank() || enabled.trim().equalsIgnoreCase("false")) {
            return Optional.empty();
        }
        if (!enabled.trim().equalsIgnoreCase("true")) {
            throw new IllegalArgumentException(ENABLED_ENV + " must be true or false");
        }
        String guild = required(values, GUILD_ID_ENV);
        if (!guild.matches("[1-9][0-9]{0,19}")) {
            throw new IllegalArgumentException(GUILD_ID_ENV + " must be a Discord snowflake");
        }
        DiscordAuthorizationLimits limits = new DiscordAuthorizationLimits(
                seconds(values, HELPER_MAX_MUTE_ENV, LEGACY_HELPER_MAX_MUTE_ENV),
                seconds(values, MOD_MAX_MUTE_ENV, LEGACY_MOD_MAX_MUTE_ENV),
                seconds(values, MOD_MAX_BAN_ENV, LEGACY_MOD_MAX_BAN_ENV),
                seconds(values, MOD_MAX_RESTRICTION_ENV, LEGACY_MOD_MAX_RESTRICTION_ENV)
        );
        return Optional.of(new PaperCrossPlatformConfiguration(new DiscordGuildId(guild), limits));
    }

    private static Duration seconds(Map<String, String> values, String canonical, String legacy) {
        String raw = values.get(canonical);
        if (raw == null || raw.isBlank()) {
            raw = values.get(legacy);
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(canonical + " is required when cross-platform moderation is enabled");
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            if (seconds <= 0) {
                throw new IllegalArgumentException(canonical + " must be positive");
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(canonical + " must be an integer", exception);
        }
    }

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required when cross-platform moderation is enabled");
        }
        return value.trim();
    }
}
