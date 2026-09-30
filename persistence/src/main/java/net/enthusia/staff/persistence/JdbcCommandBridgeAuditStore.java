package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.ports.CommandBridgeAuditStore;

/** Uses the existing append-only audit ledger as the durable command request claim and terminal journal. */
public final class JdbcCommandBridgeAuditStore implements CommandBridgeAuditStore {
    private static final String REQUEST_PREFIX = "discord-console:req:";
    private static final String TERMINAL_PREFIX = "discord-console:terminal:";
    private static final String REQUEST_EVENT = "DISCORD_CONSOLE_REQUEST";
    private static final String RESULT_EVENT = "DISCORD_CONSOLE_RESULT";
    private static final String RECEIVED = "RECEIVED";

    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcCommandBridgeAuditStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("command bridge audit data source is required");
        }
        this.dataSource = dataSource;
        this.json = new ObjectMapper();
    }

    @Override
    public Claim claim(RequestAudit request) {
        if (request == null) {
            throw new IllegalArgumentException("command bridge audit request is required");
        }
        try (Connection connection = dataSource.getConnection()) {
            if (insertRequest(connection, request)) {
                return Claim.claimed();
            }
            StoredRequest stored = loadRequest(connection, request.requestId());
            if (!stored.fingerprint().equals(request.requestFingerprint())) {
                return Claim.conflict();
            }
            Optional<CommandBridgeOutcome> terminal = loadTerminal(connection, request.requestId());
            return terminal.map(Claim::terminal).orElseGet(Claim::unresolved);
        } catch (SQLException | JsonProcessingException failure) {
            throw new IllegalStateException("command bridge audit claim failed", failure);
        }
    }

    @Override
    public void complete(UUID requestId, CommandBridgeOutcome outcome, Instant completedAt) {
        if (requestId == null || outcome == null || completedAt == null) {
            throw new IllegalArgumentException("command bridge terminal audit fields are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            StoredRequest request = loadRequest(connection, requestId);
            if (insertTerminal(connection, request, outcome, completedAt)) {
                return;
            }
            CommandBridgeOutcome existing = loadTerminal(connection, requestId)
                    .orElseThrow(() -> new IllegalStateException("terminal audit insert was not durable"));
            if (existing != outcome) {
                throw new IllegalStateException("terminal command outcome conflicts with durable audit");
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new IllegalStateException("command bridge terminal audit failed", failure);
        }
    }

    private boolean insertRequest(Connection connection, RequestAudit request)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO audit_events(
                    event_id, correlation_id, actor_id, event_type, outcome,
                    event_json, idempotency_key, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(request.requestId()));
            statement.setBytes(3, UuidBytes.toBytes(request.actorPlayerId()));
            statement.setString(4, REQUEST_EVENT);
            statement.setString(5, RECEIVED);
            statement.setString(6, requestJson(request));
            statement.setString(7, requestKey(request.requestId()));
            statement.setTimestamp(8, Timestamp.from(request.requestedAt()));
            return statement.executeUpdate() == 1;
        }
    }

    private boolean insertTerminal(
            Connection connection,
            StoredRequest request,
            CommandBridgeOutcome outcome,
            Instant completedAt
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO audit_events(
                    event_id, correlation_id, actor_id, event_type, outcome,
                    event_json, idempotency_key, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(request.requestId()));
            statement.setBytes(3, UuidBytes.toBytes(request.actorPlayerId()));
            statement.setString(4, RESULT_EVENT);
            statement.setString(5, outcome.name());
            statement.setString(6, terminalJson(request.requestId(), outcome));
            statement.setString(7, terminalKey(request.requestId()));
            statement.setTimestamp(8, Timestamp.from(completedAt));
            return statement.executeUpdate() == 1;
        }
    }

    private StoredRequest loadRequest(Connection connection, UUID requestId)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT actor_id, event_json
                FROM audit_events
                WHERE idempotency_key = ? AND event_type = ?
                """)) {
            statement.setString(1, requestKey(requestId));
            statement.setString(2, REQUEST_EVENT);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("command bridge request claim does not exist");
                }
                JsonNode payload = json.readTree(result.getString("event_json"));
                return new StoredRequest(
                        requestId,
                        UuidBytes.fromBytes(result.getBytes("actor_id")),
                        requiredText(payload, "requestFingerprint")
                );
            }
        }
    }

    private Optional<CommandBridgeOutcome> loadTerminal(Connection connection, UUID requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT outcome
                FROM audit_events
                WHERE idempotency_key = ? AND event_type = ?
                """)) {
            statement.setString(1, terminalKey(requestId));
            statement.setString(2, RESULT_EVENT);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(CommandBridgeOutcome.valueOf(result.getString("outcome")));
            }
        }
    }

    private String requestJson(RequestAudit request) throws JsonProcessingException {
        ObjectNode payload = json.createObjectNode();
        payload.put("requestId", request.requestId().toString());
        payload.put("subjectId", request.subjectId().toString());
        payload.put("actorPlayerId", request.actorPlayerId().toString());
        payload.put("targetServer", request.targetServer());
        payload.put("commandName", request.commandName());
        payload.put("requestFingerprint", request.requestFingerprint());
        return json.writeValueAsString(payload);
    }

    private String terminalJson(UUID requestId, CommandBridgeOutcome outcome) throws JsonProcessingException {
        ObjectNode payload = json.createObjectNode();
        payload.put("requestId", requestId.toString());
        payload.put("outcome", outcome.name());
        return json.writeValueAsString(payload);
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("command bridge audit payload is malformed");
        }
        return value.textValue();
    }

    private static String requestKey(UUID requestId) {
        return REQUEST_PREFIX + requestId;
    }

    private static String terminalKey(UUID requestId) {
        return TERMINAL_PREFIX + requestId;
    }

    private record StoredRequest(UUID requestId, UUID actorPlayerId, String fingerprint) {
    }
}
