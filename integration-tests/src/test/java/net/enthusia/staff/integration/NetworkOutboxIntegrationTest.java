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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.network.NetworkOutboxMessage;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NetworkOutboxIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String OWNER = "velocity-network:test-owner";
    private static final String OTHER_OWNER = "velocity-network:other-owner";
    private static final String PAPER_A = "paper-a";
    private static final String PAPER_B = "paper-b";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_network_outbox_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void completesOnlyAfterEveryPreparedDestinationAcknowledges() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            UUID messageId = enqueue(NOW);
            NetworkOutboxStore store = runtime.networkOutboxStore();
            NetworkOutboxMessage message = claim(store, messageId, OWNER, NOW);
            assertEquals(0, message.attemptCount());

            store.prepareDeliveries(messageId, List.of(PAPER_A, PAPER_B, PAPER_A));
            store.prepareDeliveries(messageId, List.of(PAPER_A, PAPER_B));
            assertEquals(Set.of(PAPER_A, PAPER_B), store.pendingDestinations(messageId));
            assertFalse(store.complete(messageId, OWNER, NOW.plusSeconds(1)));

            store.acknowledgeDelivery(messageId, PAPER_A, NOW.plusSeconds(2));
            store.acknowledgeDelivery(messageId, PAPER_A, NOW.plusSeconds(3));
            assertEquals(1, deliveryAttemptCount(messageId, PAPER_A));
            assertEquals(Set.of(PAPER_B), store.pendingDestinations(messageId));

            store.acknowledgeDelivery(messageId, PAPER_B, NOW.plusSeconds(4));
            assertFalse(store.complete(messageId, OTHER_OWNER, NOW.plusSeconds(5)));
            assertTrue(store.complete(messageId, OWNER, NOW.plusSeconds(6)));
            assertEquals("ACKNOWLEDGED", messageState(messageId));
            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.prepareDeliveries(messageId, List.of("paper-c"))
            );
            assertEquals(2L, deliveryCount(messageId));
        }
    }

    @Test
    void retryAndDeadLetterRejectAStaleLeaseOwner() throws SQLException {
        Instant retryAt = NOW.plusSeconds(10);
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            UUID messageId = enqueue(NOW);
            NetworkOutboxStore store = runtime.networkOutboxStore();
            claim(store, messageId, OWNER, NOW);

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.retry(messageId, OTHER_OWNER, retryAt, "BACKEND_NOT_ACKNOWLEDGED")
            );
            assertEquals("LEASED", messageState(messageId));

            store.retry(messageId, OWNER, retryAt, "BACKEND_NOT_ACKNOWLEDGED");
            assertEquals("PENDING", messageState(messageId));
            assertTrue(store.claimDue(OWNER, 1, LEASE, retryAt.minusNanos(1)).isEmpty());
            assertEquals(1, claim(store, messageId, OWNER, retryAt).attemptCount());

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.deadLetter(messageId, OTHER_OWNER, "DELIVERY_ATTEMPTS_EXHAUSTED")
            );
            assertEquals("LEASED", messageState(messageId));
            store.deadLetter(messageId, OWNER, "DELIVERY_ATTEMPTS_EXHAUSTED");
            assertEquals("DEAD_LETTER", messageState(messageId));
        }
    }

    @Test
    void inboxRecordingIsAtomicAndIdempotent() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            NetworkOutboxStore store = runtime.networkOutboxStore();
            UUID messageId = UUID.randomUUID();

            assertTrue(store.recordInboxOnce(
                    PAPER_A,
                    messageId,
                    "PUNISHMENT_CREATED",
                    "{\"outcome\":\"applied\"}",
                    NOW
            ));
            assertFalse(store.recordInboxOnce(
                    PAPER_A,
                    messageId,
                    "SANCTION_CHANGED",
                    "{\"outcome\":\"duplicate\"}",
                    NOW.plusSeconds(1)
            ));
            assertEquals(
                    new InboxRecord("PUNISHMENT_CREATED", "{\"outcome\":\"applied\"}", NOW),
                    inboxRecord(PAPER_A, messageId)
            );
        }
    }

    private static NetworkOutboxMessage claim(
            NetworkOutboxStore store,
            UUID expectedMessageId,
            String owner,
            Instant now
    ) {
        List<NetworkOutboxMessage> messages = store.claimDue(owner, 1, LEASE, now);
        assertEquals(1, messages.size());
        NetworkOutboxMessage message = messages.getFirst();
        assertEquals(expectedMessageId, message.messageId());
        return message;
    }

    private static UUID enqueue(Instant now) throws SQLException {
        UUID messageId = UUID.randomUUID();
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO network_outbox(
                         message_id, idempotency_key, destination, message_type,
                         protocol_version, payload_json, available_at, created_at
                     ) VALUES (?, ?, 'broadcast', 'TEST_EVENT', 1, '{}', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(messageId));
            statement.setString(2, "test:network:" + messageId);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
            assertEquals(1, statement.executeUpdate());
        }
        return messageId;
    }

    private static String messageState(UUID messageId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM network_outbox WHERE message_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(messageId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static int deliveryAttemptCount(UUID messageId, String serverId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT attempt_count FROM network_outbox_deliveries
                     WHERE message_id = ? AND server_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(messageId));
            statement.setString(2, serverId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static long deliveryCount(UUID messageId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM network_outbox_deliveries WHERE message_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(messageId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static InboxRecord inboxRecord(String consumerId, UUID messageId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT message_type, outcome_json, processed_at
                     FROM network_inbox
                     WHERE consumer_id = ? AND message_id = ?
                     """)) {
            statement.setString(1, consumerId);
            statement.setBytes(2, uuidBytes(messageId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return new InboxRecord(
                        result.getString("message_type"),
                        result.getString("outcome_json"),
                        result.getTimestamp("processed_at").toInstant()
                );
            }
        }
    }

    private record InboxRecord(String messageType, String outcomeJson, Instant processedAt) {
    }
}
