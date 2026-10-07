package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionSpec;

final class PolicyV2SanctionRevisionJdbc {
    private static final int MAX_HISTORY = 500;
    private static final String SANCTION_OPERATION = "SANCTION_REVISION";
    private static final String INITIAL_SANCTION_REASON = "Initial Policy v2 sanctions";

    private final PolicyV2JdbcSupport support;
    private final PolicyV2CaseReaderJdbc reader;

    PolicyV2SanctionRevisionJdbc(PolicyV2JdbcSupport support, PolicyV2CaseReaderJdbc reader) {
        this.support = support;
        this.reader = reader;
    }

    List<PolicyV2Store.SanctionRevisionRecord> sanctionRevisions(String caseId, int limit) {
        validateHistoryRequest(caseId, limit);
        return support.transaction(
                "Unable to read Policy v2 sanction revisions",
                connection -> loadSanctionRevisions(connection, caseId.trim(), limit)
        );
    }

    PolicyV2Store.SanctionRevisionRecord reviseSanctions(PolicyV2Store.SanctionRevisionRequest request) {
        String requestHash = sanctionHash(request);
        return support.transaction("Unable to revise Policy v2 sanctions", connection -> {
            long currentRevision = lockSanctionRevision(connection, request.caseId());
            Optional<PolicyV2JdbcSupport.Operation> existing =
                    support.operation(connection, request.operationKey());
            if (existing.isPresent()) {
                PolicyV2JdbcSupport.Operation replay = support.requireReplay(
                        existing,
                        SANCTION_OPERATION,
                        requestHash,
                        request.operationKey()
                );
                return reader.loadSanctions(
                        connection,
                        request.caseId(),
                        Long.parseLong(replay.resultReference())
                );
            }
            if (currentRevision != request.expectedSanctionRevision()) {
                throw new PolicyV2Store.Conflict("Policy v2 sanction revision is stale");
            }
            long nextRevision = Math.addExact(currentRevision, 1L);
            updateSanctionRevision(
                    connection,
                    request.caseId(),
                    currentRevision,
                    nextRevision,
                    request.occurredAt()
            );
            insertSanctionRevision(
                    connection,
                    SanctionRevisionWrite.fromRevision(request, nextRevision, requestHash)
            );
            recordSanctionRevision(connection, request, requestHash, nextRevision);
            return reader.loadSanctions(connection, request.caseId(), nextRevision);
        });
    }

    void insertInitialSanctions(
            Connection connection,
            PolicyV2Store.CreateCase request,
            String requestHash
    ) throws SQLException {
        insertSanctionRevision(
                connection,
                SanctionRevisionWrite.initial(request, requestHash)
        );
    }

    private void recordSanctionRevision(
            Connection connection,
            PolicyV2Store.SanctionRevisionRequest request,
            String requestHash,
            long nextRevision
    ) throws SQLException {
        support.recordOperation(
                connection,
                request.operationKey(),
                SANCTION_OPERATION,
                requestHash,
                request.caseId(),
                Long.toString(nextRevision),
                request.occurredAt()
        );
        support.audit(
                connection,
                request.caseId(),
                "SANCTION_REVISED",
                request.actorId(),
                new SanctionAudit(
                        request.changeKind(),
                        nextRevision,
                        request.revision().reason(),
                        request.appealReference()
                ),
                request.occurredAt()
        );
    }

