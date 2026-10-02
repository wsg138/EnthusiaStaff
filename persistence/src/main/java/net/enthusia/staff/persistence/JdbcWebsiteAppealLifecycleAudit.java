package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

final class JdbcWebsiteAppealLifecycleAudit {
    private static final int EXPECTED_UPDATE_COUNT = 1;
    private static final String KEY_PREFIX = "website-appeal:";
    private final ObjectMapper json;

    JdbcWebsiteAppealLifecycleAudit(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("Website appeal lifecycle audit JSON mapper is required");
        }
        this.json = json;
    }

    Optional<Entry> find(
            Connection connection,
            UUID appealId,
            String operation,
            String clientKey
    ) throws SQLException {
        String key = durableKey(appealId, operation, clientKey);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event_type, actor_id, case_id, event_json
                FROM audit_events
                WHERE idempotency_key = ?
                """)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                byte[] actor = result.getBytes("actor_id");
                return Optional.of(new Entry(
                        result.getString("event_type"),
                        actor == null ? null : UuidBytes.fromBytes(actor),
                        result.getString("case_id"),
                        parse(result.getString("event_json"))
                ));
            }
        }
    }

    void write(Connection connection, AuditRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, idempotency_key, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(record.appealId()));
            if (record.actorId() == null) statement.setNull(3, Types.BINARY);
            else statement.setBytes(3, UuidBytes.toBytes(record.actorId()));
            statement.setNull(4, Types.BINARY);
            statement.setString(5, record.caseId().value());
            statement.setString(6, record.eventType());
            statement.setString(7, serialize(record.details()));
            statement.setString(8, durableKey(record.appealId(), record.operation(), record.clientKey()));
            statement.setTimestamp(9, Timestamp.from(record.now()));
            requireSingleUpdate(statement.executeUpdate());
        }
    }

    private JsonNode parse(String value) throws SQLException {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to parse website appeal lifecycle audit", exception);
        }
    }

    private String serialize(Map<String, Object> details) throws SQLException {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize website appeal lifecycle audit", exception);
        }
    }

    private static String durableKey(UUID appealId, String operation, String clientKey) {
        String material = appealId + ":" + operation + ":" + clientKey.length() + ":" + clientKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSingleUpdate(int updated) throws SQLException {
        if (updated != EXPECTED_UPDATE_COUNT) {
            throw new SQLException("Website appeal lifecycle audit was not inserted");
        }
    }

    record AuditRecord(
            UUID appealId,
            String operation,
            String clientKey,
            String eventType,
            UUID actorId,
            CaseId caseId,
            Map<String, Object> details,
            Instant now
    ) {
        AuditRecord {
            if (appealId == null || operation == null || clientKey == null || eventType == null
                    || caseId == null || details == null || now == null) {
                throw new IllegalArgumentException("Website appeal lifecycle audit fields are required");
            }
            details = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(details)
            );
        }
    }

    record Entry(String eventType, UUID actorId, String caseId, JsonNode details) {
    }
}
