package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.enthusia.staff.domain.discord.DiscordPunishment;

/** Proves managed-mute ownership across a crash without adopting externally assigned roles. */
final class JdaMuteRoleOwnership {
    private static final int AUDIT_LIMIT = 100;
    private static final int MAX_AUDIT_REASON = 500;
    private static final Duration AUDIT_CLOCK_SKEW = Duration.ofMinutes(1);
    private static final String MARKER_PREFIX = "Enthusia D07 mute ";

    record Observation(ActionType type, long targetId, long actorId, String reason, Instant createdAt) {
    }

    void requirePermissions(Guild guild) {
        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES)) {
            throw failure("MUTE_ROLE_MANAGE_PERMISSION_MISSING", false);
        }
        if (!self.hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            throw failure("MUTE_AUDIT_LOG_PERMISSION_MISSING", false);
        }
    }

    static void requireFreshRoleAbsent(boolean rolePresent) {
        if (rolePresent) {
            throw failure("MUTE_ROLE_ALREADY_PRESENT", false);
        }
    }

    void requireOwnedApplyRetry(Guild guild, DiscordPunishment punishment) {
        long targetId = Long.parseUnsignedLong(punishment.targetUserId().value());
        long actorId = guild.getSelfMember().getIdLong();
        List<AuditLogEntry> entries = guild.retrieveAuditLogs()
                .type(ActionType.MEMBER_ROLE_UPDATE)
                .user(guild.getSelfMember())
                .limit(AUDIT_LIMIT)
                .complete();
        boolean owned = entries.stream()
                .map(JdaMuteRoleOwnership::observation)
                .anyMatch(entry -> provesOwnership(
                        entry,
                        punishment.punishmentId(),
                        targetId,
                        actorId,
                        punishment.issuedAt()
                ));
        if (!owned) {
            throw failure("MUTE_ROLE_OWNERSHIP_UNVERIFIED", true);
        }
    }

    String auditReason(DiscordPunishment punishment) {
        String reason = marker(punishment.punishmentId()) + " " + punishment.intent().publicReason();
        return reason.length() <= MAX_AUDIT_REASON ? reason : reason.substring(0, MAX_AUDIT_REASON);
    }

    static boolean provesOwnership(
            Observation observation,
            UUID punishmentId,
            long targetId,
            long actorId,
            Instant issuedAt
    ) {
        if (observation == null || punishmentId == null || issuedAt == null) {
            return false;
        }
        return observation.type() == ActionType.MEMBER_ROLE_UPDATE
                && observation.targetId() == targetId
                && observation.actorId() == actorId
                && ownsReason(observation.reason(), punishmentId)
                && !observation.createdAt().isBefore(issuedAt.minus(AUDIT_CLOCK_SKEW));
    }

    static String marker(UUID punishmentId) {
        return MARKER_PREFIX + punishmentId + ":";
    }

    private static boolean ownsReason(String reason, UUID punishmentId) {
        return reason != null && reason.startsWith(marker(punishmentId));
    }

    private static Observation observation(AuditLogEntry entry) {
        return new Observation(
                entry.getType(),
                entry.getTargetIdLong(),
                entry.getUserIdLong(),
                entry.getReason(),
                entry.getTimeCreated().toInstant()
        );
    }

    private static DiscordPunishmentGateway.EffectException failure(String code, boolean retryable) {
        return new DiscordPunishmentGateway.EffectException(code, retryable);
    }
}
