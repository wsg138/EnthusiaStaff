package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import java.util.regex.Pattern;
import javax.sql.DataSource;
import net.enthusia.staff.domain.discord.DiscordChannelStatus;
import net.enthusia.staff.domain.discord.DiscordFailureOutcome;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;

public final class JdbcDiscordOutboxStore implements DiscordOutboxStore {
    private static final int MINIMUM_COUNT = 1;
    private static final int MAX_BATCH = 100;
    private static final int MAX_MANUAL_RETRY = 500;
    private static final int MAX_OWNER_LENGTH = 128;
    private static final Pattern DESTINATION_PATTERN = Pattern.compile("[a-z-]{1,32}");
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,64}");

    private final DataSource dataSource;

    public JdbcDiscordOutboxStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<DiscordOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now) {
        validateClaim(owner, limit, lease, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to lease Discord outbox messages",
                connection -> {
                    List<DiscordOutboxMessage> messages = selectDue(connection, limit, now);
                    leaseMessages(connection, messages, owner, lease, now);
                    return List.copyOf(messages);
                }
        );
    }

    @Override
    public boolean delivered(UUID messageId, String owner, Instant now) {
        validateLeaseMutation(messageId, owner, now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to complete Discord outbox message",
                connection -> {
                    String destination = lockDestination(connection, messageId, owner);
                    if (destination == null) {
                        return false;
                    }
                    markDelivered(connection, messageId, owner, destination, now);
                    return true;
                }
        );
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
        validateFailurePolicy(availableAt, now, maximumAttempts, failureThreshold, circuitDuration);
        FailureRequest request = new FailureRequest(
                messageId,
                owner,
                safeError(errorCode),
                availableAt,
                now,
                maximumAttempts,
                failureThreshold,
                circuitDuration
        );
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to record Discord delivery failure",
                connection -> recordFailure(connection, request)
        );
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
        validateRetry(destination, now, maximumMessages);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to retry Discord channel",
                connection -> JdbcDiscordRetrySupport.execute(connection, destination, now, maximumMessages)
        );
    }

    private static List<DiscordOutboxMessage> selectDue(
            Connection connection,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT o.message_id, o.destination, o.event_type, o.payload_json,
                       o.attempt_count, o.created_at
                FROM discord_outbox o
                WHERE o.available_at <= ?
                  AND (o.state = 'PENDING' OR (o.state = 'LEASED' AND o.lease_until <= ?))
                  AND EXISTS (
                      SELECT 1
                      FROM discord_delivery_channels c
                      WHERE c.destination = o.destination
                        AND (c.open_until IS NULL OR c.open_until <= ?)
                  )
                ORDER BY o.available_at, o.created_at
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            Timestamp timestamp = Timestamp.from(now);
            select.setTimestamp(1, timestamp);
            select.setTimestamp(2, timestamp);
            select.setTimestamp(3, timestamp);
            select.setInt(4, limit);
            try (ResultSet result = select.executeQuery()) {
                List<DiscordOutboxMessage> messages = new ArrayList<>();
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
                return messages;
            }
        }
    }

    private static void leaseMessages(
            Connection connection,
            List<DiscordOutboxMessage> messages,
            String owner,
            Duration lease,
            Instant now
    ) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE discord_outbox
                SET state = 'LEASED', lease_owner = ?, lease_until = ?, attempt_count = attempt_count + 1
                WHERE message_id = ?
                """)) {
            Timestamp leaseUntil = Timestamp.from(now.plus(lease));
            for (DiscordOutboxMessage message : messages) {
                update.setString(1, owner);
                update.setTimestamp(2, leaseUntil);
                update.setBytes(3, UuidBytes.toBytes(message.messageId()));
                update.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    update.executeBatch(),
                    messages.size(),
                    "Discord outbox message disappeared while acquiring its lease"
            );
        }
    }

    private static void markDelivered(
            Connection connection,
            UUID messageId,
            String owner,
            String destination,
            Instant now
    ) throws SQLException {
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
            JdbcTransactionSupport.requireSingleUpdate(
                    message.executeUpdate(),
                    "Discord outbox message disappeared during delivery"
            );
            channel.setTimestamp(1, Timestamp.from(now));
            channel.setTimestamp(2, Timestamp.from(now));
            channel.setString(3, destination);
            JdbcTransactionSupport.requireSingleUpdate(
                    channel.executeUpdate(),
                    "Discord delivery channel disappeared during delivery"
            );
        }
    }

    private static DiscordFailureOutcome recordFailure(
            Connection connection,
            FailureRequest request
    ) throws SQLException {
        LeasedMessage leased = lockLeasedMessage(connection, request.messageId(), request.owner());
        if (leased == null) {
            return new DiscordFailureOutcome(false, false, Optional.empty());
        }
        int priorFailures = lockChannelFailures(connection, leased.destination());
        FailureDecision decision = FailureDecision.decide(leased.attemptCount(), priorFailures, request);
        writeFailure(connection, leased.destination(), request, decision);
        if (decision.openedNow()) {
            insertChannelAlert(connection, leased.destination(), request.errorCode(), request.now());
        }
        return new DiscordFailureOutcome(
                decision.deadLettered(),
                decision.openedNow(),
                decision.openUntil()
        );
    }

    private static void writeFailure(
            Connection connection,
            String destination,
            FailureRequest request,
            FailureDecision decision
    ) throws SQLException {
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
            message.setString(1, decision.deadLettered() ? "DEAD_LETTER" : "PENDING");
            message.setTimestamp(2, Timestamp.from(request.availableAt()));
            message.setString(3, request.errorCode());
            message.setBytes(4, UuidBytes.toBytes(request.messageId()));
            message.setString(5, request.owner());
            JdbcTransactionSupport.requireSingleUpdate(
                    message.executeUpdate(),
                    "Discord outbox message disappeared during failure handling"
            );

            channel.setInt(1, decision.nextFailures());
            if (decision.openUntil().isPresent()) {
                channel.setTimestamp(2, Timestamp.from(decision.openUntil().orElseThrow()));
            } else {
                channel.setNull(2, Types.TIMESTAMP);
            }
            channel.setString(3, request.errorCode());
            channel.setTimestamp(4, Timestamp.from(request.now()));
            channel.setString(5, destination);
            JdbcTransactionSupport.requireSingleUpdate(
                    channel.executeUpdate(),
                    "Discord delivery channel disappeared during failure handling"
            );
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
            statement.setString(2, JsonNodeFactory.instance.objectNode()
                    .put("destination", destination)
                    .put("errorCode", errorCode)
                    .toString());
            statement.setTimestamp(3, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Discord channel alert was not inserted"
            );
        }
    }

    private static void validateClaim(String owner, int limit, Duration lease, Instant now) {
        if (!validOwner(owner)) {
            throw new IllegalArgumentException("valid bounded Discord outbox lease fields are required");
        }
        if (limit < MINIMUM_COUNT || limit > MAX_BATCH) {
            throw new IllegalArgumentException("valid bounded Discord outbox lease fields are required");
        }
        if (!positive(lease) || now == null) {
            throw new IllegalArgumentException("valid bounded Discord outbox lease fields are required");
        }
    }

    private static void validateFailurePolicy(
            Instant availableAt,
            Instant now,
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration
    ) {
        if (availableAt == null || availableAt.isBefore(now)) {
            throw new IllegalArgumentException("valid Discord failure policy fields are required");
        }
        if (maximumAttempts < MINIMUM_COUNT || failureThreshold < MINIMUM_COUNT) {
            throw new IllegalArgumentException("valid Discord failure policy fields are required");
        }
        if (!positive(circuitDuration)) {
            throw new IllegalArgumentException("valid Discord failure policy fields are required");
        }
    }

    private static void validateRetry(String destination, Instant now, int maximumMessages) {
        if (destination == null || !DESTINATION_PATTERN.matcher(destination).matches()) {
            throw new IllegalArgumentException("valid bounded Discord retry fields are required");
        }
        if (now == null || maximumMessages < MINIMUM_COUNT || maximumMessages > MAX_MANUAL_RETRY) {
            throw new IllegalArgumentException("valid bounded Discord retry fields are required");
        }
    }

    private static void validateLeaseMutation(UUID messageId, String owner, Instant now) {
        if (messageId == null || !validOwner(owner) || now == null) {
            throw new IllegalArgumentException("valid Discord lease mutation fields are required");
        }
    }

    private static boolean validOwner(String owner) {
        return owner != null && !owner.isBlank() && owner.length() <= MAX_OWNER_LENGTH;
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

    private record FailureRequest(
            UUID messageId,
            String owner,
            String errorCode,
            Instant availableAt,
            Instant now,
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration
    ) {
    }

    private record FailureDecision(
            int nextFailures,
            boolean deadLettered,
            boolean openedNow,
            Optional<Instant> openUntil
    ) {
        private static FailureDecision decide(
                int attemptCount,
                int priorFailures,
                FailureRequest request
        ) {
            int nextFailures = priorFailures + 1;
            boolean open = nextFailures >= request.failureThreshold();
            boolean openedNow = open && priorFailures < request.failureThreshold();
            Optional<Instant> openUntil = open
                    ? Optional.of(request.now().plus(request.circuitDuration()))
                    : Optional.empty();
            return new FailureDecision(
                    nextFailures,
                    attemptCount >= request.maximumAttempts(),
                    openedNow,
                    openUntil
            );
        }
    }

    private record LeasedMessage(String destination, int attemptCount) {
    }
}
