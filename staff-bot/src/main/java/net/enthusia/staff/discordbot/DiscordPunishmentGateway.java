package net.enthusia.staff.discordbot;

import java.util.Optional;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** External Discord effect boundary. Domain authority never derives from this adapter. */
interface DiscordPunishmentGateway {
    void preflight(DiscordGuildId guildId, DiscordUserId target, DiscordPunishmentIntent intent);

    ApplyResult apply(DiscordPunishment punishment);

    void remove(DiscordPunishment punishment);

    void reconcile(DiscordPunishment punishment);

    record ApplyResult(
            DiscordDeliveryOutcome deliveryOutcome,
            Optional<DiscordPermissionSnapshot> previousRestriction
    ) {
        public ApplyResult {
            if (deliveryOutcome == null || previousRestriction == null) {
                throw new IllegalArgumentException("Discord apply result fields must be present");
            }
        }
    }

    final class EffectException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String errorCode;
        private final boolean retryable;
        private final DiscordDeliveryOutcome deliveryOutcome;

        EffectException(String errorCode, boolean retryable) {
            this(errorCode, retryable, DiscordDeliveryOutcome.NOT_ATTEMPTED, null);
        }

        EffectException(
                String errorCode,
                boolean retryable,
                DiscordDeliveryOutcome deliveryOutcome,
                Throwable cause
        ) {
            super("Discord effect failed: " + errorCode, cause);
            if (errorCode == null || errorCode.isBlank() || errorCode.length() > 96 || deliveryOutcome == null) {
                throw new IllegalArgumentException("Discord effect failure fields are invalid");
            }
            this.errorCode = errorCode;
            this.retryable = retryable;
            this.deliveryOutcome = deliveryOutcome;
        }

        String errorCode() {
            return errorCode;
        }

        boolean retryable() {
            return retryable;
        }

        DiscordDeliveryOutcome deliveryOutcome() {
            return deliveryOutcome;
        }
    }
}
