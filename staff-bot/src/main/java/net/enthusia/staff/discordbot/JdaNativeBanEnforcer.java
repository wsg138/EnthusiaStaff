package net.enthusia.staff.discordbot;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** Owns native-ban idempotency and prevents D07 from adopting or removing external bans. */
final class JdaNativeBanEnforcer {
    private static final int MAX_AUDIT_REASON = 500;
    private static final String MARKER_PREFIX = "Enthusia D07 ban ";

    void preflight(Guild guild, DiscordUserId target) {
        if (findBan(guild, target) != null) {
            throw failure("NATIVE_BAN_ALREADY_PRESENT", false);
        }
    }

    void apply(Guild guild, DiscordPunishment punishment) {
        Guild.Ban existing = findBan(guild, punishment.targetUserId());
        if (existing != null) {
            if (ownedBy(existing, punishment.punishmentId())) {
                return;
            }
            throw failure("NATIVE_BAN_OWNERSHIP_CONFLICT", false);
        }
        UserSnowflake target = target(punishment.targetUserId());
        guild.ban(target, punishment.intent().messageDeleteSeconds(), TimeUnit.SECONDS)
                .reason(auditReason(punishment))
                .complete();
    }

    void reconcile(Guild guild, DiscordPunishment punishment) {
        Guild.Ban existing = findBan(guild, punishment.targetUserId());
        if (existing == null) {
            guild.ban(target(punishment.targetUserId()), 0, TimeUnit.SECONDS)
                    .reason(auditReason(punishment))
                    .complete();
            return;
        }
        if (!ownedBy(existing, punishment.punishmentId())) {
            throw failure("NATIVE_BAN_OWNERSHIP_CONFLICT", false);
        }
    }

    void remove(Guild guild, DiscordPunishment punishment) {
        Guild.Ban existing = findBan(guild, punishment.targetUserId());
        if (existing == null || !ownedBy(existing, punishment.punishmentId())) {
            return;
        }
        try {
            guild.unban(target(punishment.targetUserId()))
                    .reason("Enthusia D07 ban removal " + punishment.punishmentId())
                    .complete();
        } catch (ErrorResponseException exception) {
            if (!isUnknownBan(exception)) {
                throw exception;
            }
        }
    }

    static boolean ownsReason(String reason, UUID punishmentId) {
        return reason != null && reason.startsWith(marker(punishmentId));
    }

    static String marker(UUID punishmentId) {
        return MARKER_PREFIX + punishmentId + ":";
    }

    private static boolean ownedBy(Guild.Ban ban, UUID punishmentId) {
        return ownsReason(ban.getReason(), punishmentId);
    }

    private static Guild.Ban findBan(Guild guild, DiscordUserId target) {
        try {
            return guild.retrieveBan(target(target)).complete();
        } catch (ErrorResponseException exception) {
            if (isUnknownBan(exception)) {
                return null;
            }
            throw exception;
        }
    }

    private static boolean isUnknownBan(ErrorResponseException exception) {
        return "UNKNOWN_BAN".equals(exception.getErrorResponse().name());
    }

    private static UserSnowflake target(DiscordUserId target) {
        return UserSnowflake.fromId(target.value());
    }

    private static String auditReason(DiscordPunishment punishment) {
        String reason = marker(punishment.punishmentId()) + " " + punishment.intent().publicReason();
        return reason.length() <= MAX_AUDIT_REASON ? reason : reason.substring(0, MAX_AUDIT_REASON);
    }

    private static DiscordPunishmentGateway.EffectException failure(String code, boolean retryable) {
        return new DiscordPunishmentGateway.EffectException(code, retryable);
    }
}