    private void insertSanctionRevision(
            Connection connection,
            SanctionRevisionWrite write
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_sanction_revisions(
                    revision_id, case_id, sanction_revision, change_kind, sanctions_json, reason,
                    actor_id, appeal_reference, operation_key, request_hash, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, write.caseId());
            statement.setLong(3, write.revision());
            statement.setString(4, write.kind().name());
            statement.setString(5, support.write(write.sanctions()));
            statement.setString(6, write.reason());
            statement.setBytes(7, UuidBytes.toBytes(write.actorId()));
            setOptionalText(statement, 8, write.appealReference());
            statement.setString(9, write.operationKey());
            statement.setString(10, write.requestHash());
            statement.setTimestamp(11, Timestamp.from(write.occurredAt()));
            statement.executeUpdate();
        }
    }

    private List<PolicyV2Store.SanctionRevisionRecord> loadSanctionRevisions(
            Connection connection,
            String caseId,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_revision
                FROM policy_v2_sanction_revisions
                WHERE case_id = ?
                ORDER BY sanction_revision DESC
                LIMIT ?
                """)) {
            statement.setString(1, caseId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyV2Store.SanctionRevisionRecord> revisions = new ArrayList<>();
                while (result.next()) {
                    revisions.add(
                            reader.loadSanctions(
                                    connection,
                                    caseId,
                                    result.getLong("sanction_revision")
                            )
                    );
                }
                return List.copyOf(revisions);
            }
        }
    }

    private long lockSanctionRevision(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_revision
                FROM policy_v2_cases
                WHERE case_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 case does not exist");
                }
                return result.getLong("sanction_revision");
            }
        }
    }

    private void updateSanctionRevision(
            Connection connection,
            String caseId,
            long expectedRevision,
            long nextRevision,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE policy_v2_cases
                SET sanction_revision = ?, updated_at = ?
                WHERE case_id = ? AND sanction_revision = ?
                """)) {
            statement.setLong(1, nextRevision);
            statement.setTimestamp(2, Timestamp.from(occurredAt));
            statement.setString(3, caseId);
            statement.setLong(4, expectedRevision);
            if (!JdbcTransactionSupport.updatedOne(statement.executeUpdate())) {
                throw new PolicyV2Store.Conflict(
                        "Policy v2 sanction revision lost a concurrent update"
                );
            }
        }
    }

    private String sanctionHash(PolicyV2Store.SanctionRevisionRequest request) {
        return support.hash(
                request.caseId(),
                Long.toString(request.expectedSanctionRevision()),
                request.changeKind().name(),
                support.write(request.revision()),
                request.actorId().toString(),
                request.appealReference().orElse(""),
                request.occurredAt().toString()
        );
    }

    private static void validateHistoryRequest(String caseId, int limit) {
        if (caseId == null || caseId.isBlank() || limit < 1 || limit > MAX_HISTORY) {
            throw new IllegalArgumentException("Policy v2 history request is invalid");
        }
    }

    private static void setOptionalText(
            PreparedStatement statement,
            int index,
            Optional<String> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private record SanctionAudit(
            PolicyV2Store.SanctionChangeKind changeKind,
            long revision,
            String reason,
            Optional<String> appealReference
    ) {
    }

    private static final class SanctionRevisionWrite {
        private final String caseId;
        private final long revision;
        private final PolicyV2Store.SanctionChangeKind kind;
        private final List<SanctionSpec> sanctions;
        private final String reason;
        private final UUID actorId;
        private final Optional<String> appealReference;
        private final String operationKey;
        private final String requestHash;
        private final Instant occurredAt;

        private SanctionRevisionWrite(
                PolicyV2Store.CreateCase request,
                String requestHash
        ) {
            this.caseId = request.caseId();
            this.revision = 0;
            this.kind = PolicyV2Store.SanctionChangeKind.INITIAL;
            this.sanctions = request.appliedSanctions();
            this.reason = INITIAL_SANCTION_REASON;
            this.actorId = request.actorId();
            this.appealReference = Optional.empty();
            this.operationKey = request.operationKey();
            this.requestHash = requestHash;
            this.occurredAt = request.recordedAt();
        }

        private SanctionRevisionWrite(
                PolicyV2Store.SanctionRevisionRequest request,
                long revision,
                String requestHash
        ) {
            this.caseId = request.caseId();
            this.revision = revision;
            this.kind = request.changeKind();
            this.sanctions = request.revision().replacementSanctions();
            this.reason = request.revision().reason();
            this.actorId = request.actorId();
            this.appealReference = request.appealReference();
            this.operationKey = request.operationKey();
            this.requestHash = requestHash;
            this.occurredAt = request.occurredAt();
        }

        static SanctionRevisionWrite initial(
                PolicyV2Store.CreateCase request,
                String requestHash
        ) {
            return new SanctionRevisionWrite(request, requestHash);
        }

        static SanctionRevisionWrite fromRevision(
                PolicyV2Store.SanctionRevisionRequest request,
                long revision,
                String requestHash
        ) {
            return new SanctionRevisionWrite(request, revision, requestHash);
        }

        String caseId() {
            return caseId;
        }

        long revision() {
            return revision;
        }

        PolicyV2Store.SanctionChangeKind kind() {
            return kind;
        }

        List<SanctionSpec> sanctions() {
            return sanctions;
        }

        String reason() {
            return reason;
        }

        UUID actorId() {
            return actorId;
        }

        Optional<String> appealReference() {
            return appealReference;
        }

        String operationKey() {
            return operationKey;
        }

        String requestHash() {
            return requestHash;
        }

        Instant occurredAt() {
            return occurredAt;
        }
    }

}
