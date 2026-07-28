package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.discord.DiscordChannelStatus;
import net.enthusia.staff.domain.discord.DiscordFailureOutcome;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;

public final class JdbcDiscordOutboxStore implements DiscordOutboxStore {
    private static final int MAX_BATCH = 100;
    private static final int MAX_MANUAL_RETRY = 500;

    private final DataSource dataSource;

    public JdbcDiscordOutboxStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<DiscordOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now) {
        if (owner == null || owner.isBlank() || owner.length() > 128 || limit < 1 || limit > MAX_BATCH
                || lease == null || lease.isZero() || lease.isNegative() || now == null) {
            throw new IllegalArgumentException("valid bounded Discord outbox lease fields are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<DiscordOutboxMessage> messages = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT o.message_id, o.destination, o.event_type, o.payload_json,
                               o.attempt_count, o.created_at
                        FROM discord_outbox o
                        JOIN discord_delivery_channels c ON c.destination = o.destination
                        WHERE o.available_at <= ?
                          AND (o.state = 'PENDING' OR (o.state = 'LEASED' AND o.lease_until <= ?))
                          AND (c.open_until IS NULL OR c.open_until <= ?)
                        ORDER BY o.available_at, o.created_at
                        LIMIT ? FOR UPDATE SKIP LOCKED
                        """)) {
                    Timestamp timestamp = Timestamp.from(now);
                    select.setTimestamp(1, timestamp);
                    select.setTimestamp(2, timestamp);
                    select.setTimestamp(3, timestamp);
                    select.setInt(4, limit);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) {
                            messages.add(new DiscordOutboxMessage(
                                    UuidBytes.fromBytes(result.getBytes("message_id")),
                                    result.getString("destination"),
                                    result.getString("event_type"),
                                    result.getString("payload_json"),
                                    result.getInt("attempt_count"),
                                    result.getTimestamp("created_at").toInstant()
                            ));
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE discord_outbox
                        SET state = 'LEASED', lease_owner = ?, lease_until = ?, attempt_count = attempt_count + 1
                        WHERE message_id = ?
                        """)) {
                    for (DiscordOutboxMessage message : messages) {
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
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to lease Discord outbox messages", exception);
        }
    }

    @Override
    public boolean delivered(UUID messageId, String owner, Instant now) {
        validateLeaseMutation(messageId, owner, now);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String destination = lockDestination(connection, messageId, owner);
                if (destination == null) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement message = connection.prepareStatement("""
                        UPDATE discord_outbox
                        SET state = 'DELIVERED', delivered_at = ?, lease_owner = NULL, lease_until = NULL,
                            last_error_code = NULL
                        WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ?
                        """);
                     PreparedStatement channel = connection.prepareStatement("""
                        UPDATE discord_delivery_channels
                        SET consecutive_failures = 0, open_until = NULL, last_error_code = NULL,
                            last_success_at = ?, updated_at = ?
                        WHERE destination = ?
                        """)) {
                    message.setTimestamp(1, Timestamp.from(now));
                    message.setBytes(2, UuidBytes.toBytes(messageId));
                    message.setString(3, owner);
                    boolean changed = message.executeUpdate() == 1;
                    channel.setTimestamp(1, Timestamp.from(now));
                    channel.setTimestamp(2, Timestamp.from(now));
                    channel.setString(3, destination);
                    channel.executeUpdate();
                    connection.commit();
                    return changed;
                }
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to complete Discord outbox message", exception);
        }
    }

    @Override
    public DiscordFailureOutcome failed(
            UUID messageId,
            String owner,
            String errorCode,
            Instant availableAt,
            Instant now,
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration
    ) {
        validateLeaseMutation(messageId, owner, now);
        if (availableAt == null || availableAt.isBefore(now) || maximumAttempts < 1 || failureThreshold < 1
                || circuitDuration == null || circuitDuration.isNegative() || circuitDuration.isZero()) {
            throw new IllegalArgumentException("valid Discord failure policy fields are required");
        }
        String safeError = safeError(errorCode);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LeasedMessage leased = lockLeasedMessage(connection, messageId, owner);
                if (leased == null) {
                    connection.rollback();
                    return new DiscordFailureOutcome(false, false, Optional.empty());
                }
                int priorFailures = lockChannelFailures(connection, leased.destination());
                int nextFailures = priorFailures + 1;
                boolean open = nextFailures >= failureThreshold;
                boolean openedNow = open && priorFailures < failureThreshold;
                Instant openUntil = open ? now.plus(circuitDuration) : null;
                boolean deadLetter = leased.attemptCount() >= maximumAttempts;
                try (PreparedStatement message = connection.prepareStatement("""
                        UPDATE discord_outbox
                        SET state = ?, available_at = ?, lease_owner = NULL, lease_until = NULL,
                            last_error_code = ?
                        WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ?
                        """);
                     PreparedStatement channel = connection.prepareStatement("""
                        UPDATE discord_delivery_channels
                        SET consecutive_failures = ?, open_until = ?, last_error_code = ?, updated_at = ?
                        WHERE destination = ?
                        """)) {
                    message.setString(1, deadLetter ? "DEAD_LETTER" : "PENDING");
                    message.setTimestamp(2, Timestamp.from(availableAt));
                    message.setString(3, safeError);
                    message.setBytes(4, UuidBytes.toBytes(messageId));
                    message.setString(5, owner);
                    message.executeUpdate();

                    channel.setInt(1, nextFailures);
                    if (openUntil == null) {
                        channel.setNull(2, Types.TIMESTAMP);
                    } else {
                        channel.setTimestamp(2, Timestamp.from(openUntil));
                    }
                    channel.setString(3, safeError);
                    channel.setTimestamp(4, Timestamp.from(now));
                    channel.setString(5, leased.destination());
                    channel.executeUpdate();
                }
                if (openedNow) {
                    insertChannelAlert(connection, leased.destination(), safeError, now);
                }
                connection.commit();
                return new DiscordFailureOutcome(
                        deadLetter, openedNow, Optional.ofNullable(openUntil)
                );
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to record Discord delivery failure", exception);
        }
    }

    @Override
    public void deferWithoutAttempt(UUID messageId, String owner, Instant availableAt) {
        if (messageId == null || owner == null || owner.isBlank() || availableAt == null) {
            throw new IllegalArgumentException("valid Discord deferral fields are required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE discord_outbox
                     SET state = 'PENDING', available_at = ?, lease_owner = NULL, lease_until = NULL,
                         attempt_count = GREATEST(0, attempt_count - 1)
                     WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ?
                     """)) {
            statement.setTimestamp(1, Timestamp.from(availableAt));
            statement.setBytes(2, UuidBytes.toBytes(messageId));
            statement.setString(3, owner);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to defer Discord outbox message", exception);
        }
    }

    @Override
    public List<DiscordChannelStatus> channelStatuses() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT c.destination, c.consecutive_failures, c.open_until, c.last_error_code,
                            c.last_success_at,
                            SUM(CASE WHEN o.state IN ('PENDING', 'LEASED') THEN 1 ELSE 0 END) pending_count,
                            SUM(CASE WHEN o.state = 'DEAD_LETTER' THEN 1 ELSE 0 END) dead_count
                     FROM discord_delivery_channels c
                     LEFT JOIN discord_outbox o ON o.destination = c.destination
                     GROUP BY c.destination, c.consecutive_failures, c.open_until,
                              c.last_error_code, c.last_success_at
                     ORDER BY c.destination
                     """)) {
            try (ResultSet result = statement.executeQuery()) {
                List<DiscordChannelStatus> statuses = new ArrayList<>();
                while (result.next()) {
                    Timestamp openUntil = result.getTimestamp("open_until");
                    Timestamp lastSuccess = result.getTimestamp("last_success_at");
                    statuses.add(new DiscordChannelStatus(
                            result.getString("destination"),
                            result.getInt("consecutive_failures"),
                            Optional.ofNullable(openUntil).map(Timestamp::toInstant),
                            Optional.ofNullable(result.getString("last_error_code")),
                            Optional.ofNullable(lastSuccess).map(Timestamp::toInstant),
                            result.getLong("pending_count"),
                            result.getLong("dead_count")
                    ));
                }
                return List.copyOf(statuses);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read Discord channel status", exception);
        }
    }

    @Override
    public int retryDestination(String destination, Instant now, int maximumMessages) {
        if (destination == null || !destination.matches("[a-z-]{1,32}") || now == null
                || maximumMessages < 1 || maximumMessages > MAX_MANUAL_RETRY) {
            throw new IllegalArgumentException("valid bounded Discord retry fields are required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement channel = connection.prepareStatement("""
                    UPDATE discord_delivery_channels
                    SET consecutive_failures = 0, open_until = NULL, last_error_code = NULL, updated_at = ?
                    WHERE destination = ?
                    """);
                 PreparedStatement messages = connection.prepareStatement("""
                    UPDATE discord_outbox
                    SET state = 'PENDING', attempt_count = 0, available_at = ?, lease_owner = NULL,
                        lease_until = NULL, last_error_code = NULL
                    WHERE destination = ? AND state = 'DEAD_LETTER'
                    ORDER BY created_at LIMIT ?
                    """)) {
                channel.setTimestamp(1, Timestamp.from(now));
                channel.setString(2, destination);
                if (channel.executeUpdate() != 1) {
                    connection.rollback();
                    return 0;
                }
                messages.setTimestamp(1, Timestamp.from(now));
                messages.setString(2, destination);
                messages.setInt(3, maximumMessages);
                int retried = messages.executeUpdate();
                connection.commit();
                return retried;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to retry Discord channel", exception);
        }
    }

    private static String lockDestination(Connection connection, UUID messageId, String owner) throws SQLException {
        LeasedMessage message = lockLeasedMessage(connection, messageId, owner);
        return message == null ? null : message.destination();
    }

    private static LeasedMessage lockLeasedMessage(Connection connection, UUID messageId, String owner)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT destination, attempt_count FROM discord_outbox
                WHERE message_id = ? AND state = 'LEASED' AND lease_owner = ? FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(messageId));
            statement.setString(2, owner);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new LeasedMessage(result.getString(1), result.getInt(2)) : null;
            }
        }
    }

    private static int lockChannelFailures(Connection connection, String destination) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT consecutive_failures FROM discord_delivery_channels
                WHERE destination = ? FOR UPDATE
                """)) {
            statement.setString(1, destination);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Discord destination is not registered");
                }
                return result.getInt(1);
            }
        }
    }

    private static void insertChannelAlert(
            Connection connection,
            String destination,
            String errorCode,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alerts(alert_id, recipient_id, minimum_rank, alert_type, payload_json, created_at)
                VALUES (?, NULL, 'ADMIN', 'DISCORD_CHANNEL_UNHEALTHY', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, "{\"destination\":\"" + destination + "\",\"errorCode\":\"" + errorCode + "\"}");
            statement.setTimestamp(3, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void validateLeaseMutation(UUID messageId, String owner, Instant now) {
        if (messageId == null || owner == null || owner.isBlank() || owner.length() > 128 || now == null) {
            throw new IllegalArgumentException("valid Discord lease mutation fields are required");
        }
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

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Closing returns the connection to the pool; the original failure remains authoritative.
        }
    }

    private record LeasedMessage(String destination, int attemptCount) {
    }
}
