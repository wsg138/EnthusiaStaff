package net.enthusia.staff.domain.auth;

import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;

/**
 * One explicit platform consequence selected before final moderation confirmation.
 *
 * <p>Cross-platform actions use one intent per selected platform so a caller cannot hide a magic
 * "both" consequence or silently copy a Discord consequence onto Minecraft.</p>
 */
public record DiscordConsequenceIntent(
        ModerationPlatform platform,
        DiscordConsequenceType type,
        SanctionLength length,
        boolean customDuration,
        boolean customConsequence
) {
    public DiscordConsequenceIntent {
        if (platform == null || type == null || length == null) {
            throw new IllegalArgumentException("platform, type and length must be present");
        }
        if (type.requiresDiscordPlatform() && platform != ModerationPlatform.DISCORD) {
            throw new IllegalArgumentException("channel restrictions are Discord-only");
        }
        if (type.requiresInstantLength() && !length.isInstant()) {
            throw new IllegalArgumentException(type + " must use an instant sanction length");
        }
        if (!type.requiresInstantLength() && length.isInstant()) {
            throw new IllegalArgumentException(type + " cannot use an instant sanction length");
        }
        if (customDuration && length.kind() != SanctionLength.Kind.TEMPORARY) {
            throw new IllegalArgumentException("custom duration requires a temporary sanction");
        }
    }
}
