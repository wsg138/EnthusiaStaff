package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.discord.DiscordChannelStatus;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exact production Velocity worker, renderer and JDK embed serializer using
 * disposable MariaDB and intercepted STAGING HTTPS, not a real Discord webhook.
 */
@Testcontainers
final class StaffActionDiscordFailureIntegrationTest {
    private static final String DESTINATION = "logs-staffmode";
    private static final String HOST = "audit-failures.example.test";
    private static final Instant T0 = Instant.parse("2026-10-12T02:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("staff_action_discord_failure_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void rateLimitServerErrorDeadLetterAndManualRetryPreserveAuditAndHealth() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(config())) {
            UUID id = seedStaffAction();
            AtomicInteger requests = new AtomicInteger();
            List<JsonNode> bodies = new ArrayList<>();
            int[] outcomes = {429, 503, 204};
            DiscordWebhookTransport.Jdk transport = intercepted((uri, body) -> {
                bodies.add(JSON.readTree(body));
                return outcomes[requests.getAndIncrement()];
            });
            DiscordOutboxStore store = runtime.discordOutboxStore();

            runAt(runtime, transport, T0); // 429 -> retry
            assertEquals("PENDING", state(id));
            assertEquals("HTTP_429", lastError(id));
            assertEquals(1, attempts(id));
            assertEquals(1, channel(store).consecutiveFailures());

            runAt(runtime, transport, T0.plusSeconds(3)); // 503 -> dead letter + circuit
            assertEquals("DEAD_LETTER", state(id));
            assertEquals("HTTP_5XX", lastError(id));
            assertEquals(2, attempts(id));
            assertEquals(2, channel(store).consecutiveFailures());
            assertEquals(1, channel(store).deadLetterMessages());
            assertEquals(1, channelAlerts());

            runAt(runtime, transport, T0.plusSeconds(8)); // circuit/dead letter: no HTTP
            assertEquals(2, requests.get());

            Instant retryAt = T0.plusSeconds(20);
            assertEquals(1, store.retryDestination(DESTINATION, retryAt, 10));
            assertEquals("PENDING", state(id));
            assertTrue(channel(store).openUntil().isEmpty());
            runAt(runtime, transport, retryAt.plusSeconds(1));
            runAt(runtime, transport, retryAt.plusSeconds(2)); // acknowledged: no resend

            assertEquals(3, requests.get());
            assertEquals("DELIVERED", state(id));
            assertEquals(0, channel(store).consecutiveFailures());
            assertEquals(0, channel(store).deadLetterMessages());
            assertEquals(1, countById(id));
            assertEquals(1, channelAlerts()); // recovery doesn't flood alerts
            assertSafeBodies(id, bodies);
        }
    }

    @Test
    void expiredLeaseIsReclaimedAndAcknowledgedWithoutDuplicatingTheOutboxRow() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(config())) {
            UUID id = seedStaffAction();
            DiscordOutboxStore store = runtime.discordOutboxStore();
            assertEquals(1, store.claimDue("abandoned-worker", 1,
                    Duration.ofSeconds(5), T0).size());
            assertEquals("LEASED", state(id));

            List<JsonNode> bodies = new ArrayList<>();
            DiscordWebhookTransport.Jdk transport = intercepted((uri, body) -> {
                bodies.add(JSON.readTree(body));
                return 204;
            });
            runAt(runtime, transport, T0.plusSeconds(7));
            runAt(runtime, transport, T0.plusSeconds(8));
            assertEquals("DELIVERED", state(id));
            assertEquals(2, attempts(id)); // original claim + recovered claim
            assertEquals(1, countById(id));
            assertEquals(1, bodies.size());
            assertSafeBodies(id, bodies);
        }
    }

    private static void runAt(MariaDbRuntime runtime, DiscordWebhookTransport transport, Instant time) {
        try (DiscordOutboxWorker worker = new DiscordOutboxWorker(
                null, null, LoggerFactory.getLogger(StaffActionDiscordFailureIntegrationTest.class),
                Clock.fixed(time, ZoneOffset.UTC), null, runtime.discordOutboxStore(),
                routes(), 2, 2, Duration.ofMinutes(5), Duration.ofSeconds(2),
                new DiscordEventRenderer(), transport)) {
            worker.runOnce();
        }
    }

    private static DiscordWebhookTransport.Jdk intercepted(Exchange exchange) {
        return new DiscordWebhookTransport.Jdk(Duration.ofSeconds(2), (uri, timeout, body) -> {
            assertEquals(HOST, uri.getHost());
            assertEquals("https", uri.getScheme());
            return exchange.send(uri, body);
        });
    }

    @FunctionalInterface
    private interface Exchange {
        int send(URI uri, String body) throws java.io.IOException;
    }

    private static Map<String, DiscordWebhookRoute> routes() {
        Set<String> hosts = Set.of(HOST);
        return Map.of("logs-staffmode", route("logs-staffmode", hosts),
                "reports", route("reports", hosts),
                "punishments", route("punishments", hosts),
                "alerts", route("alerts", hosts));
    }

    private static DiscordWebhookRoute route(String dest, Set<String> hosts) {
        return DiscordWebhookRoute.approvedStaging(dest, URI.create("https://" + HOST + "/" + dest), hosts);
    }

    private static UUID seedStaffAction() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection()) {
            try (PreparedStatement channel = connection.prepareStatement(
                    "INSERT INTO discord_delivery_channels(destination, updated_at) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at)")) {
                channel.setString(1, DESTINATION);
                channel.setTimestamp(2, Timestamp.from(T0));
                channel.executeUpdate();
            }
            try (PreparedStatement outbox = connection.prepareStatement(
                    "INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type, "
                            + "payload_json, available_at, created_at) VALUES (?, ?, ?, 'STAFF_ACTION', ?, ?, ?)")) {
                outbox.setBytes(1, bytes(id));
                outbox.setString(2, "staff-action:" + id);
                outbox.setString(3, DESTINATION);
                outbox.setString(4, "{\"eventId\":\"" + id
                        + "\",\"action\":\"staff-mode-entered\",\"token\":\"never-render\"}");
                outbox.setTimestamp(5, Timestamp.from(T0));
                outbox.setTimestamp(6, Timestamp.from(T0));
                assertEquals(1, outbox.executeUpdate());
            }
        }
        return id;
    }

    private static void assertSafeBodies(UUID id, List<JsonNode> bodies) {
        for (JsonNode body : bodies) {
            assertEquals("STAFF ACTION", body.path("embeds").get(0).path("title").asText());
            assertTrue(body.toString().contains(id.toString()));
            assertTrue(body.toString().contains("staff-mode-entered"));
            assertFalse(body.toString().contains("never-render"));
            assertFalse(body.has("content"));
            assertEquals(0, body.path("allowed_mentions").path("parse").size());
            assertFalse(body.path("allowed_mentions").has("roles"));
        }
    }

    private static DiscordChannelStatus channel(DiscordOutboxStore store) {
        return store.channelStatuses().stream()
                .filter(status -> DESTINATION.equals(status.destination())).findFirst().orElseThrow();
    }

    private static int channelAlerts() throws Exception {
        try (Connection db = connection();
             PreparedStatement q = db.prepareStatement("SELECT COUNT(*) FROM staff_alerts "
                     + "WHERE alert_type='DISCORD_CHANNEL_UNHEALTHY'")) {
            try (ResultSet result = q.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static String state(UUID id) throws Exception {
        return column(id, "state");
    }

    private static String lastError(UUID id) throws Exception {
        return column(id, "last_error_code");
    }

    private static int attempts(UUID id) throws Exception {
        return Integer.parseInt(column(id, "attempt_count"));
    }

    private static String column(UUID id, String column) throws Exception {
        // Callers above select only these fixed, audited column names.
        if (!Set.of("state", "last_error_code", "attempt_count").contains(column)) {
            throw new IllegalArgumentException("Invalid test inspection column");
        }
        try (Connection db = connection();
             PreparedStatement q = db.prepareStatement(
                     "SELECT state, last_error_code, attempt_count FROM discord_outbox WHERE message_id=?")) {
            q.setBytes(1, bytes(id));
            try (ResultSet result = q.executeQuery()) {
                assertTrue(result.next());
                return result.getString(column);
            }
        }
    }

    private static int countById(UUID id) throws Exception {
        try (Connection db = connection();
             PreparedStatement q = db.prepareStatement(
                     "SELECT COUNT(*) FROM discord_outbox WHERE message_id=?")) {
            q.setBytes(1, bytes(id));
            try (ResultSet rows = q.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    private static DatabaseConfig config() {
        return new DatabaseConfig(DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(), DATABASE.getPassword(), 4, 5000);
    }

    private static Connection connection() throws Exception {
        return java.sql.DriverManager.getConnection(
                DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(), DATABASE.getPassword());
    }
}
