package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2EvaluationJdbc {
    private static final String SNAPSHOT_OPERATION = "SNAPSHOT";
    private static final String SHADOW_OPERATION = "SHADOW_EVALUATION";
    private static final TypeReference<List<BehavioralHistoryEntry>> HISTORY_TYPE = new TypeReference<>() {
    };

    private final PolicyV2JdbcSupport support;

    PolicyV2EvaluationJdbc(PolicyV2JdbcSupport support) {
        this.support = support;
    }

    PolicyV2Store.StoredSnapshot storeSnapshot(
            PolicySnapshot snapshot,
            String operationKey,
            java.time.Instant recordedAt
    ) {
        if (snapshot == null || operationKey == null || operationKey.isBlank() || recordedAt == null) {
            throw new IllegalArgumentException("snapshot persistence request is invalid");
        }
        return support.transaction("Unable to store Policy v2 snapshot", connection -> {
            PolicyV2Store.StoredSnapshot stored = support.ensureSnapshot(connection, snapshot, recordedAt);
            String requestHash = support.hash(stored.snapshotId().toString(), recordedAt.toString());
            return recordSnapshotOperation(connection, stored, operationKey.trim(), recordedAt, requestHash);
        });
    }

    PolicySnapshot loadSnapshot(UUID snapshotId) {
        if (snapshotId == null) {
            throw new IllegalArgumentException("snapshot id must be present");
        }
        return support.transaction("Unable to load Policy v2 snapshot",
                connection -> support.loadSnapshot(connection, snapshotId));
    }

    PolicyV2Store.ShadowEvaluation recordShadow(PolicyV2Store.ShadowEvaluationRequest request) {
        return support.transaction("Unable to record Policy v2 shadow evaluation", connection -> {
            PolicyV2Store.StoredSnapshot snapshot = support.ensureSnapshot(
                    connection, request.policySnapshot(), request.evaluatedAt()
            );
            String requestHash = shadowHash(request, snapshot.snapshotId());
            Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, request.operationKey());
            if (existing.isPresent()) {
                PolicyV2JdbcSupport.Operation replay = support.requireReplay(
                        existing, SHADOW_OPERATION, requestHash, request.operationKey()
                );
                return loadShadow(connection, UUID.fromString(replay.resultReference())).orElseThrow();
            }

            UUID evaluationId = UUID.randomUUID();
            insertShadow(connection, evaluationId, snapshot.snapshotId(), request, requestHash);
            support.recordOperation(
                    connection,
                    request.operationKey(),
                    SHADOW_OPERATION,
                    requestHash,
                    null,
                    evaluationId.toString(),
                    request.evaluatedAt()
            );
            return new PolicyV2Store.ShadowEvaluation(
                    evaluationId,
                    request.subjectId(),
                    request.caseId(),
                    snapshot.snapshotId(),
                    request.finding(),
                    request.resolution(),
                    request.historyInputs(),
                    request.evaluatedAt()
            );
        });
    }

    Optional<PolicyV2Store.ShadowEvaluation> shadow(UUID evaluationId) {
        if (evaluationId == null) {
            throw new IllegalArgumentException("evaluation id must be present");
        }
        return support.transaction("Unable to load Policy v2 shadow evaluation",
                connection -> loadShadow(connection, evaluationId));
    }

    private PolicyV2Store.StoredSnapshot recordSnapshotOperation(
            Connection connection,
            PolicyV2Store.StoredSnapshot stored,
            String operationKey,
            java.time.Instant recordedAt,
            String requestHash
    ) throws SQLException {
        Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, operationKey);
        if (existing.isPresent()) {
            PolicyV2JdbcSupport.Operation replay = support.requireReplay(
                    existing, SNAPSHOT_OPERATION, requestHash, operationKey
            );
            if (!stored.snapshotId().toString().equals(replay.resultReference())) {
                throw new PolicyV2Store.Conflict("snapshot replay does not match the recorded result");
            }
            return stored;
        }
        support.recordOperation(
                connection, operationKey, SNAPSHOT_OPERATION, requestHash, null,
                stored.snapshotId().toString(), recordedAt
        );
        return stored;
    }

    private void insertShadow(
            Connection connection,
            UUID evaluationId,
            UUID snapshotId,
            PolicyV2Store.ShadowEvaluationRequest request,
            String requestHash
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_shadow_evaluations(
                    evaluation_id, subject_id, case_id, policy_snapshot_id, finding_json,
                    resolution_json, history_inputs_json, operation_key, request_hash, evaluated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(evaluationId));
            statement.setBytes(2, UuidBytes.toBytes(request.subjectId()));
            if (request.caseId().isPresent()) {
                statement.setString(3, request.caseId().orElseThrow());
            } else {
                statement.setNull(3, java.sql.Types.CHAR);
            }
            statement.setBytes(4, UuidBytes.toBytes(snapshotId));
            statement.setString(5, support.write(request.finding()));
            statement.setString(6, support.write(request.resolution()));
            statement.setString(7, support.write(request.historyInputs()));
            statement.setString(8, request.operationKey());
            statement.setString(9, requestHash);
            statement.setTimestamp(10, Timestamp.from(request.evaluatedAt()));
            statement.executeUpdate();
        }
    }

    private Optional<PolicyV2Store.ShadowEvaluation> loadShadow(
            Connection connection,
            UUID evaluationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT subject_id, case_id, policy_snapshot_id, finding_json,
                       resolution_json, history_inputs_json, evaluated_at
                FROM policy_v2_shadow_evaluations
                WHERE evaluation_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(evaluationId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PolicyV2Store.ShadowEvaluation(
                        evaluationId,
                        UuidBytes.fromBytes(result.getBytes("subject_id")),
                        Optional.ofNullable(result.getString("case_id")),
                        UuidBytes.fromBytes(result.getBytes("policy_snapshot_id")),
                        support.read(result.getString("finding_json"), IncidentFinding.class),
                        support.read(result.getString("resolution_json"), PolicyResolution.class),
                        support.read(result.getString("history_inputs_json"), HISTORY_TYPE),
                        result.getTimestamp("evaluated_at").toInstant()
                ));
            }
        }
    }

    private String shadowHash(
            PolicyV2Store.ShadowEvaluationRequest request,
            UUID snapshotId
    ) {
        return support.hash(
                request.subjectId().toString(),
                request.caseId().orElse(""),
                snapshotId.toString(),
                support.write(request.finding()),
                support.write(request.resolution()),
                support.write(request.historyInputs()),
                request.evaluatedAt().toString()
        );
    }
}
