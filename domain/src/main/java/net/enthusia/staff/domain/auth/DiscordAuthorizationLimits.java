package net.enthusia.staff.domain.auth;

import java.time.Duration;

/**
 * Runtime-supplied Discord moderation ceilings. D03 deliberately defines no production durations;
 * later runtime/configuration packages must supply approved values.
 */
public record DiscordAuthorizationLimits(
        Duration helperMaxMute,
        Duration moderatorMaxMute,
        Duration moderatorMaxBan,
        Duration moderatorMaxRestriction
) {
    public DiscordAuthorizationLimits {
        requirePositive(helperMaxMute, "helperMaxMute");
        requirePositive(moderatorMaxMute, "moderatorMaxMute");
        requirePositive(moderatorMaxBan, "moderatorMaxBan");
        requirePositive(moderatorMaxRestriction, "moderatorMaxRestriction");
        if (helperMaxMute.compareTo(moderatorMaxMute) > 0) {
            throw new IllegalArgumentException("helper mute ceiling cannot exceed moderator mute ceiling");
        }
    }

    public Duration moderatorMaximum(DiscordConsequenceType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must be present");
        }
        return switch (type) {
            case MUTE -> moderatorMaxMute;
            case BAN -> moderatorMaxBan;
            case CHANNEL_RESTRICTION -> moderatorMaxRestriction;
            case WARNING, KICK -> throw new IllegalArgumentException(type + " has no temporary duration ceiling");
        };
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
