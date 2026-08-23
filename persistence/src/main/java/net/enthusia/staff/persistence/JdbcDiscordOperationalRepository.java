package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordGuildScope;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.EnforcementScope;
import net.enthusia.staff.domain.moderation.EnforcementTarget;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.MinecraftNetworkScope;
import net.enthusia.staff.domain.moderation.MinecraftServerScope;
import net.enthusia.staff.domain.moderation.ModerationIdentity;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.EvidenceMetadata;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.MaintenanceWork;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.SecurityLock;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.StoredEnforcementTarget;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.StoredEvidence;

final class JdbcDiscordOperationalRepository {
    private static final int MAX_MAINTENANCE_BATCH = 500;

    private final DataSource dataSource;

    JdbcDiscordOperationalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    StoredEnforcementTarget recordEnforcementTarget(
            ModerationSubjectId subjectId,
            EnforcementTarget target,
            String operationKey,
            Instant now
    ) {
        requirePresent(subjectId, "subjectId");
        requirePresent(target, "target");
        requireKey(operationKey, "operationKey", 128);
        requirePresent(now, "now");
        return JdbcTransactionSupport.execute(dataSource, "Unable to persist enforcement target", connection -> {
            StoredEnforcementTarget replay = enforcementByOperation(connection, operationKey);
            if (replay != null) {
                return asReplay(replay);
            }
            requireIdentityMembership(connection, subjectId, target.identity());
            UUID targetId = UUID.randomUUID();
            EncodedTarget encoded = encodeTarget(target);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO moderation_enforcement_targets(
                        target_id, operation_key, subject_id, platform,
                        minecraft_player_id, discord_user_id, scope_type, scope_value,
                        state, revision, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(targetId));
                statement.setString(2, operationKey);
                statement.setBytes(3, UuidBytes.toBytes(subjectId.value()));
                statement.setString(4, encoded.platform());
                if (encoded.minecraftPlayerId() == null) {
                    statement.setNull(5, Types.BINARY);
                } else {
                    statement.setBytes(5, UuidBytes.toBytes(encoded.minecraftPlayerId()));
                }
                if (encoded.discordUserId() == null) {
                    statement.setNull(6, Types.DECIMAL);
                } else {
                    statement.setBigDecimal(6, discordId(encoded.discordUserId()));
                }
                statement.setString(7, encoded.scopeType());
                statement.setString(8, encoded.scopeValue());
                statement.setTimestamp(9, Timestamp.from(now));
                statement.setTimestamp(10, Timestamp.from(now));
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "enforcement target was not inserted");
            }
            return new StoredEnforcementTarget(targetId, subjectId, target, "PENDING", 0, false, now, now);
        });
    }

    StoredEvidence recordEvidence(EvidenceMetadata metadata) {
        requirePresent(metadata, "metadata");
        return JdbcTransactionSupport.execute(dataSource, "Unable to persist Discord evidence metadata", connection -> {
            StoredEvidence replay = evidenceByOperation(connection, metadata.operationKey());
            if (replay != null) {
                return new StoredEvidence(
                        replay.evidenceId(),
                        replay.revision(),
                        true,
                        replay.retainUntil(),
                        replay.purgeState()
                );
            }
            requireSubject(connection, metadata.subjectId());
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO discord_evidence_metadata(
                        evidence_id, operation_key, subject_id, case_id,
                        guild_id, channel_id, message_id, author_user_id,
                        captured_at, retain_until, metadata_json, purge_state, revision
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(metadata.evidenceId()));
                statement.setString(2, metadata.operationKey());
                statement.setBytes(3, UuidBytes.toBytes(metadata.subjectId().value()));
                if (metadata.caseId().isPresent()) {
                    statement.setString(4, metadata.caseId().orElseThrow());
                } else {
                    statement.setNull(4, Types.CHAR);
                }
                statement.setBigDecimal(5, snowflake(metadata.guildId()));
                statement.setBigDecimal(6, snowflake(metadata.channelId()));
                statement.setBigDecimal(7, snowflake(metadata.messageId()));
                statement.setBigDecimal(8, discordId(metadata.authorUserId()));
                statement.setTimestamp(9, Timestamp.from(metadata.capturedAt()));
                statement.setTimestamp(10, Timestamp.from(metadata.retainUntil()));
                statement.setString(11, metadata.metadataJson());
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord evidence was not inserted");
            }
            enqueueMaintenance(
                    connection,
                    "EVIDENCE_RETENTION",
                    metadata.evidenceId().toString(),
                    metadata.retainUntil(),
                    metadata.capturedAt()
            );
            return new StoredEvidence(metadata.evidenceId(), 0, false, metadata.retainUntil(), "ACTIVE");
        });
    }

    SecurityLock activateSecurityLock(
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            String reasonCode,
            String operationKey,
            Instant now
    ) {
        requirePresent(subjectId, "subjectId");
        requirePresent(discordUserId, "discordUserId");
        requireKey(reasonCode, "reasonCode", 96);
        requireKey(operationKey, "operationKey", 128);
        requirePresent(now, "now");
        return JdbcTransactionSupport.execute(dataSource, "Unable to activate Discord security lock", connection -> {
            SecurityLock replay = securityLockByOperation(connection, operationKey, false);
            if (replay != null) {
                return replaySecurityLock(replay);
            }
            requireDiscordMembership(connection, subjectId, discordUserId);
            SecurityLock active = activeSecurityLock(connection, discordUserId, true);
            if (active != null) {
                if (!active.subjectId().equals(subjectId)) {
                    throw new SQLException("Discord user already has an active lock for another subject");
                }
                return replaySecurityLock(active);
            }
            UUID lockId = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO discord_security_locks(
                        lock_id, operation_key, subject_id, discord_user_id,
                        reason_code, state, locked_at, revision
                    ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(lockId));
                statement.setString(2, operationKey);
                statement.setBytes(3, UuidBytes.toBytes(subjectId.value()));
                statement.setBigDecimal(4, discordId(discordUserId));
                statement.setString(5, reasonCode);
                statement.setTimestamp(6, Timestamp.from(now));
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "security lock was not inserted");
            }
            return new SecurityLock(
                    lockId,
                    subjectId,
                    discordUserId,
                    reasonCode,
                    "ACTIVE",
                    now,
                    Optional.empty(),
                    0,
                    false
            );
        });
    }

    SecurityLock releaseSecurityLock(
            UUID lockId,
            long expectedRevision,
            String operationKey,
            Instant now
    ) {
        requirePresent(lockId, "lockId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        requireKey(operationKey, "operationKey", 128);
        requirePresent(now, "now");
        return JdbcTransactionSupport.execute(dataSource, "Unable to release Discord security lock", connection -> {
            SecurityLock replay = securityLockByReleaseOperation(connection, operationKey, false);
            if (replay != null) {
                return replaySecurityLock(replay);
            }
            SecurityLock current = securityLockById(connection, lockId, true);
            if (current == null || !"ACTIVE".equals(current.state())) {
                throw new SQLException("security lock is not active");
            }
            if (current.revision() != expectedRevision) {
                throw new SQLException("security lock revision changed before release");
            }
            if (now.isBefore(current.lockedAt())) {
                throw new SQLException("security lock release precedes activation");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE discord_security_locks
                    SET state = 'RELEASED', released_at = ?, release_operation_key = ?, revision = revision + 1
                    WHERE lock_id = ? AND state = 'ACTIVE' AND revision = ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setString(2, operationKey);
                statement.setBytes(3, UuidBytes.toBytes(lockId));
                statement.setLong(4, expectedRevision);
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "security lock release lost revision race");
            }
            return new SecurityLock(
                    current.lockId(),
                    current.subjectId(),
                    current.discordUserId(),
                    current.reasonCode(),
                    "RELEASED",
                    current.lockedAt(),
                    Optional.of(now),
                    expectedRevision + 1,
                    false
            );
        });
    }

    ReconciliationState saveReconciliation(
            ReconciliationState state,
            long expectedRevision,
            Instant now
    ) {
        requirePresent(state, "state");
        requirePresent(now, "now");
        if (expectedRevision < -1) {
            throw new IllegalArgumentException("expectedRevision must be -1 for create or nonnegative for update");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to persist Discord reconciliation state", connection -> {
            ReconciliationState current = reconciliationByKey(connection, state.reconciliationKey(), true);
            if (current == null) {
                if (expectedRevision != -1) {
                    throw new SQLException("reconciliation state does not exist at expected revision");
                }
                insertReconciliation(connection, state, now);
                return withRevision(state, 0);
            }
            if (current.revision() != expectedRevision) {
                throw new SQLException("reconciliation revision changed before update");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE discord_reconciliation_state
                    SET resource_type = ?, resource_id = ?, desired_state_json = ?, observed_state_json = ?,
                        state = ?, attempt_count = ?, next_attempt_at = ?, last_error_code = ?,
                        revision = revision + 1, updated_at = ?
                    WHERE reconciliation_key = ? AND revision = ?
                    """)) {
                bindReconciliation(statement, state, now);
                statement.setString(10, state.reconciliationKey());
                statement.setLong(11, expectedRevision);
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "reconciliation update lost revision race");
            }
            return withRevision(state, expectedRevision + 1);
        });
    }

    MaintenanceWork enqueueMaintenance(
            String workType,
            String resourceKey,
            Instant dueAt,
            Instant now
    ) {
        requireKey(workType, "workType", 48);
        requireKey(resourceKey, "resourceKey", 160);
        requirePresent(dueAt, "dueAt");
        requirePresent(now, "now");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to enqueue Discord maintenance work",
                connection -> enqueueMaintenance(connection, workType, resourceKey, dueAt, now)
        );
    }

    List<MaintenanceWork> claimDueMaintenance(
            Instant now,
            int limit,
            String leaseOwner,
            Instant leaseUntil
    ) {
        requirePresent(now, "now");
        if (limit < 1 || limit > MAX_MAINTENANCE_BATCH) {
            throw new IllegalArgumentException("maintenance claim limit must be between 1 and 500");
        }
        requireKey(leaseOwner, "leaseOwner", 96);
        requirePresent(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to claim Discord maintenance work", connection -> {
            List<MaintenanceWork> candidates = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT work_id, work_type, resource_key, due_at, state,
                           lease_owner, lease_until, attempt_count, revision
                    FROM discord_maintenance_work
                    WHERE due_at <= ?
                      AND (state = 'PENDING' OR (state = 'CLAIMED' AND lease_until <= ?))
                    ORDER BY due_at, work_id
                    LIMIT ?
                    FOR UPDATE
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setInt(3, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        candidates.add(readMaintenance(result));
                    }
                }
            }
            List<MaintenanceWork> claimed = new ArrayList<>(candidates.size());
            for (MaintenanceWork candidate : candidates) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE discord_maintenance_work
                        SET state = 'CLAIMED', lease_owner = ?, lease_until = ?,
                            attempt_count = attempt_count + 1, revision = revision + 1, updated_at = ?
                        WHERE work_id = ? AND revision = ?
                        """)) {
                    statement.setString(1, leaseOwner);
                    statement.setTimestamp(2, Timestamp.from(leaseUntil));
                    statement.setTimestamp(3, Timestamp.from(now));
                    statement.setBytes(4, UuidBytes.toBytes(candidate.workId()));
                    statement.setLong(5, candidate.revision());
                    JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "maintenance claim lost revision race");
                }
                claimed.add(new MaintenanceWork(
                        candidate.workId(),
                        candidate.workType(),
                        candidate.resourceKey(),
                        candidate.dueAt(),
                        "CLAIMED",
                        Optional.of(leaseOwner),
                        Optional.of(leaseUntil),
                        candidate.attemptCount() + 1,
                        candidate.revision() + 1
                ));
            }
            return List.copyOf(claimed);
        });
    }

    boolean completeMaintenance(
            UUID workId,
            long expectedRevision,
            String leaseOwner,
            Instant now
    ) {
        requirePresent(workId, "workId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        requireKey(leaseOwner, "leaseOwner", 96);
        requirePresent(now, "now");
        return JdbcTransactionSupport.execute(dataSource, "Unable to complete Discord maintenance work", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE discord_maintenance_work
                    SET state = 'COMPLETE', lease_owner = NULL, lease_until = NULL,
                        revision = revision + 1, updated_at = ?
                    WHERE work_id = ? AND revision = ? AND state = 'CLAIMED' AND lease_owner = ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setBytes(2, UuidBytes.toBytes(workId));
                statement.setLong(3, expectedRevision);
                statement.setString(4, leaseOwner);
                return JdbcTransactionSupport.updatedOne(statement.executeUpdate());
            }
        });
    }

    private static StoredEnforcementTarget enforcementByOperation(Connection connection, String operationKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT target_id, subject_id, platform, minecraft_player_id, discord_user_id,
                       scope_type, scope_value, state, revision, created_at, updated_at
                FROM moderation_enforcement_targets WHERE operation_key = ?
                """)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new StoredEnforcementTarget(
                        UuidBytes.fromBytes(result.getBytes("target_id")),
                        new ModerationSubjectId(UuidBytes.fromBytes(result.getBytes("subject_id"))),
                        decodeTarget(result),
                        result.getString("state"),
                        result.getLong("revision"),
                        false,
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant()
                );
            }
        }
    }

    private static StoredEnforcementTarget asReplay(StoredEnforcementTarget value) {
        return new StoredEnforcementTarget(
                value.targetId(),
                value.subjectId(),
                value.target(),
                value.state(),
                value.revision(),
                true,
                value.createdAt(),
                value.updatedAt()
        );
    }

    private static StoredEvidence evidenceByOperation(Connection connection, String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT evidence_id, revision, retain_until, purge_state
                FROM discord_evidence_metadata WHERE operation_key = ?
                """)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new StoredEvidence(
                                UuidBytes.fromBytes(result.getBytes("evidence_id")),
                                result.getLong("revision"),
                                false,
                                result.getTimestamp("retain_until").toInstant(),
                                result.getString("purge_state")
                        )
                        : null;
            }
        }
    }

    private static SecurityLock securityLockByOperation(Connection connection, String key, boolean lock)
            throws SQLException {
        return securityLockByKey(connection, "operation_key", key, lock);
    }

    private static SecurityLock securityLockByReleaseOperation(Connection connection, String key, boolean lock)
            throws SQLException {
        return securityLockByKey(connection, "release_operation_key", key, lock);
    }

    private static SecurityLock securityLockByKey(Connection connection, String column, String key, boolean lock)
            throws SQLException {
        String sql = """
                SELECT lock_id, subject_id, discord_user_id, reason_code, state,
                       locked_at, released_at, revision
                FROM discord_security_locks WHERE %s = ?
                """.formatted(column) + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readSecurityLock(result, false) : null;
            }
        }
    }

    private static SecurityLock activeSecurityLock(Connection connection, DiscordUserId userId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT lock_id, subject_id, discord_user_id, reason_code, state,
                       locked_at, released_at, revision
                FROM discord_security_locks
                WHERE discord_user_id = ? AND state = 'ACTIVE'
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, discordId(userId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readSecurityLock(result, false) : null;
            }
        }
    }

    private static SecurityLock securityLockById(Connection connection, UUID lockId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT lock_id, subject_id, discord_user_id, reason_code, state,
                       locked_at, released_at, revision
                FROM discord_security_locks WHERE lock_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(lockId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readSecurityLock(result, false) : null;
            }
        }
    }

    private static SecurityLock readSecurityLock(ResultSet result, boolean replayed) throws SQLException {
        Timestamp released = result.getTimestamp("released_at");
        return new SecurityLock(
                UuidBytes.fromBytes(result.getBytes("lock_id")),
                new ModerationSubjectId(UuidBytes.fromBytes(result.getBytes("subject_id"))),
                discordUserId(result.getBigDecimal("discord_user_id")),
                result.getString("reason_code"),
                result.getString("state"),
                result.getTimestamp("locked_at").toInstant(),
                released == null ? Optional.empty() : Optional.of(released.toInstant()),
                result.getLong("revision"),
                replayed
        );
    }

    private static SecurityLock replaySecurityLock(SecurityLock value) {
        return new SecurityLock(
                value.lockId(),
                value.subjectId(),
                value.discordUserId(),
                value.reasonCode(),
                value.state(),
                value.lockedAt(),
                value.releasedAt(),
                value.revision(),
                true
        );
    }

    private static ReconciliationState reconciliationByKey(Connection connection, String key, boolean lock)
            throws SQLException {
        String sql = """
                SELECT reconciliation_key, resource_type, resource_id, desired_state_json,
                       observed_state_json, state, attempt_count, next_attempt_at,
                       last_error_code, revision
                FROM discord_reconciliation_state WHERE reconciliation_key = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Timestamp nextAttempt = result.getTimestamp("next_attempt_at");
                return new ReconciliationState(
                        result.getString("reconciliation_key"),
                        result.getString("resource_type"),
                        result.getString("resource_id"),
                        result.getString("desired_state_json"),
                        Optional.ofNullable(result.getString("observed_state_json")),
                        result.getString("state"),
                        result.getInt("attempt_count"),
                        nextAttempt == null ? Optional.empty() : Optional.of(nextAttempt.toInstant()),
                        Optional.ofNullable(result.getString("last_error_code")),
                        result.getLong("revision")
                );
            }
        }
    }

    private static void insertReconciliation(Connection connection, ReconciliationState state, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_reconciliation_state(
                    reconciliation_key, resource_type, resource_id, desired_state_json,
                    observed_state_json, state, attempt_count, next_attempt_at,
                    last_error_code, revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """)) {
            statement.setString(1, state.reconciliationKey());
            bindReconciliation(statement, state, now, 2);
            statement.setTimestamp(10, Timestamp.from(now));
            statement.setTimestamp(11, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "reconciliation state was not inserted");
        }
    }

    private static void bindReconciliation(PreparedStatement statement, ReconciliationState state, Instant now)
            throws SQLException {
        bindReconciliation(statement, state, now, 1);
    }

    private static void bindReconciliation(
            PreparedStatement statement,
            ReconciliationState state,
            Instant now,
            int offset
    ) throws SQLException {
        statement.setString(offset, state.resourceType());
        statement.setString(offset + 1, state.resourceId());
        statement.setString(offset + 2, state.desiredStateJson());
        if (state.observedStateJson().isPresent()) {
            statement.setString(offset + 3, state.observedStateJson().orElseThrow());
        } else {
            statement.setNull(offset + 3, Types.VARCHAR);
        }
        statement.setString(offset + 4, state.state());
        statement.setInt(offset + 5, state.attemptCount());
        if (state.nextAttemptAt().isPresent()) {
            statement.setTimestamp(offset + 6, Timestamp.from(state.nextAttemptAt().orElseThrow()));
        } else {
            statement.setNull(offset + 6, Types.TIMESTAMP);
        }
        if (state.lastErrorCode().isPresent()) {
            statement.setString(offset + 7, state.lastErrorCode().orElseThrow());
        } else {
            statement.setNull(offset + 7, Types.VARCHAR);
        }
        statement.setTimestamp(offset + 8, Timestamp.from(now));
    }

    private static ReconciliationState withRevision(ReconciliationState value, long revision) {
        return new ReconciliationState(
                value.reconciliationKey(),
                value.resourceType(),
                value.resourceId(),
                value.desiredStateJson(),
                value.observedStateJson(),
                value.state(),
                value.attemptCount(),
                value.nextAttemptAt(),
                value.lastErrorCode(),
                revision
        );
    }

    private static MaintenanceWork enqueueMaintenance(
            Connection connection,
            String workType,
            String resourceKey,
            Instant dueAt,
            Instant now
    ) throws SQLException {
        UUID workId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_maintenance_work(
                    work_id, work_type, resource_key, due_at, state,
                    attempt_count, revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING', 0, 0, ?, ?)
                ON DUPLICATE KEY UPDATE
                    due_at = LEAST(due_at, VALUES(due_at)),
                    updated_at = VALUES(updated_at)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(workId));
            statement.setString(2, workType);
            statement.setString(3, resourceKey);
            statement.setTimestamp(4, Timestamp.from(dueAt));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            int count = statement.executeUpdate();
            if (count < 1 || count > 2) {
                throw new SQLException("unexpected maintenance enqueue update count: " + count);
            }
        }
        return maintenanceByResource(connection, workType, resourceKey);
    }

    private static MaintenanceWork maintenanceByResource(
            Connection connection,
            String workType,
            String resourceKey
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT work_id, work_type, resource_key, due_at, state,
                       lease_owner, lease_until, attempt_count, revision
                FROM discord_maintenance_work WHERE work_type = ? AND resource_key = ?
                """)) {
            statement.setString(1, workType);
            statement.setString(2, resourceKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("maintenance work disappeared after enqueue");
                }
                return readMaintenance(result);
            }
        }
    }

    private static MaintenanceWork readMaintenance(ResultSet result) throws SQLException {
        Timestamp leaseUntil = result.getTimestamp("lease_until");
        return new MaintenanceWork(
                UuidBytes.fromBytes(result.getBytes("work_id")),
                result.getString("work_type"),
                result.getString("resource_key"),
                result.getTimestamp("due_at").toInstant(),
                result.getString("state"),
                Optional.ofNullable(result.getString("lease_owner")),
                leaseUntil == null ? Optional.empty() : Optional.of(leaseUntil.toInstant()),
                result.getInt("attempt_count"),
                result.getLong("revision")
        );
    }

    private static void requireSubject(Connection connection, ModerationSubjectId subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM moderation_subjects WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("moderation subject does not exist");
                }
            }
        }
    }

    private static void requireIdentityMembership(
            Connection connection,
            ModerationSubjectId subjectId,
            ModerationIdentity identity
    ) throws SQLException {
        if (identity instanceof MinecraftIdentityRef minecraft) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT 1 FROM moderation_subject_minecraft_identities
                    WHERE subject_id = ? AND player_id = ?
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
                statement.setBytes(2, UuidBytes.toBytes(minecraft.playerId()));
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return;
                    }
                }
            }
        } else if (identity instanceof DiscordIdentityRef discord) {
            requireDiscordMembership(connection, subjectId, discord.userId());
            return;
        }
        throw new SQLException("enforcement identity does not belong to moderation subject");
    }

    private static void requireDiscordMembership(
            Connection connection,
            ModerationSubjectId subjectId,
            DiscordUserId userId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM moderation_subject_discord_identities
                WHERE subject_id = ? AND discord_user_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBigDecimal(2, discordId(userId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Discord identity does not belong to moderation subject");
                }
            }
        }
    }

    private static EncodedTarget encodeTarget(EnforcementTarget target) {
        ModerationIdentity identity = target.identity();
        EnforcementScope scope = target.scope();
        if (identity instanceof DiscordIdentityRef discord && scope instanceof DiscordGuildScope guild) {
            return new EncodedTarget(
                    "DISCORD",
                    null,
                    discord.userId(),
                    "DISCORD_GUILD",
                    guild.guildId().value()
            );
        }
        if (identity instanceof MinecraftIdentityRef minecraft && scope instanceof MinecraftServerScope server) {
            return new EncodedTarget(
                    "MINECRAFT",
                    minecraft.playerId(),
                    null,
                    "MINECRAFT_SERVER",
                    server.serverId()
            );
        }
        if (identity instanceof MinecraftIdentityRef minecraft && scope instanceof MinecraftNetworkScope) {
            return new EncodedTarget(
                    "MINECRAFT",
                    minecraft.playerId(),
                    null,
                    "MINECRAFT_NETWORK",
                    "network"
            );
        }
        throw new IllegalArgumentException("unsupported enforcement target contract");
    }

    private static EnforcementTarget decodeTarget(ResultSet result) throws SQLException {
        String platform = result.getString("platform");
        String scopeType = result.getString("scope_type");
        if ("DISCORD".equals(platform)) {
            DiscordIdentityRef identity = new DiscordIdentityRef(
                    discordUserId(result.getBigDecimal("discord_user_id"))
            );
            return new EnforcementTarget(
                    identity,
                    new DiscordGuildScope(new DiscordGuildId(result.getString("scope_value")))
            );
        }
        MinecraftIdentityRef identity = new MinecraftIdentityRef(
                UuidBytes.fromBytes(result.getBytes("minecraft_player_id"))
        );
        return switch (scopeType) {
            case "MINECRAFT_SERVER" -> new EnforcementTarget(
                    identity,
                    new MinecraftServerScope(result.getString("scope_value"))
            );
            case "MINECRAFT_NETWORK" -> new EnforcementTarget(identity, new MinecraftNetworkScope());
            default -> throw new SQLException("unknown Minecraft enforcement scope: " + scopeType);
        };
    }

    private static BigDecimal discordId(DiscordUserId userId) {
        return new BigDecimal(userId.value());
    }

    private static DiscordUserId discordUserId(BigDecimal value) {
        return new DiscordUserId(value.toBigIntegerExact().toString());
    }

    private static BigDecimal snowflake(String value) {
        if (value == null || !value.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("Discord snowflake must contain 1-20 decimal digits");
        }
        BigDecimal parsed = new BigDecimal(value);
        if (parsed.compareTo(new BigDecimal("18446744073709551615")) > 0) {
            throw new IllegalArgumentException("Discord snowflake exceeds unsigned 64-bit range");
        }
        return parsed;
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
    }

    private static void requireKey(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " must be nonblank and at most " + maximumLength + " characters"
            );
        }
    }

    private record EncodedTarget(
            String platform,
            UUID minecraftPlayerId,
            DiscordUserId discordUserId,
            String scopeType,
            String scopeValue
    ) {
    }
}
