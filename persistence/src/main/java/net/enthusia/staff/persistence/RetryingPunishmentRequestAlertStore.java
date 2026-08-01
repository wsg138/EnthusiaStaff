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
import java.util.concurrent.atomic.AtomicReference;
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

/**
 * Preserves independent audience-recipient progress when MariaDB skips a shared parent-intent row.
 *
 * <p>The primary store performs materialization and the normal claim. If that claim is empty, this
 * wrapper performs one bounded fallback transaction whose locking selection touches only the
 * recipient delivery row. Parent intent state and current authorization are rechecked through an
 * eligibility subquery, so no stale or ineligible delivery can be leased.</p>
 */
final class RetryingPunishmentRequestAlertStore implements PunishmentRequestAlertStore {
    private static final Duration FALLBACK_INTERVAL = Duration.ofSeconds(1);

    private final PunishmentRequestAlertStore delegate;
    private final Duration fallbackInterval;
    private final FallbackClaimer fallbackClaimer;
    private final AtomicReference<Instant> nextReviewerFallback = new AtomicReference<>();
    private final AtomicReference<Instant> nextOperationalFallback = new AtomicReference<>();

    RetryingPunishmentRequestAlertStore(
            DataSource dataSource,
            PunishmentRequestAlertStore delegate
    ) {
        this(
                delegate,
                FALLBACK_INTERVAL,
                (audience, recipientId, recipientRank, owner, limit, lease, now) ->
                        JdbcTransactionSupport.execute(
                                dataSource,
                                "Unable to retry audience punishment request alert deliveries",
                                connection -> fallbackClaim(
                                        connection,
                                        audience,
                                        recipientId,
                                        recipientRank,
                                        owner,
                                        limit,
                                        lease,
                                        now
                                )
                        )
        );
        if (dataSource == null) {
            throw new IllegalArgumentException("data source must be present");
        }
    }

    RetryingPunishmentRequestAlertStore(
            PunishmentRequestAlertStore delegate,
            Duration fallbackInterval,
            FallbackClaimer fallbackClaimer
    ) {
        if (delegate == null || fallbackInterval == null || fallbackInterval.isNegative()
                || fallbackInterval.isZero() || fallbackClaimer == null) {
            throw new IllegalArgumentException("retrying alert store dependencies must be present");
        }
        this.delegate = delegate;
        this.fallbackInterval = fallbackInterval;
        this.fallbackClaimer = fallbackClaimer;
    }

    @Override
    public boolean insert(PunishmentRequestAlertIntent intent) {
        return delegate.insert(intent);
    }

    @Override
    public List<PunishmentRequestAlertClaim> claimDirect(
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) {
        return delegate.claimDirect(recipientId, owner, limit, lease, now);
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
        List<PunishmentRequestAlertClaim> claims = delegate.claimAudience(
                audience, recipientId, recipientRank, owner, limit, lease, now);
        if (!claims.isEmpty()) {
            if (audience != PunishmentRequestAlertAudience.DIRECT_RECIPIENT) {
                fallbackGate(audience).set(null);
            }
            return claims;
        }
        if (!reserveFallback(audience, now)) {
            return List.of();
        }
        return fallbackClaimer.claim(
                audience,
                recipientId,
                recipientRank,
                owner,
                limit,
                lease,
                now
        );
    }

    @Override
    public int reconcileRecipientAuthorization(
            UUID recipientId,
            StaffRank currentRank,
            Instant now,
            int limit
    ) {
        return delegate.reconcileRecipientAuthorization(recipientId, currentRank, now, limit);
    }

    @Override
    public boolean delivered(PunishmentRequestAlertDeliveryId deliveryId, String owner, Instant now) {
        return delegate.delivered(deliveryId, owner, now);
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
        return delegate.failed(deliveryId, owner, errorCode, availableAt, now, maximumAttempts);
    }

    @Override
    public boolean cancel(
            PunishmentRequestAlertDeliveryId deliveryId,
            String owner,
            String reason,
            Instant now
    ) {
        return delegate.cancel(deliveryId, owner, reason, now);
    }

