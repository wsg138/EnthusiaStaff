package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    private static final String STATE_EVENT = "CASE_STATE";
    private static final String JOURNAL_NAME = "synthetic.json";
    private static final String CREATED = "2026-10-21T09:00:00Z";
    private static final String EXPIRES = "2026-10-22T09:00:00Z";
    @TempDir Path directory;

    private CaseEvent event(String operation, String type, long revision, String state) {
        return new CaseEvent(1, operation.repeat(64), type, CASE_ID, revision,
                "minecraft", "smp", state, "NORMAL", CREATED, EXPIRES, List.of("first"));
    }

    private void writeCorruptedHistoricalEvent(String field, String value) throws Exception {
        Path file = directory.resolve(JOURNAL_NAME);
        ReceiverJournal journal = new ReceiverJournal(file);
        // Missed birth: events are persisted for resync with NO delivery jobs.
        // Thus destination job IDs cannot incidentally catch a forged old scope.
        CaseEvent historicalEvent = event("b", STATE_EVENT, 2, "CLAIMED");
        CaseEvent newest = event("c", STATE_EVENT, 3, "CLOSED");
        if (journal.accept(historicalEvent) != ReceiverJournal.Outcome.ACCEPTED
                || journal.accept(newest) != ReceiverJournal.Outcome.ACCEPTED
                || !journal.cursor(CASE_ID).needsResync()) {
            throw new IllegalStateException("synthetic missed-birth setup failed");
        }

        ObjectNode root = (ObjectNode) JSON.readTree(Files.readString(file));
        ObjectNode historical = (ObjectNode) root.path("operations")
                .path(historicalEvent.operationId());
        historical.put(field, value);
        Files.writeString(file, JSON.writeValueAsString(root));

    }

    @Test
    void restartPreservesValidMissingBirthResyncWithoutOutbox() throws Exception {
        Path file = directory.resolve(JOURNAL_NAME);
        ReceiverJournal journal = new ReceiverJournal(file);
        ReceiverJournal.Outcome first = journal.accept(event("b", STATE_EVENT, 2, "CLAIMED"));
        ReceiverJournal.Outcome second = journal.accept(event("c", STATE_EVENT, 3, "CLOSED"));
        ReceiverJournal recovered = new ReceiverJournal(file);
        String recoveredState = first + "|" + second
                + "|" + recovered.cursor(CASE_ID).revision()
                + "|" + recovered.cursor(CASE_ID).caseState()
                + "|" + recovered.cursor(CASE_ID).needsResync()
                + "|" + recovered.ready("minecraft", "smp", 10,
                    java.time.Instant.parse(CREATED)).isEmpty();
        assertEquals("ACCEPTED|ACCEPTED|3|CLOSED|true|true", recoveredState,
                "valid missed-birth recovery must retain state and suppress delivery");
    }

    @Test
    void restartRejectsChangedHistoricalScope() throws Exception {
        assertThrows(IOException.class, () -> {
            writeCorruptedHistoricalEvent("scopeId", "foreign");
            new ReceiverJournal(directory.resolve(JOURNAL_NAME));
        }, "a past event must retain the cursor scope");
    }

    @Test
    void restartRejectsChangedHistoricalOrigin() throws Exception {
        assertThrows(IOException.class, () -> {
            writeCorruptedHistoricalEvent("originPlatform", "discord");
            new ReceiverJournal(directory.resolve(JOURNAL_NAME));
        }, "a past event must retain the cursor platform");
    }
}
