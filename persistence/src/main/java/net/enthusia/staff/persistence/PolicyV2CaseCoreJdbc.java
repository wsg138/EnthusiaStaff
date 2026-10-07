package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2CaseCoreJdbc {
    private static final int MAX_HISTORY = 500;
    private static final String CREATE_OPERATION = "CREATE_CASE";

    private final PolicyV2JdbcSupport support;
    private final PolicyV2CaseReaderJdbc reader;
    private final PolicyV2RevisionJdbc revisions;

    PolicyV2CaseCoreJdbc(
            PolicyV2JdbcSupport support,
            PolicyV2CaseReaderJdbc reader,
            PolicyV2RevisionJdbc revisions
    ) {
        this.support = support;
        this.reader = reader;
        this.revisions = revisions;
    }

    PolicyV2Store.CaseRecord create(PolicyV2Store.CreateCase request) {
        return support.transaction("Unable to create Policy v2 case", connection -> {
            requireLegacyCase(connection, request.caseId());
            PolicyV2Store.StoredSnapshot snapshot = support.ensureSnapshot(
                    connection, request.policySnapshot(), request.recordedAt()
            );
            String requestHash = createHash(request, snapshot.snapshotId());
            Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, request.operationKey());
            if (existing.isPresent()) {
                PolicyV2JdbcSupport.Operation replay = support.requireReplay(
                        existing, CREATE_OPERATION, requestHash, request.operationKey()
                );
                return reader.loadCase(connection, replay.caseId())
                        .orElseThrow(() -> new PolicyV2Store.MissingRecord("replayed Policy v2 case is missing"));
            }
            UUID resolutionId = UUID.randomUUID();
            if (reader.loadCase(connection, request.caseId()).isPresent()) {
                throw new PolicyV2Store.Conflict(
                        "Policy v2 case already exists for a different operation"
                );
            }
            insertCase(connection, snapshot.snapshotId(), request);
            insertResolution(connection, resolutionId, snapshot.snapshotId(), request);
            insertRemedies(connection, request.caseId(), request.resolution().remedies(), request.recordedAt());
            revisions.insertInitialSanctions(connection, request, requestHash);
            support.recordOperation(
                    connection,
                    request.operationKey(),
                    CREATE_OPERATION,
                    requestHash,
                    request.caseId(),
                    request.caseId(),
                    request.recordedAt()
            );
            support.audit(
                    connection,
                    request.caseId(),
                    "CASE_CREATED",
                    request.actorId(),
                    new CaseCreatedAudit(snapshot.snapshotId(), resolutionId, request.finding().offenseId()),
                    request.recordedAt()
            );
            return reader.loadCase(connection, request.caseId()).orElseThrow();
        });
    }

    Optional<PolicyV2Store.CaseRecord> find(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("case id must not be blank");
        }
        return support.transaction("Unable to read Policy v2 case",
                connection -> reader.loadCase(connection, caseId.trim()));
    }

    List<BehavioralHistoryEntry> history(UUID subjectId, Instant asOf, int limit) {
        if (subjectId == null || asOf == null || limit < 1 || limit > MAX_HISTORY) {
            throw new IllegalArgumentException("Policy v2 history request is invalid");
        }
        return support.transaction("Unable to read Policy v2 history",
                connection -> reader.loadHistory(connection, subjectId, asOf, limit));
    }


    private void insertCase(
            Connection connection,
            UUID snapshotId,
            PolicyV2Store.CreateCase request
    ) throws SQLException {
        String findingJson = support.write(request.finding());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_cases(
                    case_id, policy_snapshot_id, original_finding_json, effective_finding_json,
                    finding_state, incident_at, finding_revision, sanction_revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'CONFIRMED', ?, 0, 0, ?, ?)
                """)) {
            statement.setString(1, request.caseId());
            statement.setBytes(2, UuidBytes.toBytes(snapshotId));
            statement.setString(3, findingJson);
            statement.setString(4, findingJson);
            statement.setTimestamp(5, Timestamp.from(request.incidentAt()));
            statement.setTimestamp(6, Timestamp.from(request.recordedAt()));
            statement.setTimestamp(7, Timestamp.from(request.recordedAt()));
            statement.executeUpdate();
        }
    }

    private void insertResolution(
            Connection connection,
            UUID resolutionId,
            UUID snapshotId,
            PolicyV2Store.CreateCase request
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_resolutions(
                    resolution_id, case_id, policy_snapshot_id, resolution_json, history_inputs_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(resolutionId));
            statement.setString(2, request.caseId());
            statement.setBytes(3, UuidBytes.toBytes(snapshotId));
            statement.setString(4, support.write(request.resolution()));
            statement.setString(5, support.write(request.historyInputs()));
            statement.setTimestamp(6, Timestamp.from(request.recordedAt()));
            statement.executeUpdate();
        }
    }

    private void insertRemedies(
            Connection connection,
            String caseId,
            List<RemedySpec> remedies,
            Instant recordedAt
    ) throws SQLException {
        if (remedies.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_remedies(
                    case_id, remedy_id, remedy_json, status, revision, updated_at
                ) VALUES (?, ?, ?, 'REQUIRED', 0, ?)
                """)) {
            for (RemedySpec remedy : remedies) {
                statement.setString(1, caseId);
                statement.setString(2, remedy.id());
                statement.setString(3, support.write(remedy));
                statement.setTimestamp(4, Timestamp.from(recordedAt));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }


    private void requireLegacyCase(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM cases WHERE case_id = ? FOR UPDATE"
        )) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("base moderation case does not exist");
                }
            }
        }
    }


    private String createHash(PolicyV2Store.CreateCase request, UUID snapshotId) {
        return support.hash(
                request.caseId(),
                snapshotId.toString(),
                support.write(request.finding()),
                request.incidentAt().toString(),
                support.write(request.resolution()),
                support.write(request.historyInputs()),
                support.write(request.appliedSanctions()),
                request.actorId().toString(),
                request.recordedAt().toString()
        );
    }


    private record CaseCreatedAudit(UUID snapshotId, UUID resolutionId, String offenseId) {
    }

}
