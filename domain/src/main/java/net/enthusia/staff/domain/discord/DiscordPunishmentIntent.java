package net.enthusia.staff.domain.discord;

import java.util.Optional;
import net.enthusia.staff.domain.auth.DiscordConsequenceIntent;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;

/** Fully explicit Discord-only consequence selected before confirmation. */
public record DiscordPunishmentIntent(
        DiscordConsequenceType type,
        SanctionLength length,
        boolean customDuration,
        boolean customConsequence,
        Optional<DiscordRestrictionTarget> restriction,
        String publicReason,
        String internalExplanation,
        int messageDeleteSeconds,
        boolean notifyTarget
) {
    private static final int MAX_REASON_LENGTH = 512;
    private static final int MAX_EXPLANATION_LENGTH = 2_000;
    private static final int MAX_MESSAGE_DELETE_SECONDS = 604_800;

    public DiscordPunishmentIntent {
        if (type == null || length == null || restriction == null) {
            throw new IllegalArgumentException("punishment intent fields must be present");
        }
        publicReason = bounded(publicReason, "publicReason", MAX_REASON_LENGTH, false);
        internalExplanation = bounded(internalExplanation, "internalExplanation", MAX_EXPLANATION_LENGTH, true);
        validateRestriction(type, restriction);
        validateDeletion(type, messageDeleteSeconds);
        if (type == DiscordConsequenceType.WARNING && !notifyTarget) {
            throw new IllegalArgumentException("Discord warnings must notify the target");
        }
        // Reuse D03 validation for instant/temporary/permanent and custom-duration shape.
        new DiscordConsequenceIntent(
                ModerationPlatform.DISCORD,
                type,
                length,
                customDuration,
                customConsequence
        );
    }

    public DiscordConsequenceIntent authorizationIntent() {
        return new DiscordConsequenceIntent(
                ModerationPlatform.DISCORD,
                type,
                length,
                customDuration,
                customConsequence
        );
    }

    public boolean reversible() {
        return type == DiscordConsequenceType.MUTE
                || type == DiscordConsequenceType.BAN
                || type == DiscordConsequenceType.CHANNEL_RESTRICTION;
    }

    private static void validateRestriction(
            DiscordConsequenceType type,
            Optional<DiscordRestrictionTarget> restriction
    ) {
        boolean required = type == DiscordConsequenceType.CHANNEL_RESTRICTION;
        if (required != restriction.isPresent()) {
            throw new IllegalArgumentException("channel restriction target must match consequence type");
        }
    }

    private static void validateDeletion(DiscordConsequenceType type, int seconds) {
        if (seconds < 0 || seconds > MAX_MESSAGE_DELETE_SECONDS) {
            throw new IllegalArgumentException("messageDeleteSeconds is outside Discord limits");
        }
        if (seconds != 0 && type != DiscordConsequenceType.BAN) {
            throw new IllegalArgumentException("message deletion is only valid for native bans");
        }
    }

    private static String bounded(String value, String field, int maximum, boolean allowBlank) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must be present");
        }
        String normalized = value.trim();
        if ((!allowBlank && normalized.isBlank()) || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
