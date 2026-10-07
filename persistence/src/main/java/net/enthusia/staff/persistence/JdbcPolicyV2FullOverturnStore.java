package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnStore;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

public final class JdbcPolicyV2FullOverturnStore implements PolicyV2FullOverturnStore {
    private final PolicyV2JdbcSupport support;

    public JdbcPolicyV2FullOverturnStore(DataSource dataSource) {
        this.support = new PolicyV2JdbcSupport(dataSource, new PolicyV2JsonCodec());
    }

    @Override
    public Operation begin(BeginRequest request) {
        String hash = requestHash(request);
        return support.transaction(
                "Unable to begin Policy v2 full overturn",
                connection -> begin(connection, request, hash)
        );
    }

    @Override
    public Optional<Operation> find(UUID operationId) {
        if (operationId == null) {
            throw new IllegalArgumentException("operationId must be present");
        }
        return support.transaction(
                "Unable to read Policy v2 full overturn",
                connection -> load(connection, operationId, false)
        );
    }

    @Override
    public Operation advance(
            UUID operationId,
            long expectedRevision,
            Stage expectedStage,
            Stage nextStage,
            Instant occurredAt
    ) {
        validateAdvance(operationId, expectedRevision, expectedStage, nextStage, occurredAt);
        return support.transaction(
                "Unable to advance Policy v2 full overturn",
                connection -> advance(
                        connection,
                        operationId,
                        expectedRevision,
                        expectedStage,
                        nextStage,
                        occurredAt
                )
        );
    }

    private Operation begin(Connection connection, BeginRequest request, String hash) throws SQLException {
        Optional<OperationRow> existing = loadRow(connection, request.operationId(), true);
        if (existing.isPresent()) {
            return requireReplay(existing.orElseThrow(), request, hash);
        }
        requireCaseAvailable(connection, request.caseId(), request.operationId());
        try {
            insert(connection, request, hash);
        } catch (SQLException exception) {
            if (!PolicyV2JdbcSupport.isDuplicateKey(exception)) {
                throw exception;
            }
            Optional<OperationRow> raced = loadRow(connection, request.operationId(), true);
            if (raced.isPresent()) {
                return requireReplay(raced.orElseThrow(), request, hash);
            }
            throw new PolicyV2Store.Conflict("Policy v2 case already has a full-overturn operation");
        }
        auditBegin(connection, request);
        return load(connection, request.operationId(), false).orElseThrow();
    }

    private Operation advance(
            Connection connection,
            UUID operationId,
            long expectedRevision,
            Stage expectedStage,
            Stage nextStage,
            Instant occurredAt
    ) throws SQLException {
        Operation current = load(connection, operationId, true)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Full-overturn operation does not exist"));
        if (current.stage() == nextStage && current.revision() == expectedRevision + 1L) {
            return current;
        }
        requireAdvanceFence(current, expectedRevision, expectedStage);
        long nextRevision = Math.addExact(expectedRevision, 1L);
        updateStage(connection, current, nextStage, nextRevision, occurredAt);
        auditAdvance(connection, current, nextStage, nextRevision, occurredAt);
        return load(connection, operationId, false).orElseThrow();
    }

