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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;

final class ExactSanctionEventWriter {
    private static final int PROTOCOL_VERSION = 1;

    private final ObjectMapper json;

    ExactSanctionEventWriter(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    void write(
            Connection connection,
            ExactSanctionChangeRequest request,
            ExactSanctionRow row,
            ExactSanctionMutationDecision.Apply mutation,
            Instant now
    ) throws SQLException, JsonProcessingException {
        Map<String, Object> payload = eventPayload(request, row, mutation);
        String encodedPayload = json.writeValueAsString(payload);
        insertSanctionEvent(connection, request, row, mutation, now, encodedPayload);
        insertAudit(connection, request, row, now, encodedPayload);
        insertOutboxes(connection, request, now, encodedPayload);
    }

    private static void insertSanctionEvent(
            Connection connection,
            ExactSanctionChangeRequest request,
            ExactSanctionRow row,
            ExactSanctionMutationDecision.Apply mutation,
            Instant now,
            String encodedPayload
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanction_events(
                    event_id, sanction_id, case_id, subject_id, event_type,
                    previous_status, resulting_status, previous_expiration,
                    resulting_expiration, linked_appeal_id,
                    linked_punishment_request_id, origin_runtime, actor_id,
                    occurred_at, reason, event_json, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(row.sanctionId()));
            statement.setString(3, row.caseId().value());
            statement.setBytes(4, UuidBytes.toBytes(row.subjectId()));
            statement.setString(5, request.action().name());
            statement.setString(6, row.status().name());
            statement.setString(7, mutation.resultingStatus().name());
            setInstant(statement, 8, row.expiration());
            setInstant(statement, 9, mutation.resultingExpiration());
            setUuid(statement, 10, request.linkedAppealId());
            setUuid(statement, 11, request.linkedPunishmentRequestId());
            statement.setString(12, request.originRuntime());
            statement.setBytes(13, UuidBytes.toBytes(request.actor().id()));
            statement.setTimestamp(14, Timestamp.from(now));
            statement.setString(15, request.reason());
            statement.setString(16, encodedPayload);
            statement.setString(17, request.idempotencyKey().value());
            statement.executeUpdate();
        }
    }

    private static void insertAudit(
            Connection connection,
            ExactSanctionChangeRequest request,
            ExactSanctionRow row,
            Instant now,
            String encodedPayload
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(3, UuidBytes.toBytes(request.actor().id()));
            statement.setBytes(4, UuidBytes.toBytes(row.subjectId()));
            statement.setString(5, row.caseId().value());
            statement.setString(6, auditEventType(request.action()));
            statement.setString(7, encodedPayload);
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setString(9, request.idempotencyKey().value());
            statement.executeUpdate();
        }
    }

    private static void insertOutboxes(
            Connection connection,
            ExactSanctionChangeRequest request,
            Instant now,
            String encodedPayload
    ) throws SQLException {
        try (PreparedStatement network = connection.prepareStatement("""
                INSERT INTO network_outbox(
                    message_id, idempotency_key, destination, message_type,
                    protocol_version, payload_json, available_at, created_at
                ) VALUES (?, ?, 'broadcast', 'SANCTION_CHANGED', ?, ?, ?, ?)
                """);
             PreparedStatement discord = connection.prepareStatement("""
                INSERT INTO discord_outbox(
                    message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at
                ) VALUES (?, ?, 'punishments', 'SANCTION_CHANGED', ?, ?, ?)
                """)) {
            network.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            network.setString(2, request.idempotencyKey().value() + ":network");
            network.setInt(3, PROTOCOL_VERSION);
            network.setString(4, encodedPayload);
            network.setTimestamp(5, Timestamp.from(now));
            network.setTimestamp(6, Timestamp.from(now));
            network.executeUpdate();

            discord.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            discord.setString(2, request.idempotencyKey().value() + ":discord");
            discord.setString(3, encodedPayload);
            discord.setTimestamp(4, Timestamp.from(now));
            discord.setTimestamp(5, Timestamp.from(now));
            discord.executeUpdate();
        }
    }

    private static String auditEventType(SanctionChangeAction action) {
        return switch (action) {
            case REDUCE_DURATION -> "SANCTION_REDUCED";
            case END_EARLY -> "SANCTION_ENDED_EARLY";
            case REVOKE -> "SANCTION_REVOKED";
            case FULL_OVERTURN -> "SANCTION_OVERTURNED";
            default -> "SANCTION_CHANGED";
        };
    }

    private static Map<String, Object> eventPayload(
            ExactSanctionChangeRequest request,
            ExactSanctionRow row,
            ExactSanctionMutationDecision.Apply mutation
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", row.caseId().value());
        payload.put("sanctionId", row.sanctionId().toString());
        payload.put("subjectId", row.subjectId().toString());
        payload.put("action", request.action().name());
        payload.put("previousStatus", row.status().name());
        payload.put("resultingStatus", mutation.resultingStatus().name());
        payload.put("previousExpiration", row.expiration().map(Instant::toString).orElse(null));
        payload.put("resultingExpiration", mutation.resultingExpiration().map(Instant::toString).orElse(null));
        payload.put("reason", request.reason());
        payload.put("actorId", request.actor().id().toString());
        payload.put("actorName", request.actor().displayName());
        payload.put("originRuntime", request.originRuntime());
        payload.put("linkedAppealId", request.linkedAppealId().map(UUID::toString).orElse(null));
        payload.put(
                "linkedPunishmentRequestId",
                request.linkedPunishmentRequestId().map(UUID::toString).orElse(null)
        );
        return payload;
    }

    private static void setInstant(
            PreparedStatement statement,
            int index,
            Optional<Instant> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        }
    }

    private static void setUuid(
            PreparedStatement statement,
            int index,
            Optional<UUID> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setBytes(index, UuidBytes.toBytes(value.orElseThrow()));
        } else {
            statement.setNull(index, java.sql.Types.BINARY);
        }
    }
}
