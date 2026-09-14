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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;

/** V19-backed D07 repository. No D07 migration is required. */
public final class JdbcDiscordPunishmentRepository implements DiscordPunishmentRepository {
    private static final String RESOURCE_TYPE = "D07_DISCORD_PUNISHMENT";
    private static final String KEY_PREFIX = "d07:punishment:";
    private static final int MAX_ACTIVE_QUERY = 500;

    private final DataSource dataSource;
    private final DiscordPunishmentJsonCodec codec = new DiscordPunishmentJsonCodec();
    private final JdbcDiscordPunishmentWorkQueue workQueue = new JdbcDiscordPunishmentWorkQueue();

    public JdbcDiscordPunishmentRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public StoredPunishment create(DiscordPunishment punishment, String operationKey, Instant now) {
        validateCreate(punishment, operationKey, now);
        return JdbcTransactionSupport.execute(dataSource, "Unable to create Discord punishment", connection -> {
            Current replay = byOperation(connection, operationKey, true);
            if (replay != null) {
                requireReplay(replay.punishment(), punishment);
                return replay.stored(true);
            }
            requireDiscordMembership(connection, punishment);
            requireNoConflictingActive(connection, punishment);
            insertTarget(connection, punishment, operationKey, now);
            insertReconciliation(connection, punishment, now);
            workQueue.upsert(connection, punishment.punishmentId(), new WorkSchedule(WorkType.APPLY, now), now);
            return new StoredPunishment(punishment, 0, false);
        });
    }

    @Override
    public Optional<StoredPunishment> find(UUID punishmentId) {
        if (punishmentId == null) {
            throw new IllegalArgumentException("punishmentId must be present");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read Discord punishment", connection ->
                Optional.ofNullable(byId(connection, punishmentId, false)).map(current -> current.stored(false)));
    }

    @Override
    public List<StoredPunishment> activeForTarget(
            DiscordGuildId guildId,
            DiscordUserId userId,
            DiscordConsequenceType type,
            int limit
    ) {
        validateActiveQuery(guildId, userId, type, limit);
        return JdbcTransactionSupport.execute(dataSource, "Unable to read active Discord punishment", connection ->
                activeForTarget(connection, guildId, userId, type, limit));
    }

    @Override
    public StoredPunishment transition(
            StoredPunishment expected,
            DiscordPunishment replacement,
            String operationKey,
            List<WorkSchedule> work,
            Instant now
    ) {
        validateTransition(expected, replacement, operationKey, work, now);
        return JdbcTransactionSupport.execute(dataSource, "Unable to transition Discord punishment", connection -> {
            Current current = requireCurrent(connection, replacement.punishmentId());
            if (operationKey.equals(current.punishment().lastTransitionOperationKey().orElse(null))) {
                requireReplay(current.punishment(), replacement);
                return current.stored(true);
            }
            requireRevision(current, expected.revision());
            requireImmutable(current.punishment(), replacement);
            updateState(connection, current, replacement, current.attemptCount(), work, now);
            for (WorkSchedule schedule : work) {
                workQueue.upsert(connection, replacement.punishmentId(), schedule, now);
            }
            return new StoredPunishment(replacement, expected.revision() + 1, false);
        });
    }

