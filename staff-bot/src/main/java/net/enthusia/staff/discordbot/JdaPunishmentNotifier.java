package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Sends player-facing moderation notifications without changing enforcement state. */
final class JdaPunishmentNotifier {
    static final String APPEAL_CHANNEL =
            "https://discord.com/channels/1410303324745371709/1511217148230373568";
    static final String APPEAL_SITE = "https://enthusia.info/appeal";
    private static final String SECTION_BREAK = "\n\n";
    private static final String PREVIEW_REASON = "Notification test";
    private static final List<String> PRIVATE_EXPLANATION_PREFIXES = List.of(
            "Discord message reference:",
            "External evidence reference:"
    );

    private final DiscordPunishmentConfiguration configuration;

    JdaPunishmentNotifier(DiscordPunishmentConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Discord punishment configuration must be present");
        }
        this.configuration = configuration;
    }

    DiscordDeliveryOutcome notifyApplied(JDA jda, DiscordPunishment punishment) {
        return notify(jda, punishment.targetUserId().value(), appliedMessage(punishment),
                PunishmentNotificationDiscordPresentation.color(punishment.intent().type()));
    }

    DiscordDeliveryOutcome notifyRemoved(JDA jda, DiscordPunishment punishment) {
        return notify(jda, punishment.targetUserId().value(), removalMessage(punishment),
                PunishmentNotificationDiscordPresentation.UPDATE_COLOR);
    }

    DiscordDeliveryOutcome notifyMinecraftBan(JDA jda, MinecraftBanNotification notification) {
        return notify(jda, notification.discordUserId(), minecraftBanMessage(notification),
                PunishmentNotificationDiscordPresentation.BAN_COLOR);
    }

    DiscordDeliveryOutcome notifyMinecraftWarningOrMute(
            JDA jda,
            MinecraftWarningOrMuteNotification notification
    ) {
        return notify(jda, notification.discordUserId(), minecraftWarningOrMuteMessage(notification),
                PunishmentNotificationDiscordPresentation.color(notification.type()));
    }

    static DiscordDeliveryOutcome notifyPreview(
            JDA jda,
            String userId,
            DiscordConsequenceType type,
            Instant now
    ) {
        return notify(jda, userId, previewMessage(type, now),
                PunishmentNotificationDiscordPresentation.color(type));
    }

    static DiscordDeliveryOutcome notifyMinecraftPreview(
            JDA jda,
            String userId,
            SanctionType type,
            Instant now
    ) {
        if (type == null || now == null) {
            throw new IllegalArgumentException("Minecraft notification preview fields must be present");
        }
        String message = switch (type) {
            case WARNING -> minecraftWarningOrMuteMessage(new MinecraftWarningOrMuteNotification(
                    userId,
                    "ExamplePlayer",
                    PREVIEW_REASON,
                    now,
                    SanctionType.WARNING,
                    Optional.empty()
            ));
            case MUTE -> minecraftWarningOrMuteMessage(new MinecraftWarningOrMuteNotification(
                    userId,
                    "ExamplePlayer",
                    PREVIEW_REASON,
                    now,
                    SanctionType.MUTE,
                    Optional.of(now.plus(Duration.ofHours(1)))
            ));
            case BAN -> minecraftBanMessage(new MinecraftBanNotification(
                    userId,
                    "ExamplePlayer",
                    PREVIEW_REASON,
                    now,
                    Optional.of(now.plus(Duration.ofDays(7)))
            ));
            default -> throw new IllegalArgumentException("Minecraft notification preview type is unsupported");
        };
        return notify(jda, userId, message, PunishmentNotificationDiscordPresentation.color(type));
    }

    private static DiscordDeliveryOutcome notify(JDA jda, String userId, String message, int color) {
        try {
            User user = jda.retrieveUserById(userId).complete();
            user.openPrivateChannel().complete()
                    .sendMessageEmbeds(PunishmentNotificationDiscordPresentation.embed(
                            message, color, PunishmentNotificationDiscordPresentation.guildIconUrl(jda)))
                    .setAllowedMentions(List.of())
                    .addComponents(PunishmentNotificationDiscordPresentation.appealRow())
                    .complete();
            return DiscordDeliveryOutcome.DELIVERED;
        } catch (RuntimeException failure) {
            return failureOutcome(failure);
        }
    }

    String appliedMessage(DiscordPunishment punishment) {
        DiscordConsequenceType type = punishment.intent().type();
        return appliedMessage(
                type,
                duration(punishment),
                punishment.intent().publicReason(),
                punishment.intent().internalExplanation(),
                punishment.expiresAt()
        );
    }

    static String previewMessage(DiscordConsequenceType type, Instant now) {
        if (type == null || now == null) {
            throw new IllegalArgumentException("notification preview fields must be present");
        }
        return switch (type) {
            case WARNING -> appliedMessage(
                    type,
                    "instant",
                    PREVIEW_REASON,
                    "This is a StaffBot preview sent only to you. No punishment was created or applied.",
                    Optional.empty()
            );
            case MUTE -> appliedMessage(
                    type,
                    "1 hour",
                    PREVIEW_REASON,
                    "This is a StaffBot preview sent only to you. No punishment was created or applied.",
                    Optional.of(now.plus(Duration.ofHours(1)))
            );
            case BAN -> appliedMessage(
                    type,
                    "7 days",
                    PREVIEW_REASON,
                    "This is a StaffBot preview sent only to you. No punishment was created or applied.",
                    Optional.of(now.plus(Duration.ofDays(7)))
            );
            default -> throw new IllegalArgumentException("notification preview type is unsupported");
        };
    }

    /** Simulates the exact pending player's notice, without creating or sending an action. */
    static String previewMessage(DiscordPunishmentIntent intent, Instant now) {
        if (intent == null || now == null) {
            throw new IllegalArgumentException("punishment preview requires an intent and current time");
        }
        String duration = switch (intent.length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> friendlyDuration(intent.length().temporary().orElseThrow());
        };
        return appliedMessage(
                intent.type(), duration, intent.publicReason(), intent.internalExplanation(),
                intent.length().expirationFrom(now));
    }

    private static String appliedMessage(
            DiscordConsequenceType type,
            String duration,
            String publicReason,
            String internalExplanation,
            Optional<Instant> expiresAt
    ) {
        String durationClause = hasExpiry(type) ? " for `%s`".formatted(duration) : "";
        String explanation = playerSafeExplanation(internalExplanation)
                .map(value -> "**Staff explanation:** " + value + SECTION_BREAK)
                .orElse("");
        String expiry = hasExpiry(type) ? expiryText(expiresAt) : "";
        return "# Punishment Alert" + SECTION_BREAK
                + "You have been `" + actionName(type) + "` on the Enthusia SMP Discord"
                + durationClause + "." + SECTION_BREAK
                + "**Reason:** " + publicReason + SECTION_BREAK
                + explanation
                + expiry
                + appealText();
    }

    String removalMessage(DiscordPunishment punishment) {
        return "# Punishment Update" + SECTION_BREAK
                + "Your Enthusia SMP Discord `" + actionName(punishment.intent().type())
                + "` has been " + removalAction(punishment) + "." + SECTION_BREAK
                + "**Original reason:** " + punishment.intent().publicReason() + SECTION_BREAK
                + appealText();
    }

    static String minecraftBanMessage(MinecraftBanNotification notification) {
        String timing = notification.expiresAt().isPresent()
                ? " until the time shown below"
                : " permanently";
        return "# Punishment Alert" + SECTION_BREAK
                + "Your Minecraft account `" + notification.minecraftName()
                + "` has been `banned` from the Enthusia SMP" + timing + "." + SECTION_BREAK
                + "**Reason:** " + notification.publicReason() + SECTION_BREAK
                + expiryText(notification.expiresAt())
                + appealText();
    }

    static String minecraftWarningOrMuteMessage(MinecraftWarningOrMuteNotification notification) {
        String action = notification.type() == SanctionType.WARNING ? "warned" : "muted";
        String timing = notification.type() == SanctionType.MUTE
                ? notification.expiresAt().isPresent()
                        ? " until the time shown below"
                        : " permanently"
                : "";
        String expiry = notification.type() == SanctionType.MUTE
                ? expiryText(notification.expiresAt())
                : "";
        return "# Punishment Alert" + SECTION_BREAK
                + "Your Minecraft account `" + notification.minecraftName()
                + "` has been `" + action + "` on the Enthusia SMP" + timing + "." + SECTION_BREAK
                + "**Reason:** " + notification.publicReason() + SECTION_BREAK
                + expiry
                + appealText();
    }

    private static Optional<String> playerSafeExplanation(String internal) {
        if (internal == null || internal.isBlank()) {
            return Optional.empty();
        }
        String result = internal.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> PRIVATE_EXPLANATION_PREFIXES.stream().noneMatch(line::startsWith))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .strip();
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private static boolean hasExpiry(DiscordConsequenceType type) {
        return type != DiscordConsequenceType.WARNING && type != DiscordConsequenceType.KICK;
    }

    private static String expiryText(Optional<Instant> expiresAt) {
        if (expiresAt.isEmpty()) {
            return "**Expires:** Permanent" + SECTION_BREAK;
        }
        long epoch = expiresAt.orElseThrow().getEpochSecond();
        return "**Expires:** <t:%d:F> (<t:%d:R>)".formatted(epoch, epoch) + SECTION_BREAK;
    }

    private static String appealText() {
        return "If you believe this punishment is incorrect, you can appeal in "
                + "[the Enthusia appeal channel](" + APPEAL_CHANNEL + ")"
                + " or at [Enthusia.info/appeal](" + APPEAL_SITE + ").";
    }

    private static String removalAction(DiscordPunishment punishment) {
        return switch (punishment.termination()) {
            case END -> "ended";
            case REVOKE -> "revoked";
            case OVERTURN -> "overturned";
            case EXPIRE -> "expired";
            case NONE -> "updated";
        };
    }

    private static String actionName(DiscordConsequenceType type) {
        return switch (type) {
            case WARNING -> "warned";
            case MUTE -> "muted";
            case KICK -> "kicked";
            case BAN -> "banned";
            case CHANNEL_RESTRICTION -> "restricted";
            default -> type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
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
            case TEMPORARY -> friendlyDuration(punishment.intent().length().temporary().orElseThrow());
        };
    }

    private static String friendlyDuration(Duration duration) {
        long days = duration.toDays();
        if (days > 0 && duration.equals(Duration.ofDays(days))) {
            return plural(days, "day");
        }
        long hours = duration.toHours();
        if (hours > 0 && duration.equals(Duration.ofHours(hours))) {
            return plural(hours, "hour");
        }
        long minutes = duration.toMinutes();
        if (minutes > 0 && duration.equals(Duration.ofMinutes(minutes))) {
            return plural(minutes, "minute");
        }
        return duration.toString();
    }

    private static String plural(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }

    record MinecraftBanNotification(
            String discordUserId,
            String minecraftName,
            String publicReason,
            Instant issuedAt,
            Optional<Instant> expiresAt
    ) {
        MinecraftBanNotification {
            if (discordUserId == null || discordUserId.isBlank()
                    || minecraftName == null || minecraftName.isBlank()
                    || publicReason == null || publicReason.isBlank()
                    || issuedAt == null || expiresAt == null) {
                throw new IllegalArgumentException("Minecraft ban notification is incomplete");
            }
        }
    }

    record MinecraftWarningOrMuteNotification(
            String discordUserId,
            String minecraftName,
            String publicReason,
            Instant issuedAt,
            SanctionType type,
            Optional<Instant> expiresAt
    ) {
        MinecraftWarningOrMuteNotification {
            if (discordUserId == null || discordUserId.isBlank()
                    || minecraftName == null || minecraftName.isBlank()
                    || publicReason == null || publicReason.isBlank()
                    || issuedAt == null || type == null || expiresAt == null
                    || (type != SanctionType.WARNING && type != SanctionType.MUTE)
                    || (type == SanctionType.WARNING && expiresAt.isPresent())) {
                throw new IllegalArgumentException(
                        "Minecraft warning/mute notification is incomplete");
            }
        }
    }
}
