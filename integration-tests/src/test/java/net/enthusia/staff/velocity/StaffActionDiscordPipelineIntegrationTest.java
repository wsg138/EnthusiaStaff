package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Connects the actual shaded Paper logger, MariaDB outbox, production Velocity
 * worker, renderer and webhook JSON serializer. The intercepted HTTPS exchange
 * is intentionally NOT a claim of real Discord channel delivery or a Paper event.
 */
@Testcontainers
final class StaffActionDiscordPipelineIntegrationTest {
    private static final String DESTINATION = "logs-staffmode";
    private static final String STAGING_HOST = "audit-staging.example.test";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_delivery_pipeline_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @TempDir
    Path directory;

    @Test
    void actualPaperJournalReplaysOnceAndVelocityDeliversMentionSafeEmbed() throws Exception {
        Path paperJar = Path.of(System.getProperty("enthusia.paperRuntimeJar"));
        assertTrue(Files.isRegularFile(paperJar));
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig());
             URLClassLoader isolated = new URLClassLoader(
                     new URL[]{paperJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Class<?> loggerType = Class.forName(
                    "net.enthusia.staff.paper.audit.StaffActionLogger", true, isolated);
            Class<?> rankType = Class.forName(
                    "net.enthusia.staff.domain.auth.StaffRank", true, isolated);
            Constructor<?> constructor = loggerType.getConstructor(Logger.class, ExecutorService.class,
                    Supplier.class, String.class, String.class, Path.class);
            Method append = loggerType.getMethod("log", UUID.class, String.class, rankType,
                    boolean.class, boolean.class, String.class, String.class);
            Method recover = loggerType.getMethod("recoverOutboxOnce");
            Method close = loggerType.getMethod("close");
            AtomicBoolean disconnected = new AtomicBoolean(true);
            Supplier<DataSource> source = () -> disconnected.get() ? null : runtime.dataSource();
            Object paperLogger = constructor.newInstance(
                    Logger.getLogger(getClass().getName()), executor, source, "TEST", "", directory);
            try {
                UUID eventId = recordAndReplay(paperLogger, append, recover, disconnected, executor);
                verifyVelocityDelivery(runtime, eventId);
            } finally {
                close.invoke(paperLogger);
            }
        }
    }

    private UUID recordAndReplay(
            Object logger, Method append, Method recover, AtomicBoolean disconnected,
            ExecutorService executor
    ) throws Exception {
        UUID actorId = UUID.randomUUID();
        append.invoke(logger, actorId, "TestModerator", null,
                false, true, "staff-mode-entered", "path=fresh-entry");
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        Path journal = singleJournal();
        JsonNode record = new ObjectMapper().readTree(Files.readString(journal, StandardCharsets.UTF_8));
        UUID eventId = UUID.fromString(record.path("eventId").asText());
        assertEquals(actorId.toString(), record.path("actor").asText());
        assertEquals(0, countByEvent(eventId));

        disconnected.set(false);
        recover.invoke(logger);
        assertEquals(1, countByEvent(eventId));
        Path cursor = journal.resolveSibling(journal.getFileName() + ".cursor");
        assertTrue(Files.isRegularFile(cursor));
        Files.delete(cursor); // Simulated crash after MariaDB commit but before cursor replacement.
        recover.invoke(logger); // Must offer the same event ID again, without duplicating the row.
        assertEquals(1, countByEvent(eventId));
        assertEquals("PENDING", state(eventId));
        insertChannel();
        return eventId;
    }

    private Path singleJournal() throws Exception {
        List<Path> journals;
        try (var listed = Files.list(directory.resolve("discord-outbox"))) {
            journals = listed.filter(file -> file.getFileName().toString().startsWith("staff-actions-")
                    && file.getFileName().toString().endsWith(".jsonl")).toList();
        }
        assertEquals(1, journals.size());
        return journals.getFirst();
    }

    private void verifyVelocityDelivery(MariaDbRuntime runtime, UUID eventId) throws Exception {
        ObjectMapper json = new ObjectMapper();
        List<JsonNode> requests = new ArrayList<>();
        DiscordWebhookTransport.Jdk transport = new DiscordWebhookTransport.Jdk(
                Duration.ofSeconds(2), (uri, timeout, body) -> {
                    assertEquals("https", uri.getScheme());
                    assertEquals(STAGING_HOST, uri.getHost());
                    assertEquals(Duration.ofSeconds(2), timeout);
                    requests.add(json.readTree(body));
                    return 204;
                });
        Clock clock = Clock.fixed(Instant.now().plusSeconds(30), ZoneOffset.UTC);
        try (DiscordOutboxWorker worker = new DiscordOutboxWorker(
                null, null, LoggerFactory.getLogger(getClass()), clock, null,
                runtime.discordOutboxStore(), routes(), 3, 2,
                Duration.ofMinutes(5), Duration.ofSeconds(2),
                new DiscordEventRenderer(), transport)) {
            worker.runOnce();
            worker.runOnce();
        }
        assertEquals(1, countByEvent(eventId));
        assertEquals("DELIVERED", state(eventId));
        assertEquals(1, requests.size());
        assertMentionSafeStaffAction(requests.getFirst(), eventId);
    }

    private static void assertMentionSafeStaffAction(JsonNode payload, UUID eventId) {
        assertEquals("STAFF ACTION", payload.path("embeds").get(0).path("title").asText());
        assertTrue(payload.toString().contains(eventId.toString()));
        assertTrue(payload.toString().contains("staff-mode-entered"));
        assertTrue(payload.toString().contains("fresh-entry"));
        assertFalse(payload.has("content"), "Staff-action logs must never carry role mentions");
        assertEquals(0, payload.path("allowed_mentions").path("parse").size());
        assertFalse(payload.path("allowed_mentions").has("roles"));
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(), DATABASE.getPassword(), 4, 5000);
    }

    private static Map<String, DiscordWebhookRoute> routes() {
        Set<String> approved = Set.of(STAGING_HOST);
        return Map.of(
                "logs-staffmode", stagingRoute("logs-staffmode", approved),
                "reports", stagingRoute("reports", approved),
                "punishments", stagingRoute("punishments", approved),
                "alerts", stagingRoute("alerts", approved));
    }

    private static DiscordWebhookRoute stagingRoute(String destination, Set<String> approved) {
        return DiscordWebhookRoute.approvedStaging(destination,
                URI.create("https://" + STAGING_HOST + "/" + destination), approved);
    }

    private static void insertChannel() throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO discord_delivery_channels(destination, updated_at) "
                     + "VALUES (?, ?) ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at)")) {
            statement.setString(1, DESTINATION);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            assertTrue(statement.executeUpdate() > 0);
        }
    }

    private static int countByEvent(UUID eventId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM discord_outbox WHERE idempotency_key=?")) {
            statement.setString(1, "staff-action:" + eventId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static String state(UUID eventId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state FROM discord_outbox WHERE message_id=?")) {
            statement.setBytes(1, ByteBuffer.allocate(16)
                    .putLong(eventId.getMostSignificantBits())
                    .putLong(eventId.getLeastSignificantBits()).array());
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private static Connection connection() throws Exception {
        return java.sql.DriverManager.getConnection(
                DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(), DATABASE.getPassword());
    }
}
