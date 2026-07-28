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
import java.util.regex.Pattern;
import javax.sql.DataSource;
import net.enthusia.staff.domain.network.NetworkOutboxMessage;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;

public final class JdbcNetworkOutboxStore implements NetworkOutboxStore {
    private static final int MINIMUM_COUNT = 1;
    private static final int MAX_BATCH = 100;
    private static final int MAX_DESTINATIONS = 64;
    private static final int MAX_OWNER_LENGTH = 128;
    private static final int MAX_SERVER_ID_LENGTH = 64;
    private static final int MAX_CONSUMER_ID_LENGTH = 64;
    private static final int MAX_MESSAGE_TYPE_LENGTH = 64;
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,64}");

    private final DataSource dataSource;

    public JdbcNetworkOutboxStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<NetworkOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now) {
        validateClaim(owner, limit, lease, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to lease network outbox messages",
                connection -> {
                    List<NetworkOutboxMessage> messages = selectDue(connection, limit, now);
                    leaseMessages(connection, messages, owner, lease, now);
                    return List.copyOf(messages);
                }
        );
    }

    @Override
    public void prepareDeliveries(UUID messageId, Collection<String> serverIds) {
        List<String> destinations = validatedDestinations(messageId, serverIds);
        JdbcTransactionSupport.execute(
                dataSource,
                "Unable to prepare network deliveries",
                connection -> {
                    requireLeasedMessage(connection, messageId);
                    insertDeliveries(connection, messageId, destinations);
                    return null;
                }
        );
    }

    @Override
    public Set<String> pendingDestinations(UUID messageId) {
        requireMessageId(messageId);
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
        validateDelivery(messageId, serverId, now);
        updateDelivery(messageId, serverId, now);
    }

    @Override
    public boolean complete(UUID messageId, String owner, Instant now) {
        validateLeaseMutation(messageId, owner, now);
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
        validateLeaseMutation(messageId, owner, availableAt);
        retryMessage(messageId, owner, availableAt, safeError(errorCode));
    }

    @Override
    public void deadLetter(UUID messageId, String owner, String errorCode) {
        validateLeaseOwner(messageId, owner);
        deadLetterMessage(messageId, owner, safeError(errorCode));
    }

    @Override
    public boolean recordInboxOnce(
            String consumerId,
            UUID messageId,
            String messageType,
            String outcomeJson,
            Instant now
    ) {
        validateInbox(consumerId, messageId, messageType, outcomeJson, now);
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
            int updateCount = statement.executeUpdate();
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    updateCount,
                    "Network inbox insert returned an invalid update count"
            );
            return JdbcTransactionSupport.updatedOne(updateCount);
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
                     WHERE message_id = ? AND server_id = ? AND state = 'PENDING'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(messageId));
            statement.setString(4, serverId);
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    statement.executeUpdate(),
                    "Network delivery acknowledgement returned an invalid update count"
            );
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to acknowledge network delivery", exception);
        }
    }

    private void retryMessage(
            UUID messageId,
            String owner,
            Instant availableAt,
            String errorCode
    ) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE network_outbox
                     SET state = 'PENDING', available_at = ?, lease_owner = NULL, lease_until = NULL,
                         last_error_code = ?
                     WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ?
                     """)) {
            statement.setTimestamp(1, Timestamp.from(availableAt));
            statement.setString(2, errorCode);
            statement.setBytes(3, UuidBytes.toBytes(messageId));
            statement.setString(4, owner);
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Network outbox message lost its lease before retry"
            );
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to retry network outbox message", exception);
        }
    }

    private void deadLetterMessage(UUID messageId, String owner, String errorCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE network_outbox
                     SET state = 'DEAD_LETTER', lease_owner = NULL, lease_until = NULL, last_error_code = ?
                     WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ?
                     """)) {
            statement.setString(1, errorCode);
            statement.setBytes(2, UuidBytes.toBytes(messageId));
            statement.setString(3, owner);
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Network outbox message lost its lease before dead-lettering"
            );
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to dead-letter network outbox message", exception);
        }
    }

    private static List<NetworkOutboxMessage> selectDue(
            Connection connection,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT message_id, idempotency_key, destination, message_type, protocol_version,
                       payload_json, attempt_count, created_at
                FROM network_outbox
                WHERE available_at <= ?
                  AND (state = 'PENDING' OR (state = 'LEASED' AND lease_until <= ?))
                ORDER BY available_at, created_at
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            Timestamp timestamp = Timestamp.from(now);
            select.setTimestamp(1, timestamp);
            select.setTimestamp(2, timestamp);
            select.setInt(3, limit);
            try (ResultSet result = select.executeQuery()) {
                List<NetworkOutboxMessage> messages = new ArrayList<>();
                while (result.next()) {
                    messages.add(read(result));
                }
                return messages;
            }
        }
    }

    private static void leaseMessages(
            Connection connection,
            List<NetworkOutboxMessage> messages,
            String owner,
            Duration lease,
            Instant now
    ) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE network_outbox
                SET state = 'LEASED', lease_owner = ?, lease_until = ?, attempt_count = attempt_count + 1
                WHERE message_id = ?
                """)) {
            Timestamp leaseUntil = Timestamp.from(now.plus(lease));
            for (NetworkOutboxMessage message : messages) {
                update.setString(1, owner);
                update.setTimestamp(2, leaseUntil);
                update.setBytes(3, UuidBytes.toBytes(message.messageId()));
                update.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    update.executeBatch(),
                    messages.size(),
                    "Network outbox message disappeared while acquiring its lease"
            );
        }
    }

    private static void requireLeasedMessage(Connection connection, UUID messageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT state FROM network_outbox
                WHERE message_id = ? FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(messageId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"LEASED".equals(result.getString(1))) {
                    throw new SQLException("Network deliveries require a leased outbox message");
                }
            }
        }
    }

    private static void insertDeliveries(
            Connection connection,
            UUID messageId,
            List<String> destinations
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO network_outbox_deliveries(message_id, server_id)
                VALUES (?, ?)
                """)) {
            for (String serverId : destinations) {
                statement.setBytes(1, UuidBytes.toBytes(messageId));
                statement.setString(2, serverId);
                statement.addBatch();
            }
            JdbcTransactionSupport.requireIdempotentBatchUpdate(
                    statement.executeBatch(),
                    destinations.size(),
                    "Network delivery preparation returned an invalid batch result"
            );
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

    private static void validateClaim(String owner, int limit, Duration lease, Instant now) {
        if (!validIdentifier(owner, MAX_OWNER_LENGTH)) {
            throw new IllegalArgumentException("valid bounded outbox lease fields are required");
        }
        if (limit < MINIMUM_COUNT || limit > MAX_BATCH || !positive(lease) || now == null) {
            throw new IllegalArgumentException("valid bounded outbox lease fields are required");
        }
    }

    private static List<String> validatedDestinations(UUID messageId, Collection<String> serverIds) {
        requireMessageId(messageId);
        if (serverIds == null || serverIds.isEmpty() || serverIds.size() > MAX_DESTINATIONS) {
            throw new IllegalArgumentException("message and bounded destinations are required");
        }
        Set<String> unique = new LinkedHashSet<>(serverIds);
        for (String serverId : unique) {
            if (!validIdentifier(serverId, MAX_SERVER_ID_LENGTH)) {
                throw new IllegalArgumentException("invalid backend server ID");
            }
        }
        return List.copyOf(unique);
    }

    private static void validateDelivery(UUID messageId, String serverId, Instant now) {
        requireMessageId(messageId);
        if (!validIdentifier(serverId, MAX_SERVER_ID_LENGTH) || now == null) {
            throw new IllegalArgumentException("valid network delivery fields are required");
        }
    }

    private static void validateLeaseMutation(UUID messageId, String owner, Instant timestamp) {
        validateLeaseOwner(messageId, owner);
        if (timestamp == null) {
            throw new IllegalArgumentException("valid network outbox lease mutation fields are required");
        }
    }

    private static void validateLeaseOwner(UUID messageId, String owner) {
        requireMessageId(messageId);
        if (!validIdentifier(owner, MAX_OWNER_LENGTH)) {
            throw new IllegalArgumentException("valid network outbox lease mutation fields are required");
        }
    }

    private static void validateInbox(
            String consumerId,
            UUID messageId,
            String messageType,
            String outcomeJson,
            Instant now
    ) {
        requireMessageId(messageId);
        if (!validIdentifier(consumerId, MAX_CONSUMER_ID_LENGTH)
                || !validIdentifier(messageType, MAX_MESSAGE_TYPE_LENGTH)) {
            throw new IllegalArgumentException("valid inbox fields are required");
        }
        if (outcomeJson == null || outcomeJson.isBlank() || now == null) {
            throw new IllegalArgumentException("valid inbox fields are required");
        }
    }

    private static void requireMessageId(UUID messageId) {
        if (messageId == null) {
            throw new IllegalArgumentException("messageId must be present");
        }
    }

    private static boolean validIdentifier(String value, int maximumLength) {
        return value != null && !value.isBlank() && value.length() <= maximumLength;
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    private static String safeError(String errorCode) {
        if (errorCode == null || !ERROR_CODE_PATTERN.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode must be a stable sanitized identifier");
        }
        return errorCode;
    }
}