    @Override
    public boolean requeueDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            Instant availableAt,
            String reason,
            Instant now
    ) {
        return delegate.requeueDeadLetter(deliveryId, availableAt, reason, now);
    }

    @Override
    public boolean resolveDeadLetter(
            PunishmentRequestAlertDeliveryId deliveryId,
            String reason,
            Instant now
    ) {
        return delegate.resolveDeadLetter(deliveryId, reason, now);
    }

    @Override
    public boolean closeIntent(UUID alertId, String reason, Instant now) {
        return delegate.closeIntent(alertId, reason, now);
    }

    @Override
    public int expireIntents(Instant now, int limit) {
        return delegate.expireIntents(now, limit);
    }

    @Override
    public int reclaimExpiredDeliveries(Instant now, int limit) {
        return delegate.reclaimExpiredDeliveries(now, limit);
    }

    @Override
    public PunishmentRequestAlertBacklog backlog(Instant now) {
        return delegate.backlog(now);
    }

    @Override
    public int deleteTerminalIntentsBefore(Instant cutoff, int limit) {
        return delegate.deleteTerminalIntentsBefore(cutoff, limit);
    }

    private boolean reserveFallback(PunishmentRequestAlertAudience audience, Instant now) {
        if (audience == PunishmentRequestAlertAudience.DIRECT_RECIPIENT) {
            return false;
        }
        AtomicReference<Instant> gate = fallbackGate(audience);
        while (true) {
            Instant next = gate.get();
            if (next != null && now.isBefore(next)) {
                return false;
            }
            if (gate.compareAndSet(next, now.plus(fallbackInterval))) {
                return true;
            }
        }
    }

    private AtomicReference<Instant> fallbackGate(PunishmentRequestAlertAudience audience) {
        return switch (audience) {
            case ELIGIBLE_REVIEWERS -> nextReviewerFallback;
            case OPERATIONAL_ADMINISTRATORS -> nextOperationalFallback;
            case DIRECT_RECIPIENT -> throw new IllegalArgumentException(
                    "direct recipient delivery does not use audience fallback");
        };
    }

    private static List<PunishmentRequestAlertClaim> fallbackClaim(
            Connection connection,
            PunishmentRequestAlertAudience audience,
            UUID recipientId,
            StaffRank recipientRank,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        if (audience == PunishmentRequestAlertAudience.DIRECT_RECIPIENT) {
            return List.of();
        }
        List<DeliveryCandidate> candidates = audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS
                ? selectReviewer(connection, recipientId, recipientRank, limit, now)
                : selectOperational(connection, recipientId, recipientRank, limit, now);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Instant leaseUntil = now.plus(lease);
        if (audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS) {
            leaseReviewer(connection, candidates, recipientRank, owner, now, leaseUntil);
        } else {
            leaseOperational(connection, candidates, recipientRank, owner, now, leaseUntil);
        }
        return loadClaims(connection, candidates, leaseUntil);
    }

    private static List<DeliveryCandidate> selectReviewer(
            Connection connection,
            UUID recipientId,
            StaffRank recipientRank,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT d.alert_id, d.recipient_id, d.attempt_count
                FROM staff_alert_deliveries d
                WHERE d.recipient_id = ?
                  AND ((d.state = 'PENDING' AND d.available_at <= ?)
                       OR (d.state = 'LEASED' AND d.lease_until <= ?))
                  AND EXISTS (
                      SELECT 1 FROM staff_alerts i
                      WHERE i.alert_id = d.alert_id
                        AND i.intent_state = 'ACTIVE' AND i.expires_at > ?
                        AND i.audience = 'ELIGIBLE_REVIEWERS'
                        AND i.excluded_recipient_id <> d.recipient_id
                        AND CASE i.minimum_rank
                              WHEN 'HELPER' THEN 1 WHEN 'MOD' THEN 1
                              WHEN 'ADMIN' THEN 2 WHEN 'FOUNDER' THEN 3 ELSE 99 END <= ?
                  )
                ORDER BY d.available_at, d.created_at, d.alert_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            bindSelection(statement, recipientId, now);
            statement.setInt(5, reviewerLevel(recipientRank));
            statement.setInt(6, limit);
            return readCandidates(statement);
        }
    }

    private static List<DeliveryCandidate> selectOperational(
            Connection connection,
            UUID recipientId,
            StaffRank recipientRank,
            int limit,
            Instant now
    ) throws SQLException {
        if (recipientRank != StaffRank.ADMIN && recipientRank != StaffRank.FOUNDER) {
            return List.of();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT d.alert_id, d.recipient_id, d.attempt_count
                FROM staff_alert_deliveries d
                WHERE d.recipient_id = ?
                  AND ((d.state = 'PENDING' AND d.available_at <= ?)
                       OR (d.state = 'LEASED' AND d.lease_until <= ?))
                  AND EXISTS (
                      SELECT 1 FROM staff_alerts i
                      WHERE i.alert_id = d.alert_id
                        AND i.intent_state = 'ACTIVE' AND i.expires_at > ?
                        AND i.audience = 'OPERATIONAL_ADMINISTRATORS'
                  )
                ORDER BY d.available_at, d.created_at, d.alert_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            bindSelection(statement, recipientId, now);
            statement.setInt(5, limit);
            return readCandidates(statement);
        }
    }

    private static void bindSelection(
            PreparedStatement statement,
            UUID recipientId,
            Instant now
    ) throws SQLException {
        Timestamp timestamp = Timestamp.from(now);
        statement.setBytes(1, UuidBytes.toBytes(recipientId));
        statement.setTimestamp(2, timestamp);
        statement.setTimestamp(3, timestamp);
        statement.setTimestamp(4, timestamp);
    }

    private static List<DeliveryCandidate> readCandidates(PreparedStatement statement)
            throws SQLException {
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

    private static void leaseReviewer(
            Connection connection,
            List<DeliveryCandidate> candidates,
            StaffRank recipientRank,
            String owner,
            Instant now,
            Instant leaseUntil
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
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
                        AND i.audience = 'ELIGIBLE_REVIEWERS'
                        AND i.excluded_recipient_id <> d.recipient_id
                        AND CASE i.minimum_rank
                              WHEN 'HELPER' THEN 1 WHEN 'MOD' THEN 1
                              WHEN 'ADMIN' THEN 2 WHEN 'FOUNDER' THEN 3 ELSE 99 END <= ?
                  )
                """)) {
            for (DeliveryCandidate candidate : candidates) {
                bindLease(statement, candidate, owner, now, leaseUntil);
                statement.setInt(8, reviewerLevel(recipientRank));
                statement.addBatch();
            }
            requireLeaseBatch(statement, candidates.size());
        }
    }

    private static void leaseOperational(
            Connection connection,
            List<DeliveryCandidate> candidates,
            StaffRank recipientRank,
            String owner,
            Instant now,
            Instant leaseUntil
    ) throws SQLException {
        if (recipientRank != StaffRank.ADMIN && recipientRank != StaffRank.FOUNDER) {
            throw new SQLException("operational alert recipient lost authorization before leasing");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
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
                        AND i.audience = 'OPERATIONAL_ADMINISTRATORS'
                  )
                """)) {
            for (DeliveryCandidate candidate : candidates) {
                bindLease(statement, candidate, owner, now, leaseUntil);
                statement.addBatch();
            }
            requireLeaseBatch(statement, candidates.size());
        }
    }

    private static void bindLease(
            PreparedStatement statement,
            DeliveryCandidate candidate,
            String owner,
            Instant now,
            Instant leaseUntil
    ) throws SQLException {
        statement.setString(1, owner);
        statement.setTimestamp(2, Timestamp.from(leaseUntil));
        statement.setTimestamp(3, Timestamp.from(now));
        statement.setBytes(4, UuidBytes.toBytes(candidate.deliveryId().alertId()));
        statement.setBytes(5, UuidBytes.toBytes(candidate.deliveryId().recipientId()));
        statement.setTimestamp(6, Timestamp.from(now));
        statement.setTimestamp(7, Timestamp.from(now));
    }

    private static void requireLeaseBatch(PreparedStatement statement, int expected)
            throws SQLException {
        JdbcTransactionSupport.requireBatchUpdate(
                statement.executeBatch(),
                expected,
                "punishment request alert delivery lost eligibility while acquiring its lease"
        );
    }

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

    private static UUID uuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? null : UuidBytes.fromBytes(value);
    }

    private static StaffRank rank(String value) {
        return value == null ? null : StaffRank.valueOf(value);
    }

    private static int reviewerLevel(StaffRank rank) {
        return switch (rank) {
            case MOD -> 1;
            case ADMIN -> 2;
            case FOUNDER -> 3;
            default -> 0;
        };
    }

    @FunctionalInterface
    interface FallbackClaimer {
        List<PunishmentRequestAlertClaim> claim(
                PunishmentRequestAlertAudience audience,
                UUID recipientId,
                StaffRank recipientRank,
                String owner,
                int limit,
                Duration lease,
                Instant now
        );
    }

    private record DeliveryCandidate(
            PunishmentRequestAlertDeliveryId deliveryId,
            int attemptCount
    ) {
    }
}
