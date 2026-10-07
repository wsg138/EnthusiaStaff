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
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2ComplianceJdbc {
    private static final int MAX_HISTORY = 500;
    private static final String REMEDY_OPERATION = "REMEDY_UPDATE";
    private static final String APPEAL_OPERATION = "APPEAL_EVENT";

    private final PolicyV2JdbcSupport support;

    PolicyV2ComplianceJdbc(PolicyV2JdbcSupport support) {
        this.support = support;
    }

    PolicyV2Store.RemedyRecord updateRemedy(PolicyV2Store.RemedyUpdateRequest request) {
        String requestHash = remedyHash(request);
        return support.transaction("Unable to update Policy v2 remedy", connection -> {
            PolicyV2Store.RemedyRecord current = lockRemedy(connection, request.caseId(), request.remedyId());
            Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, request.operationKey());
            if (existing.isPresent()) {
                support.requireReplay(existing, REMEDY_OPERATION, requestHash, request.operationKey());
                return loadRemedy(connection, request.caseId(), request.remedyId());
            }
            if (current.revision() != request.expectedRevision()) {
                throw new PolicyV2Store.Conflict("Policy v2 remedy revision is stale");
            }
            PolicyV2Store.RemedyRecord updated = applyRemedyUpdate(connection, request, current);
            support.recordOperation(
                    connection,
                    request.operationKey(),
                    REMEDY_OPERATION,
                    requestHash,
                    request.caseId(),
                    request.remedyId() + ":" + updated.revision(),
                    request.occurredAt()
            );
            support.audit(
                    connection,
                    request.caseId(),
                    "REMEDY_UPDATED",
                    request.actorId(),
                    new RemedyAudit(request.remedyId(), current.status(), updated.status(),
                            request.reason(), updated.revision()),
                    request.occurredAt()
            );
            return updated;
        });
    }

    List<PolicyV2Store.AppealEvent> appealHistory(String caseId, int limit) {
        validateHistoryRequest(caseId, limit);
        return support.transaction("Unable to read Policy v2 appeal history",
                connection -> loadAppealHistory(connection, caseId.trim(), limit));
    }

    List<PolicyV2Store.AuditEvent> auditHistory(String caseId, int limit) {
        validateHistoryRequest(caseId, limit);
        return support.transaction("Unable to read Policy v2 audit history",
                connection -> loadAuditHistory(connection, caseId.trim(), limit));
    }

    PolicyV2Store.AppealEvent appendAppealEvent(PolicyV2Store.AppealEventRequest request) {
        String requestHash = appealHash(request);
        return support.transaction("Unable to append Policy v2 appeal event", connection -> {
            requirePolicyCase(connection, request.caseId());
            Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, request.operationKey());
            if (existing.isPresent()) {
                PolicyV2JdbcSupport.Operation replay = support.requireReplay(
                        existing, APPEAL_OPERATION, requestHash, request.operationKey()
                );
                return loadAppealEvent(connection, UUID.fromString(replay.resultReference()));
            }
            UUID eventId = UUID.randomUUID();
            insertAppealEvent(connection, eventId, request, requestHash);
            support.recordOperation(
                    connection,
                    request.operationKey(),
                    APPEAL_OPERATION,
                    requestHash,
                    request.caseId(),
                    eventId.toString(),
                    request.occurredAt()
            );
            support.audit(
                    connection,
                    request.caseId(),
                    "APPEAL_" + request.eventType().name(),
                    request.actorId().orElse(null),
                    new AppealAudit(request.appealReference(), request.eventType(), request.note()),
                    request.occurredAt()
            );
            return new PolicyV2Store.AppealEvent(
                    eventId,
                    request.caseId(),
                    request.appealReference(),
                    request.eventType(),
                    request.actorId(),
                    request.note(),
                    request.occurredAt()
            );
        });
    }

    private List<PolicyV2Store.AppealEvent> loadAppealHistory(
            Connection connection,
            String caseId,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event_id
                FROM policy_v2_appeal_events
                WHERE case_id = ?
                ORDER BY occurred_at DESC, event_id DESC
                LIMIT ?
                """)) {
            statement.setString(1, caseId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyV2Store.AppealEvent> events = new ArrayList<>();
                while (result.next()) {
                    events.add(loadAppealEvent(connection, UuidBytes.fromBytes(result.getBytes("event_id"))));
                }
                return List.copyOf(events);
            }
        }
    }

    private List<PolicyV2Store.AuditEvent> loadAuditHistory(
            Connection connection,
            String caseId,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT audit_id, event_type, actor_id, occurred_at
                FROM policy_v2_audit_events
                WHERE case_id = ?
                ORDER BY occurred_at DESC, audit_id DESC
                LIMIT ?
                """)) {
            statement.setString(1, caseId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyV2Store.AuditEvent> events = new ArrayList<>();
                while (result.next()) {
                    events.add(new PolicyV2Store.AuditEvent(
                            UuidBytes.fromBytes(result.getBytes("audit_id")),
                            caseId,
                            result.getString("event_type"),
                            PolicyV2JdbcSupport.optionalUuid(result, "actor_id"),
                            result.getTimestamp("occurred_at").toInstant()
                    ));
                }
                return List.copyOf(events);
            }
        }
    }

    private static void validateHistoryRequest(String caseId, int limit) {
        if (caseId == null || caseId.isBlank() || limit < 1 || limit > MAX_HISTORY) {
            throw new IllegalArgumentException("Policy v2 history request is invalid");
        }
    }


    private PolicyV2Store.RemedyRecord lockRemedy(
            Connection connection,
            String caseId,
            String remedyId
    ) throws SQLException {
        return readRemedy(connection, caseId, remedyId, true);
    }

    private PolicyV2Store.RemedyRecord loadRemedy(
            Connection connection,
            String caseId,
            String remedyId
    ) throws SQLException {
        return readRemedy(connection, caseId, remedyId, false);
    }

    private PolicyV2Store.RemedyRecord readRemedy(
            Connection connection,
            String caseId,
            String remedyId,
            boolean lock
    ) throws SQLException {
        String sql = """
                SELECT remedy_json, status, revision, updated_at
                FROM policy_v2_remedies
                WHERE case_id = ? AND remedy_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId);
            statement.setString(2, remedyId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 remedy does not exist");
                }
                return new PolicyV2Store.RemedyRecord(
                        caseId,
                        support.read(result.getString("remedy_json"), RemedySpec.class),
                        PolicyV2Store.RemedyStatus.valueOf(result.getString("status")),
                        result.getLong("revision"),
                        result.getTimestamp("updated_at").toInstant()
                );
            }
        }
    }

    private PolicyV2Store.RemedyRecord applyRemedyUpdate(
            Connection connection,
            PolicyV2Store.RemedyUpdateRequest request,
            PolicyV2Store.RemedyRecord current
    ) throws SQLException {
        long nextRevision = Math.addExact(current.revision(), 1L);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE policy_v2_remedies
                SET status = ?, revision = ?, updated_at = ?
                WHERE case_id = ? AND remedy_id = ? AND revision = ?
                """)) {
            statement.setString(1, request.status().name());
            statement.setLong(2, nextRevision);
            statement.setTimestamp(3, Timestamp.from(request.occurredAt()));
            statement.setString(4, request.caseId());
            statement.setString(5, request.remedyId());
            statement.setLong(6, request.expectedRevision());
            if (!JdbcTransactionSupport.updatedOne(statement.executeUpdate())) {
                throw new PolicyV2Store.Conflict("Policy v2 remedy update lost a concurrent write");
            }
        }
        return new PolicyV2Store.RemedyRecord(
                request.caseId(), current.remedy(), request.status(), nextRevision, request.occurredAt()
        );
    }

    private void insertAppealEvent(
            Connection connection,
            UUID eventId,
            PolicyV2Store.AppealEventRequest request,
            String requestHash
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_appeal_events(
                    event_id, case_id, appeal_reference, event_type, actor_id, note,
                    operation_key, request_hash, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(eventId));
            statement.setString(2, request.caseId());
            statement.setString(3, request.appealReference());
            statement.setString(4, request.eventType().name());
            PolicyV2JdbcSupport.setUuid(statement, 5, request.actorId().orElse(null));
            statement.setString(6, request.note());
            statement.setString(7, request.operationKey());
            statement.setString(8, requestHash);
            statement.setTimestamp(9, Timestamp.from(request.occurredAt()));
            statement.executeUpdate();
        }
    }

    private PolicyV2Store.AppealEvent loadAppealEvent(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, appeal_reference, event_type, actor_id, note, occurred_at
                FROM policy_v2_appeal_events
                WHERE event_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(eventId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 appeal event does not exist");
                }
                return new PolicyV2Store.AppealEvent(
                        eventId,
                        result.getString("case_id"),
                        result.getString("appeal_reference"),
                        PolicyV2Store.AppealEventType.valueOf(result.getString("event_type")),
                        PolicyV2JdbcSupport.optionalUuid(result, "actor_id"),
                        result.getString("note"),
                        result.getTimestamp("occurred_at").toInstant()
                );
            }
        }
    }


    private void requirePolicyCase(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM policy_v2_cases WHERE case_id = ? FOR UPDATE"
        )) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 case does not exist");
                }
            }
        }
    }


    private String remedyHash(PolicyV2Store.RemedyUpdateRequest request) {
        return support.hash(
                request.caseId(),
                request.remedyId(),
                Long.toString(request.expectedRevision()),
                request.status().name(),
                request.actorId().toString(),
                request.reason(),
                request.occurredAt().toString()
        );
    }

    private String appealHash(PolicyV2Store.AppealEventRequest request) {
        return support.hash(
                request.caseId(),
                request.appealReference(),
                request.eventType().name(),
                request.actorId().map(UUID::toString).orElse(""),
                request.note(),
                request.occurredAt().toString()
        );
    }


    private record RemedyAudit(
            String remedyId,
            PolicyV2Store.RemedyStatus fromStatus,
            PolicyV2Store.RemedyStatus toStatus,
            String reason,
            long revision
    ) {
    }

    private record AppealAudit(
            String appealReference,
            PolicyV2Store.AppealEventType eventType,
            String note
    ) {
    }
}
