package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DiscordOutboxRecoveryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(2);
    private static final String RESTART_DESTINATION = "test-restart";
    private static final String CONCURRENT_DESTINATION = "test-concurrent";
    private static final int ONE_MESSAGE = 1;
    private static final int TWO_MESSAGES = 2;

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_discord_recovery_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void expiredLeaseIsRecoveredAfterRuntimeRestart() throws SQLException {
        UUID messageId;
        try (MariaDbRuntime first = MariaDb.initialize(databaseConfig(DATABASE))) {
            clearTestRows();
            messageId = enqueue(RESTART_DESTINATION, "restart");
            List<DiscordOutboxMessage> claimed = first.discordOutboxStore()
                    .claimDue("velocity-discord:first", ONE_MESSAGE, LEASE, NOW);
            assertEquals(ONE_MESSAGE, claimed.size());
            assertEquals(messageId, claimed.getFirst().messageId());
            assertEquals(0, claimed.getFirst().attemptCount());
        }

        try (MariaDbRuntime second = MariaDb.initialize(databaseConfig(DATABASE))) {
            List<DiscordOutboxMessage> recovered = second.discordOutboxStore()
                    .claimDue("velocity-discord:second", ONE_MESSAGE, LEASE, NOW.plusSeconds(3));
            assertEquals(ONE_MESSAGE, recovered.size());
            assertEquals(messageId, recovered.getFirst().messageId());
            assertEquals(ONE_MESSAGE, recovered.getFirst().attemptCount());
        }
    }

    @Test
    void concurrentWorkersNeverLeaseTheSameMessage() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            clearTestRows();
            UUID firstId = enqueue(CONCURRENT_DESTINATION, "concurrent-a");
            UUID secondId = enqueue(CONCURRENT_DESTINATION, "concurrent-b");
            DiscordOutboxStore store = runtime.discordOutboxStore();
            ExecutorService executor = Executors.newFixedThreadPool(TWO_MESSAGES);
            try {
                CompletableFuture<List<DiscordOutboxMessage>> first = CompletableFuture.supplyAsync(
                        () -> store.claimDue("velocity-discord:a", ONE_MESSAGE, LEASE, NOW),
                        executor
                );
                CompletableFuture<List<DiscordOutboxMessage>> second = CompletableFuture.supplyAsync(
                        () -> store.claimDue("velocity-discord:b", ONE_MESSAGE, LEASE, NOW),
                        executor
                );
                List<DiscordOutboxMessage> concurrentClaims = new ArrayList<>();
                concurrentClaims.addAll(first.get(10, TimeUnit.SECONDS));
                concurrentClaims.addAll(second.get(10, TimeUnit.SECONDS));

                assertTrue(concurrentClaims.size() >= ONE_MESSAGE && concurrentClaims.size() <= TWO_MESSAGES);
                Set<UUID> concurrentIds = concurrentClaims.stream()
                        .map(DiscordOutboxMessage::messageId)
                        .collect(Collectors.toSet());
                assertEquals(concurrentClaims.size(), concurrentIds.size());

                if (concurrentClaims.size() == ONE_MESSAGE) {
                    List<DiscordOutboxMessage> remaining = store.claimDue(
                            "velocity-discord:c", ONE_MESSAGE, LEASE, NOW
                    );
                    assertEquals(ONE_MESSAGE, remaining.size());
                    concurrentClaims.addAll(remaining);
                }

                assertEquals(
                        Set.of(firstId, secondId),
                        concurrentClaims.stream()
                                .map(DiscordOutboxMessage::messageId)
                                .collect(Collectors.toSet())
                );
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static void clearTestRows() throws SQLException {
        try (Connection connection = connection(DATABASE);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM discord_outbox WHERE destination LIKE 'test-%'");
            statement.executeUpdate("DELETE FROM discord_delivery_channels WHERE destination LIKE 'test-%'");
        }
    }

    private static UUID enqueue(String destination, String suffix) throws SQLException {
        insertChannel(destination);
        UUID messageId = UUID.randomUUID();
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO discord_outbox(
                         message_id, idempotency_key, destination, event_type,
                         payload_json, available_at, created_at
                     ) VALUES (?, ?, ?, 'REPORT_CREATED', '{}', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(messageId));
            statement.setString(2, "test:discord-recovery:" + suffix + ':' + messageId);
            statement.setString(3, destination);
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.setTimestamp(5, Timestamp.from(NOW));
            assertEquals(ONE_MESSAGE, statement.executeUpdate());
        }
        return messageId;
    }

    private static void insertChannel(String destination) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO discord_delivery_channels(destination, updated_at)
                     VALUES (?, ?)
                     ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
                     """)) {
            statement.setString(1, destination);
            statement.setTimestamp(2, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }
}
