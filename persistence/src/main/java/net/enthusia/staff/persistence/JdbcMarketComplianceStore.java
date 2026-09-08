package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.market.MarketComplianceRequest;
import net.enthusia.staff.domain.market.MarketComplianceResult;
import net.enthusia.staff.domain.market.MarketComplianceState;
import net.enthusia.staff.domain.market.MarketComplianceUpdate;
import net.enthusia.staff.domain.ports.MarketComplianceStore;

/** Staff-side durable intent, audit, and recovery journal for EnthusiaMarket. */
public final class JdbcMarketComplianceStore implements MarketComplianceStore {
    private static final long MINIMUM_JOURNAL_REVISION = 0L;
    private static final int MAXIMUM_BATCH = 256;
    private static final String COLUMNS = """
            compliance_id, idempotency_key, case_id, target_id, stall_id, state,
            review_due_at, recovery_until, snapshot_json, revision, created_at,
            updated_at, review_alerted_at
            """;

    private final DataSource dataSource;
    private final MarketCompliancePayloadCodec payloads;

    public JdbcMarketComplianceStore(DataSource dataSource, ObjectMapper json) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.payloads = new MarketCompliancePayloadCodec(Objects.requireNonNull(json, "json"));
    }

    @Override
    public MarketComplianceResult start(MarketComplianceRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            return JdbcTransactionSupport.execute(
                    dataSource,
                    "Unable to start market compliance operation",
                    connection -> start(connection, request)
            );
        } catch (ModerationPersistenceException failure) {
            Optional<MarketComplianceOperation> raced = findByIdempotency(request.idempotencyKey());
            if (raced.isPresent()) {
                return classifyExisting(raced.orElseThrow(), request);
            }
            throw failure;
        }
    }

    @Override
    public Optional<MarketComplianceOperation> find(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, operationId, false);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read market compliance operation", exception);
        }
    }

    @Override
    public MarketComplianceResult update(
            UUID operationId,
            long expectedJournalRevision,
            MarketComplianceUpdate update
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(update, "update");
        if (expectedJournalRevision < MINIMUM_JOURNAL_REVISION) {
            throw new IllegalArgumentException("expectedJournalRevision cannot be negative");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to update market compliance operation",
                connection -> update(connection, operationId, expectedJournalRevision, update)
        );
    }

    @Override
    public List<MarketComplianceOperation> recoverable(int limit) {
        int bounded = boundedLimit(limit);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT %s FROM market_compliance_cases
                     WHERE idempotency_key IS NOT NULL
                       AND state IN ('PREPARING', 'PREPARED', 'MODERATION_HOLD')
                     ORDER BY updated_at, compliance_id LIMIT ?
                     """.formatted(COLUMNS))) {
            statement.setInt(1, bounded);
            try (ResultSet result = statement.executeQuery()) {
                List<MarketComplianceOperation> operations = new ArrayList<>();
                while (result.next()) {
                    operations.add(payloads.read(result));
                }
                return List.copyOf(operations);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read market recovery operations", exception);
        }
    }

    @Override
    public int emitDueReviewAlerts(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        int bounded = boundedLimit(limit);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to publish due market review alerts",
                connection -> emitDueReviewAlerts(connection, now, bounded)
        );
    }

    private MarketComplianceResult start(Connection connection, MarketComplianceRequest request)
            throws SQLException {
        Optional<MarketComplianceOperation> byOperation = find(connection, request.operationId(), true);
        if (byOperation.isPresent()) {
            return classifyExisting(byOperation.orElseThrow(), request);
        }
        Optional<MarketComplianceOperation> byKey = findByIdempotency(connection, request.idempotencyKey(), true);
        if (byKey.isPresent()) {
            return classifyExisting(byKey.orElseThrow(), request);
        }
        insert(connection, request);
        MarketComplianceOperation created = find(connection, request.operationId(), true)
                .orElseThrow(() -> new SQLException("Market compliance row was unreadable after insert"));
        return result(MarketComplianceResult.Status.CREATED, created, "Market provider intent is durable");
    }

    private void insert(Connection connection, MarketComplianceRequest request) throws SQLException {
        MarketComplianceUpdate pending = new MarketComplianceUpdate(
                MarketComplianceState.PREPARING,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0L,
                "Market provider operation is pending",
                request.createdAt()
        );
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO market_compliance_cases(
                    compliance_id, idempotency_key, case_id, target_id, stall_id, state,
                    review_due_at, recovery_until, snapshot_json, revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PREPARING', ?, ?, ?, 0, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(request.operationId()));
            statement.setString(2, request.idempotencyKey().value());
            statement.setString(3, request.caseId().value());
            statement.setBytes(4, UuidBytes.toBytes(request.targetId()));
            if (request.stallId().isPresent()) {
                statement.setString(5, request.stallId().orElseThrow());
            } else {
                statement.setNull(5, java.sql.Types.VARCHAR);
            }
            statement.setTimestamp(6, Timestamp.from(request.reviewDueAt()));
            statement.setTimestamp(7, Timestamp.from(request.recoveryUntil()));
            statement.setString(8, payloads.operationPayload(request, pending));
            statement.setTimestamp(9, Timestamp.from(request.createdAt()));
            statement.setTimestamp(10, Timestamp.from(request.createdAt()));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Market compliance insert did not affect exactly one row"
            );
        }
    }

    private MarketComplianceResult update(
            Connection connection,
            UUID operationId,
            long expectedJournalRevision,
            MarketComplianceUpdate update
    ) throws SQLException {
        MarketComplianceOperation current = find(connection, operationId, true).orElse(null);
        if (current == null) {
            return result(MarketComplianceResult.Status.NOT_FOUND, null, "Market operation does not exist");
        }
        if (sameProviderState(current, update)) {
            return result(MarketComplianceResult.Status.REPLAYED, current, "Market provider state is already durable");
        }
        if (current.journalRevision() != expectedJournalRevision
                || update.providerRevision() < current.providerRevision()) {
            return result(MarketComplianceResult.Status.STALE, current, "Market journal revision changed");
        }
        if (!allowedTransition(current.state(), update.state())) {
            return result(MarketComplianceResult.Status.CONFLICT, current, "Market state transition is not allowed");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE market_compliance_cases
                SET state = ?, snapshot_json = ?, revision = revision + 1, updated_at = ?
                WHERE compliance_id = ? AND revision = ?
                """)) {
            statement.setString(1, update.state().name());
            statement.setString(2, payloads.operationPayload(current.request(), update));
            statement.setTimestamp(3, Timestamp.from(update.updatedAt()));
            statement.setBytes(4, UuidBytes.toBytes(operationId));
            statement.setLong(5, expectedJournalRevision);
            if (!JdbcTransactionSupport.updatedOne(statement.executeUpdate())) {
                MarketComplianceOperation raced = find(connection, operationId, true).orElse(null);
                return result(MarketComplianceResult.Status.STALE, raced, "Market journal changed concurrently");
            }
        }
        MarketComplianceOperation updated = find(connection, operationId, true)
                .orElseThrow(() -> new SQLException("Market compliance row disappeared after update"));
        return result(MarketComplianceResult.Status.UPDATED, updated, "Market provider state recorded");
    }

    private int emitDueReviewAlerts(Connection connection, Instant now, int limit) throws SQLException {
        List<MarketComplianceOperation> due = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT %s FROM market_compliance_cases
                WHERE idempotency_key IS NOT NULL AND state = 'PREPARED'
                  AND review_due_at <= ? AND review_alerted_at IS NULL
                ORDER BY review_due_at, compliance_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """.formatted(COLUMNS))) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    due.add(payloads.read(result));
                }
            }
        }
        for (MarketComplianceOperation operation : due) {
            markReviewAlerted(connection, operation, now);
            String alertPayload = payloads.alertPayload(operation);
            insertStaffAlert(connection, alertPayload, now);
            insertDiscordAlert(connection, operation, alertPayload, now);
        }
        return due.size();
    }

    private void markReviewAlerted(
            Connection connection,
            MarketComplianceOperation operation,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE market_compliance_cases
                SET review_alerted_at = ?, revision = revision + 1, updated_at = ?
                WHERE compliance_id = ? AND revision = ? AND review_alerted_at IS NULL
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(operation.operationId()));
            statement.setLong(4, operation.journalRevision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Market review alert claim lost its journal revision"
            );
        }
    }

    private void insertStaffAlert(
            Connection connection,
            String alertPayload,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alerts(
                    alert_id, recipient_id, minimum_rank, alert_type, payload_json, created_at
                ) VALUES (?, NULL, 'ADMIN', 'MARKET_REVIEW_DUE', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, alertPayload);
            statement.setTimestamp(3, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Market review staff alert was not inserted"
            );
        }
    }

    private void insertDiscordAlert(
            Connection connection,
            MarketComplianceOperation operation,
            String alertPayload,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(
                    message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at
                ) VALUES (?, ?, 'alerts', 'MARKET_REVIEW_DUE', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, "market-review:" + operation.operationId());
            statement.setString(3, alertPayload);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Market review Discord alert was not inserted"
            );
        }
    }

    private Optional<MarketComplianceOperation> findByIdempotency(IdempotencyKey key) {
        try (Connection connection = dataSource.getConnection()) {
            return findByIdempotency(connection, key, false);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to reconcile market idempotency race", exception);
        }
    }

    private Optional<MarketComplianceOperation> find(
            Connection connection,
            UUID operationId,
            boolean lock
    ) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM market_compliance_cases "
                        + "WHERE compliance_id = ? AND idempotency_key IS NOT NULL" + suffix)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(payloads.read(result)) : Optional.empty();
            }
        }
    }

    private Optional<MarketComplianceOperation> findByIdempotency(
            Connection connection,
            IdempotencyKey key,
            boolean lock
    ) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM market_compliance_cases "
                        + "WHERE idempotency_key = ?" + suffix)) {
            statement.setString(1, key.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(payloads.read(result)) : Optional.empty();
            }
        }
    }

    private static MarketComplianceResult classifyExisting(
            MarketComplianceOperation existing,
            MarketComplianceRequest requested
    ) {
        if (sameLogicalRequest(existing.request(), requested)) {
            return result(MarketComplianceResult.Status.REPLAYED, existing, "Market request already exists");
        }
        return result(
                MarketComplianceResult.Status.CONFLICT,
                existing,
                "Market idempotency key or operation id belongs to another request"
        );
    }

    private static boolean sameLogicalRequest(
            MarketComplianceRequest left,
            MarketComplianceRequest right
    ) {
        return left.idempotencyKey().equals(right.idempotencyKey())
                && left.caseId().equals(right.caseId())
                && left.targetId().equals(right.targetId())
                && left.kind() == right.kind()
                && left.stallId().equals(right.stallId())
                && left.requestedBy().equals(right.requestedBy())
                && left.blacklistExpiresAt().equals(right.blacklistExpiresAt())
                && left.expectedBlacklistRevision().equals(right.expectedBlacklistRevision());
    }

    private static boolean sameProviderState(
            MarketComplianceOperation current,
            MarketComplianceUpdate update
    ) {
        return current.state() == update.state()
                && current.reviewedBy().equals(update.reviewedBy())
                && current.snapshotChecksum().equals(update.snapshotChecksum())
                && current.currentChecksum().equals(update.currentChecksum())
                && current.providerRevision() == update.providerRevision();
    }

    private static boolean allowedTransition(
            MarketComplianceState current,
            MarketComplianceState next
    ) {
        if (current == next) {
            return true;
        }
        return switch (current) {
            case PREPARING -> next != MarketComplianceState.PREPARING;
            case PREPARED -> next == MarketComplianceState.MODERATION_HOLD
                    || next == MarketComplianceState.RELEASED
                    || next == MarketComplianceState.QUARANTINED;
            case MODERATION_HOLD -> next == MarketComplianceState.RESTORED
                    || next == MarketComplianceState.QUARANTINED;
            default -> false;
        };
    }

    private static MarketComplianceResult result(
            MarketComplianceResult.Status status,
            MarketComplianceOperation operation,
            String detail
    ) {
        return new MarketComplianceResult(status, Optional.ofNullable(operation), detail);
    }

    private static int boundedLimit(int limit) {
        if (limit < 1 || limit > MAXIMUM_BATCH) {
            throw new IllegalArgumentException("market query limit must be between 1 and 256");
        }
        return limit;
    }

}
