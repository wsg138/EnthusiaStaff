package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.CaseRevision;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2FindingRevisionJdbc {
    private static final int MAX_HISTORY = 500;
    private static final String FINDING_OPERATION = "FINDING_REVISION";

    private final PolicyV2JdbcSupport support;
    private final PolicyV2CaseReaderJdbc reader;

    PolicyV2FindingRevisionJdbc(PolicyV2JdbcSupport support, PolicyV2CaseReaderJdbc reader) {
        this.support = support;
        this.reader = reader;
    }

    List<PolicyV2Store.FindingRevisionRecord> findingRevisions(String caseId, int limit) {
        validateHistoryRequest(caseId, limit);
        return support.transaction(
                "Unable to read Policy v2 finding revisions",
                connection -> loadFindingRevisions(connection, caseId.trim(), limit)
        );
    }

    PolicyV2Store.CaseRecord reviseFinding(PolicyV2Store.FindingRevisionRequest request) {
        String requestHash = findingHash(request);
        return support.transaction("Unable to revise Policy v2 finding", connection -> {
            FindingRow current = lockFinding(connection, request.caseId());
            Optional<PolicyV2JdbcSupport.Operation> existing =
                    support.operation(connection, request.operationKey());
            if (existing.isPresent()) {
                support.requireReplay(existing, FINDING_OPERATION, requestHash, request.operationKey());
                return reader.loadCase(connection, request.caseId()).orElseThrow();
            }
            requireFindingRevision(current, request);
            long nextRevision = Math.addExact(current.revision(), 1L);
            FindingResult result = findingResult(request, current);
            updateFinding(connection, request, nextRevision, result);
            insertFindingRevision(connection, request, requestHash, current, nextRevision, result);
            recordFindingRevision(connection, request, requestHash, current, nextRevision, result);
            return reader.loadCase(connection, request.caseId()).orElseThrow();
        });
    }

    private void recordFindingRevision(
            Connection connection,
            PolicyV2Store.FindingRevisionRequest request,
            String requestHash,
            FindingRow current,
            long nextRevision,
            FindingResult result
    ) throws SQLException {
        support.recordOperation(
                connection,
                request.operationKey(),
                FINDING_OPERATION,
                requestHash,
                request.caseId(),
                Long.toString(nextRevision),
                request.occurredAt()
        );
        support.audit(
                connection,
                request.caseId(),
                result.auditType(),
                request.actorId(),
                new FindingAudit(
                        current.offenseId(),
                        result.offenseId(),
                        request.revision().reason(),
                        request.appealReference()
                ),
                request.occurredAt()
        );
    }

    private FindingRow lockFinding(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT original_finding_json, effective_finding_json, finding_state, finding_revision
                FROM policy_v2_cases
                WHERE case_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 case does not exist");
                }
                String findingJson = result.getString("effective_finding_json");
                IncidentFinding finding = findingJson == null ? null
                        : support.read(findingJson, IncidentFinding.class);
                IncidentFinding original = support.read(
                        result.getString("original_finding_json"),
                        IncidentFinding.class
                );
                return new FindingRow(
                        original.offenseId(),
                        finding,
                        BehavioralHistoryEntry.FindingState.valueOf(result.getString("finding_state")),
                        result.getLong("finding_revision")
                );
            }
        }
    }

    private void requireFindingRevision(
            FindingRow current,
            PolicyV2Store.FindingRevisionRequest request
    ) {
        if (current.revision() != request.expectedFindingRevision()) {
            throw new PolicyV2Store.Conflict("Policy v2 finding revision is stale");
        }
        if (current.state() == BehavioralHistoryEntry.FindingState.OVERTURNED || current.finding() == null) {
            throw new PolicyV2Store.Conflict("overturned findings cannot be revised");
        }
        String expectedOffense = current.finding().offenseId();
        String revisionOffense = request.revision() instanceof CaseRevision.FindingReclassification reclassification
                ? reclassification.fromOffenseId()
                : ((CaseRevision.FindingOverturn) request.revision()).offenseId();
        if (!expectedOffense.equals(revisionOffense)) {
            throw new PolicyV2Store.Conflict("finding revision does not match the effective offense");
        }
    }

    private FindingResult findingResult(
            PolicyV2Store.FindingRevisionRequest request,
            FindingRow current
    ) {
        if (request.revision() instanceof CaseRevision.FindingReclassification) {
            IncidentFinding replacement = request.replacementFinding().orElseThrow();
            BehavioralHistoryEntry.FindingState state = replacement.offenseId().equals(current.originalOffenseId())
                    ? BehavioralHistoryEntry.FindingState.CONFIRMED
                    : BehavioralHistoryEntry.FindingState.RECLASSIFIED;
            return new FindingResult(
                    replacement,
                    state,
                    "RECLASSIFICATION",
                    "FINDING_RECLASSIFIED"
            );
        }
        return new FindingResult(
                null,
                BehavioralHistoryEntry.FindingState.OVERTURNED,
                "OVERTURN",
                "FINDING_OVERTURNED"
        );
    }

    private void updateFinding(
            Connection connection,
            PolicyV2Store.FindingRevisionRequest request,
            long nextRevision,
            FindingResult result
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE policy_v2_cases
                SET effective_finding_json = ?, finding_state = ?, finding_revision = ?, updated_at = ?
                WHERE case_id = ? AND finding_revision = ?
                """)) {
            if (result.finding() == null) {
                statement.setNull(1, java.sql.Types.LONGVARCHAR);
            } else {
                statement.setString(1, support.write(result.finding()));
            }
            statement.setString(2, result.state().name());
            statement.setLong(3, nextRevision);
            statement.setTimestamp(4, Timestamp.from(request.occurredAt()));
            statement.setString(5, request.caseId());
            statement.setLong(6, request.expectedFindingRevision());
            if (!JdbcTransactionSupport.updatedOne(statement.executeUpdate())) {
                throw new PolicyV2Store.Conflict("Policy v2 finding revision lost a concurrent update");
            }
        }
    }

    private void insertFindingRevision(
            Connection connection,
            PolicyV2Store.FindingRevisionRequest request,
            String requestHash,
            FindingRow current,
            long nextRevision,
            FindingResult result
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_finding_revisions(
                    revision_id, case_id, finding_revision, revision_type, from_offense_id,
                    to_offense_id, resulting_finding_json, reason, actor_id, appeal_reference,
                    operation_key, request_hash, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, request.caseId());
            statement.setLong(3, nextRevision);
            statement.setString(4, result.revisionType());
            statement.setString(5, current.offenseId());
            setNullableString(statement, 6, result.offenseId());
            setNullableString(statement, 7, result.finding() == null ? null : support.write(result.finding()));
            statement.setString(8, request.revision().reason());
            statement.setBytes(9, UuidBytes.toBytes(request.actorId()));
            setOptionalText(statement, 10, request.appealReference());
            statement.setString(11, request.operationKey());
            statement.setString(12, requestHash);
            statement.setTimestamp(13, Timestamp.from(request.occurredAt()));
            statement.executeUpdate();
        }
    }

    private List<PolicyV2Store.FindingRevisionRecord> loadFindingRevisions(
            Connection connection,
            String caseId,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT finding_revision, revision_type, from_offense_id, to_offense_id,
                       resulting_finding_json, reason, actor_id, appeal_reference, occurred_at
                FROM policy_v2_finding_revisions
                WHERE case_id = ?
                ORDER BY finding_revision DESC
                LIMIT ?
                """)) {
            statement.setString(1, caseId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyV2Store.FindingRevisionRecord> revisions = new ArrayList<>();
                while (result.next()) {
                    revisions.add(readFindingRevision(result, caseId));
                }
                return List.copyOf(revisions);
            }
        }
    }

    private PolicyV2Store.FindingRevisionRecord readFindingRevision(
            ResultSet result,
            String caseId
    ) throws SQLException {
        String findingJson = result.getString("resulting_finding_json");
        return new PolicyV2Store.FindingRevisionRecord(
                caseId,
                result.getLong("finding_revision"),
                PolicyV2Store.FindingChangeKind.valueOf(result.getString("revision_type")),
                result.getString("from_offense_id"),
                Optional.ofNullable(result.getString("to_offense_id")),
                findingJson == null
                        ? Optional.empty()
                        : Optional.of(support.read(findingJson, IncidentFinding.class)),
                result.getString("reason"),
                UuidBytes.fromBytes(result.getBytes("actor_id")),
                Optional.ofNullable(result.getString("appeal_reference")),
                result.getTimestamp("occurred_at").toInstant()
        );
    }

    private String findingHash(PolicyV2Store.FindingRevisionRequest request) {
        return support.hash(
                request.caseId(),
                Long.toString(request.expectedFindingRevision()),
                support.write(request.revision()),
                request.replacementFinding().map(support::write).orElse(""),
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
        setNullableString(statement, index, value.orElse(null));
    }

    private static void setNullableString(
            PreparedStatement statement,
            int index,
            String value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private record FindingRow(
            String originalOffenseId,
            IncidentFinding finding,
            BehavioralHistoryEntry.FindingState state,
            long revision
    ) {
        String offenseId() {
            return finding == null ? null : finding.offenseId();
        }
    }

    private record FindingResult(
            IncidentFinding finding,
            BehavioralHistoryEntry.FindingState state,
            String revisionType,
            String auditType
    ) {
        String offenseId() {
            return finding == null ? null : finding.offenseId();
        }
    }

    private record FindingAudit(
            String fromOffenseId,
            String toOffenseId,
            String reason,
            Optional<String> appealReference
    ) {
    }
}
