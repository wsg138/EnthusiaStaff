package net.enthusia.staff.discordbot;

import java.util.Locale;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;

/** Sends player-facing D07 notifications without changing enforcement state. */
final class JdaPunishmentNotifier {
    private final DiscordPunishmentConfiguration configuration;

    JdaPunishmentNotifier(DiscordPunishmentConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Discord punishment configuration must be present");
        }
        this.configuration = configuration;
    }

    DiscordDeliveryOutcome notifyApplied(JDA jda, DiscordPunishment punishment) {
        return notify(jda, punishment, appliedMessage(punishment));
    }

    DiscordDeliveryOutcome notifyRemoved(JDA jda, DiscordPunishment punishment) {
        return notify(jda, punishment, removalMessage(punishment));
    }

    private DiscordDeliveryOutcome notify(JDA jda, DiscordPunishment punishment, String message) {
        if (!punishment.intent().notifyTarget()) {
            return DiscordDeliveryOutcome.NOT_ATTEMPTED;
        }
        try {
            User user = jda.retrieveUserById(punishment.targetUserId().value()).complete();
            user.openPrivateChannel().complete().sendMessage(message).complete();
            return DiscordDeliveryOutcome.DELIVERED;
        } catch (RuntimeException failure) {
            return failureOutcome(failure);
        }
    }

    private String appliedMessage(DiscordPunishment punishment) {
        return "Enthusia moderation action: " + punishment.intent().type()
                + "\nDuration: " + duration(punishment)
                + "\nReason: " + punishment.intent().publicReason()
                + "\n" + configuration.supportMessage();
    }

    private String removalMessage(DiscordPunishment punishment) {
        return "Enthusia moderation update: " + punishment.intent().type()
                + " " + punishment.termination().name().toLowerCase(Locale.ROOT)
                + "\nOriginal reason: " + punishment.intent().publicReason()
                + "\n" + configuration.supportMessage();
    }

    private static DiscordDeliveryOutcome failureOutcome(RuntimeException failure) {
        if (failure instanceof ErrorResponseException response) {
            String code = response.getErrorResponse().name();
            return retryableCode(code)
                    ? DiscordDeliveryOutcome.FAILED_RETRYABLE
                    : DiscordDeliveryOutcome.FAILED_TERMINAL;
        }
        return DiscordDeliveryOutcome.FAILED_RETRYABLE;
    }

    private static boolean retryableCode(String code) {
        return code.contains("SERVER") || code.contains("TEMPORAR") || code.contains("RATE_LIMIT");
    }

    private static String duration(DiscordPunishment punishment) {
        return switch (punishment.intent().length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> punishment.intent().length().temporary().orElseThrow().toString();
        };
    }
}
