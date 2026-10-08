package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionSpec;

final class PolicyV2CaseReaderJdbc {
    private static final TypeReference<List<BehavioralHistoryEntry>> HISTORY_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SanctionSpec>> SANCTIONS_TYPE = new TypeReference<>() {
    };

    private final PolicyV2JdbcSupport support;

    PolicyV2CaseReaderJdbc(PolicyV2JdbcSupport support) {
        this.support = support;
    }

    Optional<PolicyV2Store.CaseRecord> loadCase(Connection connection, String caseId) throws SQLException {
        if (caseId == null) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.target_id AS canonical_subject_id, v.policy_snapshot_id, v.original_finding_json, v.effective_finding_json,
                       v.finding_state, v.incident_at, v.finding_revision, v.sanction_revision,
                       r.resolution_id, r.resolution_json, r.history_inputs_json
                FROM policy_v2_cases v
                JOIN cases c ON c.case_id = v.case_id
                JOIN policy_v2_resolutions r ON r.case_id = v.case_id
                WHERE v.case_id = ?
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long sanctionRevision = result.getLong("sanction_revision");
                return Optional.of(new PolicyV2Store.CaseRecord(
                        caseId,
                        UuidBytes.fromBytes(result.getBytes("canonical_subject_id")),
                        UuidBytes.fromBytes(result.getBytes("policy_snapshot_id")),
                        UuidBytes.fromBytes(result.getBytes("resolution_id")),
                        support.read(result.getString("original_finding_json"), IncidentFinding.class),
                        readOptionalFinding(result.getString("effective_finding_json")),
                        BehavioralHistoryEntry.FindingState.valueOf(result.getString("finding_state")),
                        result.getTimestamp("incident_at").toInstant(),
                        result.getLong("finding_revision"),
                        sanctionRevision,
                        support.read(result.getString("resolution_json"), PolicyResolution.class),
                        support.read(result.getString("history_inputs_json"), HISTORY_TYPE),
                        loadRemedies(connection, caseId),
                        loadSanctions(connection, caseId, sanctionRevision)
                ));
            }
        }
    }

    private List<PolicyV2Store.RemedyRecord> loadRemedies(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT remedy_json, status, revision, updated_at
                FROM policy_v2_remedies
                WHERE case_id = ?
                ORDER BY remedy_id
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyV2Store.RemedyRecord> remedies = new ArrayList<>();
                while (result.next()) {
                    RemedySpec remedy = support.read(result.getString("remedy_json"), RemedySpec.class);
                    remedies.add(new PolicyV2Store.RemedyRecord(
                            caseId,
                            remedy,
                            PolicyV2Store.RemedyStatus.valueOf(result.getString("status")),
                            result.getLong("revision"),
                            result.getTimestamp("updated_at").toInstant()
                    ));
                }
                return List.copyOf(remedies);
            }
        }
    }

    PolicyV2Store.SanctionRevisionRecord loadSanctions(
            Connection connection,
            String caseId,
            long revision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT change_kind, sanctions_json, reason, actor_id, appeal_reference, occurred_at
                FROM policy_v2_sanction_revisions
                WHERE case_id = ? AND sanction_revision = ?
                """)) {
            statement.setString(1, caseId);
            statement.setLong(2, revision);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 sanction revision does not exist");
                }
                return new PolicyV2Store.SanctionRevisionRecord(
                        caseId,
                        revision,
                        PolicyV2Store.SanctionChangeKind.valueOf(result.getString("change_kind")),
                        support.read(result.getString("sanctions_json"), SANCTIONS_TYPE),
                        result.getString("reason"),
                        UuidBytes.fromBytes(result.getBytes("actor_id")),
                        Optional.ofNullable(result.getString("appeal_reference")),
                        result.getTimestamp("occurred_at").toInstant()
                );
            }
        }
    }

    List<BehavioralHistoryEntry> loadCompleteHistory(
            Connection connection,
            UUID subjectId,
            Instant asOf
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.case_id, v.original_finding_json, v.effective_finding_json,
                       v.finding_state, v.incident_at
                FROM policy_v2_cases v
                JOIN cases c ON c.case_id = v.case_id
                WHERE c.target_id = ? AND v.incident_at <= ?
                ORDER BY v.incident_at ASC, v.case_id ASC
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId));
            statement.setTimestamp(2, Timestamp.from(asOf));
            try (ResultSet result = statement.executeQuery()) {
                List<BehavioralHistoryEntry> history = new ArrayList<>();
                while (result.next()) {
                    history.add(readHistoryEntry(result));
                }
                return List.copyOf(history);
            }
        }
    }

    List<BehavioralHistoryEntry> loadHistory(
            Connection connection,
            UUID subjectId,
            Instant asOf,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.case_id, v.original_finding_json, v.effective_finding_json,
                       v.finding_state, v.incident_at
                FROM policy_v2_cases v
                JOIN cases c ON c.case_id = v.case_id
                WHERE c.target_id = ? AND v.incident_at <= ?
                ORDER BY v.incident_at DESC, v.case_id DESC
                LIMIT ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId));
            statement.setTimestamp(2, Timestamp.from(asOf));
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<BehavioralHistoryEntry> history = new ArrayList<>();
                while (result.next()) {
                    history.add(readHistoryEntry(result));
                }
                Collections.reverse(history);
                return List.copyOf(history);
            }
        }
    }

    private BehavioralHistoryEntry readHistoryEntry(ResultSet result) throws SQLException {
        IncidentFinding original = support.read(result.getString("original_finding_json"), IncidentFinding.class);
        String effectiveJson = result.getString("effective_finding_json");
        IncidentFinding effective = effectiveJson == null ? null : support.read(effectiveJson, IncidentFinding.class);
        BehavioralHistoryEntry.FindingState state =
                BehavioralHistoryEntry.FindingState.valueOf(result.getString("finding_state"));
        return new BehavioralHistoryEntry(
                result.getString("case_id"),
                result.getTimestamp("incident_at").toInstant(),
                original.offenseId(),
                effective == null ? null : effective.offenseId(),
                state
        );
    }


    private Optional<IncidentFinding> readOptionalFinding(String json) {
        return json == null ? Optional.empty() : Optional.of(support.read(json, IncidentFinding.class));
    }

}
