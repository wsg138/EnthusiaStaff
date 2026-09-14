package net.enthusia.staff.discordbot;

import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** External Discord effect boundary. Domain authority never derives from this adapter. */
interface DiscordPunishmentGateway {
    void preflight(DiscordGuildId guildId, DiscordUserId target, DiscordPunishmentIntent intent);

    DiscordPermissionSnapshot captureRestrictionSnapshot(DiscordPunishment punishment);

    void apply(DiscordPunishment punishment);

    DiscordDeliveryOutcome notifyApplied(DiscordPunishment punishment);

    void remove(DiscordPunishment punishment);

    DiscordDeliveryOutcome notifyRemoved(DiscordPunishment punishment);

    void reconcile(DiscordPunishment punishment);

    final class EffectException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String errorCode;
        private final boolean retryable;

        EffectException(String errorCode, boolean retryable) {
            this(errorCode, retryable, null);
        }

        EffectException(String errorCode, boolean retryable, Throwable cause) {
            super("Discord effect failed: " + errorCode, cause);
            if (errorCode == null || errorCode.isBlank() || errorCode.length() > 96) {
                throw new IllegalArgumentException("Discord effect failure fields are invalid");
            }
            this.errorCode = errorCode;
            this.retryable = retryable;
        }

        String errorCode() {
            return errorCode;
        }

        boolean retryable() {
            return retryable;
        }
    }
}
