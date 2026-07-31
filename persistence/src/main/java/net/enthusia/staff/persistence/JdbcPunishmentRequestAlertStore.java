package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;

public final class JdbcPunishmentRequestAlertStore implements PunishmentRequestAlertStore {
    private static final int MAX_BATCH = 100;
    private static final int MAX_OWNER = 128;
    private final DataSource dataSource;

    public JdbcPunishmentRequestAlertStore(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource must be present");
        this.dataSource = dataSource;
    }

    @Override
    public boolean insert(PunishmentRequestAlertIntent intent) {
        if (intent == null) throw new IllegalArgumentException("alert intent must be present");
        String sql = """
                INSERT IGNORE INTO staff_alerts(
                    alert_id, intent_key, request_id, request_revision, lifecycle_event, audience,
                    recipient_id, minimum_rank, excluded_recipient_id, visibility, schema_version,
                    alert_type, payload_json, state, attempt_count, available_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, JSON_OBJECT('schemaVersion', ?),
                    'PENDING', 0, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to insert punishment request alert", exception);
        }
    }

    @Override
    public List<PunishmentRequestAlertClaim> claimDirect(
            UUID recipientId, String owner, int limit, Duration lease, Instant now) {
        if (recipientId == null) throw new IllegalArgumentException("direct alert recipient must be present");
        return claim("recipient_id = ? AND audience = 'DIRECT_RECIPIENT'", recipientId, null,
                owner, limit, lease, now);
    }

    @Override
    public List<PunishmentRequestAlertClaim> claimAudience(
            PunishmentRequestAlertAudience audience, UUID recipientId, StaffRank recipientRank,
            String owner, int limit, Duration lease, Instant now) {
        if (audience == null || audience == PunishmentRequestAlertAudience.DIRECT_RECIPIENT
                || recipientId == null || recipientRank == null) {
            throw new IllegalArgumentException("valid audience authorization fields are required");
        }
        if (audience == PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS
                && recipientRank != StaffRank.ADMIN && recipientRank != StaffRank.FOUNDER) return List.of();
        if (audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS
                && !recipientRank.canApprovePunishmentRequests()) return List.of();
        return claim("audience = ? AND (excluded_recipient_id IS NULL OR excluded_recipient_id <> ?)",
                audience, new AudienceAuthorization(recipientId, recipientRank), owner, limit, lease, now);
    }

    private List<PunishmentRequestAlertClaim> claim(
            String targetClause, Object target, AudienceAuthorization authorization,
            String owner, int limit, Duration lease, Instant now) {
        validateClaim(owner, limit, lease, now);
        return JdbcTransactionSupport.execute(dataSource, "Unable to claim punishment request alerts", connection -> {
            String rankClause = authorization == null ? "" : " AND (audience <> 'ELIGIBLE_REVIEWERS' OR "
                    + reviewerRankClause(authorization.rank()) + ")";
            String sql = """
                    SELECT alert_id, intent_key, request_id, request_revision, lifecycle_event, audience,
                           recipient_id, excluded_recipient_id, minimum_rank, visibility, schema_version,
                           attempt_count, created_at
                    FROM staff_alerts
                    WHERE %s
                      AND available_at <= ?
                      AND (state = 'PENDING' OR (state = 'LEASED' AND lease_until <= ?))
                      %s
                    ORDER BY available_at, created_at, alert_id
                    LIMIT ? FOR UPDATE SKIP LOCKED
                    """.formatted(targetClause, rankClause);
            List<PunishmentRequestAlertClaim> claims = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(sql)) {
                int index = bindTarget(select, target, authorization);
                select.setTimestamp(index++, Timestamp.from(now));
                select.setTimestamp(index++, Timestamp.from(now));
                select.setInt(index, limit);
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) claims.add(readClaim(result, now.plus(lease)));
                }
            }
            lease(connection, claims, owner, now.plus(lease));
            return List.copyOf(claims);
        });
    }

    @Override
    public boolean delivered(UUID alertId, String owner, Instant now) {
        validateMutation(alertId, owner, now);
        return mutateLease(alertId, owner, """
                UPDATE staff_alerts SET state = 'DELIVERED', delivered_at = ?, lease_owner = NULL,
                    lease_until = NULL, last_error_code = NULL
                WHERE alert_id = ? AND state = 'LEASED' AND lease_owner = ? AND lease_until > ?
                """, now, null, 0);
    }

    @Override
    public boolean failed(UUID alertId, String owner, String errorCode,
                          Instant availableAt, int maximumAttempts) {
        if (availableAt == null || maximumAttempts < 1)
            throw new IllegalArgumentException("valid alert failure policy is required");
        validateMutation(alertId, owner, availableAt);
        return mutateLease(alertId, owner, """
                UPDATE staff_alerts
                SET state = CASE WHEN attempt_count >= ? THEN 'DEAD_LETTER' ELSE 'PENDING' END,
                    available_at = ?, lease_owner = NULL, lease_until = NULL, last_error_code = ?
                WHERE alert_id = ? AND state = 'LEASED' AND lease_owner = ?
                """, availableAt, safeError(errorCode), maximumAttempts);
    }

    @Override
    public int reclaimExpired(Instant now, int limit) {
        validateLimit(limit, now);
        return JdbcTransactionSupport.execute(dataSource, "Unable to reclaim punishment request alerts", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE staff_alerts SET state = 'PENDING', lease_owner = NULL, lease_until = NULL
                    WHERE state = 'LEASED' AND lease_until <= ? ORDER BY lease_until, alert_id LIMIT ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setInt(2, limit);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public PunishmentRequestAlertBacklog backlog(Instant now) {
        if (now == null) throw new IllegalArgumentException("current time must be present");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT SUM(state='PENDING' AND available_at <= ?) pending_count,
                            SUM(state='LEASED') leased_count,
                            SUM(state='DELIVERED') delivered_count,
                            SUM(state='DEAD_LETTER') dead_count
                     FROM staff_alerts
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new PunishmentRequestAlertBacklog(
                        result.getLong(1), result.getLong(2), result.getLong(3), result.getLong(4));
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to count punishment request alerts", exception);
        }
    }

    @Override
    public int deleteDeliveredBefore(Instant cutoff, int limit) {
        validateLimit(limit, cutoff);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM staff_alerts WHERE state = 'DELIVERED' AND delivered_at < ?
                     ORDER BY delivered_at, alert_id LIMIT ?
                     """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, limit);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to clean punishment request alerts", exception);
        }
    }

    private static PunishmentRequestAlertClaim readClaim(ResultSet result, Instant leaseUntil) throws SQLException {
        UUID alertId = UuidBytes.fromBytes(result.getBytes("alert_id"));
        byte[] recipient = result.getBytes("recipient_id");
        byte[] excluded = result.getBytes("excluded_recipient_id");
        String rank = result.getString("minimum_rank");
        PunishmentRequestAlertIntent intent = new PunishmentRequestAlertIntent(
                alertId, result.getString("intent_key"), UuidBytes.fromBytes(result.getBytes("request_id")),
                result.getLong("request_revision"),
                PunishmentRequestLifecycleEventType.valueOf(result.getString("lifecycle_event")),
                PunishmentRequestAlertAudience.valueOf(result.getString("audience")),
                recipient == null ? null : UuidBytes.fromBytes(recipient),
                excluded == null ? null : UuidBytes.fromBytes(excluded),
                rank == null ? null : StaffRank.valueOf(rank),
                CaseVisibility.valueOf(result.getString("visibility")),
                result.getInt("schema_version"), result.getTimestamp("created_at").toInstant());
        return new PunishmentRequestAlertClaim(alertId, intent, result.getInt("attempt_count") + 1, leaseUntil);
    }

    private static void lease(Connection connection, List<PunishmentRequestAlertClaim> claims,
                              String owner, Instant leaseUntil) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_alerts SET state='LEASED', lease_owner=?, lease_until=?,
                    attempt_count=attempt_count+1 WHERE alert_id=?
                """)) {
            for (PunishmentRequestAlertClaim claim : claims) {
                statement.setString(1, owner);
                statement.setTimestamp(2, Timestamp.from(leaseUntil));
                statement.setBytes(3, UuidBytes.toBytes(claim.alertId()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean mutateLease(UUID alertId, String owner, String sql,
                                Instant time, String error, int maximumAttempts) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (maximumAttempts == 0) {
                statement.setTimestamp(1, Timestamp.from(time));
                statement.setBytes(2, UuidBytes.toBytes(alertId));
                statement.setString(3, owner);
                statement.setTimestamp(4, Timestamp.from(time));
            } else {
                statement.setInt(1, maximumAttempts);
                statement.setTimestamp(2, Timestamp.from(time));
                statement.setString(3, error);
                statement.setBytes(4, UuidBytes.toBytes(alertId));
                statement.setString(5, owner);
            }
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to mutate punishment request alert lease", exception);
        }
    }

    private static int bindTarget(PreparedStatement statement, Object target,
                                  AudienceAuthorization authorization) throws SQLException {
        if (target instanceof UUID uuid) {
            statement.setBytes(1, UuidBytes.toBytes(uuid));
            return 2;
        }
        statement.setString(1, ((PunishmentRequestAlertAudience) target).name());
        statement.setBytes(2, UuidBytes.toBytes(authorization.recipientId()));
        return 3;
    }

    private static String reviewerRankClause(StaffRank rank) {
        return switch (rank) {
            case MOD -> "minimum_rank IN ('HELPER','MOD')";
            case ADMIN -> "minimum_rank IN ('HELPER','MOD','ADMIN')";
            case FOUNDER -> "minimum_rank IN ('HELPER','MOD','ADMIN','FOUNDER')";
            default -> "FALSE";
        };
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BINARY);
        else statement.setBytes(index, UuidBytes.toBytes(value));
    }

    private static void setRank(PreparedStatement statement, int index, StaffRank value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value.name());
    }

    private static void validateClaim(String owner, int limit, Duration lease, Instant now) {
        if (owner == null || owner.isBlank() || owner.length() > MAX_OWNER || lease == null
                || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("valid bounded alert lease fields are required");
        }
        validateLimit(limit, now);
    }

    private static void validateMutation(UUID alertId, String owner, Instant now) {
        if (alertId == null || owner == null || owner.isBlank() || owner.length() > MAX_OWNER || now == null)
            throw new IllegalArgumentException("valid alert lease mutation fields are required");
    }

    private static void validateLimit(int limit, Instant time) {
        if (limit < 1 || limit > MAX_BATCH || time == null)
            throw new IllegalArgumentException("valid bounded alert operation fields are required");
    }

    private static String safeError(String error) {
        if (error == null || error.isBlank()) return "UNKNOWN";
        String normalized = error.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private record AudienceAuthorization(UUID recipientId, StaffRank rank) {
    }
}
