package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.enthusia.staff.domain.discord.DiscordPunishment;

/** Proves managed-mute ownership across a crash without adopting externally assigned roles. */
final class JdaMuteRoleOwnership {
    private static final int AUDIT_LIMIT = 100;
    private static final int MAX_AUDIT_REASON = 500;
    private static final Duration AUDIT_CLOCK_SKEW = Duration.ofMinutes(1);
    private static final String MARKER_PREFIX = "Enthusia D07 mute ";

    record Observation(
            ActionType type,
            long targetId,
            long actorId,
            String reason,
            Instant createdAt,
            Set<Long> addedRoleIds,
            Set<Long> removedRoleIds
    ) {
        Observation {
            addedRoleIds = Set.copyOf(addedRoleIds);
            removedRoleIds = Set.copyOf(removedRoleIds);
        }

        Observation(ActionType type, long targetId, long actorId, String reason, Instant createdAt) {
            this(type, targetId, actorId, reason, createdAt, Set.of(), Set.of());
        }

        boolean changes(long roleId) {
            return addedRoleIds.contains(roleId) || removedRoleIds.contains(roleId);
        }
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

    void requireOwnedCurrentRole(Guild guild, Role role, DiscordPunishment punishment) {
        requirePermissions(guild);
        long targetId = Long.parseLong(punishment.targetUserId().value());
        long actorId = guild.getSelfMember().getIdLong();
        List<Observation> observations = guild.retrieveAuditLogs()
                .type(ActionType.MEMBER_ROLE_UPDATE)
                .limit(AUDIT_LIMIT)
                .complete().stream()
                .map(JdaMuteRoleOwnership::observation)
                .toList();
        boolean owned = provesCurrentOwnership(
                observations,
                punishment.punishmentId(),
                targetId,
                actorId,
                role.getIdLong(),
                punishment.issuedAt()
        );
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

    static boolean provesCurrentOwnership(
            List<Observation> observations,
            UUID punishmentId,
            long targetId,
            long actorId,
            long roleId,
            Instant issuedAt
    ) {
        if (observations == null || punishmentId == null || issuedAt == null) {
            return false;
        }
        return observations.stream()
                .filter(entry -> entry.type() == ActionType.MEMBER_ROLE_UPDATE)
                .filter(entry -> entry.targetId() == targetId && entry.changes(roleId))
                .filter(entry -> !entry.createdAt().isBefore(issuedAt.minus(AUDIT_CLOCK_SKEW)))
                .max(java.util.Comparator.comparing(Observation::createdAt))
                .map(entry -> entry.addedRoleIds().contains(roleId)
                        && !entry.removedRoleIds().contains(roleId)
                        && provesOwnership(entry, punishmentId, targetId, actorId, issuedAt))
                .orElse(false);
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
                entry.getTimeCreated().toInstant(),
                roleIds(entry.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD)),
                roleIds(entry.getChangeByKey(AuditLogKey.MEMBER_ROLES_REMOVE))
        );
    }

    private static Set<Long> roleIds(AuditLogChange change) {
        if (change == null || !(change.getNewValue() instanceof List<?> values)) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> role && role.get("id") != null) {
                addRoleId(ids, role.get("id"));
            }
        }
        return Set.copyOf(ids);
    }

    private static void addRoleId(Set<Long> ids, Object raw) {
        try {
            ids.add(Long.parseLong(raw.toString()));
        } catch (NumberFormatException ignored) {
            // Malformed audit data cannot prove ownership.
        }
    }

    private static DiscordPunishmentGateway.EffectException failure(String code, boolean retryable) {
        return new DiscordPunishmentGateway.EffectException(code, retryable);
    }
}
