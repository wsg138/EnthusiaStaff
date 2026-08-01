package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertOccurrence;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;

/** Connection-scoped immutable alert persistence used by workers and lifecycle transactions. */
final class JdbcPunishmentRequestAlertWriter {
    boolean insertOrReplay(Connection connection, PunishmentRequestAlertIntent intent) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(intent, "intent");
        try {
            insertIntent(connection, intent);
            ensureDirectDelivery(connection, intent);
            return true;
        } catch (SQLException exception) {
            if (!JdbcSqlErrors.isDuplicateKey(exception)) {
                throw exception;
            }
            StoredIntent byKey = lockByIntentKey(connection, intent.intentKey());
            StoredIntent byId = lockByAlertId(connection, intent.alertId());
            if (byKey == null) {
                throw duplicateConflict(
                        "duplicate alert identifier does not match the deterministic intent key", exception);
            }
            if (byId != null && !byId.alertId().equals(byKey.alertId())) {
                throw duplicateConflict(
                        "alert identifier and deterministic intent key resolve to different rows", exception);
            }
            if (!byKey.matches(intent)) {
                throw duplicateConflict(
                        "deterministic intent key conflicts with different immutable fields", exception);
            }
            ensureDirectDelivery(connection, byKey.asIntent());
            return false;
        }
    }

    boolean closeIntent(Connection connection, UUID alertId, String reason, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_alerts
                SET intent_state = 'CLOSED', closed_at = ?, close_reason = ?
                WHERE alert_id = ? AND intent_state = 'ACTIVE'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, reason);
            statement.setBytes(3, UuidBytes.toBytes(alertId));
            if (statement.executeUpdate() != 1) {
                return false;
            }
        }
        cancelPendingForIntent(connection, alertId, reason, now);
        return true;
    }

    int expireIntents(Connection connection, Instant now, int limit) throws SQLException {
        List<UUID> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT alert_id
                FROM staff_alerts
                WHERE intent_state = 'ACTIVE' AND expires_at <= ?
                ORDER BY expires_at, alert_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    candidates.add(UuidBytes.fromBytes(result.getBytes(1)));
                }
            }
        }
        for (UUID alertId : candidates) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE staff_alerts
                    SET intent_state = 'EXPIRED', closed_at = expires_at,
                        close_reason = 'INTENT_EXPIRED'
                    WHERE alert_id = ? AND intent_state = 'ACTIVE'
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(alertId));
                JdbcTransactionSupport.requireSingleUpdate(
                        statement.executeUpdate(),
                        "punishment request alert intent disappeared while expiring"
                );
            }
            cancelPendingForIntent(connection, alertId, "INTENT_EXPIRED", now);
        }
        return candidates.size();
    }

    int cancelPendingForIntent(Connection connection, UUID alertId, String reason, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_alert_deliveries
                SET state = 'CANCELLED', cancelled_at = ?, cancel_reason = ?,
                    lease_owner = NULL, lease_until = NULL, updated_at = ?
                WHERE alert_id = ? AND state = 'PENDING'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, reason);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setBytes(4, UuidBytes.toBytes(alertId));
            return statement.executeUpdate();
        }
    }

    int closeActiveReviewerWork(
            Connection connection,
            UUID requestId,
            String reason,
            Instant now
    ) throws SQLException {
        List<UUID> alertIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT alert_id
                FROM staff_alerts
                WHERE request_id = ?
                  AND audience = 'ELIGIBLE_REVIEWERS'
                  AND intent_state = 'ACTIVE'
                ORDER BY created_at, alert_id
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(requestId));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    alertIds.add(UuidBytes.fromBytes(result.getBytes(1)));
                }
            }
        }
        for (UUID alertId : alertIds) {
            closeIntent(connection, alertId, reason, now);
        }
        return alertIds.size();
    }

    private static void insertIntent(Connection connection, PunishmentRequestAlertIntent intent)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alerts(
                    alert_id, intent_key, request_id, request_revision, lifecycle_event,
                    occurrence_key, lifecycle_actor_id, audience, recipient_id, minimum_rank,
                    excluded_recipient_id, visibility, schema_version, alert_type, payload_json,
                    state, attempt_count, available_at, created_at, expires_at, intent_state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    JSON_OBJECT('schemaVersion', ?, 'occurrenceKey', ?, 'lifecycleActorId', ?),
                    'PENDING', 0, ?, ?, ?, 'ACTIVE')
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(intent.alertId()));
            statement.setString(2, intent.intentKey());
            statement.setBytes(3, UuidBytes.toBytes(intent.requestId()));
            statement.setLong(4, intent.requestRevision());
            statement.setString(5, intent.eventType().name());
            statement.setString(6, intent.occurrence().key());
            setUuid(statement, 7, intent.occurrence().actorId());
            statement.setString(8, intent.audience().name());
            setUuid(statement, 9, intent.recipientId());
            setRank(statement, 10, intent.minimumRank());
            setUuid(statement, 11, intent.excludedRecipientId());
            statement.setString(12, intent.visibility().name());
            statement.setInt(13, intent.schemaVersion());
            statement.setString(14, intent.eventType().name());
            statement.setInt(15, intent.schemaVersion());
            statement.setString(16, intent.occurrence().key());
            if (intent.occurrence().actorId() == null) {
                statement.setNull(17, Types.VARCHAR);
            } else {
                statement.setString(17, intent.occurrence().actorId().toString());
            }
            statement.setTimestamp(18, Timestamp.from(intent.createdAt()));
            statement.setTimestamp(19, Timestamp.from(intent.createdAt()));
            statement.setTimestamp(20, Timestamp.from(intent.expiresAt()));
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
            Timestamp created = Timestamp.from(intent.createdAt());
            statement.setBytes(1, UuidBytes.toBytes(intent.alertId()));
            statement.setBytes(2, UuidBytes.toBytes(intent.recipientId()));
            statement.setTimestamp(3, created);
            statement.setTimestamp(4, created);
            statement.setTimestamp(5, created);
            statement.executeUpdate();
        }
    }

    private static StoredIntent lockByIntentKey(Connection connection, String intentKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STORED_INTENT_BY_KEY_FOR_UPDATE)) {
            statement.setString(1, intentKey);
            return readOne(statement);
        }
    }

    private static StoredIntent lockByAlertId(Connection connection, UUID alertId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STORED_INTENT_BY_ID_FOR_UPDATE)) {
            statement.setBytes(1, UuidBytes.toBytes(alertId));
            return readOne(statement);
        }
    }

    private static final String SELECT_STORED_INTENT = """
            SELECT alert_id, intent_key, request_id, request_revision, lifecycle_event,
                   occurrence_key, lifecycle_actor_id, audience, recipient_id,
                   excluded_recipient_id, minimum_rank, visibility, schema_version,
                   created_at, expires_at
            FROM staff_alerts
            """;

    private static final String SELECT_STORED_INTENT_BY_KEY_FOR_UPDATE = SELECT_STORED_INTENT + """
            WHERE intent_key = ? FOR UPDATE
            """;

    private static final String SELECT_STORED_INTENT_BY_ID_FOR_UPDATE = SELECT_STORED_INTENT + """
            WHERE alert_id = ? FOR UPDATE
            """;

    private static StoredIntent readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            return result.next() ? StoredIntent.read(result) : null;
        }
    }

    private static SQLException duplicateConflict(String message, SQLException cause) {
        return new SQLException(message, cause.getSQLState(), cause.getErrorCode(), cause);
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

    private static UUID uuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? null : UuidBytes.fromBytes(value);
    }

    private static StaffRank rank(String value) {
        return value == null ? null : StaffRank.valueOf(value);
    }

    private static boolean sameInstant(Instant first, Instant second) {
        if (first == null || second == null) {
            return Objects.equals(first, second);
        }
        return first.truncatedTo(ChronoUnit.MICROS)
                .equals(second.truncatedTo(ChronoUnit.MICROS));
    }

    private record StoredIntent(
            UUID alertId,
            String intentKey,
            UUID requestId,
            Long requestRevision,
            String lifecycleEvent,
            String occurrenceKey,
            UUID lifecycleActorId,
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
                    result.getString("occurrence_key"),
                    uuid(result, "lifecycle_actor_id"),
                    result.getString("audience"),
                    uuid(result, "recipient_id"),
                    uuid(result, "excluded_recipient_id"),
                    result.getString("minimum_rank"),
                    result.getString("visibility"),
                    version == null ? null : ((Number) version).intValue(),
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("expires_at").toInstant()
            );
        }

        private boolean matches(PunishmentRequestAlertIntent intent) {
            return Objects.equals(intentKey, intent.intentKey())
                    && Objects.equals(requestId, intent.requestId())
                    && Objects.equals(requestRevision, intent.requestRevision())
                    && Objects.equals(lifecycleEvent, intent.eventType().name())
                    && Objects.equals(occurrenceKey, intent.occurrence().key())
                    && Objects.equals(lifecycleActorId, intent.occurrence().actorId())
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

        private PunishmentRequestAlertIntent asIntent() {
            return new PunishmentRequestAlertIntent(
                    alertId,
                    intentKey,
                    requestId,
                    requestRevision,
                    PunishmentRequestLifecycleEventType.valueOf(lifecycleEvent),
                    new PunishmentRequestAlertOccurrence(occurrenceKey, lifecycleActorId),
                    PunishmentRequestAlertAudience.valueOf(audience),
                    recipientId,
                    excludedRecipientId,
                    rank(minimumRank),
                    CaseVisibility.valueOf(visibility),
                    schemaVersion,
                    createdAt,
                    expiresAt
            );
        }
    }
}
