package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.economy.EconomyAmountMode;
import net.enthusia.staff.domain.economy.EconomyJournalResult;
import net.enthusia.staff.domain.economy.EconomyOperation;
import net.enthusia.staff.domain.economy.EconomyOperationState;
import net.enthusia.staff.domain.economy.EconomyPreparation;
import net.enthusia.staff.domain.economy.EconomyPrepareRequest;
import net.enthusia.staff.domain.economy.EconomyTerminalOutcome;
import net.enthusia.staff.domain.economy.EconomyTerminalUpdate;
import net.enthusia.staff.domain.economy.EconomyValidatedPlan;
import net.enthusia.staff.domain.ports.EconomyJournalStore;

public final class JdbcEconomyJournalStore implements EconomyJournalStore {
    private static final Duration MAXIMUM_LEASE = Duration.ofMinutes(15);
    private static final String COLUMNS = """
            operation_id, idempotency_key, case_id, target_id, actor_id,
            amount_mode, requested_amount, authoritative_total, owning_server_id,
            state, terminal_outcome, fencing_token, lease_until,
            before_checksum, replacement_checksum, before_snapshot, plan_json,
            result_total, result_checksum, result_snapshot,
            failure_code, failure_detail, created_at, updated_at
            """;

    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcEconomyJournalStore(DataSource dataSource, ObjectMapper json) {
        if (dataSource == null || json == null) {
            throw new IllegalArgumentException("dataSource and json must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
    }

    @Override
    public EconomyPreparation prepare(
            EconomyPrepareRequest request,
            Duration leaseDuration,
            Instant now
    ) {
        validateLease(request, leaseDuration, now);
        return transaction(connection -> {
            Optional<EconomyOperation> replay = findReplay(connection, request);
            if (replay.isPresent()) {
                validateReplay(replay.orElseThrow(), request);
                return prepared(
                        EconomyPreparation.Status.REPLAYED,
                        replay,
                        "The economy operation was already prepared"
                );
            }
            if (unresolvedForTarget(connection, request.targetId()).isPresent()) {
                return prepared(
                        EconomyPreparation.Status.LOCKED,
                        Optional.empty(),
                        "Another economy operation requires completion or recovery"
                );
            }
            long fence = acquireLease(
                    connection,
                    resourceKey(request.targetId()),
                    request.operationId(),
                    now.plus(leaseDuration),
                    now
            );
            if (fence < 1L) {
                return prepared(
                        EconomyPreparation.Status.LOCKED,
                        Optional.empty(),
                        "Another economy operation owns the network asset lease"
                );
            }
            insertOperation(connection, request, fence, now.plus(leaseDuration), now);
            insertEvent(
                    connection,
                    request.operationId(),
                    EconomyOperationState.PREPARED,
                    fence,
                    Map.of("amountMode", request.amountMode().name()),
                    now
            );
            insertEvent(
                    connection,
                    request.operationId(),
                    EconomyOperationState.LOCKED,
                    fence,
                    Map.of("owningServerId", request.owningServerId()),
                    now
            );
            insertAudit(
                    connection,
                    request.operationId(),
                    request.actorId(),
                    request.targetId(),
                    request.caseId(),
                    "ECONOMY_OPERATION_LOCKED",
                    "LOCKED",
                    Map.of("amountMode", request.amountMode().name()),
                    "economy:locked:" + request.operationId(),
                    now
            );
            EconomyOperation operation = lockOperation(connection, request.operationId()).orElseThrow();
            return prepared(
                    EconomyPreparation.Status.PREPARED,
                    Optional.of(operation),
                    "Economy operation and network asset lease committed"
            );
        });
    }

    @Override
    public Optional<EconomyOperation> renewLease(
            UUID operationId,
            long fencingToken,
            Duration leaseDuration,
            Instant now
    ) {
        validateLeaseIdentity(operationId, fencingToken, leaseDuration, now);
        return transaction(connection -> {
            EconomyOperation operation = lockOperation(connection, operationId).orElse(null);
            if (operation == null || operation.state().released()
                    || operation.fencingToken() != fencingToken
                    || !ownsLiveLease(connection, operation, now)) {
                return Optional.empty();
            }
            Instant nextExpiry = now.plus(leaseDuration);
            updateLease(connection, operation, nextExpiry, now);
            return lockOperation(connection, operationId);
        });
    }

    @Override
    public Optional<EconomyOperation> reclaim(
            UUID operationId,
            Duration leaseDuration,
            Instant now
    ) {
        if (operationId == null) {
            throw new IllegalArgumentException("operationId must be present");
        }
        validateLeaseIdentity(operationId, 1L, leaseDuration, now);
        return transaction(connection -> {
            EconomyOperation operation = lockOperation(connection, operationId).orElse(null);
            if (operation == null || operation.state().released()
                    || operation.state() == EconomyOperationState.QUARANTINED) {
                return Optional.empty();
            }
            if (ownsLiveLease(connection, operation, now)) {
                return Optional.empty();
            }
            long fence = claimLease(
                    connection,
                    operation,
                    now.plus(leaseDuration),
                    now
            );
            if (fence < 1L) {
                return Optional.empty();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE economy_operations
                    SET lease_owner = ?, lease_until = ?, fencing_token = ?, updated_at = ?
                    WHERE operation_id = ?
                    """)) {
                statement.setString(1, operationId.toString());
                statement.setTimestamp(2, Timestamp.from(now.plus(leaseDuration)));
                statement.setLong(3, fence);
                statement.setTimestamp(4, Timestamp.from(now));
                statement.setBytes(5, UuidBytes.toBytes(operationId));
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Economy operation disappeared during lease reclaim");
                }
            }
            insertEvent(
                    connection,
                    operationId,
                    operation.state(),
                    fence,
                    Map.of("recoveredLease", true),
                    now
            );
            return lockOperation(connection, operationId);
        });
    }

    @Override
    public EconomyJournalResult saveValidatedPlan(
            UUID operationId,
            long fencingToken,
            EconomyValidatedPlan plan,
            Instant now
    ) {
        if (operationId == null || fencingToken < 1L || plan == null || now == null) {
            throw new IllegalArgumentException("validated economy plan identity is invalid");
        }
        return transaction(connection -> {
            EconomyOperation operation = lockOperation(connection, operationId).orElse(null);
            if (operation == null) {
                return result(EconomyJournalResult.Status.NOT_FOUND, null, "Economy operation was not found");
            }
            if (validatedPlanMatches(operation, plan)
                    && (operation.state() == EconomyOperationState.VALIDATED
                    || operation.state() == EconomyOperationState.APPLYING
                    || operation.state().terminalBeforeUnlock()
                    || operation.state() == EconomyOperationState.UNLOCKED)) {
                return result(
                        EconomyJournalResult.Status.REPLAYED,
                        operation,
                        "The exact economy plan was already saved"
                );
            }
            if (operation.state() != EconomyOperationState.LOCKED
                    && operation.state() != EconomyOperationState.SNAPSHOT_SAVED) {
                return result(
                        EconomyJournalResult.Status.INVALID_STATE,
                        operation,
                        "Economy operation cannot accept a plan in " + operation.state()
                );
            }
            if (!ownsFence(connection, operation, fencingToken, now)) {
                return result(
                        EconomyJournalResult.Status.FENCE_LOST,
                        operation,
                        "Economy asset lease is no longer owned"
                );
            }
            if (operation.amountMode() == EconomyAmountMode.CUSTOM
                    && (operation.requestedAmount().isEmpty()
                    || operation.requestedAmount().orElseThrow() != plan.actualRequestedAmount())) {
                return result(
                        EconomyJournalResult.Status.STALE,
                        operation,
                        "The exact amount no longer matches the prepared request"
                );
            }
            saveSnapshotPhase(connection, operation, plan, now);
            insertEvent(
                    connection,
                    operationId,
                    EconomyOperationState.SNAPSHOT_SAVED,
                    fencingToken,
                    Map.of(
                            "beforeChecksum", plan.beforeChecksum(),
                            "authoritativeTotal", plan.authoritativeTotal()
                    ),
                    now
            );
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE economy_operations
                    SET state = 'VALIDATED', updated_at = ?
                    WHERE operation_id = ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setBytes(2, UuidBytes.toBytes(operationId));
                statement.executeUpdate();
            }
            insertEvent(
                    connection,
                    operationId,
                    EconomyOperationState.VALIDATED,
                    fencingToken,
                    Map.of(
                            "requestedAmount", plan.actualRequestedAmount(),
                            "replacementChecksum", plan.replacementChecksum()
                    ),
                    now
            );
            insertAudit(
                    connection,
                    operationId,
                    operation.actorId().orElse(null),
                    operation.targetId(),
                    operation.caseId(),
                    "ECONOMY_PLAN_VALIDATED",
                    "VALIDATED",
                    Map.of(
                            "requestedAmount", plan.actualRequestedAmount(),
                            "authoritativeTotal", plan.authoritativeTotal(),
                            "beforeChecksum", plan.beforeChecksum(),
                            "replacementChecksum", plan.replacementChecksum()
                    ),
                    "economy:validated:" + operationId,
                    now
            );
            return result(
                    EconomyJournalResult.Status.UPDATED,
                    lockOperation(connection, operationId).orElseThrow(),
                    "Before snapshot and exact removal plan committed"
            );
        });
    }

    @Override
    public EconomyJournalResult markApplying(UUID operationId, long fencingToken, Instant now) {
        if (operationId == null || fencingToken < 1L || now == null) {
            throw new IllegalArgumentException("economy apply identity is invalid");
        }
        return transaction(connection -> {
            EconomyOperation operation = lockOperation(connection, operationId).orElse(null);
            if (operation == null) {
                return result(EconomyJournalResult.Status.NOT_FOUND, null, "Economy operation was not found");
            }
            if (operation.state() == EconomyOperationState.APPLYING) {
                if (operation.fencingToken() != fencingToken
                        || !ownsLiveLease(connection, operation, now)) {
                    return result(
                            EconomyJournalResult.Status.FENCE_LOST,
                            operation,
                            "Economy operation is already applying under another lease"
                    );
                }
                return result(EconomyJournalResult.Status.REPLAYED, operation, "Economy operation is already applying");
            }
            if (operation.state() != EconomyOperationState.VALIDATED) {
                return result(
                        EconomyJournalResult.Status.INVALID_STATE,
                        operation,
                        "Economy operation cannot apply in " + operation.state()
                );
            }
            if (!ownsFence(connection, operation, fencingToken, now)) {
                return result(
                        EconomyJournalResult.Status.FENCE_LOST,
                        operation,
                        "Economy asset lease is no longer owned"
                );
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE economy_operations
                    SET state = 'APPLYING', updated_at = ?
                    WHERE operation_id = ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setBytes(2, UuidBytes.toBytes(operationId));
                statement.executeUpdate();
            }
            insertEvent(
                    connection,
                    operationId,
                    EconomyOperationState.APPLYING,
                    fencingToken,
                    Map.of(),
                    now
            );
            return result(
                    EconomyJournalResult.Status.UPDATED,
                    lockOperation(connection, operationId).orElseThrow(),
                    "Economy operation marked as applying"
            );
        });
    }

    @Override
    public EconomyJournalResult finish(
            UUID operationId,
            long fencingToken,
            EconomyTerminalUpdate update,
            Instant now
    ) {
        if (operationId == null || fencingToken < 1L || update == null || now == null) {
            throw new IllegalArgumentException("economy terminal update identity is invalid");
        }
        return transaction(connection -> {
            EconomyOperation operation = lockOperation(connection, operationId).orElse(null);
            if (operation == null) {
                return result(EconomyJournalResult.Status.NOT_FOUND, null, "Economy operation was not found");
            }
            if (operation.terminalOutcome().orElse(null) == update.outcome()) {
                if (operation.terminalMatches(update)) {
                    return result(
                            EconomyJournalResult.Status.REPLAYED,
                            operation,
                            "The exact economy terminal outcome was already committed"
                    );
                }
                return result(
                        EconomyJournalResult.Status.INVALID_STATE,
                        operation,
                        "Economy terminal outcome is already bound to different evidence"
                );
            }
            if (operation.state() == EconomyOperationState.UNLOCKED
                    || operation.state().terminalBeforeUnlock()) {
                return result(
                        EconomyJournalResult.Status.INVALID_STATE,
                        operation,
                        "Economy operation already has another terminal outcome"
                );
            }
            if (operation.fencingToken() != fencingToken) {
                return result(
                        EconomyJournalResult.Status.FENCE_LOST,
                        operation,
                        "Economy fencing token changed"
                );
            }
            if (update.outcome() != EconomyTerminalOutcome.QUARANTINED
                    && !ownsLiveLease(connection, operation, now)) {
                return result(
                        EconomyJournalResult.Status.FENCE_LOST,
                        operation,
                        "Economy asset lease expired before the terminal update"
                );
            }
            EconomyJournalResult invalid = validateTerminalTransition(operation, update);
            if (invalid != null) {
                return invalid;
            }
            EconomyOperationState state = switch (update.outcome()) {
                case COMMITTED -> EconomyOperationState.COMMITTED;
                case ROLLED_BACK -> EconomyOperationState.ROLLED_BACK;
                case QUARANTINED -> EconomyOperationState.QUARANTINED;
            };
            writeTerminal(connection, operation, state, update, now);
            insertEvent(
                    connection,
                    operationId,
                    state,
                    fencingToken,
                    terminalEvent(update),
                    now
            );
            if (state == EconomyOperationState.QUARANTINED) {
                insertQuarantine(connection, operation, update, now);
            }
            insertAudit(
                    connection,
                    operationId,
                    operation.actorId().orElse(null),
                    operation.targetId(),
                    operation.caseId(),
                    "ECONOMY_OPERATION_" + update.outcome().name(),
                    update.outcome().name(),
                    terminalEvent(update),
                    "economy:terminal:" + operationId,
                    now
            );
            return result(
                    EconomyJournalResult.Status.UPDATED,
                    lockOperation(connection, operationId).orElseThrow(),
                    "Economy operation recorded as " + update.outcome()
            );
        });
    }

    @Override
    public EconomyJournalResult release(UUID operationId, long fencingToken, Instant now) {
        if (operationId == null || fencingToken < 1L || now == null) {
            throw new IllegalArgumentException("economy release identity is invalid");
        }
        return transaction(connection -> {
            EconomyOperation operation = lockOperation(connection, operationId).orElse(null);
            if (operation == null) {
                return result(EconomyJournalResult.Status.NOT_FOUND, null, "Economy operation was not found");
            }
            if (operation.state() == EconomyOperationState.UNLOCKED) {
                return result(EconomyJournalResult.Status.REPLAYED, operation, "Economy operation is already unlocked");
            }
            if (operation.state() != EconomyOperationState.COMMITTED
                    && operation.state() != EconomyOperationState.ROLLED_BACK) {
                return result(
                        EconomyJournalResult.Status.INVALID_STATE,
                        operation,
                        "Only committed or rolled-back economy operations may unlock"
                );
            }
            if (operation.fencingToken() != fencingToken
                    || !leaseOwnedOrAbsent(connection, operation)) {
                return result(
                        EconomyJournalResult.Status.FENCE_LOST,
                        operation,
                        "Economy operation no longer owns its release fence"
                );
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE economy_operations
                    SET state = 'UNLOCKED', lease_owner = NULL, lease_until = NULL,
                        released_at = ?, updated_at = ?
                    WHERE operation_id = ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setBytes(3, UuidBytes.toBytes(operationId));
                statement.executeUpdate();
            }
            releaseLease(connection, operation);
            insertEvent(
                    connection,
                    operationId,
                    EconomyOperationState.UNLOCKED,
                    fencingToken,
                    Map.of("terminalOutcome", operation.terminalOutcome().orElseThrow().name()),
                    now
            );
            insertAudit(
                    connection,
                    operationId,
                    operation.actorId().orElse(null),
                    operation.targetId(),
                    operation.caseId(),
                    "ECONOMY_OPERATION_UNLOCKED",
                    operation.terminalOutcome().orElseThrow().name(),
                    Map.of("fencingToken", fencingToken),
                    "economy:unlocked:" + operationId,
                    now
            );
            return result(
                    EconomyJournalResult.Status.UPDATED,
                    lockOperation(connection, operationId).orElseThrow(),
                    "Economy operation unlocked"
            );
        });
    }

    @Override
    public Optional<EconomyOperation> find(UUID operationId) {
        if (operationId == null) {
            throw new IllegalArgumentException("operationId must be present");
        }
        String sql = "SELECT " + COLUMNS + " FROM economy_operations WHERE operation_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readOperation(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("Unable to load the economy operation", exception);
        }
    }

    @Override
    public List<EconomyOperation> recoverableForTarget(
            UUID targetId,
            String owningServerId,
            int limit
    ) {
        if (targetId == null || owningServerId == null || owningServerId.isBlank()
                || owningServerId.length() > 64 || limit < 1 || limit > 16) {
            throw new IllegalArgumentException("economy recovery query is invalid");
        }
        String sql = """
                SELECT %s
                FROM economy_operations
                WHERE target_id = ? AND owning_server_id = ? AND state <> 'UNLOCKED'
                ORDER BY created_at
                LIMIT ?
                """.formatted(COLUMNS);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            statement.setString(2, owningServerId);
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<EconomyOperation> operations = new ArrayList<>();
                while (result.next()) {
                    operations.add(readOperation(result));
                }
                return List.copyOf(operations);
            }
        } catch (SQLException exception) {
            throw failure("Unable to load recoverable economy operations", exception);
        }
    }

    @Override
    public Optional<String> lockedOwningServer(UUID targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("targetId must be present");
        }
        String sql = """
                SELECT owning_server_id
                FROM economy_operations
                WHERE target_id = ? AND state <> 'UNLOCKED' AND owning_server_id IS NOT NULL
                ORDER BY created_at
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString("owning_server_id")) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("Unable to inspect the economy operation owner", exception);
        }
    }

    private void insertOperation(
            Connection connection,
            EconomyPrepareRequest request,
            long fence,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO economy_operations(
                    operation_id, idempotency_key, case_id, target_id, actor_id,
                    amount_mode, owning_server_id, requested_amount, authoritative_total,
                    state, lease_owner, lease_until, fencing_token,
                    plan_json, before_snapshot, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 'LOCKED', ?, ?, ?, NULL, NULL, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(request.operationId()));
            statement.setString(2, request.idempotencyKey());
            statement.setString(3, request.caseId());
            statement.setBytes(4, UuidBytes.toBytes(request.targetId()));
            statement.setBytes(5, UuidBytes.toBytes(request.actorId()));
            statement.setString(6, request.amountMode().name());
            statement.setString(7, request.owningServerId());
            if (request.requestedAmount().isPresent()) {
                statement.setLong(8, request.requestedAmount().orElseThrow());
            } else {
                statement.setNull(8, Types.BIGINT);
            }
            statement.setString(9, request.operationId().toString());
            statement.setTimestamp(10, Timestamp.from(leaseUntil));
            statement.setLong(11, fence);
            statement.setTimestamp(12, Timestamp.from(now));
            statement.setTimestamp(13, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private void saveSnapshotPhase(
            Connection connection,
            EconomyOperation operation,
            EconomyValidatedPlan plan,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE economy_operations
                SET requested_amount = ?, authoritative_total = ?,
                    state = 'SNAPSHOT_SAVED', before_snapshot = ?, plan_json = ?,
                    before_checksum = ?, replacement_checksum = ?, updated_at = ?
                WHERE operation_id = ?
                """)) {
            statement.setLong(1, plan.actualRequestedAmount());
            statement.setLong(2, plan.authoritativeTotal());
            statement.setString(3, plan.beforeSnapshotJson());
            statement.setString(4, plan.planJson());
            statement.setString(5, plan.beforeChecksum());
            statement.setString(6, plan.replacementChecksum());
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setBytes(8, UuidBytes.toBytes(operation.operationId()));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Economy operation disappeared while saving its snapshot");
            }
        }
    }

    private EconomyJournalResult validateTerminalTransition(
            EconomyOperation operation,
            EconomyTerminalUpdate update
    ) {
        if (update.outcome() == EconomyTerminalOutcome.COMMITTED) {
            if (operation.state() != EconomyOperationState.APPLYING
                    || operation.authoritativeTotal().isEmpty()
                    || operation.requestedAmount().isEmpty()
                    || operation.replacementChecksum().isEmpty()) {
                return result(
                        EconomyJournalResult.Status.INVALID_STATE,
                        operation,
                        "A committed economy outcome requires an applying exact plan"
                );
            }
            long expectedTotal = operation.authoritativeTotal().orElseThrow()
                    - operation.requestedAmount().orElseThrow();
            if (update.resultTotal().orElseThrow() != expectedTotal
                    || !update.resultChecksum().orElseThrow()
                    .equals(operation.replacementChecksum().orElseThrow())) {
                return result(
                        EconomyJournalResult.Status.STALE,
                        operation,
                        "Committed result does not match the exact removal plan"
                );
            }
        }
        if (update.outcome() == EconomyTerminalOutcome.ROLLED_BACK
                && update.resultTotal().isPresent()
                && operation.authoritativeTotal().isPresent()
                && update.resultTotal().orElseThrow() != operation.authoritativeTotal().orElseThrow()) {
            return result(
                    EconomyJournalResult.Status.STALE,
                    operation,
                    "Rolled-back result does not retain the authoritative before total"
            );
        }
        return null;
    }

    private void writeTerminal(
            Connection connection,
            EconomyOperation operation,
            EconomyOperationState state,
            EconomyTerminalUpdate update,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE economy_operations
                SET state = ?, terminal_outcome = ?, result_total = ?,
                    result_checksum = ?, result_snapshot = ?,
                    failure_code = ?, failure_detail = ?, committed_at = ?, updated_at = ?
                WHERE operation_id = ?
                """)) {
            statement.setString(1, state.name());
            statement.setString(2, update.outcome().name());
            setOptionalLong(statement, 3, update.resultTotal());
            setOptionalString(statement, 4, update.resultChecksum(), Types.CHAR);
            setOptionalString(statement, 5, update.resultSnapshotJson(), Types.LONGVARCHAR);
            setOptionalString(statement, 6, update.failureCode(), Types.VARCHAR);
            setOptionalString(statement, 7, update.failureDetail(), Types.VARCHAR);
            if (state == EconomyOperationState.COMMITTED) {
                statement.setTimestamp(8, Timestamp.from(now));
            } else {
                statement.setNull(8, Types.TIMESTAMP);
            }
            statement.setTimestamp(9, Timestamp.from(now));
            statement.setBytes(10, UuidBytes.toBytes(operation.operationId()));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Economy operation disappeared during terminal update");
            }
        }
    }

    private void insertQuarantine(
            Connection connection,
            EconomyOperation operation,
            EconomyTerminalUpdate update,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recovery_quarantine(
                    quarantine_id, operation_type, operation_id, resource_key,
                    reason_code, detail_json, quarantined_at
                ) VALUES (?, 'ECONOMY', ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE reason_code = VALUES(reason_code),
                    detail_json = VALUES(detail_json), quarantined_at = VALUES(quarantined_at)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(operation.operationId()));
            statement.setString(3, resourceKey(operation.targetId()));
            statement.setString(4, update.failureCode().orElseThrow());
            statement.setString(5, json(Map.of(
                    "detail", update.failureDetail().orElse("Manual economy review is required"),
                    "fencingToken", operation.fencingToken()
            )));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static Map<String, Object> terminalEvent(EconomyTerminalUpdate update) {
        Map<String, Object> values = new LinkedHashMap<>();
        update.resultTotal().ifPresent(value -> values.put("resultTotal", value));
        update.resultChecksum().ifPresent(value -> values.put("resultChecksum", value));
        update.failureCode().ifPresent(value -> values.put("failureCode", value));
        update.failureDetail().ifPresent(value -> values.put("failureDetail", value));
        return Map.copyOf(values);
    }

    private Optional<EconomyOperation> findReplay(
            Connection connection,
            EconomyPrepareRequest request
    ) throws SQLException {
        String sql = """
                SELECT %s
                FROM economy_operations
                WHERE operation_id = ? OR idempotency_key = ?
                LIMIT 1
                FOR UPDATE
                """.formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(request.operationId()));
            statement.setString(2, request.idempotencyKey());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readOperation(result)) : Optional.empty();
            }
        }
    }

    private Optional<EconomyOperation> unresolvedForTarget(Connection connection, UUID targetId)
            throws SQLException {
        String sql = """
                SELECT %s
                FROM economy_operations
                WHERE target_id = ? AND state <> 'UNLOCKED'
                ORDER BY created_at
                LIMIT 1
                FOR UPDATE
                """.formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readOperation(result)) : Optional.empty();
            }
        }
    }

    private Optional<EconomyOperation> lockOperation(Connection connection, UUID operationId)
            throws SQLException {
        String sql = """
                SELECT %s
                FROM economy_operations
                WHERE operation_id = ?
                FOR UPDATE
                """.formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readOperation(result)) : Optional.empty();
            }
        }
    }

    private static EconomyOperation readOperation(ResultSet result) throws SQLException {
        byte[] actorBytes = result.getBytes("actor_id");
        return new EconomyOperation(
                UuidBytes.fromBytes(result.getBytes("operation_id")),
                result.getString("idempotency_key"),
                result.getString("case_id"),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                actorBytes == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(actorBytes)),
                EconomyAmountMode.valueOf(result.getString("amount_mode")),
                optionalLong(result, "requested_amount"),
                optionalLong(result, "authoritative_total"),
                Optional.ofNullable(result.getString("owning_server_id")),
                EconomyOperationState.valueOf(result.getString("state")),
                optionalEnum(result.getString("terminal_outcome")),
                result.getLong("fencing_token"),
                optionalInstant(result.getTimestamp("lease_until")),
                Optional.ofNullable(result.getString("before_checksum")),
                Optional.ofNullable(result.getString("replacement_checksum")),
                Optional.ofNullable(result.getString("before_snapshot")),
                Optional.ofNullable(result.getString("plan_json")),
                optionalLong(result, "result_total"),
                Optional.ofNullable(result.getString("result_checksum")),
                Optional.ofNullable(result.getString("result_snapshot")),
                Optional.ofNullable(result.getString("failure_code")),
                Optional.ofNullable(result.getString("failure_detail")),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant()
        );
    }

    private static OptionalLong optionalLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private static Optional<Instant> optionalInstant(Timestamp value) {
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static Optional<EconomyTerminalOutcome> optionalEnum(String value) {
        return value == null ? Optional.empty() : Optional.of(EconomyTerminalOutcome.valueOf(value));
    }

    private static void validateReplay(EconomyOperation operation, EconomyPrepareRequest request) {
        boolean amountMatches = request.amountMode() == EconomyAmountMode.ALL
                || operation.requestedAmount().equals(request.requestedAmount());
        if (!operation.operationId().equals(request.operationId())
                || !operation.idempotencyKey().equals(request.idempotencyKey())
                || !operation.caseId().equals(request.caseId())
                || !operation.targetId().equals(request.targetId())
                || !operation.actorId().equals(Optional.of(request.actorId()))
                || operation.amountMode() != request.amountMode()
                || !amountMatches
                || !operation.owningServerId().equals(Optional.of(request.owningServerId()))) {
            throw new IllegalArgumentException("idempotency key is already bound to another economy operation");
        }
    }

    private static boolean validatedPlanMatches(
            EconomyOperation operation,
            EconomyValidatedPlan plan
    ) {
        return operation.requestedAmount().isPresent()
                && operation.requestedAmount().orElseThrow() == plan.actualRequestedAmount()
                && operation.authoritativeTotal().isPresent()
                && operation.authoritativeTotal().orElseThrow() == plan.authoritativeTotal()
                && operation.beforeChecksum().equals(Optional.of(plan.beforeChecksum()))
                && operation.replacementChecksum().equals(Optional.of(plan.replacementChecksum()))
                && operation.beforeSnapshotJson().equals(Optional.of(plan.beforeSnapshotJson()))
                && operation.planJson().equals(Optional.of(plan.planJson()));
    }

    private static long acquireLease(
            Connection connection,
            String resourceKey,
            UUID operationId,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            select.setString(1, resourceKey);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO operation_leases(
                                resource_key, owner_id, fencing_token, lease_until, updated_at
                            ) VALUES (?, ?, 1, ?, ?)
                            """)) {
                        insert.setString(1, resourceKey);
                        insert.setString(2, operationId.toString());
                        insert.setTimestamp(3, Timestamp.from(leaseUntil));
                        insert.setTimestamp(4, Timestamp.from(now));
                        insert.executeUpdate();
                    }
                    return 1L;
                }
                String owner = result.getString("owner_id");
                long currentFence = result.getLong("fencing_token");
                Instant currentExpiry = result.getTimestamp("lease_until").toInstant();
                if (currentExpiry.isAfter(now) && !owner.equals(operationId.toString())) {
                    return 0L;
                }
                long nextFence = currentFence + 1L;
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE operation_leases
                        SET owner_id = ?, fencing_token = ?, lease_until = ?, updated_at = ?
                        WHERE resource_key = ?
                        """)) {
                    update.setString(1, operationId.toString());
                    update.setLong(2, nextFence);
                    update.setTimestamp(3, Timestamp.from(leaseUntil));
                    update.setTimestamp(4, Timestamp.from(now));
                    update.setString(5, resourceKey);
                    update.executeUpdate();
                }
                return nextFence;
            }
        }
    }

    private static long claimLease(
            Connection connection,
            EconomyOperation operation,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        String key = resourceKey(operation.targetId());
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            select.setString(1, key);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    long fence = operation.fencingToken() + 1L;
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO operation_leases(
                                resource_key, owner_id, fencing_token, lease_until, updated_at
                            ) VALUES (?, ?, ?, ?, ?)
                            """)) {
                        insert.setString(1, key);
                        insert.setString(2, operation.operationId().toString());
                        insert.setLong(3, fence);
                        insert.setTimestamp(4, Timestamp.from(leaseUntil));
                        insert.setTimestamp(5, Timestamp.from(now));
                        insert.executeUpdate();
                    }
                    return fence;
                }
                String owner = result.getString("owner_id");
                long currentFence = result.getLong("fencing_token");
                Instant currentExpiry = result.getTimestamp("lease_until").toInstant();
                if (currentExpiry.isAfter(now)
                        && !owner.equals(operation.operationId().toString())) {
                    return 0L;
                }
                boolean liveSameFence = currentExpiry.isAfter(now)
                        && owner.equals(operation.operationId().toString())
                        && currentFence == operation.fencingToken();
                long fence = liveSameFence
                        ? currentFence
                        : Math.max(currentFence, operation.fencingToken()) + 1L;
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE operation_leases
                        SET owner_id = ?, fencing_token = ?, lease_until = ?, updated_at = ?
                        WHERE resource_key = ?
                        """)) {
                    update.setString(1, operation.operationId().toString());
                    update.setLong(2, fence);
                    update.setTimestamp(3, Timestamp.from(leaseUntil));
                    update.setTimestamp(4, Timestamp.from(now));
                    update.setString(5, key);
                    update.executeUpdate();
                }
                return fence;
            }
        }
    }

    private static boolean ownsFence(
            Connection connection,
            EconomyOperation operation,
            long fencingToken,
            Instant now
    ) throws SQLException {
        return operation.fencingToken() == fencingToken && ownsLiveLease(connection, operation, now);
    }

    private static boolean ownsLiveLease(
            Connection connection,
            EconomyOperation operation,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            statement.setString(1, resourceKey(operation.targetId()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        && result.getString("owner_id").equals(operation.operationId().toString())
                        && result.getLong("fencing_token") == operation.fencingToken()
                        && result.getTimestamp("lease_until").toInstant().isAfter(now);
            }
        }
    }

    private static boolean leaseOwnedOrAbsent(
            Connection connection,
            EconomyOperation operation
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT owner_id, fencing_token
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            statement.setString(1, resourceKey(operation.targetId()));
            try (ResultSet result = statement.executeQuery()) {
                return !result.next()
                        || result.getString("owner_id").equals(operation.operationId().toString())
                        && result.getLong("fencing_token") == operation.fencingToken();
            }
        }
    }

    private static void updateLease(
            Connection connection,
            EconomyOperation operation,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        try (PreparedStatement lease = connection.prepareStatement("""
                UPDATE operation_leases
                SET lease_until = ?, updated_at = ?
                WHERE resource_key = ? AND owner_id = ? AND fencing_token = ?
                """);
             PreparedStatement operationUpdate = connection.prepareStatement("""
                UPDATE economy_operations
                SET lease_until = ?, updated_at = ?
                WHERE operation_id = ? AND fencing_token = ?
                """)) {
            lease.setTimestamp(1, Timestamp.from(leaseUntil));
            lease.setTimestamp(2, Timestamp.from(now));
            lease.setString(3, resourceKey(operation.targetId()));
            lease.setString(4, operation.operationId().toString());
            lease.setLong(5, operation.fencingToken());
            if (lease.executeUpdate() != 1) {
                throw new SQLException("Economy lease disappeared during renewal");
            }
            operationUpdate.setTimestamp(1, Timestamp.from(leaseUntil));
            operationUpdate.setTimestamp(2, Timestamp.from(now));
            operationUpdate.setBytes(3, UuidBytes.toBytes(operation.operationId()));
            operationUpdate.setLong(4, operation.fencingToken());
            if (operationUpdate.executeUpdate() != 1) {
                throw new SQLException("Economy operation disappeared during lease renewal");
            }
        }
    }

    private static void releaseLease(Connection connection, EconomyOperation operation)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM operation_leases
                WHERE resource_key = ? AND owner_id = ? AND fencing_token = ?
                """)) {
            statement.setString(1, resourceKey(operation.targetId()));
            statement.setString(2, operation.operationId().toString());
            statement.setLong(3, operation.fencingToken());
            statement.executeUpdate();
        }
    }

    private void insertEvent(
            Connection connection,
            UUID operationId,
            EconomyOperationState state,
            long fencingToken,
            Map<String, ?> detail,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO economy_operation_events(
                    event_id, operation_id, state, fencing_token, event_json, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(operationId));
            statement.setString(3, state.name());
            statement.setLong(4, fencingToken);
            statement.setString(5, json(detail));
            statement.setTimestamp(6, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
    }

    private void insertAudit(
            Connection connection,
            UUID correlationId,
            UUID actorId,
            UUID targetId,
            String caseId,
            String eventType,
            String outcome,
            Map<String, ?> detail,
            String idempotencyKey,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, idempotency_key, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(correlationId));
            if (actorId == null) {
                statement.setNull(3, Types.BINARY);
            } else {
                statement.setBytes(3, UuidBytes.toBytes(actorId));
            }
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, caseId);
            statement.setString(6, eventType);
            statement.setString(7, outcome);
            statement.setString(8, json(detail));
            statement.setString(9, idempotencyKey);
            statement.setTimestamp(10, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
    }

    private String json(Map<String, ?> values) {
        try {
            return json.writeValueAsString(new LinkedHashMap<>(values));
        } catch (JsonProcessingException exception) {
            throw failure("Unable to serialize economy journal metadata", exception);
        }
    }

    private static void setOptionalLong(
            PreparedStatement statement,
            int index,
            OptionalLong value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setLong(index, value.orElseThrow());
        } else {
            statement.setNull(index, Types.BIGINT);
        }
    }

    private static void setOptionalString(
            PreparedStatement statement,
            int index,
            Optional<String> value,
            int sqlType
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, sqlType);
        }
    }

    private static void validateLease(
            EconomyPrepareRequest request,
            Duration leaseDuration,
            Instant now
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request must be present");
        }
        validateLeaseIdentity(request.operationId(), 1L, leaseDuration, now);
    }

    private static void validateLeaseIdentity(
            UUID operationId,
            long fencingToken,
            Duration leaseDuration,
            Instant now
    ) {
        if (operationId == null || fencingToken < 1L || leaseDuration == null
                || leaseDuration.isNegative() || leaseDuration.isZero()
                || leaseDuration.compareTo(MAXIMUM_LEASE) > 0 || now == null) {
            throw new IllegalArgumentException("economy lease fields are invalid");
        }
    }

    private static String resourceKey(UUID targetId) {
        return "economy:" + targetId;
    }

    private static EconomyPreparation prepared(
            EconomyPreparation.Status status,
            Optional<EconomyOperation> operation,
            String detail
    ) {
        return new EconomyPreparation(status, operation, detail);
    }

    private static EconomyJournalResult result(
            EconomyJournalResult.Status status,
            EconomyOperation operation,
            String detail
    ) {
        return new EconomyJournalResult(status, Optional.ofNullable(operation), detail);
    }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                connection.setAutoCommit(true);
                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure("Economy journal transaction failed", exception);
        }
    }

    private static void rollback(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollback) {
            cause.addSuppressed(rollback);
            return;
        }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException reset) {
            cause.addSuppressed(reset);
        }
    }

    private static ModerationPersistenceException failure(String message, Exception cause) {
        return new ModerationPersistenceException(message, cause);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
