package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

final class JdbcWebsiteAuditWriter {
    private final ObjectMapper json;

    JdbcWebsiteAuditWriter(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("Website audit JSON mapper is required");
        }
        this.json = json;
    }

    void write(
            Connection connection,
            String eventType,
            UUID actorId,
            UUID targetId,
            CaseId caseId,
            Map<String, Object> details,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            if (actorId == null) {
                statement.setNull(3, Types.BINARY);
            } else {
                statement.setBytes(3, UuidBytes.toBytes(actorId));
            }
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, caseId.value());
            statement.setString(6, eventType);
            statement.setString(7, serialize(details));
            statement.setTimestamp(8, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website audit event was not inserted"
            );
        }
    }

    private String serialize(Map<String, Object> details) throws SQLException {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize website audit event", exception);
        }
    }
}
