package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Loads the actual shaded Paper production logger from the artifact rather than
 * reimplementing its SQL or relaxing package visibility for test convenience.
 */
@Testcontainers
class StaffActionJournalRecoveryIntegrationTest {
    private static final String PAPER_JAR_PROPERTY = "enthusia.paperRuntimeJar";
    private static final String LOGGER_CLASS = "net.enthusia.staff.paper.audit.StaffActionLogger";
    private static final String OUTBOX_KEY_PREFIX = "staff-action:";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_journal_replay_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @TempDir
    Path directory;

    @Test
    void actualPaperLoggerRecoversIntoMariaDbWithoutDuplicatingDeliveredOutboxRows() throws Exception {
        String paperJar = System.getProperty(PAPER_JAR_PROPERTY);
        assertNotNull(paperJar);
        Path jar = Path.of(paperJar);
        assertTrue(Files.isRegularFile(jar));

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             HikariDataSource datasource = datasource();
             URLClassLoader isolated = new URLClassLoader(
                     new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {

            assertNotNull(runtime.discordOutboxStore());
            Class<?> loggerType = Class.forName(LOGGER_CLASS, true, isolated);
            Class<?> rankType = Class.forName("net.enthusia.staff.domain.auth.StaffRank", true, isolated);
            Constructor<?> create = loggerType.getConstructor(Logger.class, ExecutorService.class,
                    Supplier.class, String.class, String.class, Path.class);
            Method append = loggerType.getMethod("log", UUID.class, String.class, rankType,
                    boolean.class, boolean.class, String.class, String.class);
            Method recover = loggerType.getMethod("recoverOutboxOnce");
            Method close = loggerType.getMethod("close");
            AtomicBoolean databaseOffline = new AtomicBoolean(true);
            Supplier<DataSource> source = () -> databaseOffline.get() ? null : datasource;

            Object logger = create.newInstance(Logger.getLogger(getClass().getName()),
                    executor, source, "SMP", "", directory);
            UUID actor = UUID.randomUUID();
            append.invoke(logger, actor, "TestModerator", null, true, true,
                    "world-interact", "bounded-private-test");
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            Path journal = directory.resolve("discord-outbox/staff-actions-"
                    + LocalDate.now(ZoneOffset.UTC) + ".jsonl");
            JsonNode first = new ObjectMapper().readTree(Files.readString(journal, StandardCharsets.UTF_8));
            UUID eventId = UUID.fromString(first.path("eventId").asText());
            assertEquals(0, outboxCount(eventId));
            databaseOffline.set(false);
            recover.invoke(logger);
            assertEquals(1, outboxCount(eventId));

            // A later crash must not reset an already delivered database event.
            markDelivered(eventId);
            close.invoke(logger);
            assertRestartKeepsDeliveredState(create, recover, close,
                    executor, source, journal, eventId);
        }
    }

    @Test
    void oldJournalWithMiddleLegacyRecordRemainsIntactAfterRealMariaDbAcceptance() throws Exception {
        String paperJar = System.getProperty(PAPER_JAR_PROPERTY);
        assertNotNull(paperJar);
        Path jar = Path.of(paperJar);
        assertTrue(Files.isRegularFile(jar));

        UUID firstId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        Path journal = writeMixedLegacyJournal(firstId, thirdId);
        String originalRecords = Files.readString(journal, StandardCharsets.UTF_8);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
             HikariDataSource datasource = datasource();
             URLClassLoader isolated = new URLClassLoader(
                     new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            assertNotNull(runtime.discordOutboxStore());
            Class<?> loggerType = Class.forName(LOGGER_CLASS, true, isolated);
            Constructor<?> create = loggerType.getConstructor(Logger.class, ExecutorService.class,
                    Supplier.class, String.class, String.class, Path.class);
            Method recover = loggerType.getMethod("recoverOutboxOnce");
            Method close = loggerType.getMethod("close");
            Supplier<DataSource> source = () -> datasource;
            Object logger = create.newInstance(Logger.getLogger(getClass().getName()),
                    executor, source, "SMP", "", directory);
            try {
                recover.invoke(logger);
                assertEquals(1, outboxCount(firstId));
                assertEquals(1, outboxCount(thirdId));
                assertEquals(1, outboxCountByKey(OUTBOX_KEY_PREFIX + firstId));
                assertEquals(1, outboxCountByKey(OUTBOX_KEY_PREFIX + thirdId));
                Path cursor = journal.resolveSibling(journal.getFileName() + ".cursor");
                assertTrue(Files.exists(cursor));
                assertEquals(Files.size(journal),
                        Long.parseLong(Files.readString(cursor, StandardCharsets.UTF_8).strip()));
                assertTrue(Files.exists(journal),
                        "Unidentified middle record must not be age-pruned despite EOF checkpoint");
                recover.invoke(logger);
                assertTrue(Files.exists(journal));
                assertEquals(originalRecords, Files.readString(journal, StandardCharsets.UTF_8));
            } finally {
                close.invoke(logger);
            }
        }
    }

    private Path writeMixedLegacyJournal(UUID firstId, UUID thirdId) throws IOException {
        String timestamp = Instant.now().toString();
        Path history = directory.resolve("discord-outbox");
        Files.createDirectories(history);
        Path journal = history.resolve("staff-actions-"
                + LocalDate.now(ZoneOffset.UTC).minusDays(45) + ".jsonl");
        String acceptedRecordOne = "{\"eventId\":\"" + firstId
                + "\",\"ts\":\"" + timestamp + "\",\"action\":\"first\",\"forwardChannel\":\"\"}\n";
        String legacyRecord = "{\"ts\":\"" + timestamp
                + "\",\"action\":\"legacy\",\"forwardChannel\":\"\"}\n";
        String acceptedRecordThree = "{\"eventId\":\"" + thirdId
                + "\",\"ts\":\"" + timestamp + "\",\"action\":\"third\",\"forwardChannel\":\"\"}\n";
        Files.writeString(journal, acceptedRecordOne + legacyRecord + acceptedRecordThree,
                StandardCharsets.UTF_8);
        return journal;
    }

    private void assertRestartKeepsDeliveredState(
            Constructor<?> create, Method recover, Method close, ExecutorService executor,
            Supplier<DataSource> source, Path journal, UUID eventId
    ) throws Exception {
        Path cursor = journal.resolveSibling(journal.getFileName() + ".cursor");
        assertTrue(Files.isRegularFile(cursor));
        Files.delete(cursor); // Simulate crash between DB commit and cursor replacement.
        Object restarted = create.newInstance(Logger.getLogger(getClass().getName()),
                executor, source, "SMP", "", directory);
        try {
            recover.invoke(restarted);
            assertEquals(1, outboxCount(eventId));
            assertEquals("DELIVERED", outboxState(eventId));
            assertEquals(1, outboxCountByKey(OUTBOX_KEY_PREFIX + eventId));
        } finally {
            close.invoke(restarted);
        }
    }

    private static HikariDataSource datasource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"));
        config.setUsername(DATABASE.getUsername());
        config.setPassword(DATABASE.getPassword());
        config.setMaximumPoolSize(2);
        config.setPoolName("staff-action-replay-integration");
        return new HikariDataSource(config);
    }

