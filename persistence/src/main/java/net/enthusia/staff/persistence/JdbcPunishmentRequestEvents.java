package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.auth.StaffRank;

final class JdbcPunishmentRequestEvents {
    private final ObjectMapper json;

    JdbcPunishmentRequestEvents(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("json mapper must be present");
        }
        this.json = json;
    }

    void submitted(Connection connection, PunishmentApprovalRequest request) throws SQLException {
        append(
                connection,
                request.requestId(),
                "SUBMITTED",
                request.proposal().requester().id(),
                null,
                null,
                "Punishment request submitted",
                request.createdAt()
        );
        notifyPending(connection, request);
    }

    void leaseAcquired(
            Connection connection,
            UUID requestId,
            UUID ownerId,
            long fenceToken,
            Instant now
    ) throws SQLException {
        append(
                connection,
                requestId,
                "LEASE_ACQUIRED",
                ownerId,
                fenceToken,
                null,
                "Punishment request review lease acquired",
                now
        );
    }

    void approved(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID approverId,
            long fenceToken,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        append(
                connection,
                request.requestId(),
                "APPROVED",
                approverId,
                fenceToken,
                caseId,
                "Punishment request approved and committed",
                now
        );
        notifyResolution(connection, request, "PUNISHMENT_REQUEST_APPROVED", caseId, now);
    }

    void denied(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID approverId,
            long fenceToken,
            String note,
            Instant now
    ) throws SQLException {
        append(
                connection,
                request.requestId(),
                "DENIED",
                approverId,
                fenceToken,
                null,
                note,
                now
        );
        notifyResolution(connection, request, "PUNISHMENT_REQUEST_DENIED", null, now);
    }

    void expired(Connection connection, PunishmentApprovalRequest request, Instant now)
            throws SQLException {
        append(
                connection,
                request.requestId(),
                "EXPIRED",
                null,
                null,
                null,
                "Punishment request expired without a decision",
                now
        );
        notifyResolution(connection, request, "PUNISHMENT_REQUEST_EXPIRED", null, now);
    }

    static void fulfilledExternally(
            Connection connection,
            UUID requestId,
            UUID actorId,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        append(
                connection,
                requestId,
                "FULFILLED_EXTERNALLY",
                actorId,
                null,
                caseId,
                "Exact matching punishment was applied independently",
                now
        );
    }

    private void notifyPending(Connection connection, PunishmentApprovalRequest request)
            throws SQLException {
        Map<String, Object> payload = basePayload(request);
        payload.put("requiredRank", approvalMinimum(request.proposal().requiredRank()).name());
        payload.put("expiresAt", request.expiresAt().toString());
        String serialized = serialize(payload);
        insertAlert(
                connection,
                null,
                approvalMinimum(request.proposal().requiredRank()),
                "PUNISHMENT_REQUEST_PENDING",
                serialized,
                request.createdAt()
        );
        insertDiscord(
                connection,
                "punishment-request:" + request.requestId() + ":pending",
                "PUNISHMENT_REQUEST_PENDING",
                serialized,
                request.createdAt()
        );
    }

    private void notifyResolution(
            Connection connection,
            PunishmentApprovalRequest request,
            String eventType,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        Map<String, Object> payload = basePayload(request);
        payload.put("status", eventType);
        if (caseId != null) {
            payload.put("caseId", caseId.value());
        }
        String serialized = serialize(payload);
        insertAlert(
                connection,
                request.proposal().requester().id(),
                StaffRank.HELPER,
                eventType,
                serialized,
                now
        );
        insertDiscord(
                connection,
                "punishment-request:" + request.requestId() + ':' + eventType.toLowerCase(java.util.Locale.ROOT),
                eventType,
                serialized,
                now
        );
    }

    private static Map<String, Object> basePayload(PunishmentApprovalRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.requestId().toString());
        payload.put("requesterId", request.proposal().requester().id().toString());
        payload.put("targetId", request.proposal().targetId().toString());
        payload.put("reasonId", request.proposal().reasonId());
        return payload;
    }

    private static StaffRank approvalMinimum(StaffRank requiredRank) {
        return requiredRank == StaffRank.ADMIN || requiredRank == StaffRank.FOUNDER
                ? requiredRank
                : StaffRank.MOD;
    }

    private static void insertAlert(
            Connection connection,
            UUID recipientId,
            StaffRank minimumRank,
            String alertType,
            String payload,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alerts(alert_id, recipient_id, minimum_rank, alert_type,
                    payload_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            if (recipientId == null) {
                statement.setNull(2, java.sql.Types.BINARY);
            } else {
                statement.setBytes(2, UuidBytes.toBytes(recipientId));
            }
            statement.setString(3, minimumRank.name());
            statement.setString(4, alertType);
            statement.setString(5, payload);
            statement.setTimestamp(6, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request alert was not inserted"
            );
        }
    }

    private static void insertDiscord(
            Connection connection,
            String idempotencyKey,
            String eventType,
            String payload,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'punishments', ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, idempotencyKey);
            statement.setString(3, eventType);
            statement.setString(4, payload);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request Discord message was not inserted"
            );
        }
    }

    private String serialize(Map<String, Object> payload) throws SQLException {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize punishment request notification", exception);
        }
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