    @Override
    public List<WorkLease> claimDue(Instant now, int limit, String leaseOwner, Instant leaseUntil) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to claim D07 Discord work", connection ->
                workQueue.claimDue(connection, now, limit, leaseOwner, leaseUntil));
    }

    @Override
    public StoredPunishment settle(
            WorkLease work,
            StoredPunishment expected,
            DiscordPunishment replacement,
            List<WorkSchedule> nextWork,
            Instant now
    ) {
        validateSettlement(work, expected, replacement, nextWork, now);
        return JdbcTransactionSupport.execute(dataSource, "Unable to settle D07 Discord work", connection -> {
            Current current = requireCurrent(connection, replacement.punishmentId());
            requireRevision(current, expected.revision());
            requireImmutable(current.punishment(), replacement);
            updateState(connection, current, replacement, work.attemptCount(), nextWork, now);
            workQueue.finishAndSchedule(connection, work, nextWork, now);
            return new StoredPunishment(replacement, expected.revision() + 1, false);
        });
    }

    @Override
    public List<StoredPunishment> activeNativeBans(DiscordGuildId guildId, int limit) {
        return activeForGuildType(guildId, DiscordConsequenceType.BAN, limit);
    }

    private List<StoredPunishment> activeForTarget(
            Connection connection,
            DiscordGuildId guildId,
            DiscordUserId userId,
            DiscordConsequenceType type,
            int limit
    ) throws SQLException {
        return queryActive(connection, guildId, Optional.of(userId), type, limit, false);
    }

    private List<StoredPunishment> activeForGuildType(
            DiscordGuildId guildId,
            DiscordConsequenceType type,
            int limit
    ) {
        if (guildId == null || type == null || limit < 1 || limit > MAX_ACTIVE_QUERY) {
            throw new IllegalArgumentException("active Discord punishment query is invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read active Discord punishments", connection ->
                queryActive(connection, guildId, Optional.empty(), type, limit, false));
    }

    private List<StoredPunishment> queryActive(
            Connection connection,
            DiscordGuildId guildId,
            Optional<DiscordUserId> userId,
            DiscordConsequenceType type,
            int limit,
            boolean lock
    ) throws SQLException {
        List<StoredPunishment> result = new ArrayList<>();
        String targetPredicate = userId.isPresent()
                ? " AND JSON_UNQUOTE(JSON_EXTRACT(desired_state_json, '$.targetUserId')) = ?"
                : "";
        String lockSuffix = lock ? " FOR UPDATE" : "";
        String sql = """
                SELECT desired_state_json, revision
                FROM discord_reconciliation_state
                WHERE resource_type = ?
                  AND state IN ('PENDING_APPLY', 'RETRY_APPLY', 'APPLIED',
                                'PENDING_REMOVE', 'RETRY_REMOVE', 'FAILED_REMOVE')
                  AND JSON_UNQUOTE(JSON_EXTRACT(desired_state_json, '$.guildId')) = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(desired_state_json, '$.type')) = ?
                """ + targetPredicate + " ORDER BY updated_at DESC, reconciliation_key DESC LIMIT ?" + lockSuffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, RESOURCE_TYPE);
            statement.setString(index++, guildId.value());
            statement.setString(index++, type.name());
            if (userId.isPresent()) {
                statement.setString(index++, userId.orElseThrow().value());
            }
            statement.setInt(index, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new StoredPunishment(
                            codec.decode(rows.getString("desired_state_json")),
                            rows.getLong("revision"),
                            false
                    ));
                }
            }
        }
        return List.copyOf(result);
    }

    private void requireNoConflictingActive(
            Connection connection,
            DiscordPunishment punishment
    ) throws SQLException {
        DiscordConsequenceType type = punishment.intent().type();
        if (type == DiscordConsequenceType.WARNING || type == DiscordConsequenceType.KICK) {
            return;
        }
        List<StoredPunishment> active = queryActive(
                connection,
                punishment.guildId(),
                Optional.of(punishment.targetUserId()),
                type,
                MAX_ACTIVE_QUERY,
                true
        );
        if (DiscordPunishmentConflictPolicy.conflicts(punishment, active)) {
            throw new SQLException("conflicting active Discord punishment already exists");
        }
    }

    private Current requireCurrent(Connection connection, UUID punishmentId) throws SQLException {
        Current current = byId(connection, punishmentId, true);
        if (current == null) {
            throw new SQLException("Discord punishment does not exist");
        }
        return current;
    }

    private Current byId(Connection connection, UUID punishmentId, boolean lock) throws SQLException {
        String lockSuffix = lock ? " FOR UPDATE" : "";
        String sql = """
                SELECT r.desired_state_json, r.attempt_count, r.revision AS reconciliation_revision,
                       e.revision AS target_revision
                FROM discord_reconciliation_state r
                JOIN moderation_enforcement_targets e ON e.target_id = ?
                WHERE r.resource_type = ? AND r.resource_id = ? AND r.reconciliation_key = ?
                """ + lockSuffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            statement.setString(2, RESOURCE_TYPE);
            statement.setString(3, punishmentId.toString());
            statement.setString(4, key(punishmentId));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readCurrent(rows) : null;
            }
        }
    }

    private Current byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        UUID punishmentId = targetIdForOperation(connection, operationKey, lock);
        return punishmentId == null ? null : byId(connection, punishmentId, lock);
    }

    private UUID targetIdForOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT target_id
                FROM moderation_enforcement_targets
                WHERE operation_key = ?
                """ + suffix)) {
            statement.setString(1, operationKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? UuidBytes.fromBytes(rows.getBytes("target_id")) : null;
            }
        }
    }

    private Current readCurrent(ResultSet rows) throws SQLException {
        long reconciliationRevision = rows.getLong("reconciliation_revision");
        long targetRevision = rows.getLong("target_revision");
        if (reconciliationRevision != targetRevision) {
            throw new SQLException("D07 target/reconciliation revisions diverged");
        }
        return new Current(
                codec.decode(rows.getString("desired_state_json")),
                reconciliationRevision,
                rows.getInt("attempt_count")
        );
    }

    private void requireDiscordMembership(Connection connection, DiscordPunishment punishment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM moderation_subject_discord_identities
                WHERE subject_id = ? AND discord_user_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(punishment.subjectId().value()));
            statement.setBigDecimal(2, new BigDecimal(punishment.targetUserId().value()));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Discord punishment target is not a member of the subject");
                }
            }
        }
    }

    private void insertTarget(
            Connection connection,
            DiscordPunishment punishment,
            String operationKey,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO moderation_enforcement_targets(
                    target_id, operation_key, subject_id, platform,
                    minecraft_player_id, discord_user_id, scope_type, scope_value,
                    state, revision, created_at, updated_at
                ) VALUES (?, ?, ?, 'DISCORD', NULL, ?, 'DISCORD_GUILD', ?, ?, 0, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(punishment.punishmentId()));
            statement.setString(2, operationKey);
            statement.setBytes(3, UuidBytes.toBytes(punishment.subjectId().value()));
            statement.setBigDecimal(4, new BigDecimal(punishment.targetUserId().value()));
            statement.setString(5, punishment.guildId().value());
            statement.setString(6, punishment.state().name());
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 enforcement target was not inserted");
        }
    }

    private void insertReconciliation(
            Connection connection,
            DiscordPunishment punishment,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_reconciliation_state(
                    reconciliation_key, resource_type, resource_id, desired_state_json,
                    observed_state_json, state, attempt_count, next_attempt_at,
                    last_error_code, revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, NULL, 0, ?, ?)
                """)) {
            statement.setString(1, key(punishment.punishmentId()));
            statement.setString(2, RESOURCE_TYPE);
            statement.setString(3, punishment.punishmentId().toString());
            statement.setString(4, codec.encode(punishment));
            statement.setString(5, codec.encodeObservation(punishment));
            statement.setString(6, punishment.state().name());
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 reconciliation state was not inserted");
        }
    }

    private void updateState(
            Connection connection,
            Current current,
            DiscordPunishment replacement,
            int attemptCount,
            List<WorkSchedule> nextWork,
            Instant now
    ) throws SQLException {
        Instant nextAttempt = nextWork.stream().map(WorkSchedule::dueAt).min(Instant::compareTo).orElse(null);
        updateReconciliation(connection, current, replacement, attemptCount, nextAttempt, now);
        updateTarget(connection, current, replacement, now);
    }

    private void updateReconciliation(
            Connection connection,
            Current current,
            DiscordPunishment replacement,
            int attemptCount,
            Instant nextAttempt,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_reconciliation_state
                SET desired_state_json = ?, observed_state_json = ?, state = ?, attempt_count = ?,
                    next_attempt_at = ?, last_error_code = ?, revision = revision + 1, updated_at = ?
                WHERE reconciliation_key = ? AND revision = ?
                """)) {
            statement.setString(1, codec.encode(replacement));
            statement.setString(2, codec.encodeObservation(replacement));
            statement.setString(3, replacement.state().name());
            statement.setInt(4, attemptCount);
            if (nextAttempt == null) {
                statement.setNull(5, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(5, Timestamp.from(nextAttempt));
            }
            if (replacement.lastErrorCode().isPresent()) {
                statement.setString(6, replacement.lastErrorCode().orElseThrow());
            } else {
                statement.setNull(6, Types.VARCHAR);
            }
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setString(8, key(replacement.punishmentId()));
            statement.setLong(9, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 reconciliation update lost revision race");
        }
    }

    private void updateTarget(
            Connection connection,
            Current current,
            DiscordPunishment replacement,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_enforcement_targets
                SET state = ?, revision = revision + 1, updated_at = ?
                WHERE target_id = ? AND revision = ?
                """)) {
            statement.setString(1, replacement.state().name());
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(replacement.punishmentId()));
            statement.setLong(4, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 enforcement update lost revision race");
        }
    }

    private static void requireRevision(Current current, long expectedRevision) throws SQLException {
        if (current.revision() != expectedRevision) {
            throw new SQLException("Discord punishment revision changed before update");
        }
    }

    private static void requireReplay(DiscordPunishment current, DiscordPunishment requested) throws SQLException {
        if (!current.equals(requested)) {
            throw new SQLException("D07 operation key replay does not match the original punishment");
        }
    }

    private static void requireImmutable(DiscordPunishment current, DiscordPunishment replacement) throws SQLException {
        boolean equal = current.punishmentId().equals(replacement.punishmentId())
                && current.subjectId().equals(replacement.subjectId())
                && current.targetUserId().equals(replacement.targetUserId())
                && current.guildId().equals(replacement.guildId())
                && current.issuer().equals(replacement.issuer())
                && current.intent().equals(replacement.intent())
                && current.issuedAt().equals(replacement.issuedAt())
                && current.expiresAt().equals(replacement.expiresAt());
        if (!equal) {
            throw new SQLException("immutable Discord punishment fields changed");
        }
    }

    private static void validateCreate(DiscordPunishment punishment, String operationKey, Instant now) {
        validateMutation(punishment, operationKey, now);
        if (punishment.state() != DiscordPunishmentState.PENDING_APPLY) {
            throw new IllegalArgumentException("new Discord punishment must be pending apply");
        }
    }

    private static void validateMutation(DiscordPunishment punishment, String operationKey, Instant now) {
        if (punishment == null || now == null || operationKey == null || operationKey.isBlank()
                || operationKey.length() > 128) {
            throw new IllegalArgumentException("Discord punishment mutation is invalid");
        }
    }

    private static void validateTransition(
            StoredPunishment expected,
            DiscordPunishment replacement,
            String operationKey,
            List<WorkSchedule> work,
            Instant now
    ) {
        if (expected == null || replacement == null) {
            throw new IllegalArgumentException("Discord punishment transition fields must be present");
        }
        validateMutation(replacement, operationKey, now);
        validateSchedules(work);
    }

    private static void validateSettlement(
            WorkLease work,
            StoredPunishment expected,
            DiscordPunishment replacement,
            List<WorkSchedule> nextWork,
            Instant now
    ) {
        if (work == null || expected == null || replacement == null || now == null) {
            throw new IllegalArgumentException("Discord punishment settlement fields must be present");
        }
        validateSchedules(nextWork);
        if (!work.punishmentId().equals(replacement.punishmentId())) {
            throw new IllegalArgumentException("work lease and punishment do not match");
        }
    }

    private static void validateSchedules(List<WorkSchedule> schedules) {
        if (schedules == null) {
            throw new IllegalArgumentException("work schedules must be present");
        }
        EnumSet<WorkType> types = EnumSet.noneOf(WorkType.class);
        for (WorkSchedule schedule : schedules) {
            if (schedule == null || !types.add(schedule.type())) {
                throw new IllegalArgumentException("work schedules must contain unique types");
            }
        }
    }

    private static void validateActiveQuery(
            DiscordGuildId guildId,
            DiscordUserId userId,
            DiscordConsequenceType type,
            int limit
    ) {
        if (guildId == null || userId == null || type == null || limit < 1 || limit > MAX_ACTIVE_QUERY) {
            throw new IllegalArgumentException("active Discord punishment query is invalid");
        }
    }

    private static String key(UUID punishmentId) {
        return KEY_PREFIX + punishmentId;
    }

    private record Current(DiscordPunishment punishment, long revision, int attemptCount) {
        StoredPunishment stored(boolean replayed) {
            return new StoredPunishment(punishment, revision, replayed);
        }
    }
}
