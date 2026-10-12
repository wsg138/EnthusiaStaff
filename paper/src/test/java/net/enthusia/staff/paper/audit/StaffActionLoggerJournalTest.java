package net.enthusia.staff.paper.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffActionLoggerJournalTest {
    @FunctionalInterface
    private interface AuditAppend {
        void append(UUID actorId, String actorName,
                net.enthusia.staff.domain.auth.StaffRank rank,
                boolean vanished, boolean staffMode, String action, String detail);
    }

    @TempDir
    Path directory;

    @Test
    void brokenJournalWriterIsClosedAndReopenedAfterFilesystemRecovery() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        Path journal = directory.resolve("discord-outbox/staff-actions-" + date + ".jsonl");
        Files.createDirectories(journal); // A directory at the file path prevents opening.
        try {
            StaffActionLogger logger = new StaffActionLogger(
                    Logger.getLogger(StaffActionLoggerJournalTest.class.getName()),
                    executor, () -> null, "SMP", "", directory);
            AuditAppend append = logger::log;
            append.append(UUID.randomUUID(), "Example", null, true, true,
                    "block-break", "initial-journal-unavailable");
            Files.delete(journal);
            append.append(UUID.randomUUID(), "Example", null, true, true,
                    "block-break", "recovered-journal");
            logger.close();

            String content = Files.readString(journal, StandardCharsets.UTF_8);
            assertEquals(1, content.lines().count());
            assertEquals("recovered-journal",
                    new ObjectMapper().readTree(content).path("detail").asText());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void writesOneStableEventIdToBothLocalAuditsWithBoundedSafeDetail() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            Logger diagnosticLogger = Logger.getLogger(StaffActionLoggerJournalTest.class.getName()
                    + ".unconditional-audit");
            diagnosticLogger.setLevel(Level.OFF);
            StaffActionLogger logger = new StaffActionLogger(
                    diagnosticLogger, executor, () -> null, "SMP", "", directory);
            // This audit append is unconditional; it must not be gated on JUL levels.
            AuditAppend append = logger::recordStaffAction;
            append.append(UUID.randomUUID(), "Example", null, true, true,
                    "block-break", "x".repeat(5_000));
            logger.close();
            String today = LocalDate.now(ZoneOffset.UTC).toString();
            String audit = Files.readString(directory.resolve("logs/staff-actions-" + today + ".log"),
                    StandardCharsets.UTF_8);
            String journal = Files.readString(directory.resolve(
                    "discord-outbox/staff-actions-" + today + ".jsonl"), StandardCharsets.UTF_8);
            assertEquals(audit, journal);
            JsonNode entry = new ObjectMapper().readTree(journal);
            UUID.fromString(entry.path("eventId").asText());
            assertEquals("SMP", entry.path("server").asText());
            assertEquals("", entry.path("forwardChannel").asText());
            assertTrue(entry.path("detail").asText().endsWith("[truncated]"));
            assertTrue(entry.path("detail").asText().length() < 1_100);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void truncationRetainsWholeUnicodeCharactersAcrossTheUtf8JournalBoundary() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            StaffActionLogger logger = new StaffActionLogger(
                    Logger.getLogger(StaffActionLoggerJournalTest.class.getName() + ".unicode"),
                    executor, () -> null, "SMP", "", directory);
            String splitPair = "x".repeat(1023) + "😀" + "secret-after-boundary";
            String completePair = "x".repeat(1022) + "😀" + "secret-after-boundary";
            logger.recordStaffAction(UUID.randomUUID(), "Example", null, false, true,
                    "block-place", splitPair);
            logger.recordStaffAction(UUID.randomUUID(), "Example", null, false, true,
                    "block-place", completePair);
            logger.close();

            Path journal;
            try (var files = Files.list(directory.resolve("discord-outbox"))) {
                journal = files.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                        .findFirst().orElseThrow();
            }
            var lines = Files.readAllLines(journal, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            ObjectMapper json = new ObjectMapper();
            assertEquals("x".repeat(1023) + " [truncated]",
                    json.readTree(lines.get(0)).path("detail").asText());
            assertEquals("x".repeat(1022) + "😀 [truncated]",
                    json.readTree(lines.get(1)).path("detail").asText());
            String auditName = journal.getFileName().toString().replace(".jsonl", ".log");
            assertEquals(lines, Files.readAllLines(
                    directory.resolve("logs").resolve(auditName), StandardCharsets.UTF_8));
        } finally {
            executor.shutdownNow();
        }
    }
}
