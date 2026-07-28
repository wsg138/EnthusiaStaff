package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.network.NetworkOutboxMessage;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;

public final class JdbcNetworkOutboxStore implements NetworkOutboxStore {
    private static final int MAX_BATCH = 100;
    private static final int MAX_DESTINATIONS = 64;

    private final DataSource dataSource;

    public JdbcNetworkOutboxStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<NetworkOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now) {
        if (owner == null || owner.isBlank() || owner.length() > 128 || limit < 1 || limit > MAX_BATCH
                || lease == null || lease.isNegative() || lease.isZero() || now == null) {
            throw new IllegalArgumentException("valid bounded outbox lease fields are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<NetworkOutboxMessage> messages = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT message_id, idempotency_key, destination, message_type, protocol_version,
                               payload_json, attempt_count, created_at
                        FROM network_outbox
                        WHERE available_at <= ?
                          AND (state = 'PENDING' OR (state = 'LEASED' AND lease_until <= ?))
                        ORDER BY available_at, created_at
                        LIMIT ? FOR UPDATE SKIP LOCKED
                        """)) {
                    select.setTimestamp(1, Timestamp.from(now));
                    select.setTimestamp(2, Timestamp.from(now));
                    select.setInt(3, limit);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) {
                            messages.add(read(result));
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE network_outbox
                        SET state = 'LEASED', lease_owner = ?, lease_until = ?, attempt_count = attempt_count + 1
                        WHERE message_id = ?
                        """)) {
                    for (NetworkOutboxMessage message : messages) {
                        update.setString(1, owner);
                        update.setTimestamp(2, Timestamp.from(now.plus(lease)));
                        update.setBytes(3, UuidBytes.toBytes(message.messageId()));
                        update.addBatch();
                    }
                    update.executeBatch();
                }
                connection.commit();
                return List.copyOf(messages);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to lease network outbox messages", exception);
        }
    }

    @Override
    public void prepareDeliveries(UUID messageId, Collection<String> serverIds) {
        if (messageId == null || serverIds == null || serverIds.isEmpty() || serverIds.size() > MAX_DESTINATIONS) {
            throw new IllegalArgumentException("message and bounded destinations are required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO network_outbox_deliveries(message_id, server_id)
                     VALUES (?, ?)
                     """)) {
            for (String serverId : serverIds) {
                if (serverId == null || serverId.isBlank() || serverId.length() > 64) {
                    throw new IllegalArgumentException("invalid backend server ID");
                }
                statement.setBytes(1, UuidBytes.toBytes(messageId));
                statement.setString(2, serverId);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to prepare network deliveries", exception);
        }
    }

    @Override
    public Set<String> pendingDestinations(UUID messageId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT server_id FROM network_outbox_deliveries
                     WHERE message_id = ? AND state = 'PENDING'
                     ORDER BY server_id
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(messageId));
            try (ResultSet result = statement.executeQuery()) {
                Set<String> destinations = new LinkedHashSet<>();
                while (result.next()) {
                    destinations.add(result.getString(1));
                }
                return Set.copyOf(destinations);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read pending network deliveries", exception);
        }
    }

    @Override
    public void acknowledgeDelivery(UUID messageId, String serverId, Instant now) {
        updateDelivery(messageId, serverId, now);
    }

    @Override
    public boolean complete(UUID messageId, String owner, Instant now) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE network_outbox
                     SET state = 'ACKNOWLEDGED', acknowledged_at = ?, lease_owner = NULL, lease_until = NULL
                     WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ?
                       AND NOT EXISTS (
                           SELECT 1 FROM network_outbox_deliveries d
                           WHERE d.message_id = network_outbox.message_id AND d.state <> 'ACKNOWLEDGED'
                       )
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(messageId));
            statement.setString(3, owner);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to complete network outbox message", exception);
        }
    }

    @Override
    public void retry(UUID messageId, String owner, Instant availableAt, String errorCode) {
        updateOutboxState(messageId, owner, "PENDING", availableAt, errorCode);
    }

    @Override
    public void deadLetter(UUID messageId, String owner, String errorCode) {
        updateOutboxState(messageId, owner, "DEAD_LETTER", Instant.EPOCH, errorCode);
    }

    @Override
    public boolean recordInboxOnce(
            String consumerId,
            UUID messageId,
            String messageType,
            String outcomeJson,
            Instant now
    ) {
        if (consumerId == null || consumerId.isBlank() || consumerId.length() > 64 || messageId == null
                || messageType == null || messageType.isBlank() || outcomeJson == null || outcomeJson.isBlank()
                || now == null) {
            throw new IllegalArgumentException("valid inbox fields are required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO network_inbox(
                         consumer_id, message_id, message_type, outcome_code, outcome_json, processed_at
                     ) VALUES (?, ?, ?, 'APPLIED', ?, ?)
                     """)) {
            statement.setString(1, consumerId);
            statement.setBytes(2, UuidBytes.toBytes(messageId));
            statement.setString(3, messageType);
            statement.setString(4, outcomeJson);
            statement.setTimestamp(5, Timestamp.from(now));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to record network inbox result", exception);
        }
    }

    private void updateDelivery(UUID messageId, String serverId, Instant now) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE network_outbox_deliveries
                     SET state = 'ACKNOWLEDGED', acknowledged_at = ?, last_attempt_at = ?,
                         attempt_count = attempt_count + 1
                     WHERE message_id = ? AND server_id = ?
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(messageId));
            statement.setString(4, serverId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to acknowledge network delivery", exception);
        }
    }

    private void updateOutboxState(
            UUID messageId,
            String owner,
            String state,
            Instant availableAt,
            String errorCode
    ) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE network_outbox
                     SET state = ?, available_at = ?, lease_owner = NULL, lease_until = NULL, last_error_code = ?
                     WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ?
                     """)) {
            statement.setString(1, state);
            statement.setTimestamp(2, Timestamp.from(availableAt));
            statement.setString(3, safeError(errorCode));
            statement.setBytes(4, UuidBytes.toBytes(messageId));
            statement.setString(5, owner);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to update network outbox state", exception);
        }
    }

    private static NetworkOutboxMessage read(ResultSet result) throws SQLException {
        return new NetworkOutboxMessage(
                UuidBytes.fromBytes(result.getBytes("message_id")),
                result.getString("idempotency_key"),
                result.getString("destination"),
                result.getString("message_type"),
                result.getInt("protocol_version"),
                result.getString("payload_json"),
                result.getInt("attempt_count"),
                result.getTimestamp("created_at").toInstant()
        );
    }

    private static String safeError(String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("errorCode must be a stable sanitized identifier");
        }
        return errorCode;
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
