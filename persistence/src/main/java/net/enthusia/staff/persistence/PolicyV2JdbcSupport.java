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
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2JdbcSupport {
    private static final String SNAPSHOT_SELECT = """
            SELECT snapshot_id, policy_version, content_sha256, created_at
            FROM policy_v2_policy_snapshots
            WHERE policy_version = ?
            """;

    private final DataSource dataSource;
    private final PolicyV2JsonCodec json;

    PolicyV2JdbcSupport(DataSource dataSource, PolicyV2JsonCodec json) {
        if (dataSource == null || json == null) {
            throw new IllegalArgumentException("Policy v2 persistence dependencies must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
    }

    <T> T transaction(String failureMessage, JdbcTransactionSupport.TransactionWork<T> work) {
        return JdbcTransactionSupport.execute(
                dataSource,
                failureMessage,
                Connection.TRANSACTION_READ_COMMITTED,
                work
        );
    }

    PolicyV2Store.StoredSnapshot ensureSnapshot(
            Connection connection,
            PolicySnapshot snapshot,
            Instant recordedAt
    ) throws SQLException {
        String snapshotJson = json.write(snapshot);
        String hash = json.hash(snapshotJson);
        Optional<PolicyV2Store.StoredSnapshot> existing = findSnapshot(connection, snapshot.version());
        if (existing.isPresent()) {
            return requireMatchingSnapshot(connection, existing.orElseThrow(), snapshot);
        }

        UUID snapshotId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_policy_snapshots(
                    snapshot_id, policy_version, schema_version, content_sha256, snapshot_json, created_at
                ) VALUES (?, ?, 1, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(snapshotId));
            statement.setString(2, snapshot.version());
            statement.setString(3, hash);
            statement.setString(4, snapshotJson);
            statement.setTimestamp(5, Timestamp.from(recordedAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
            Optional<PolicyV2Store.StoredSnapshot> raced = findSnapshot(connection, snapshot.version());
            if (raced.isEmpty()) {
                throw exception;
            }
            return requireMatchingSnapshot(connection, raced.orElseThrow(), snapshot);
        }
        return new PolicyV2Store.StoredSnapshot(snapshotId, snapshot.version(), hash, recordedAt);
    }

    PolicySnapshot loadSnapshot(Connection connection, UUID snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT snapshot_json
                FROM policy_v2_policy_snapshots
                WHERE snapshot_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(snapshotId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 snapshot does not exist");
                }
                return json.read(result.getString("snapshot_json"), PolicySnapshot.class);
            }
        }
    }

    Optional<Operation> operation(Connection connection, String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_kind, request_hash, case_id, result_reference, created_at
                FROM policy_v2_operations
                WHERE operation_key = ?
                """)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Operation(
                        result.getString("operation_kind"),
                        result.getString("request_hash"),
                        result.getString("case_id"),
                        result.getString("result_reference"),
                        result.getTimestamp("created_at").toInstant()
                ));
            }
        }
    }

    Operation requireReplay(
            Optional<Operation> existing,
            String expectedKind,
            String expectedHash,
            String operationKey
    ) {
        Operation operation = existing.orElseThrow();
        if (!operation.kind().equals(expectedKind) || !operation.requestHash().equals(expectedHash)) {
            throw new PolicyV2Store.Conflict(
                    "Policy v2 operation key was reused for a different request: " + operationKey
            );
        }
        return operation;
    }

    void recordOperation(
            Connection connection,
            String operationKey,
            String kind,
            String requestHash,
            String caseId,
            String resultReference,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_operations(
                    operation_key, operation_kind, request_hash, case_id, result_reference, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, operationKey);
            statement.setString(2, kind);
            statement.setString(3, requestHash);
            if (caseId == null) {
                statement.setNull(4, java.sql.Types.CHAR);
            } else {
                statement.setString(4, caseId);
            }
            statement.setString(5, resultReference);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    void audit(
            Connection connection,
            String caseId,
            String eventType,
            UUID actorId,
            Object event,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_audit_events(
                    audit_id, case_id, event_type, actor_id, event_json, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            if (caseId == null) {
                statement.setNull(2, java.sql.Types.CHAR);
            } else {
                statement.setString(2, caseId);
            }
            statement.setString(3, eventType);
            setUuid(statement, 4, actorId);
            statement.setString(5, json.write(event));
            statement.setTimestamp(6, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
    }

    String hash(String... parts) {
        return json.hash(parts);
    }

    String write(Object value) {
        return json.write(value);
    }

    <T> T read(String value, Class<T> type) {
        return json.read(value, type);
    }

    <T> T read(String value, com.fasterxml.jackson.core.type.TypeReference<T> type) {
        return json.read(value, type);
    }

    static boolean isDuplicateKey(SQLException exception) {
        return "23000".equals(exception.getSQLState()) && exception.getErrorCode() == 1062;
    }

    static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BINARY);
        } else {
            statement.setBytes(index, UuidBytes.toBytes(value));
        }
    }

    static Optional<UUID> optionalUuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(value));
    }

    private PolicyV2Store.StoredSnapshot requireMatchingSnapshot(
            Connection connection,
            PolicyV2Store.StoredSnapshot stored,
            PolicySnapshot expected
    ) throws SQLException {
        PolicySnapshot persisted = loadSnapshot(connection, stored.snapshotId());
        if (!persisted.equals(expected)) {
            throw new PolicyV2Store.Conflict(
                    "Policy v2 version already exists with different immutable content"
            );
        }
        return stored;
    }

    private Optional<PolicyV2Store.StoredSnapshot> findSnapshot(
            Connection connection,
            String version
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SNAPSHOT_SELECT)) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PolicyV2Store.StoredSnapshot(
                        UuidBytes.fromBytes(result.getBytes("snapshot_id")),
                        result.getString("policy_version"),
                        result.getString("content_sha256"),
                        result.getTimestamp("created_at").toInstant()
                ));
            }
        }
    }

    record Operation(
            String kind,
            String requestHash,
            String caseId,
            String resultReference,
            Instant createdAt
    ) {
    }
}
