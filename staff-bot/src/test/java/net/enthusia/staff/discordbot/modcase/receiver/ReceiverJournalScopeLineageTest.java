package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline synthetic restart test; never touches a real Minecraft/Discord service. */
class ReceiverJournalScopeLineageTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CASE_ID = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String CREATED = "2026-10-21T09:00:00Z";
    private static final String EXPIRES = "2026-10-22T09:00:00Z";
    @TempDir Path directory;

    private CaseEvent event(String operation, String type, long revision, String state) {
        return new CaseEvent(1, operation.repeat(64), type, CASE_ID, revision,
                "minecraft", "smp", state, "NORMAL", CREATED, EXPIRES, List.of("first"));
    }

    private void corruptedHistoricalEvent(String field, String value) throws Exception {
        Path file = directory.resolve("synthetic.json");
        ReceiverJournal journal = new ReceiverJournal(file);
        // Missed birth: events are persisted for resync with NO delivery jobs.
        // Thus destination job IDs cannot incidentally catch a forged old scope.
        CaseEvent historicalEvent = event("b", "CASE_STATE", 2, "CLAIMED");
        CaseEvent newest = event("c", "CASE_STATE", 3, "CLOSED");
        assertEquals(ReceiverJournal.Outcome.ACCEPTED, journal.accept(historicalEvent));
        assertEquals(ReceiverJournal.Outcome.ACCEPTED, journal.accept(newest));
        assertTrue(journal.cursor(CASE_ID).needsResync());

        ObjectNode root = (ObjectNode) JSON.readTree(Files.readString(file));
        ObjectNode historical = (ObjectNode) root.path("operations")
                .path(historicalEvent.operationId());
        historical.put(field, value);
        Files.writeString(file, JSON.writeValueAsString(root));

        assertThrows(IOException.class, () -> new ReceiverJournal(file));
    }

    @Test
    void restartPreservesValidMissingBirthResyncWithoutOutbox() throws Exception {
        Path file = directory.resolve("synthetic.json");
        ReceiverJournal journal = new ReceiverJournal(file);
        assertEquals(ReceiverJournal.Outcome.ACCEPTED,
                journal.accept(event("b", "CASE_STATE", 2, "CLAIMED")));
        assertEquals(ReceiverJournal.Outcome.ACCEPTED,
                journal.accept(event("c", "CASE_STATE", 3, "CLOSED")));

        ReceiverJournal recovered = new ReceiverJournal(file);
        assertEquals(3, recovered.cursor(CASE_ID).revision());
        assertEquals("CLOSED", recovered.cursor(CASE_ID).caseState());
        assertTrue(recovered.cursor(CASE_ID).needsResync());
        assertTrue(recovered.ready("minecraft", "smp", 10,
                java.time.Instant.parse(CREATED)).isEmpty());
    }

    @Test
    void restartRejectsChangedHistoricalScope() throws Exception {
        corruptedHistoricalEvent("scopeId", "foreign");
    }

    @Test
    void restartRejectsChangedHistoricalOrigin() throws Exception {
        corruptedHistoricalEvent("originPlatform", "discord");
    }
}
