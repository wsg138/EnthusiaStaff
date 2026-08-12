package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.InventoryOperationState;
import net.enthusia.staff.domain.inventory.InventoryRecoveryResult;
import net.enthusia.staff.domain.ports.InventoryRecoveryStore;

public final class JdbcInventoryRecoveryStore implements InventoryRecoveryStore {
    private static final String CONFISCATION = "CONFISCATION";
    private static final String RESTORATION = "RESTORE_CONFISCATED";
    private static final int OPEN_CANDIDATE_LIMIT = 2;

    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcInventoryRecoveryStore(DataSource dataSource, ObjectMapper json) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public InventoryRecoveryResult requeueCaseAssets(CaseId caseId, UUID actorId, Instant now) {
        if (caseId == null || actorId == null || now == null) {
            throw new IllegalArgumentException("caseId, actorId, and now must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to authorize quarantined item recovery",
                connection -> requeue(connection, caseId, actorId, now)
        );
    }

    private InventoryRecoveryResult requeue(
            Connection connection,
            CaseId caseId,
            UUID actorId,
            Instant now
    ) throws SQLException {
        List<RecoveryCandidate> candidates = lockOpenCandidates(connection, caseId);
        if (candidates.size() > 1) {
            return result(
                    InventoryRecoveryResult.Status.AMBIGUOUS,
                    Optional.empty(),
                    "Multiple quarantined item operations match this case; no retry was authorized"
            );
        }
        if (candidates.isEmpty()) {
            return replayOrNotFound(connection, caseId);
        }
        RecoveryCandidate candidate = candidates.getFirst();
        requireOpenCoherence(candidate);
        if (hasLiveLease(connection, candidate.resourceKey(), now)) {
            return result(
                    InventoryRecoveryResult.Status.AMBIGUOUS,
                    Optional.empty(),
                    "The quarantined inventory resource currently has a live lease; no retry was authorized"
            );
        }
        requeuePatch(connection, candidate);
        requeueOperation(connection, candidate, now);
        resolveQuarantine(connection, candidate, actorId, now);
        insertAudit(connection, candidate, actorId, now);
        return result(
                InventoryRecoveryResult.Status.REQUEUED,
                Optional.of(candidate.operationId()),
                "The quarantined item operation was authorized for checksum-verified retry"
        );
    }

    private List<RecoveryCandidate> lockOpenCandidates(Connection connection, CaseId caseId)
            throws SQLException {
        String sql = """
                SELECT q.patch_id, q.operation_id, q.profile_id, q.case_id,
                    p.player_id, c.target_id AS case_target_id,
                    p.scope_id, p.owning_server_id, q.fencing_token,
                    q.state AS patch_state, o.state AS operation_state,
                    o.fencing_token AS operation_fencing_token,
                    o.profile_id AS operation_profile_id, o.operation_type,
                    rq.quarantine_id, rq.resource_key, rq.reason_code, rq.resolved_at
                FROM inventory_pending_patches q
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                JOIN cases c ON c.case_id = q.case_id
                JOIN recovery_quarantine rq
                    ON rq.operation_type = 'INVENTORY'
                    AND rq.operation_id = q.operation_id
                WHERE q.case_id = ? AND o.case_id = ?
                    AND o.operation_type IN (?, ?)
                    AND (q.state = 'QUARANTINED' OR o.state = 'QUARANTINED')
                    AND rq.resolved_at IS NULL
                ORDER BY q.created_at DESC
                LIMIT ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId.value());
            statement.setString(2, caseId.value());
            statement.setString(3, CONFISCATION);
            statement.setString(4, RESTORATION);
            statement.setInt(5, OPEN_CANDIDATE_LIMIT);
            try (ResultSet result = statement.executeQuery()) {
                List<RecoveryCandidate> candidates = new ArrayList<>(OPEN_CANDIDATE_LIMIT);
                while (result.next()) {
                    candidates.add(readCandidate(result));
                }
                return List.copyOf(candidates);
            }
        }
    }

    private InventoryRecoveryResult replayOrNotFound(Connection connection, CaseId caseId)
            throws SQLException {
        Optional<RecoveryCandidate> latest = lockLatestItemCandidate(connection, caseId);
        if (latest.isEmpty()) {
            return result(
                    InventoryRecoveryResult.Status.NOT_FOUND,
                    Optional.empty(),
                    "No case-linked item operation exists for recovery"
            );
        }
        RecoveryCandidate candidate = latest.orElseThrow();
        if (!candidate.quarantineResolved()) {
            return result(
                    InventoryRecoveryResult.Status.NOT_FOUND,
                    Optional.empty(),
                    "No unresolved quarantined item operation matches this case"
            );
        }
        requireReplayCoherence(candidate);
        return result(
                InventoryRecoveryResult.Status.REPLAYED,
                Optional.of(candidate.operationId()),
                "Recovery for the latest case-linked item operation was already authorized"
        );
    }

    private Optional<RecoveryCandidate> lockLatestItemCandidate(Connection connection, CaseId caseId)
            throws SQLException {
        String sql = """
                SELECT q.patch_id, q.operation_id, q.profile_id, q.case_id,
                    p.player_id, c.target_id AS case_target_id,
                    p.scope_id, p.owning_server_id, q.fencing_token,
                    q.state AS patch_state, o.state AS operation_state,
                    o.fencing_token AS operation_fencing_token,
                    o.profile_id AS operation_profile_id, o.operation_type,
                    rq.quarantine_id, rq.resource_key, rq.reason_code, rq.resolved_at
                FROM inventory_pending_patches q
                JOIN inventory_operations o ON o.operation_id = q.operation_id
                JOIN inventory_profiles p ON p.profile_id = q.profile_id
                JOIN cases c ON c.case_id = q.case_id
                LEFT JOIN recovery_quarantine rq
                    ON rq.operation_type = 'INVENTORY'
                    AND rq.operation_id = q.operation_id
                WHERE q.case_id = ? AND o.case_id = ?
                    AND o.operation_type IN (?, ?)
                ORDER BY q.created_at DESC
                LIMIT 1
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId.value());
            statement.setString(2, caseId.value());
            statement.setString(3, CONFISCATION);
            statement.setString(4, RESTORATION);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readCandidate(result)) : Optional.empty();
            }
        }
    }

    private static RecoveryCandidate readCandidate(ResultSet result) throws SQLException {
        byte[] quarantineBytes = result.getBytes("quarantine_id");
        String scopeId = result.getString("scope_id");
        UUID playerId = UuidBytes.fromBytes(result.getBytes("player_id"));
        String storedResourceKey = result.getString("resource_key");
        return new RecoveryCandidate(
                UuidBytes.fromBytes(result.getBytes("patch_id")),
                UuidBytes.fromBytes(result.getBytes("operation_id")),
                UuidBytes.fromBytes(result.getBytes("profile_id")),
                result.getString("case_id"),
                playerId,
                UuidBytes.fromBytes(result.getBytes("case_target_id")),
                scopeId,
                result.getString("owning_server_id"),
                result.getLong("fencing_token"),
                InventoryOperationState.valueOf(result.getString("patch_state")),
                InventoryOperationState.valueOf(result.getString("operation_state")),
                result.getLong("operation_fencing_token"),
                UuidBytes.fromBytes(result.getBytes("operation_profile_id")),
                result.getString("operation_type"),
                quarantineBytes == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(quarantineBytes)),
                storedResourceKey == null ? resourceKey(playerId, scopeId) : storedResourceKey,
                Optional.ofNullable(result.getString("reason_code")),
                result.getTimestamp("resolved_at") != null
        );
    }

    private static void requireOpenCoherence(RecoveryCandidate candidate) throws SQLException {
        requireIdentityCoherence(candidate);
        if (candidate.patchState() != InventoryOperationState.QUARANTINED
                || candidate.operationState() != InventoryOperationState.QUARANTINED
                || candidate.quarantineId().isEmpty()
                || candidate.quarantineResolved()) {
            throw new SQLException("Quarantined item recovery evidence is not coherent");
        }
    }

    private static void requireReplayCoherence(RecoveryCandidate candidate) throws SQLException {
        requireIdentityCoherence(candidate);
        if (candidate.quarantineId().isEmpty()) {
            throw new SQLException("Recovered item operation has no quarantine evidence");
        }
        boolean retrying = (candidate.patchState() == InventoryOperationState.PENDING
                && candidate.operationState() == InventoryOperationState.PENDING)
                || (candidate.patchState() == InventoryOperationState.APPLYING
                && candidate.operationState() == InventoryOperationState.APPLYING);
        boolean committed = candidate.patchState() == InventoryOperationState.APPLIED
                && candidate.operationState() == InventoryOperationState.COMMITTED;
        if (!retrying && !committed) {
            throw new SQLException("Recovered item operation no longer has a replay-safe state");
        }
    }

    private static void requireIdentityCoherence(RecoveryCandidate candidate) throws SQLException {
        if (!candidate.playerId().equals(candidate.caseTargetId())
                || !candidate.profileId().equals(candidate.operationProfileId())
                || candidate.fencingToken() != candidate.operationFencingToken()
                || !candidate.resourceKey().equals(resourceKey(candidate.playerId(), candidate.scopeId()))) {
            throw new SQLException("Item recovery journal identity or fencing evidence diverged");
        }
    }

    private static boolean hasLiveLease(Connection connection, String resourceKey, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            statement.setString(1, resourceKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getTimestamp("lease_until").toInstant().isAfter(now);
            }
        }
    }

    private static void requeuePatch(Connection connection, RecoveryCandidate candidate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE inventory_pending_patches
                SET state = 'PENDING', conflict_code = NULL, conflict_detail = NULL
                WHERE patch_id = ? AND operation_id = ? AND profile_id = ?
                    AND state = 'QUARANTINED' AND fencing_token = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(candidate.patchId()));
            statement.setBytes(2, UuidBytes.toBytes(candidate.operationId()));
            statement.setBytes(3, UuidBytes.toBytes(candidate.profileId()));
            statement.setLong(4, candidate.fencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Item recovery patch changed before requeue"
            );
        }
    }

    private static void requeueOperation(
            Connection connection,
            RecoveryCandidate candidate,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE inventory_operations
                SET state = 'PENDING', updated_at = ?
                WHERE operation_id = ? AND profile_id = ?
                    AND state = 'QUARANTINED' AND fencing_token = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(candidate.operationId()));
            statement.setBytes(3, UuidBytes.toBytes(candidate.profileId()));
            statement.setLong(4, candidate.fencingToken());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Item recovery operation changed before requeue"
            );
        }
    }

    private void resolveQuarantine(
            Connection connection,
            RecoveryCandidate candidate,
            UUID actorId,
            Instant now
    ) throws SQLException {
        String resolution = serialize(Map.of(
                "action", "REQUEUE_FOR_VERIFIED_RETRY",
                "patchId", candidate.patchId().toString(),
                "fencingToken", candidate.fencingToken(),
                "previousReasonCode", candidate.reasonCode().orElse("UNKNOWN")
        ));
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE recovery_quarantine
                SET resolved_at = ?, resolved_by = ?, resolution_json = ?
                WHERE quarantine_id = ? AND operation_type = 'INVENTORY'
                    AND operation_id = ? AND resolved_at IS NULL
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(actorId));
            statement.setString(3, resolution);
            statement.setBytes(4, UuidBytes.toBytes(candidate.quarantineId().orElseThrow()));
            statement.setBytes(5, UuidBytes.toBytes(candidate.operationId()));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Item recovery quarantine changed before resolution"
            );
        }
    }

    private void insertAudit(
            Connection connection,
            RecoveryCandidate candidate,
            UUID actorId,
            Instant now
    ) throws SQLException {
        String payload = serialize(Map.of(
                "operationType", candidate.operationType(),
                "scopeId", candidate.scopeId(),
                "owningServerId", candidate.owningServerId(),
                "patchId", candidate.patchId().toString(),
                "fencingToken", candidate.fencingToken(),
                "action", "REQUEUE_FOR_VERIFIED_RETRY"
        ));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, idempotency_key, occurred_at
                ) VALUES (?, ?, ?, ?, ?, 'INVENTORY_QUARANTINE_REQUEUED',
                    'AUTHORIZED_RETRY', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(candidate.operationId()));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setBytes(4, UuidBytes.toBytes(candidate.playerId()));
            statement.setString(5, candidate.caseId());
            statement.setString(6, payload);
            statement.setString(7, "inventory:quarantine-requeue:"
                    + candidate.operationId() + ':' + candidate.fencingToken());
            statement.setTimestamp(8, Timestamp.from(now));
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    statement.executeUpdate(),
                    "Multiple inventory recovery audit rows were written"
            );
        }
    }

    private String serialize(Map<String, ?> values) throws SQLException {
        try {
            return json.writeValueAsString(new LinkedHashMap<>(values));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize item recovery metadata", exception);
        }
    }

    private static InventoryRecoveryResult result(
            InventoryRecoveryResult.Status status,
            Optional<UUID> operationId,
            String detail
    ) {
        return new InventoryRecoveryResult(status, operationId, detail);
    }

    private static String resourceKey(UUID playerId, String scopeId) {
        return "inventory:" + playerId + ':' + scopeId;
    }

    private record RecoveryCandidate(
            UUID patchId,
            UUID operationId,
            UUID profileId,
            String caseId,
            UUID playerId,
            UUID caseTargetId,
            String scopeId,
            String owningServerId,
            long fencingToken,
            InventoryOperationState patchState,
            InventoryOperationState operationState,
            long operationFencingToken,
            UUID operationProfileId,
            String operationType,
            Optional<UUID> quarantineId,
            String resourceKey,
            Optional<String> reasonCode,
            boolean quarantineResolved
    ) {
    }
}
