package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertDeliveryId;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;

public final class JdbcPunishmentRequestAlertStore implements PunishmentRequestAlertStore {
    private static final int MARIA_DB_DUPLICATE_KEY = 1062;
    private static final int MAX_BATCH = 100;
    private static final int MAX_OWNER = 128;
    private static final int MAX_REASON = 64;

    private final DataSource dataSource;

    public JdbcPunishmentRequestAlertStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public boolean insert(PunishmentRequestAlertIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("alert intent must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to insert punishment request alert intent",
                connection -> insertOrReplay(connection, intent)
        );
    }

    @Override
    public List<PunishmentRequestAlertClaim> claimDirect(
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) {
        if (recipientId == null) {
            throw new IllegalArgumentException("direct alert recipient must be present");
        }
        validateClaim(owner, limit, lease, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to claim direct punishment request alert deliveries",
                connection -> {
                    materializeDirect(connection, recipientId, limit, now);
                    return claimDeliveries(
                            connection,
                            PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                            recipientId,
                            null,
                            owner,
                            limit,
                            lease,
                            now
                    );
                }
        );
    }

    @Override
    public List<PunishmentRequestAlertClaim> claimAudience(
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) {
        if (audience == null || audience == PunishmentRequestAlertAudience.DIRECT_RECIPIENT
                || recipientId == null || recipientRank == null) {
            throw new IllegalArgumentException("valid audience authorization fields are required");
        }
        validateClaim(owner, limit, lease, now);
        if (!eligibleForAudience(audience, recipientRank)) {
            return List.of();
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to claim audience punishment request alert deliveries",
                connection -> {
                    materializeAudience(connection, audience, recipientId, recipientRank, limit, now);
                    return claimDeliveries(
                            connection,
                            audience,
                            recipientId,
                            recipientRank,
                            owner,
                            limit,
                            lease,
                            now
                    );
                }
        );
    }

