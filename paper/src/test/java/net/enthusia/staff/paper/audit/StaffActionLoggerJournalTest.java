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
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffActionLoggerJournalTest {
    @TempDir
    Path directory;

    @Test
    void writesOneStableEventIdToBothLocalAuditsWithBoundedSafeDetail() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            StaffActionLogger logger = new StaffActionLogger(
                    Logger.getLogger(StaffActionLoggerJournalTest.class.getName()),
                    executor, () -> null, "SMP", "", directory);
            logger.log(UUID.randomUUID(), "Example", null, true, true,
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
}
