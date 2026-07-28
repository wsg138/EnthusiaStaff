package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.discord.DiscordChannelStatus;
import net.enthusia.staff.domain.discord.DiscordFailureOutcome;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordOutboxIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-28T06:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration CIRCUIT_DURATION = Duration.ofMinutes(5);
    private static final String OWNER = "velocity-discord:test-owner";
    private static final String DELIVERY_DESTINATION = "test-delivery";
    private static final String FAILURE_DESTINATION = "test-failure";
    private static final String MISSING_DESTINATION = "test-missing";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void deliveryRequiresTheLeaseOwnerAndUpdatesChannelState() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            UUID messageId = enqueue(DELIVERY_DESTINATION, NOW);
            DiscordOutboxStore store = runtime.discordOutboxStore();
            DiscordOutboxMessage message = claim(store, OWNER, NOW, messageId);
            assertEquals(0, message.attemptCount());

            assertFalse(store.delivered(messageId, "velocity-discord:other-owner", NOW.plusSeconds(1)));
            assertEquals("LEASED", messageState(messageId));
            assertTrue(store.delivered(messageId, OWNER, NOW.plusSeconds(2)));
            assertEquals("DELIVERED", messageState(messageId));

            DiscordChannelStatus status = channelStatus(store, DELIVERY_DESTINATION);
            assertEquals(0, status.consecutiveFailures());
            assertEquals(Optional.of(NOW.plusSeconds(2)), status.lastSuccessAt());
            assertEquals(0L, status.pendingMessages());
        }
    }

    @Test
    void failureOpensCircuitDeadLettersAndSupportsBoundedRetry() throws SQLException {
        Instant failedAt = NOW.plusSeconds(1);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            UUID messageId = enqueue(FAILURE_DESTINATION, NOW);
            DiscordOutboxStore store = runtime.discordOutboxStore();
            claim(store, OWNER, NOW, messageId);

            DiscordFailureOutcome outcome = store.failed(
                    messageId,
                    OWNER,
                    "HTTP_5XX",
                    failedAt.plusSeconds(10),
                    failedAt,
                    1,
                    1,
                    CIRCUIT_DURATION
            );
            assertTrue(outcome.deadLettered());
            assertTrue(outcome.circuitOpened());
            assertEquals(Optional.of(failedAt.plus(CIRCUIT_DURATION)), outcome.openUntil());
            assertEquals("DEAD_LETTER", messageState(messageId));
            assertEquals(1L, alertCount(FAILURE_DESTINATION));

            DiscordChannelStatus failed = channelStatus(store, FAILURE_DESTINATION);
            assertEquals(1, failed.consecutiveFailures());
            assertEquals(1L, failed.deadLetterMessages());
            assertEquals(1, store.retryDestination(FAILURE_DESTINATION, failedAt.plusSeconds(1), 10));
            assertEquals("PENDING", messageState(messageId));

            DiscordChannelStatus reset = channelStatus(store, FAILURE_DESTINATION);
            assertEquals(0, reset.consecutiveFailures());
            assertTrue(reset.openUntil().isEmpty());
            assertEquals(0L, reset.deadLetterMessages());
            assertEquals(0, claim(store, OWNER, failedAt.plusSeconds(1), messageId).attemptCount());
        }
    }

    @Test
    void missingDeliveryChannelRollsBackTheMessageTransition() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            UUID messageId = enqueue(MISSING_DESTINATION, NOW);
            DiscordOutboxStore store = runtime.discordOutboxStore();
            claim(store, OWNER, NOW, messageId);
            deleteChannel(MISSING_DESTINATION);

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.delivered(messageId, OWNER, NOW.plusSeconds(1))
            );
            assertEquals("LEASED", messageState(messageId));

            insertChannel(MISSING_DESTINATION, NOW.plusSeconds(2));
            assertTrue(store.delivered(messageId, OWNER, NOW.plusSeconds(3)));
            assertEquals("DELIVERED", messageState(messageId));
        }
    }

    private static DiscordOutboxMessage claim(
            DiscordOutboxStore store,
            String owner,
            Instant now,
            UUID expectedMessageId
    ) {
        var messages = store.claimDue(owner, 1, LEASE, now);
        assertEquals(1, messages.size());
        DiscordOutboxMessage message = messages.getFirst();
        assertEquals(expectedMessageId, message.messageId());
        return message;
    }

    private static UUID enqueue(String destination, Instant now) throws SQLException {
        insertChannel(destination, now);
        UUID messageId = UUID.randomUUID();
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO discord_outbox(
                         message_id, idempotency_key, destination, event_type,
                         payload_json, available_at, created_at
                     ) VALUES (?, ?, ?, 'TEST_EVENT', '{}', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(messageId));
            statement.setString(2, "test:discord:" + messageId);
            statement.setString(3, destination);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            assertEquals(1, statement.executeUpdate());
        }
        return messageId;
    }

    private static void insertChannel(String destination, Instant now) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO discord_delivery_channels(destination, updated_at)
                     VALUES (?, ?)
                     ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
                     """)) {
            statement.setString(1, destination);
            statement.setTimestamp(2, Timestamp.from(now));
            assertTrue(statement.executeUpdate() > 0);
        }
    }

    private static void deleteChannel(String destination) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM discord_delivery_channels WHERE destination = ?
                     """)) {
            statement.setString(1, destination);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String messageState(UUID messageId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM discord_outbox WHERE message_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(messageId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static long alertCount(String destination) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM staff_alerts
                     WHERE alert_type = 'DISCORD_CHANNEL_UNHEALTHY'
                       AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.destination')) = ?
                     """)) {
            statement.setString(1, destination);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static DiscordChannelStatus channelStatus(DiscordOutboxStore store, String destination) {
        return store.channelStatuses().stream()
                .filter(status -> status.destination().equals(destination))
                .findFirst()
                .orElseThrow();
    }

}