    @Override
    public boolean delivered(PunishmentRequestAlertDeliveryId deliveryId, String owner, Instant now) {
        validateMutation(deliveryId, owner, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to complete punishment request alert delivery",
                connection -> {
                    int updated;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alert_deliveries
                            SET state = 'DELIVERED', delivered_at = ?, lease_owner = NULL,
                                lease_until = NULL, last_error_code = NULL, updated_at = ?
                            WHERE alert_id = ? AND recipient_id = ? AND state = 'LEASED'
                              AND lease_owner = ? AND lease_until > ?
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setTimestamp(2, Timestamp.from(now));
                        bindDeliveryId(statement, 3, deliveryId);
                        statement.setString(5, owner);
                        statement.setTimestamp(6, Timestamp.from(now));
                        updated = statement.executeUpdate();
                    }
                    if (updated != 1) {
                        return false;
                    }
                    try (PreparedStatement intent = connection.prepareStatement("""
                            UPDATE staff_alerts
                            SET intent_state = 'CLOSED', closed_at = ?, close_reason = 'DIRECT_DELIVERED'
                            WHERE alert_id = ? AND audience = 'DIRECT_RECIPIENT'
                              AND intent_state = 'ACTIVE'
                            """)) {
                        intent.setTimestamp(1, Timestamp.from(now));
                        intent.setBytes(2, UuidBytes.toBytes(deliveryId.alertId()));
                        intent.executeUpdate();
                    }
                    return true;
                }
        );
    }

    @Override
    public boolean failed(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            String errorCode,
            Instant availableAt,
            Instant now,
            int maximumAttempts
    ) {
        validateMutation(deliveryId, owner, now);
        if (availableAt == null || availableAt.isBefore(now) || maximumAttempts < 1) {
            throw new IllegalArgumentException("valid alert failure policy is required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE staff_alert_deliveries
                     SET state = CASE WHEN attempt_count >= ? THEN 'DEAD_LETTER' ELSE 'PENDING' END,
                         available_at = ?, lease_owner = NULL, lease_until = NULL,
                         last_error_code = ?, updated_at = ?
                     WHERE alert_id = ? AND recipient_id = ? AND state = 'LEASED'
                       AND lease_owner = ? AND lease_until > ?
                     """)) {
            statement.setInt(1, maximumAttempts);
            statement.setTimestamp(2, Timestamp.from(availableAt));
            statement.setString(3, safeError(errorCode));
            statement.setTimestamp(4, Timestamp.from(now));
            bindDeliveryId(statement, 5, deliveryId);
            statement.setString(7, owner);
            statement.setTimestamp(8, Timestamp.from(now));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to fail punishment request alert delivery", exception);
        }
    }

    @Override
    public boolean closeIntent(UUID alertId, String reason, Instant now) {
        if (alertId == null || now == null) {
            throw new IllegalArgumentException("valid alert intent closure fields are required");
        }
        String normalizedReason = safeReason(reason);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE staff_alerts
                     SET intent_state = 'CLOSED', closed_at = ?, close_reason = ?
                     WHERE alert_id = ? AND intent_state = 'ACTIVE'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, normalizedReason);
            statement.setBytes(3, UuidBytes.toBytes(alertId));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to close punishment request alert intent", exception);
        }
    }

    @Override
    public int expireIntents(Instant now, int limit) {
        validateLimit(limit, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to expire punishment request alert intents",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alerts
                            SET intent_state = 'EXPIRED', closed_at = expires_at,
                                close_reason = 'INTENT_EXPIRED'
                            WHERE intent_state = 'ACTIVE' AND expires_at <= ?
                            ORDER BY expires_at, alert_id
                            LIMIT ?
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setInt(2, limit);
                        return statement.executeUpdate();
                    }
                }
        );
    }

    @Override
    public int reclaimExpiredDeliveries(Instant now, int limit) {
        validateLimit(limit, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to reclaim punishment request alert deliveries",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alert_deliveries
                            SET state = 'PENDING', lease_owner = NULL, lease_until = NULL,
                                updated_at = ?
                            WHERE state = 'LEASED' AND lease_until <= ?
                            ORDER BY lease_until, alert_id, recipient_id
                            LIMIT ?
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setTimestamp(2, Timestamp.from(now));
                        statement.setInt(3, limit);
                        return statement.executeUpdate();
                    }
                }
        );
    }

    @Override
    public PunishmentRequestAlertBacklog backlog(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("current time must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         (SELECT COUNT(*) FROM staff_alerts i
                          WHERE i.intent_state = 'ACTIVE' AND i.expires_at > ?) active_intents,
                         COALESCE(SUM(d.state = 'PENDING'), 0) pending_deliveries,
                         COALESCE(SUM(d.state = 'LEASED'), 0) leased_deliveries,
                         COALESCE(SUM(d.state = 'DELIVERED'), 0) delivered_deliveries,
                         COALESCE(SUM(d.state = 'DEAD_LETTER'), 0) dead_deliveries,
                         COALESCE(SUM(d.state = 'LEASED' AND d.lease_until <= ?), 0) reclaimable_leases
                     FROM staff_alert_deliveries d
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new PunishmentRequestAlertBacklog(
                        result.getLong("active_intents"),
                        result.getLong("pending_deliveries"),
                        result.getLong("leased_deliveries"),
                        result.getLong("delivered_deliveries"),
                        result.getLong("dead_deliveries"),
                        result.getLong("reclaimable_leases")
                );
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to count punishment request alert work", exception);
        }
    }

    @Override
    public int deleteTerminalIntentsBefore(Instant cutoff, int limit) {
        validateLimit(limit, cutoff);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to clean retained punishment request alert intents",
                connection -> {
                    List<UUID> candidates = terminalCleanupCandidates(connection, cutoff, limit);
                    if (candidates.isEmpty()) {
                        return 0;
                    }
                    deleteDeliveries(connection, candidates);
                    deleteIntents(connection, candidates);
                    return candidates.size();
                }
        );
    }

    private static boolean insertOrReplay(Connection connection, PunishmentRequestAlertIntent intent)
            throws SQLException {
        try {
            insertIntent(connection, intent);
            ensureDirectDelivery(connection, intent);
            return true;
        } catch (SQLException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
            StoredIntent byKey = findStoredIntent(connection, "intent_key = ?", intent.intentKey());
            StoredIntent byId = findStoredIntent(connection, "alert_id = ?", intent.alertId());
            if (byKey == null) {
                throw duplicateConflict("duplicate alert identifier does not match the deterministic intent key", exception);
            }
            if (byId != null && !byId.alertId().equals(byKey.alertId())) {
                throw duplicateConflict("alert identifier and deterministic intent key resolve to different rows", exception);
            }
            if (!byKey.matches(intent)) {
                throw duplicateConflict("deterministic intent key conflicts with different immutable fields", exception);
            }
            ensureDirectDelivery(connection, byKey.withStoredIdentity(intent));
            return false;
        }
    }

    private static void insertIntent(Connection connection, PunishmentRequestAlertIntent intent)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alerts(
                    alert_id, intent_key, request_id, request_revision, lifecycle_event, audience,
                    recipient_id, minimum_rank, excluded_recipient_id, visibility, schema_version,
                    alert_type, payload_json, state, attempt_count, available_at, created_at,
                    expires_at, intent_state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, JSON_OBJECT('schemaVersion', ?),
                    'PENDING', 0, ?, ?, ?, 'ACTIVE')
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(intent.alertId()));
            statement.setString(2, intent.intentKey());
            statement.setBytes(3, UuidBytes.toBytes(intent.requestId()));
            statement.setLong(4, intent.requestRevision());
            statement.setString(5, intent.eventType().name());
            statement.setString(6, intent.audience().name());
            setUuid(statement, 7, intent.recipientId());
            setRank(statement, 8, intent.minimumRank());
            setUuid(statement, 9, intent.excludedRecipientId());
            statement.setString(10, intent.visibility().name());
            statement.setInt(11, intent.schemaVersion());
            statement.setString(12, intent.eventType().name());
            statement.setInt(13, intent.schemaVersion());
            statement.setTimestamp(14, Timestamp.from(intent.createdAt()));
            statement.setTimestamp(15, Timestamp.from(intent.createdAt()));
            statement.setTimestamp(16, Timestamp.from(intent.expiresAt()));
            statement.executeUpdate();
        }
    }

    private static void ensureDirectDelivery(Connection connection, PunishmentRequestAlertIntent intent)
            throws SQLException {
        if (intent.audience() != PunishmentRequestAlertAudience.DIRECT_RECIPIENT) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alert_deliveries(
                    alert_id, recipient_id, state, attempt_count, available_at, created_at, updated_at)
                VALUES (?, ?, 'PENDING', 0, ?, ?, ?)
                ON DUPLICATE KEY UPDATE alert_id = VALUES(alert_id)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(intent.alertId()));
            statement.setBytes(2, UuidBytes.toBytes(intent.recipientId()));
            Timestamp created = Timestamp.from(intent.createdAt());
            statement.setTimestamp(3, created);
            statement.setTimestamp(4, created);
            statement.setTimestamp(5, created);
            statement.executeUpdate();
        }
    }

    private static void materializeDirect(
            Connection connection,
            UUID recipientId,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alert_deliveries(
                    alert_id, recipient_id, state, attempt_count, available_at, created_at, updated_at)
                SELECT i.alert_id, i.recipient_id, 'PENDING', 0, ?, ?, ?
                FROM staff_alerts i
                WHERE i.audience = 'DIRECT_RECIPIENT'
                  AND i.recipient_id = ?
                  AND i.intent_state = 'ACTIVE'
                  AND i.expires_at > ?
                  AND NOT EXISTS (
                      SELECT 1 FROM staff_alert_deliveries d
                      WHERE d.alert_id = i.alert_id AND d.recipient_id = i.recipient_id
                  )
                ORDER BY i.created_at, i.alert_id
                LIMIT ?
                ON DUPLICATE KEY UPDATE alert_id = VALUES(alert_id)
                """)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setTimestamp(1, timestamp);
            statement.setTimestamp(2, timestamp);
            statement.setTimestamp(3, timestamp);
            statement.setBytes(4, UuidBytes.toBytes(recipientId));
            statement.setTimestamp(5, timestamp);
            statement.setInt(6, limit);
            statement.executeUpdate();
        }
    }

    private static void materializeAudience(
            Connection connection,
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            int limit,
            Instant now
    ) throws SQLException {
        String eligibility = audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS
                ? "AND i.excluded_recipient_id <> ? AND " + reviewerRankClause(recipientRank)
                : "";
        String sql = """
                INSERT INTO staff_alert_deliveries(
                    alert_id, recipient_id, state, attempt_count, available_at, created_at, updated_at)
                SELECT i.alert_id, ?, 'PENDING', 0, ?, ?, ?
                FROM staff_alerts i
                WHERE i.audience = ?
                  AND i.intent_state = 'ACTIVE'
                  AND i.expires_at > ?
                  %s
                  AND NOT EXISTS (
                      SELECT 1 FROM staff_alert_deliveries d
                      WHERE d.alert_id = i.alert_id AND d.recipient_id = ?
                  )
                ORDER BY i.created_at, i.alert_id
                LIMIT ?
                ON DUPLICATE KEY UPDATE alert_id = VALUES(alert_id)
                """.formatted(eligibility);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp timestamp = Timestamp.from(now);
            int index = 1;
            statement.setBytes(index++, UuidBytes.toBytes(recipientId));
            statement.setTimestamp(index++, timestamp);
            statement.setTimestamp(index++, timestamp);
            statement.setTimestamp(index++, timestamp);
            statement.setString(index++, audience.name());
            statement.setTimestamp(index++, timestamp);
            if (audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS) {
                statement.setBytes(index++, UuidBytes.toBytes(recipientId));
            }
            statement.setBytes(index++, UuidBytes.toBytes(recipientId));
            statement.setInt(index, limit);
            statement.executeUpdate();
        }
    }

    private static List<PunishmentRequestAlertClaim> claimDeliveries(
            Connection connection,
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        String eligibility = switch (audience) {
            case DIRECT_RECIPIENT -> "AND i.recipient_id = d.recipient_id";
            case ELIGIBLE_REVIEWERS -> "AND i.excluded_recipient_id <> d.recipient_id AND "
                    + reviewerRankClause(recipientRank);
            case OPERATIONAL_ADMINISTRATORS -> "";
        };
        String sql = """
                SELECT d.alert_id delivery_alert_id, d.recipient_id delivery_recipient_id,
                       d.attempt_count delivery_attempt_count,
                       i.intent_key, i.request_id, i.request_revision, i.lifecycle_event,
                       i.audience, i.recipient_id, i.excluded_recipient_id, i.minimum_rank,
                       i.visibility, i.schema_version, i.created_at, i.expires_at
                FROM staff_alert_deliveries d
                JOIN staff_alerts i ON i.alert_id = d.alert_id
                WHERE d.recipient_id = ?
                  AND i.audience = ?
                  AND i.intent_state = 'ACTIVE'
                  AND i.expires_at > ?
                  AND (
                      (d.state = 'PENDING' AND d.available_at <= ?)
                      OR (d.state = 'LEASED' AND d.lease_until <= ?)
                  )
                  %s
                ORDER BY d.available_at, i.created_at, d.alert_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """.formatted(eligibility);
        Instant leaseUntil = now.plus(lease);
        List<PunishmentRequestAlertClaim> claims = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setBytes(1, UuidBytes.toBytes(recipientId));
            statement.setString(2, audience.name());
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            statement.setTimestamp(5, timestamp);
            statement.setInt(6, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    claims.add(readClaim(result, leaseUntil));
                }
            }
        }
        lease(connection, claims, owner, now, leaseUntil);
        return List.copyOf(claims);
    }

    private static PunishmentRequestAlertClaim readClaim(ResultSet result, Instant leaseUntil)
            throws SQLException {
        UUID alertId = UuidBytes.fromBytes(result.getBytes("delivery_alert_id"));
        UUID deliveryRecipient = UuidBytes.fromBytes(result.getBytes("delivery_recipient_id"));
        byte[] directRecipient = result.getBytes("recipient_id");
        byte[] excludedRecipient = result.getBytes("excluded_recipient_id");
        String rank = result.getString("minimum_rank");
        PunishmentRequestAlertIntent intent = new PunishmentRequestAlertIntent(
                alertId,
                result.getString("intent_key"),
                UuidBytes.fromBytes(result.getBytes("request_id")),
                result.getLong("request_revision"),
                PunishmentRequestLifecycleEventType.valueOf(result.getString("lifecycle_event")),
                PunishmentRequestAlertAudience.valueOf(result.getString("audience")),
                directRecipient == null ? null : UuidBytes.fromBytes(directRecipient),
                excludedRecipient == null ? null : UuidBytes.fromBytes(excludedRecipient),
                rank == null ? null : StaffRank.valueOf(rank),
                CaseVisibility.valueOf(result.getString("visibility")),
                result.getInt("schema_version"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("expires_at").toInstant()
        );
        return new PunishmentRequestAlertClaim(
                new PunishmentRequestAlertDeliveryId(alertId, deliveryRecipient),
                intent,
                result.getInt("delivery_attempt_count") + 1,
                leaseUntil
        );
    }

    private static void lease(
            Connection connection,
            List<PunishmentRequestAlertClaim> claims,
            String owner,
            Instant now,
            Instant leaseUntil
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_alert_deliveries
                SET state = 'LEASED', lease_owner = ?, lease_until = ?,
                    attempt_count = attempt_count + 1, updated_at = ?
                WHERE alert_id = ? AND recipient_id = ?
                  AND (state = 'PENDING' OR (state = 'LEASED' AND lease_until <= ?))
                """)) {
            for (PunishmentRequestAlertClaim claim : claims) {
                statement.setString(1, owner);
                statement.setTimestamp(2, Timestamp.from(leaseUntil));
                statement.setTimestamp(3, Timestamp.from(now));
                bindDeliveryId(statement, 4, claim.deliveryId());
                statement.setTimestamp(6, Timestamp.from(now));
                statement.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    statement.executeBatch(),
                    claims.size(),
                    "punishment request alert delivery disappeared while acquiring its lease"
            );
        }
    }

    private static StoredIntent findStoredIntent(Connection connection, String clause, Object value)
            throws SQLException {
        String sql = """
                SELECT alert_id, intent_key, request_id, request_revision, lifecycle_event, audience,
                       recipient_id, excluded_recipient_id, minimum_rank, visibility, schema_version,
                       created_at, expires_at
                FROM staff_alerts WHERE %s
                """.formatted(clause);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof UUID uuid) {
                statement.setBytes(1, UuidBytes.toBytes(uuid));
            } else {
                statement.setString(1, Objects.toString(value));
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return StoredIntent.read(result);
            }
        }
    }

    private static List<UUID> terminalCleanupCandidates(
            Connection connection,
            Instant cutoff,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT i.alert_id
                FROM staff_alerts i
                WHERE i.intent_state IN ('CLOSED', 'EXPIRED')
                  AND COALESCE(i.closed_at, i.expires_at) < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM staff_alert_deliveries d
                      WHERE d.alert_id = i.alert_id
                        AND d.state IN ('PENDING', 'LEASED', 'DEAD_LETTER')
                  )
                ORDER BY COALESCE(i.closed_at, i.expires_at), i.alert_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<UUID> identifiers = new ArrayList<>();
                while (result.next()) {
                    identifiers.add(UuidBytes.fromBytes(result.getBytes(1)));
                }
                return identifiers;
            }
        }
    }

    private static void deleteDeliveries(Connection connection, List<UUID> alertIds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM staff_alert_deliveries WHERE alert_id = ?")) {
            for (UUID alertId : alertIds) {
                statement.setBytes(1, UuidBytes.toBytes(alertId));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteIntents(Connection connection, List<UUID> alertIds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM staff_alerts WHERE alert_id = ?")) {
            for (UUID alertId : alertIds) {
                statement.setBytes(1, UuidBytes.toBytes(alertId));
                statement.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    statement.executeBatch(),
                    alertIds.size(),
                    "punishment request alert intent disappeared during retention cleanup"
            );
        }
    }

    private static boolean eligibleForAudience(
            PunishmentRequestAlertAudience audience,
            StaffRank recipientRank
    ) {
        return switch (audience) {
            case ELIGIBLE_REVIEWERS -> recipientRank.canApprovePunishmentRequests();
            case OPERATIONAL_ADMINISTRATORS -> recipientRank == StaffRank.ADMIN
                    || recipientRank == StaffRank.FOUNDER;
            case DIRECT_RECIPIENT -> false;
        };
    }

    private static String reviewerRankClause(StaffRank rank) {
        return switch (rank) {
            case MOD -> "i.minimum_rank IN ('HELPER', 'MOD')";
            case ADMIN -> "i.minimum_rank IN ('HELPER', 'MOD', 'ADMIN')";
            case FOUNDER -> "i.minimum_rank IN ('HELPER', 'MOD', 'ADMIN', 'FOUNDER')";
            default -> "FALSE";
        };
    }

    private static boolean isDuplicateKey(SQLException exception) {
        return exception.getErrorCode() == MARIA_DB_DUPLICATE_KEY
                && "23000".equals(exception.getSQLState());
    }

    private static SQLException duplicateConflict(String message, SQLException cause) {
        return new SQLException(message, cause.getSQLState(), cause.getErrorCode(), cause);
    }

    private static void bindDeliveryId(
            PreparedStatement statement,
            int firstIndex,
            PunishmentRequestAlertDeliveryId deliveryId
    ) throws SQLException {
        statement.setBytes(firstIndex, UuidBytes.toBytes(deliveryId.alertId()));
        statement.setBytes(firstIndex + 1, UuidBytes.toBytes(deliveryId.recipientId()));
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BINARY);
        } else {
            statement.setBytes(index, UuidBytes.toBytes(value));
        }
    }

    private static void setRank(PreparedStatement statement, int index, StaffRank value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.name());
        }
    }

    private static void validateClaim(String owner, int limit, Duration lease, Instant now) {
        if (owner == null || owner.isBlank() || owner.length() > MAX_OWNER || lease == null
                || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("valid bounded alert lease fields are required");
        }
        validateLimit(limit, now);
    }

    private static void validateMutation(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            Instant now
    ) {
        if (deliveryId == null || owner == null || owner.isBlank()
                || owner.length() > MAX_OWNER || now == null) {
            throw new IllegalArgumentException("valid alert delivery lease mutation fields are required");
        }
    }

    private static void validateLimit(int limit, Instant time) {
        if (limit < 1 || limit > MAX_BATCH || time == null) {
            throw new IllegalArgumentException("valid bounded alert operation fields are required");
        }
    }

    private static String safeError(String error) {
        if (error == null || error.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = error.trim();
        return normalized.length() <= MAX_REASON ? normalized : normalized.substring(0, MAX_REASON);
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("alert intent close reason must be present");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REASON) {
            throw new IllegalArgumentException("alert intent close reason must be at most 64 characters");
        }
        return normalized;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static UUID uuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? null : UuidBytes.fromBytes(value);
    }

    private static boolean sameInstant(Instant first, Instant second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.truncatedTo(ChronoUnit.MICROS).equals(second.truncatedTo(ChronoUnit.MICROS));
    }

    private record StoredIntent(
            UUID alertId,
            String intentKey,
            UUID requestId,
            Long requestRevision,
            String lifecycleEvent,
            String audience,
            UUID recipientId,
            UUID excludedRecipientId,
            String minimumRank,
            String visibility,
            Integer schemaVersion,
            Instant createdAt,
            Instant expiresAt
    ) {
        private static StoredIntent read(ResultSet result) throws SQLException {
            Object revision = result.getObject("request_revision");
            Object version = result.getObject("schema_version");
            return new StoredIntent(
                    UuidBytes.fromBytes(result.getBytes("alert_id")),
                    result.getString("intent_key"),
                    uuid(result, "request_id"),
                    revision == null ? null : ((Number) revision).longValue(),
                    result.getString("lifecycle_event"),
                    result.getString("audience"),
                    uuid(result, "recipient_id"),
                    uuid(result, "excluded_recipient_id"),
                    result.getString("minimum_rank"),
                    result.getString("visibility"),
                    version == null ? null : ((Number) version).intValue(),
                    instant(result, "created_at"),
                    instant(result, "expires_at")
            );
        }

        private boolean matches(PunishmentRequestAlertIntent intent) {
            return Objects.equals(intentKey, intent.intentKey())
                    && Objects.equals(requestId, intent.requestId())
                    && Objects.equals(requestRevision, intent.requestRevision())
                    && Objects.equals(lifecycleEvent, intent.eventType().name())
                    && Objects.equals(audience, intent.audience().name())
                    && Objects.equals(recipientId, intent.recipientId())
                    && Objects.equals(excludedRecipientId, intent.excludedRecipientId())
                    && Objects.equals(minimumRank,
                    intent.minimumRank() == null ? null : intent.minimumRank().name())
                    && Objects.equals(visibility, intent.visibility().name())
                    && Objects.equals(schemaVersion, intent.schemaVersion())
                    && sameInstant(createdAt, intent.createdAt())
                    && sameInstant(expiresAt, intent.expiresAt());
        }

        private PunishmentRequestAlertIntent withStoredIdentity(PunishmentRequestAlertIntent source) {
            return new PunishmentRequestAlertIntent(
                    alertId,
                    intentKey,
                    source.requestId(),
                    source.requestRevision(),
                    source.eventType(),
                    source.audience(),
                    source.recipientId(),
                    source.excludedRecipientId(),
                    source.minimumRank(),
                    source.visibility(),
                    source.schemaVersion(),
                    createdAt,
                    expiresAt
            );
        }
    }
}
