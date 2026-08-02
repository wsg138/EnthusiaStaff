package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;

/** Appends durable request lifecycle events only. Notification persistence is separate. */
final class JdbcPunishmentRequestEvents {
    JdbcPunishmentRequestEvents(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("json mapper must be present");
        }
    }

    void submitted(Connection connection, PunishmentApprovalRequest request) throws SQLException {
        append(connection, request.requestId(), "SUBMITTED",
                request.proposal().requester().id(), null, null,
                "Punishment request submitted", request.createdAt());
    }

    void leaseAcquired(
            Connection connection,
            UUID requestId,
            UUID ownerId,
            long fenceToken,
            Instant now
    ) throws SQLException {
        append(connection, requestId, "LEASE_ACQUIRED", ownerId, fenceToken, null,
                "Punishment request review lease acquired", now);
    }

    void approved(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID approverId,
            long fenceToken,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        append(connection, request.requestId(), "APPROVED", approverId, fenceToken, caseId,
                "Punishment request approved and committed", now);
    }

    void denied(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID approverId,
            long fenceToken,
            String note,
            Instant now
    ) throws SQLException {
        append(connection, request.requestId(), "DENIED", approverId, fenceToken, null, note, now);
    }

    void expired(Connection connection, PunishmentApprovalRequest request, Instant now)
            throws SQLException {
        append(connection, request.requestId(), "EXPIRED", null, null, null,
                "Punishment request expired without a decision", now);
    }

    void fulfilledExternally(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID actorId,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        append(connection, request.requestId(), "FULFILLED_EXTERNALLY", actorId, null, caseId,
                "Exact matching punishment was applied independently", now);
    }

    private static void append(
            Connection connection,
            UUID requestId,
            String eventType,
            UUID actorId,
            Long fenceToken,
            CaseId caseId,
            String note,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_request_events(
                    event_id, request_id, event_type, actor_id, fence_token,
                    resulting_case_id, note, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(requestId));
            statement.setString(3, eventType);
            setUuid(statement, 4, actorId);
            if (fenceToken == null) {
                statement.setNull(5, java.sql.Types.BIGINT);
            } else {
                statement.setLong(5, fenceToken);
            }
            if (caseId == null) {
                statement.setNull(6, java.sql.Types.CHAR);
            } else {
                statement.setString(6, caseId.value());
            }
            statement.setString(7, note);
            statement.setTimestamp(8, Timestamp.from(occurredAt));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request event was not appended"
            );
        }
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BINARY);
        } else {
            statement.setBytes(index, UuidBytes.toBytes(value));
        }
    }
}