    private void insert(Connection connection, BeginRequest request, String hash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_full_overturns(
                    operation_id, case_id, appeal_reference, actor_id, reason, plan_json,
                    request_hash, stage, revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'STARTED', 0, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(request.operationId()));
            statement.setString(2, request.caseId());
            statement.setString(3, request.appealReference());
            statement.setBytes(4, UuidBytes.toBytes(request.actorId()));
            statement.setString(5, request.reason());
            statement.setString(6, support.write(request.plan()));
            statement.setString(7, hash);
            statement.setTimestamp(8, Timestamp.from(request.occurredAt()));
            statement.setTimestamp(9, Timestamp.from(request.occurredAt()));
            statement.executeUpdate();
        }
    }

    private void updateStage(
            Connection connection,
            Operation current,
            Stage nextStage,
            long nextRevision,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE policy_v2_full_overturns
                SET stage = ?, revision = ?, updated_at = ?
                WHERE operation_id = ? AND stage = ? AND revision = ?
                """)) {
            statement.setString(1, nextStage.name());
            statement.setLong(2, nextRevision);
            statement.setTimestamp(3, Timestamp.from(occurredAt));
            statement.setBytes(4, UuidBytes.toBytes(current.operationId()));
            statement.setString(5, current.stage().name());
            statement.setLong(6, current.revision());
            if (!JdbcTransactionSupport.updatedOne(statement.executeUpdate())) {
                throw new PolicyV2Store.Conflict("Full-overturn stage lost a concurrent update");
            }
        }
    }

    private Optional<Operation> load(
            Connection connection,
            UUID operationId,
            boolean forUpdate
    ) throws SQLException {
        return loadRow(connection, operationId, forUpdate).map(OperationRow::operation);
    }

    private Optional<OperationRow> loadRow(
            Connection connection,
            UUID operationId,
            boolean forUpdate
    ) throws SQLException {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, appeal_reference, actor_id, reason, plan_json, request_hash,
                       stage, revision, created_at, updated_at
                FROM policy_v2_full_overturns
                WHERE operation_id = ?
                """ + suffix)) {
            statement.setBytes(1, UuidBytes.toBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                Plan plan = support.read(result.getString("plan_json"), Plan.class);
                Operation operation = new Operation(
                        operationId,
                        result.getString("case_id"),
                        result.getString("appeal_reference"),
                        UuidBytes.fromBytes(result.getBytes("actor_id")),
                        result.getString("reason"),
                        plan,
                        Stage.valueOf(result.getString("stage")),
                        result.getLong("revision"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant()
                );
                return Optional.of(new OperationRow(operation, result.getString("request_hash")));
            }
        }
    }

    private void requireCaseAvailable(
            Connection connection,
            String caseId,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_id
                FROM policy_v2_full_overturns
                WHERE case_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && !operationId.equals(UuidBytes.fromBytes(result.getBytes("operation_id")))) {
                    throw new PolicyV2Store.Conflict("Policy v2 case already has a full-overturn operation");
                }
            }
        }
    }

    private Operation requireReplay(OperationRow existing, BeginRequest request, String hash) {
        Operation operation = existing.operation();
        boolean identityMatches = operation.caseId().equals(request.caseId())
                && operation.appealReference().equals(request.appealReference())
                && operation.actorId().equals(request.actorId())
                && operation.reason().equals(request.reason())
                && operation.plan().equals(request.plan());
        if (!identityMatches || !existing.requestHash().equals(hash)) {
            throw new PolicyV2Store.Conflict("Full-overturn operation ID was reused for a different request");
        }
        return operation;
    }

    private void auditBegin(Connection connection, BeginRequest request) throws SQLException {
        support.audit(
                connection,
                request.caseId(),
                "FULL_OVERTURN_STARTED",
                request.actorId(),
                new StageAudit(request.operationId(), null, Stage.STARTED, 0L),
                request.occurredAt()
        );
    }

    private void auditAdvance(
            Connection connection,
            Operation current,
            Stage nextStage,
            long nextRevision,
            Instant occurredAt
    ) throws SQLException {
        support.audit(
                connection,
                current.caseId(),
                "FULL_OVERTURN_" + nextStage.name(),
                current.actorId(),
                new StageAudit(current.operationId(), current.stage(), nextStage, nextRevision),
                occurredAt
        );
    }

    private String requestHash(BeginRequest request) {
        return support.hash(
                request.operationId().toString(),
                request.caseId(),
                request.appealReference(),
                request.actorId().toString(),
                request.reason(),
                support.write(request.plan()),
                request.occurredAt().toString()
        );
    }

    private static void validateAdvance(
            UUID operationId,
            long expectedRevision,
            Stage expectedStage,
            Stage nextStage,
            Instant occurredAt
    ) {
        if (operationId == null || expectedRevision < 0 || expectedStage == null
                || nextStage == null || occurredAt == null
                || nextStage.ordinal() != expectedStage.ordinal() + 1) {
            throw new IllegalArgumentException("full-overturn stage transition is invalid");
        }
    }

    private static void requireAdvanceFence(
            Operation current,
            long expectedRevision,
            Stage expectedStage
    ) {
        if (current.revision() != expectedRevision || current.stage() != expectedStage) {
            throw new PolicyV2Store.Conflict("Full-overturn operation fence is stale");
        }
    }

    private record OperationRow(Operation operation, String requestHash) {
    }

    private record StageAudit(UUID operationId, Stage from, Stage to, long revision) {
    }
}
