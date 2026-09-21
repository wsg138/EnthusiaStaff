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
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.enthusia.staff.domain.discord.DiscordPunishment;

/** Makes one-shot kicks crash-safe without risking duplicate enforcement on a reclaimed lease. */
final class JdaKickEnforcer {
    private static final int AUDIT_LIMIT = 100;
    private static final int MAX_AUDIT_REASON = 500;
    private static final Duration AUDIT_CLOCK_SKEW = Duration.ofMinutes(1);
    private static final String MARKER_PREFIX = "Enthusia D07 kick ";

    record Observation(ActionType type, long targetId, long actorId, String reason, Instant createdAt) {
    }

    void preflight(Guild guild) {
        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.KICK_MEMBERS)) {
            throw failure("KICK_PERMISSION_MISSING", false);
        }
        if (!self.hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            throw failure("KICK_AUDIT_LOG_PERMISSION_MISSING", false);
        }
    }

    void apply(Guild guild, Member target, DiscordPunishment punishment, int attemptCount) {
        boolean mayDispatch = DiscordKickRetryPolicy.mayDispatch(punishment, attemptCount);
        preflight(guild);
        if (ownedKickExists(guild, punishment)) {
            return;
        }
        if (!mayDispatch) {
            throw failure(DiscordKickRetryPolicy.RESULT_AMBIGUOUS, false);
        }
        if (target == null) {
            return;
        }
        dispatch(guild, punishment);
    }

    private void dispatch(Guild guild, DiscordPunishment punishment) {
        try {
            guild.kick(UserSnowflake.fromId(punishment.targetUserId().value()))
                    .reason(auditReason(punishment))
                    .complete();
        } catch (RuntimeException failure) {
            throw classifyDispatchFailure(failure);
        }
    }

    static DiscordPunishmentGateway.EffectException classifyDispatchFailure(RuntimeException failure) {
        if (failure instanceof DiscordPunishmentGateway.EffectException effect) {
            return effect;
        }
        if (failure instanceof InsufficientPermissionException || failure instanceof HierarchyException) {
            return failure("APPLY_PERMISSION_DENIED", false, failure);
        }
        if (failure instanceof ErrorResponseException response) {
            String code = response.getErrorResponse().name();
            if (!retryableCode(code)) {
                return failure("APPLY_DISCORD_" + code, false, failure);
            }
        }
        return failure(DiscordKickRetryPolicy.RESULT_AMBIGUOUS, false, failure);
    }

    private boolean ownedKickExists(Guild guild, DiscordPunishment punishment) {
        long targetId = Long.parseUnsignedLong(punishment.targetUserId().value());
        long actorId = guild.getSelfMember().getIdLong();
        List<AuditLogEntry> entries = guild.retrieveAuditLogs()
                .type(ActionType.KICK)
                .user(guild.getSelfMember())
                .limit(AUDIT_LIMIT)
                .complete();
        return entries.stream()
                .map(JdaKickEnforcer::observation)
                .anyMatch(entry -> provesOwnership(
                        entry,
                        punishment.punishmentId(),
                        targetId,
                        actorId,
                        punishment.issuedAt()
                ));
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
        return observation.type() == ActionType.KICK
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

    private static String auditReason(DiscordPunishment punishment) {
        String reason = marker(punishment.punishmentId()) + " " + punishment.intent().publicReason();
        return reason.length() <= MAX_AUDIT_REASON ? reason : reason.substring(0, MAX_AUDIT_REASON);
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

    private static boolean retryableCode(String code) {
        return code.contains("SERVER") || code.contains("TEMPORAR") || code.contains("RATE_LIMIT");
    }

    private static DiscordPunishmentGateway.EffectException failure(String code, boolean retryable) {
        return new DiscordPunishmentGateway.EffectException(code, retryable);
    }

    private static DiscordPunishmentGateway.EffectException failure(
            String code,
            boolean retryable,
            Throwable cause
    ) {
        return new DiscordPunishmentGateway.EffectException(code, retryable, cause);
    }
}
