package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.EnforcementTarget;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.EvidenceMetadata;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.SecurityLock;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.StoredEnforcementTarget;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.StoredEvidence;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/**
 * Verifies that an idempotent replay is actually the same logical request.
 *
 * <p>The repository tables own operation-key uniqueness. This guard prevents a caller from
 * accidentally reusing a key for a different subject, identity or resource and receiving an
 * unrelated successful result.</p>
 */
final class JdbcDiscordReplayGuard {
    private final DataSource dataSource;

    JdbcDiscordReplayGuard(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void verifyUnlinkReplay(
            VersionedLink stored,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            String operationKey
    ) {
        if (!discordUserId.equals(stored.link().discordUserId())
                || !minecraftPlayerId.equals(stored.link().minecraftPlayerId())
                || stored.revision() != expectedRevision + 1) {
            throw collision("Discord unlink");
        }
        requireOperationOwner(
                "SELECT link_id FROM discord_minecraft_links WHERE unlink_operation_key = ?",
                operationKey,
                stored.linkId(),
                "Discord unlink"
        );
    }

    void verifyEnforcementReplay(
            StoredEnforcementTarget stored,
            ModerationSubjectId subjectId,
            EnforcementTarget target,
            String operationKey
    ) {
        if (!subjectId.equals(stored.subjectId()) || !target.equals(stored.target())) {
            throw collision("enforcement target");
        }
        requireOperationOwner(
                "SELECT target_id FROM moderation_enforcement_targets WHERE operation_key = ?",
                operationKey,
                stored.targetId(),
                "enforcement target"
        );
    }

    void verifyEvidenceReplay(EvidenceMetadata requested, StoredEvidence stored) {
        if (!requested.evidenceId().equals(stored.evidenceId())) {
            throw collision("Discord evidence");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT evidence_id, subject_id, case_id, guild_id, channel_id,
                            message_id, author_user_id
                     FROM discord_evidence_metadata
                     WHERE operation_key = ?
                     """)) {
            statement.setString(1, requested.operationKey());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !requested.evidenceId().equals(UuidBytes.fromBytes(result.getBytes("evidence_id")))
                        || !requested.subjectId().value().equals(UuidBytes.fromBytes(result.getBytes("subject_id")))
                        || !Objects.equals(requested.caseId().orElse(null), result.getString("case_id"))
                        || !snowflake(requested.guildId()).equals(result.getBigDecimal("guild_id"))
                        || !snowflake(requested.channelId()).equals(result.getBigDecimal("channel_id"))
                        || !snowflake(requested.messageId()).equals(result.getBigDecimal("message_id"))
                        || !discordId(requested.authorUserId()).equals(result.getBigDecimal("author_user_id"))) {
                    throw collision("Discord evidence");
                }
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to verify Discord evidence replay", exception);
        }
    }

    void verifySecurityActivationReplay(
            SecurityLock stored,
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            String reasonCode,
            String operationKey
    ) {
        if (!subjectId.equals(stored.subjectId())
                || !discordUserId.equals(stored.discordUserId())
                || !reasonCode.equals(stored.reasonCode())) {
            throw collision("security-lock activation");
        }
        requireOperationOwner(
                "SELECT lock_id FROM discord_security_locks WHERE operation_key = ?",
                operationKey,
                stored.lockId(),
                "security-lock activation"
        );
    }

    void verifySecurityReleaseReplay(
            SecurityLock stored,
            UUID lockId,
            long expectedRevision,
            String operationKey
    ) {
        if (!lockId.equals(stored.lockId()) || stored.revision() != expectedRevision + 1) {
            throw collision("security-lock release");
        }
        requireOperationOwner(
                "SELECT lock_id FROM discord_security_locks WHERE release_operation_key = ?",
                operationKey,
                stored.lockId(),
                "security-lock release"
        );
    }

    private void requireOperationOwner(
            String sql,
            String operationKey,
            UUID expectedId,
            String operationName
    ) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !expectedId.equals(UuidBytes.fromBytes(result.getBytes(1)))) {
                    throw collision(operationName);
                }
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to verify " + operationName + " replay", exception);
        }
    }

    private static ModerationPersistenceException collision(String operationName) {
        return new ModerationPersistenceException(
                operationName + " operation key was reused for a different request"
        );
    }

    private static BigDecimal discordId(DiscordUserId userId) {
        return new BigDecimal(userId.value());
    }

    private static BigDecimal snowflake(String value) {
        return new BigDecimal(value);
    }
}