    private static int outboxCount(UUID id) throws Exception {
        try (Connection db = connection(DATABASE);
             PreparedStatement query = db.prepareStatement(
                     "SELECT COUNT(*) FROM discord_outbox WHERE message_id = ?")) {
            query.setBytes(1, uuidBytes(id));
            try (ResultSet results = query.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    private static int outboxCountByKey(String key) throws Exception {
        try (Connection db = connection(DATABASE);
             PreparedStatement query = db.prepareStatement(
                     "SELECT COUNT(*) FROM discord_outbox WHERE idempotency_key = ?")) {
            query.setString(1, key);
            try (ResultSet results = query.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    private static void markDelivered(UUID id) throws Exception {
        try (Connection db = connection(DATABASE);
             PreparedStatement statement = db.prepareStatement(
                     "UPDATE discord_outbox SET state='DELIVERED', delivered_at=? WHERE message_id=?")) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setBytes(2, uuidBytes(id));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String outboxState(UUID id) throws Exception {
        try (Connection db = connection(DATABASE);
             PreparedStatement query = db.prepareStatement(
                     "SELECT state FROM discord_outbox WHERE message_id = ?")) {
            query.setBytes(1, uuidBytes(id));
            try (ResultSet results = query.executeQuery()) {
                assertTrue(results.next());
                return results.getString(1);
            }
        }
    }
}
