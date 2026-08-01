package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertDeliveryId;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertOccurrence;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;

public final class JdbcPunishmentRequestAlertStore implements PunishmentRequestAlertStore {
    private static final int MAX_BATCH = 100;
    private static final int MAX_OWNER = 128;
    private static final int MAX_REASON = 64;

    private final DataSource dataSource;
    private final JdbcPunishmentRequestAlertWriter writer;

    public JdbcPunishmentRequestAlertStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
        this.writer = new JdbcPunishmentRequestAlertWriter();
    }

    @Override
    public boolean insert(PunishmentRequestAlertIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("alert intent must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to insert punishment request alert intent",
                connection -> writer.insertOrReplay(connection, intent)
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
                    return claimDirectDeliveries(connection, recipientId, owner, limit, lease, now);
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
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to claim audience punishment request alert deliveries",
                connection -> {
                    reconcileAudienceAuthorization(
                            connection, audience, recipientId, recipientRank, now);
                    if (!eligibleForAudience(audience, recipientRank)) {
                        return List.of();
                    }
                    if (audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS) {
                        materializeReviewer(connection, recipientId, recipientRank, limit, now);
                        return claimReviewerDeliveries(
                                connection, recipientId, recipientRank, owner, limit, lease, now);
                    }
                    materializeOperational(connection, recipientId, limit, now);
                    return claimOperationalDeliveries(
                            connection, recipientId, owner, limit, lease, now);
                }
        );
    }

    @Override
    public int reconcileRecipientAuthorization(
            UUID recipientId,
            StaffRank currentRank,
            Instant now,
            int limit
    ) {
        if (recipientId == null) {
            throw new IllegalArgumentException("alert authorization recipient must be present");
        }
        validateLimit(limit, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to reconcile punishment request alert recipient authorization",
                connection -> reconcileRecipientAuthorization(
                        connection, recipientId, currentRank, now, limit)
        );
    }

    @Override
    public boolean delivered(PunishmentRequestAlertDeliveryId deliveryId, String owner, Instant now) {
        validateMutation(deliveryId, owner, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to complete punishment request alert delivery",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alert_deliveries
                            SET state = 'DELIVERED', delivered_at = ?, cancelled_at = NULL,
                                cancel_reason = NULL, lease_owner = NULL, lease_until = NULL,
                                last_error_code = NULL, updated_at = ?
                            WHERE alert_id = ? AND recipient_id = ? AND state = 'LEASED'
                              AND lease_owner = ? AND lease_until > ?
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setTimestamp(2, Timestamp.from(now));
                        bindDeliveryId(statement, 3, deliveryId);
                        statement.setString(5, owner);
                        statement.setTimestamp(6, Timestamp.from(now));
                        if (statement.executeUpdate() != 1) {
                            return false;
                        }
                    }
                    closeDeliveredDirectIntent(connection, deliveryId.alertId(), now);
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
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to fail punishment request alert delivery",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alert_deliveries d
                            SET d.state = CASE
                                    WHEN EXISTS (
                                        SELECT 1 FROM staff_alerts i
                                        WHERE i.alert_id = d.alert_id
                                          AND i.intent_state = 'ACTIVE'
                                          AND i.expires_at > ?
                                    ) THEN CASE WHEN d.attempt_count >= ?
                                        THEN 'DEAD_LETTER' ELSE 'PENDING' END
                                    ELSE 'CANCELLED'
                                END,
                                d.available_at = ?, d.lease_owner = NULL, d.lease_until = NULL,
                                d.last_error_code = ?,
                                d.cancelled_at = CASE
                                    WHEN EXISTS (
                                        SELECT 1 FROM staff_alerts i
                                        WHERE i.alert_id = d.alert_id
                                          AND i.intent_state = 'ACTIVE'
                                          AND i.expires_at > ?
                                    ) THEN NULL ELSE ? END,
                                d.cancel_reason = CASE
                                    WHEN EXISTS (
                                        SELECT 1 FROM staff_alerts i
                                        WHERE i.alert_id = d.alert_id
                                          AND i.intent_state = 'ACTIVE'
                                          AND i.expires_at > ?
                                    ) THEN NULL ELSE 'INTENT_TERMINAL' END,
                                d.updated_at = ?
                            WHERE d.alert_id = ? AND d.recipient_id = ? AND d.state = 'LEASED'
                              AND d.lease_owner = ? AND d.lease_until > ?
                            """)) {
                        Timestamp timestamp = Timestamp.from(now);
                        statement.setTimestamp(1, timestamp);
                        statement.setInt(2, maximumAttempts);
                        statement.setTimestamp(3, Timestamp.from(availableAt));
                        statement.setString(4, safeError(errorCode));
                        statement.setTimestamp(5, timestamp);
                        statement.setTimestamp(6, timestamp);
                        statement.setTimestamp(7, timestamp);
                        statement.setTimestamp(8, timestamp);
                        bindDeliveryId(statement, 9, deliveryId);
                        statement.setString(11, owner);
                        statement.setTimestamp(12, timestamp);
                        return statement.executeUpdate() == 1;
                    }
                }
        );
    }

    @Override
    public boolean cancel(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            String reason,
            Instant now
    ) {
        validateMutation(deliveryId, owner, now);
        String safeReason = safeReason(reason);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to cancel punishment request alert delivery",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alert_deliveries
                            SET state = 'CANCELLED', cancelled_at = ?, cancel_reason = ?,
                                lease_owner = NULL, lease_until = NULL, updated_at = ?
                            WHERE alert_id = ? AND recipient_id = ? AND state = 'LEASED'
                              AND lease_owner = ? AND lease_until > ?
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setString(2, safeReason);
                        statement.setTimestamp(3, Timestamp.from(now));
                        bindDeliveryId(statement, 4, deliveryId);
                        statement.setString(6, owner);
                        statement.setTimestamp(7, Timestamp.from(now));
                        return statement.executeUpdate() == 1;
                    }
                }
        );
    }

    @Override
    public boolean requeueDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            Instant availableAt,
            String reason,
            Instant now
    ) {
        validateDeadLetterMutation(deliveryId, availableAt, reason, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to requeue punishment request alert dead letter",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alert_deliveries d
                            SET d.state = 'PENDING', d.available_at = ?, d.last_error_code = ?,
                                d.cancelled_at = NULL, d.cancel_reason = NULL, d.updated_at = ?
                            WHERE d.alert_id = ? AND d.recipient_id = ?
                              AND d.state = 'DEAD_LETTER'
                              AND EXISTS (
                                  SELECT 1 FROM staff_alerts i
                                  WHERE i.alert_id = d.alert_id
                                    AND i.intent_state = 'ACTIVE'
                                    AND i.expires_at > ?
                              )
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(availableAt));
                        statement.setString(2, safeError(reason));
                        statement.setTimestamp(3, Timestamp.from(now));
                        bindDeliveryId(statement, 4, deliveryId);
                        statement.setTimestamp(6, Timestamp.from(now));
                        return statement.executeUpdate() == 1;
                    }
                }
        );
    }

    @Override
    public boolean resolveDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            String reason,
            Instant now
    ) {
        if (deliveryId == null || now == null) {
            throw new IllegalArgumentException("valid dead-letter resolution fields are required");
        }
        String safeReason = safeReason(reason);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to resolve punishment request alert dead letter",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE staff_alert_deliveries
                            SET state = 'CANCELLED', cancelled_at = ?, cancel_reason = ?,
                                lease_owner = NULL, lease_until = NULL, updated_at = ?
                            WHERE alert_id = ? AND recipient_id = ? AND state = 'DEAD_LETTER'
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setString(2, safeReason);
                        statement.setTimestamp(3, Timestamp.from(now));
                        bindDeliveryId(statement, 4, deliveryId);
                        return statement.executeUpdate() == 1;
                    }
                }
        );
    }

    @Override
    public boolean closeIntent(UUID alertId, String reason, Instant now) {
        if (alertId == null || now == null) {
            throw new IllegalArgumentException("valid alert intent closure fields are required");
        }
        String safeReason = safeReason(reason);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to close punishment request alert intent",
                connection -> writer.closeIntent(connection, alertId, safeReason, now)
        );
    }

    @Override
    public int expireIntents(Instant now, int limit) {
        validateLimit(limit, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to expire punishment request alert intents",
                connection -> writer.expireIntents(connection, now, limit)
        );
    }

    @Override
    public int reclaimExpiredDeliveries(Instant now, int limit) {
        validateLimit(limit, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to reclaim punishment request alert deliveries",
                connection -> {
                    List<PunishmentRequestAlertDeliveryId> candidates = expiredLeaseCandidates(
                            connection, now, limit);
                    for (PunishmentRequestAlertDeliveryId candidate : candidates) {
                        reconcileExpiredLease(connection, candidate, now);
                    }
                    return candidates.size();
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
                         COALESCE(SUM(d.state = 'CANCELLED'), 0) cancelled_deliveries,
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
                        result.getLong("cancelled_deliveries"),
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

    private static void materializeDirect(Connection connection, UUID recipientId, int limit, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alert_deliveries(
                    alert_id, recipient_id, state, attempt_count, available_at, created_at, updated_at)
                SELECT i.alert_id, i.recipient_id, 'PENDING', 0, i.created_at, i.created_at, ?
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
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(recipientId));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setInt(4, limit);
            statement.executeUpdate();
        }
    }

    private static void materializeReviewer(
            Connection connection,
            UUID recipientId,
            StaffRank recipientRank,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alert_deliveries(
                    alert_id, recipient_id, state, attempt_count, available_at, created_at, updated_at)
                SELECT i.alert_id, ?, 'PENDING', 0, i.created_at, i.created_at, ?
                FROM staff_alerts i
                WHERE i.audience = 'ELIGIBLE_REVIEWERS'
                  AND i.intent_state = 'ACTIVE'
                  AND i.expires_at > ?
                  AND i.excluded_recipient_id <> ?
                  AND CASE i.minimum_rank
                        WHEN 'HELPER' THEN 1 WHEN 'MOD' THEN 1
                        WHEN 'ADMIN' THEN 2 WHEN 'FOUNDER' THEN 3 ELSE 99 END <= ?
                  AND NOT EXISTS (
                      SELECT 1 FROM staff_alert_deliveries d
                      WHERE d.alert_id = i.alert_id AND d.recipient_id = ?
                  )
                ORDER BY i.created_at, i.alert_id
                LIMIT ?
                ON DUPLICATE KEY UPDATE alert_id = VALUES(alert_id)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(recipientId));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setBytes(4, UuidBytes.toBytes(recipientId));
            statement.setInt(5, reviewerLevel(recipientRank));
            statement.setBytes(6, UuidBytes.toBytes(recipientId));
            statement.setInt(7, limit);
            statement.executeUpdate();
        }
    }

    private static void materializeOperational(
            Connection connection,
            UUID recipientId,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alert_deliveries(
                    alert_id, recipient_id, state, attempt_count, available_at, created_at, updated_at)
                SELECT i.alert_id, ?, 'PENDING', 0, i.created_at, i.created_at, ?
                FROM staff_alerts i
                WHERE i.audience = 'OPERATIONAL_ADMINISTRATORS'
                  AND i.intent_state = 'ACTIVE'
                  AND i.expires_at > ?
                  AND NOT EXISTS (
                      SELECT 1 FROM staff_alert_deliveries d
                      WHERE d.alert_id = i.alert_id AND d.recipient_id = ?
                  )
                ORDER BY i.created_at, i.alert_id
                LIMIT ?
                ON DUPLICATE KEY UPDATE alert_id = VALUES(alert_id)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(recipientId));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setBytes(4, UuidBytes.toBytes(recipientId));
            statement.setInt(5, limit);
            statement.executeUpdate();
        }
    }

    private static int reconcileRecipientAuthorization(
            Connection connection,
            UUID recipientId,
            StaffRank currentRank,
            Instant now,
            int limit
    ) throws SQLException {
        int reviewerLevel = currentRank == null ? 0 : reviewerLevel(currentRank);
        boolean operational = currentRank == StaffRank.ADMIN || currentRank == StaffRank.FOUNDER;
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_alert_deliveries d
                JOIN (
                    SELECT bounded.alert_id, bounded.recipient_id
                    FROM (
                        SELECT candidate.alert_id, candidate.recipient_id
                        FROM staff_alert_deliveries candidate
                        JOIN staff_alerts intent ON intent.alert_id = candidate.alert_id
                        WHERE candidate.recipient_id = ?
                          AND candidate.state = 'PENDING'
                          AND (
                              (intent.audience = 'ELIGIBLE_REVIEWERS' AND (
                                  intent.excluded_recipient_id = candidate.recipient_id
                                  OR CASE intent.minimum_rank
                                      WHEN 'HELPER' THEN 1 WHEN 'MOD' THEN 1
                                      WHEN 'ADMIN' THEN 2 WHEN 'FOUNDER' THEN 3 ELSE 99 END > ?
                              ))
                              OR (intent.audience = 'OPERATIONAL_ADMINISTRATORS' AND ? = 0)
                          )
                        ORDER BY candidate.available_at, candidate.created_at, candidate.alert_id
                        LIMIT ?
                    ) bounded
                ) unauthorized
                  ON unauthorized.alert_id = d.alert_id
                 AND unauthorized.recipient_id = d.recipient_id
                SET d.state = 'CANCELLED', d.cancelled_at = ?,
                    d.cancel_reason = 'RECIPIENT_INELIGIBLE', d.updated_at = ?,
                    d.lease_owner = NULL, d.lease_until = NULL
                WHERE d.state = 'PENDING'
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(recipientId));
            statement.setInt(2, reviewerLevel);
            statement.setInt(3, operational ? 1 : 0);
            statement.setInt(4, limit);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            return statement.executeUpdate();
        }
    }

    private static void reconcileAudienceAuthorization(
            Connection connection,
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            Instant now
    ) throws SQLException {
        if (audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE staff_alert_deliveries d
                    JOIN staff_alerts i ON i.alert_id = d.alert_id
                    SET d.state = 'CANCELLED', d.cancelled_at = ?,
                        d.cancel_reason = 'RECIPIENT_INELIGIBLE', d.updated_at = ?
                    WHERE d.recipient_id = ? AND d.state = 'PENDING'
                      AND i.audience = 'ELIGIBLE_REVIEWERS'
                      AND (
                          i.intent_state <> 'ACTIVE' OR i.expires_at <= ?
                          OR i.excluded_recipient_id = ?
                          OR CASE i.minimum_rank
                                WHEN 'HELPER' THEN 1 WHEN 'MOD' THEN 1
                                WHEN 'ADMIN' THEN 2 WHEN 'FOUNDER' THEN 3 ELSE 99 END > ?
                      )
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setBytes(3, UuidBytes.toBytes(recipientId));
                statement.setTimestamp(4, Timestamp.from(now));
                statement.setBytes(5, UuidBytes.toBytes(recipientId));
                statement.setInt(6, reviewerLevel(recipientRank));
                statement.executeUpdate();
            }
            return;
        }
        if (!eligibleForAudience(audience, recipientRank)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE staff_alert_deliveries d
                    JOIN staff_alerts i ON i.alert_id = d.alert_id
                    SET d.state = 'CANCELLED', d.cancelled_at = ?,
                        d.cancel_reason = 'RECIPIENT_INELIGIBLE', d.updated_at = ?
                    WHERE d.recipient_id = ? AND d.state = 'PENDING'
                      AND i.audience = 'OPERATIONAL_ADMINISTRATORS'
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setBytes(3, UuidBytes.toBytes(recipientId));
                statement.executeUpdate();
            }
        }
    }

    private static List<PunishmentRequestAlertClaim> claimDirectDeliveries(
            Connection connection,
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        return claimWithQuery(
                connection, ClaimQuery.DIRECT, recipientId, owner, limit, lease, now, 0);
    }

    private static List<PunishmentRequestAlertClaim> claimReviewerDeliveries(
            Connection connection,
            UUID recipientId,
            StaffRank rank,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        return claimWithQuery(
                connection,
                ClaimQuery.REVIEWER,
                recipientId,
                owner,
                limit,
                lease,
                now,
                reviewerLevel(rank)
        );
    }

    private static List<PunishmentRequestAlertClaim> claimOperationalDeliveries(
            Connection connection,
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        return claimWithQuery(
                connection, ClaimQuery.OPERATIONAL, recipientId, owner, limit, lease, now, 0);
    }

    private static List<PunishmentRequestAlertClaim> claimWithQuery(
            Connection connection,
            ClaimQuery query,
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now,
            int reviewerLevel
    ) throws SQLException {
        List<DeliveryCandidate> candidates = selectDue(
                connection, query, recipientId, reviewerLevel, limit, now);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Instant leaseUntil = now.plus(lease);
        lease(connection, query, candidates, owner, reviewerLevel, now, leaseUntil);
        return loadClaims(connection, candidates, leaseUntil);
    }

    private static List<DeliveryCandidate> selectDue(
            Connection connection,
            ClaimQuery query,
            UUID recipientId,
            int reviewerLevel,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = selectionStatement(connection, query)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setBytes(1, UuidBytes.toBytes(recipientId));
            statement.setTimestamp(2, timestamp);
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            int next = 5;
            if (query == ClaimQuery.REVIEWER) {
                statement.setInt(next++, reviewerLevel);
            }
            statement.setInt(next, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<DeliveryCandidate> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(new DeliveryCandidate(
                            new PunishmentRequestAlertDeliveryId(
                                    UuidBytes.fromBytes(result.getBytes("alert_id")),
                                    UuidBytes.fromBytes(result.getBytes("recipient_id"))
                            ),
                            result.getInt("attempt_count")
                    ));
                }
                return candidates;
            }
        }
    }

    private static PreparedStatement selectionStatement(
            Connection connection,
            ClaimQuery query
    ) throws SQLException {
        return switch (query) {
            case DIRECT -> connection.prepareStatement(SELECT_DIRECT_DUE);
            case REVIEWER -> connection.prepareStatement(SELECT_REVIEWER_DUE);
            case OPERATIONAL -> connection.prepareStatement(SELECT_OPERATIONAL_DUE);
        };
    }

    private static void lease(
            Connection connection,
            ClaimQuery query,
            List<DeliveryCandidate> candidates,
            String owner,
            int reviewerLevel,
            Instant now,
            Instant leaseUntil
    ) throws SQLException {
        try (PreparedStatement statement = leaseStatement(connection, query)) {
            for (DeliveryCandidate candidate : candidates) {
                statement.setString(1, owner);
                statement.setTimestamp(2, Timestamp.from(leaseUntil));
                statement.setTimestamp(3, Timestamp.from(now));
                bindDeliveryId(statement, 4, candidate.deliveryId());
                statement.setTimestamp(6, Timestamp.from(now));
                statement.setTimestamp(7, Timestamp.from(now));
                if (query == ClaimQuery.REVIEWER) {
                    statement.setInt(8, reviewerLevel);
                }
                statement.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    statement.executeBatch(),
                    candidates.size(),
                    "punishment request alert delivery lost eligibility while acquiring its lease"
            );
        }
    }

    private static PreparedStatement leaseStatement(
            Connection connection,
            ClaimQuery query
    ) throws SQLException {
        return switch (query) {
            case DIRECT -> connection.prepareStatement(LEASE_DIRECT);
            case REVIEWER -> connection.prepareStatement(LEASE_REVIEWER);
            case OPERATIONAL -> connection.prepareStatement(LEASE_OPERATIONAL);
        };
    }

    private enum ClaimQuery {
        DIRECT,
        REVIEWER,
        OPERATIONAL
    }

    private static final String BASE_SELECTION = """
            SELECT d.alert_id, d.recipient_id, d.attempt_count
            FROM staff_alert_deliveries d
            JOIN staff_alerts i ON i.alert_id = d.alert_id
            WHERE d.recipient_id = ?
              AND ((d.state = 'PENDING' AND d.available_at <= ?)
                   OR (d.state = 'LEASED' AND d.lease_until <= ?))
              AND i.intent_state = 'ACTIVE' AND i.expires_at > ?
            """;

    private static final String SELECT_DIRECT_DUE = BASE_SELECTION + """
              AND i.audience = 'DIRECT_RECIPIENT' AND i.recipient_id = d.recipient_id
            ORDER BY d.available_at, d.created_at, d.alert_id
            LIMIT ? FOR UPDATE SKIP LOCKED
            """;

    private static final String SELECT_REVIEWER_DUE = BASE_SELECTION + """
              AND i.audience = 'ELIGIBLE_REVIEWERS'
              AND i.excluded_recipient_id <> d.recipient_id
              AND CASE i.minimum_rank
                    WHEN 'HELPER' THEN 1 WHEN 'MOD' THEN 1
                    WHEN 'ADMIN' THEN 2 WHEN 'FOUNDER' THEN 3 ELSE 99 END <= ?
            ORDER BY d.available_at, d.created_at, d.alert_id
            LIMIT ? FOR UPDATE SKIP LOCKED
            """;

    private static final String SELECT_OPERATIONAL_DUE = BASE_SELECTION + """
              AND i.audience = 'OPERATIONAL_ADMINISTRATORS'
            ORDER BY d.available_at, d.created_at, d.alert_id
            LIMIT ? FOR UPDATE SKIP LOCKED
            """;

    private static final String BASE_LEASE = """
            UPDATE staff_alert_deliveries d
            SET d.state = 'LEASED', d.lease_owner = ?, d.lease_until = ?,
                d.attempt_count = d.attempt_count + 1, d.updated_at = ?,
                d.cancelled_at = NULL, d.cancel_reason = NULL
            WHERE d.alert_id = ? AND d.recipient_id = ?
              AND (d.state = 'PENDING' OR (d.state = 'LEASED' AND d.lease_until <= ?))
              AND EXISTS (
                  SELECT 1 FROM staff_alerts i
                  WHERE i.alert_id = d.alert_id
                    AND i.intent_state = 'ACTIVE' AND i.expires_at > ?
            """;

    private static final String LEASE_DIRECT = BASE_LEASE + """
                    AND i.audience = 'DIRECT_RECIPIENT'
                    AND i.recipient_id = d.recipient_id
              )
            """;

    private static final String LEASE_REVIEWER = BASE_LEASE + """
                    AND i.audience = 'ELIGIBLE_REVIEWERS'
                    AND i.excluded_recipient_id <> d.recipient_id
                    AND CASE i.minimum_rank
                          WHEN 'HELPER' THEN 1 WHEN 'MOD' THEN 1
                          WHEN 'ADMIN' THEN 2 WHEN 'FOUNDER' THEN 3 ELSE 99 END <= ?
              )
            """;

    private static final String LEASE_OPERATIONAL = BASE_LEASE + """
                    AND i.audience = 'OPERATIONAL_ADMINISTRATORS'
              )
            """;

    private static List<PunishmentRequestAlertClaim> loadClaims(
            Connection connection,
            List<DeliveryCandidate> candidates,
            Instant leaseUntil
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT intent_key, request_id, request_revision, lifecycle_event,
                       occurrence_key, lifecycle_actor_id, audience, recipient_id,
                       excluded_recipient_id, minimum_rank, visibility,
                       schema_version, created_at, expires_at
                FROM staff_alerts WHERE alert_id = ?
                """)) {
            List<PunishmentRequestAlertClaim> claims = new ArrayList<>();
            for (DeliveryCandidate candidate : candidates) {
                statement.setBytes(1, UuidBytes.toBytes(candidate.deliveryId().alertId()));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("punishment request alert intent disappeared after leasing");
                    }
                    claims.add(new PunishmentRequestAlertClaim(
                            candidate.deliveryId(),
                            readIntent(result, candidate.deliveryId().alertId()),
                            candidate.attemptCount() + 1,
                            leaseUntil
                    ));
                }
            }
            return List.copyOf(claims);
        }
    }

    private static PunishmentRequestAlertIntent readIntent(ResultSet result, UUID alertId)
            throws SQLException {
        return new PunishmentRequestAlertIntent(
                alertId,
                result.getString("intent_key"),
                UuidBytes.fromBytes(result.getBytes("request_id")),
                result.getLong("request_revision"),
                PunishmentRequestLifecycleEventType.valueOf(result.getString("lifecycle_event")),
                new PunishmentRequestAlertOccurrence(
                        result.getString("occurrence_key"),
                        uuid(result, "lifecycle_actor_id")
                ),
                PunishmentRequestAlertAudience.valueOf(result.getString("audience")),
                uuid(result, "recipient_id"),
                uuid(result, "excluded_recipient_id"),
                rank(result.getString("minimum_rank")),
                CaseVisibility.valueOf(result.getString("visibility")),
                result.getInt("schema_version"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("expires_at").toInstant()
        );
    }

    private static List<PunishmentRequestAlertDeliveryId> expiredLeaseCandidates(
            Connection connection,
            Instant now,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT alert_id, recipient_id
                FROM staff_alert_deliveries
                WHERE state = 'LEASED' AND lease_until <= ?
                ORDER BY lease_until, alert_id, recipient_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PunishmentRequestAlertDeliveryId> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(new PunishmentRequestAlertDeliveryId(
                            UuidBytes.fromBytes(result.getBytes(1)),
                            UuidBytes.fromBytes(result.getBytes(2))
                    ));
                }
                return candidates;
            }
        }
    }

    private static void reconcileExpiredLease(
            Connection connection,
            PunishmentRequestAlertDeliveryId deliveryId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_alert_deliveries d
                SET d.state = CASE WHEN EXISTS (
                        SELECT 1 FROM staff_alerts i
                        WHERE i.alert_id = d.alert_id
                          AND i.intent_state = 'ACTIVE' AND i.expires_at > ?
                    ) THEN 'PENDING' ELSE 'CANCELLED' END,
                    d.cancelled_at = CASE WHEN EXISTS (
                        SELECT 1 FROM staff_alerts i
                        WHERE i.alert_id = d.alert_id
                          AND i.intent_state = 'ACTIVE' AND i.expires_at > ?
                    ) THEN NULL ELSE ? END,
                    d.cancel_reason = CASE WHEN EXISTS (
                        SELECT 1 FROM staff_alerts i
                        WHERE i.alert_id = d.alert_id
                          AND i.intent_state = 'ACTIVE' AND i.expires_at > ?
                    ) THEN NULL ELSE 'INTENT_TERMINAL' END,
                    d.lease_owner = NULL, d.lease_until = NULL, d.updated_at = ?
                WHERE d.alert_id = ? AND d.recipient_id = ?
                  AND d.state = 'LEASED' AND d.lease_until <= ?
                """)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setTimestamp(1, timestamp);
            statement.setTimestamp(2, timestamp);
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            statement.setTimestamp(5, timestamp);
            bindDeliveryId(statement, 6, deliveryId);
            statement.setTimestamp(8, timestamp);
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "punishment request alert lease disappeared during recovery"
            );
        }
    }

    private static void closeDeliveredDirectIntent(Connection connection, UUID alertId, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE staff_alerts
                SET intent_state = 'CLOSED', closed_at = ?, close_reason = 'DIRECT_DELIVERED'
                WHERE alert_id = ? AND audience = 'DIRECT_RECIPIENT'
                  AND intent_state = 'ACTIVE'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(alertId));
            statement.executeUpdate();
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

    private static void deleteDeliveries(Connection connection, List<UUID> alertIds)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM staff_alert_deliveries WHERE alert_id = ?")) {
            for (UUID alertId : alertIds) {
                statement.setBytes(1, UuidBytes.toBytes(alertId));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteIntents(Connection connection, List<UUID> alertIds)
            throws SQLException {
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

    private static int reviewerLevel(StaffRank rank) {
        return switch (rank) {
            case MOD -> 1;
            case ADMIN -> 2;
            case FOUNDER -> 3;
            default -> 0;
        };
    }

    private static void bindDeliveryId(
            PreparedStatement statement,
            int firstIndex,
            PunishmentRequestAlertDeliveryId deliveryId
    ) throws SQLException {
        statement.setBytes(firstIndex, UuidBytes.toBytes(deliveryId.alertId()));
        statement.setBytes(firstIndex + 1, UuidBytes.toBytes(deliveryId.recipientId()));
    }

    private static UUID uuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? null : UuidBytes.fromBytes(value);
    }

    private static StaffRank rank(String value) {
        return value == null ? null : StaffRank.valueOf(value);
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

    private static void validateDeadLetterMutation(
            PunishmentRequestAlertDeliveryId deliveryId,
            Instant availableAt,
            String reason,
            Instant now
    ) {
        if (deliveryId == null || availableAt == null || now == null || availableAt.isBefore(now)) {
            throw new IllegalArgumentException("valid dead-letter recovery fields are required");
        }
        safeReason(reason);
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
            throw new IllegalArgumentException("alert terminal reason must be present");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REASON) {
            throw new IllegalArgumentException("alert terminal reason must be at most 64 characters");
        }
        return normalized;
    }

    private record DeliveryCandidate(
            PunishmentRequestAlertDeliveryId deliveryId,
            int attemptCount
    ) {
    }
}
