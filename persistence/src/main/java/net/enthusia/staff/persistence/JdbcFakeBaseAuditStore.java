package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.FakeBaseAuditStore;
import net.enthusia.staff.domain.tester.FakeBaseAuditEvent;

/** Writes coordinate-free fake-base lifecycle evidence into the existing audit ledger. */
public final class JdbcFakeBaseAuditStore implements FakeBaseAuditStore {
    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcFakeBaseAuditStore(DataSource dataSource, ObjectMapper json) {
        if (dataSource == null || json == null) {
            throw new IllegalArgumentException("dataSource and json mapper must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
    }

    @Override
    public void record(FakeBaseAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("fake-base audit event must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id,
                         event_type, outcome, event_json, occurred_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(event.eventId()));
            statement.setBytes(2, UuidBytes.toBytes(event.operationId()));
            statement.setBytes(3, UuidBytes.toBytes(event.staffId()));
            statement.setBytes(4, UuidBytes.toBytes(event.targetId()));
            statement.setString(5, "FAKE_BASE_" + event.action().name());
            statement.setString(6, event.outcome());
            statement.setString(7, eventJson(event));
            statement.setTimestamp(8, Timestamp.from(event.occurredAt()));
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to record fake-base audit event", exception);
        }
    }

    String eventJson(FakeBaseAuditEvent event) throws JsonProcessingException {
        return json.writeValueAsString(Map.of(
                "serverId", event.serverId(),
                "reasonCode", event.reasonCode()
        ));
    }
}
