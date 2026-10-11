package net.enthusia.staff.paper.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffActionOutboxRecoveryTest {
    @TempDir
    Path directory;

    private static final Logger LOG = Logger.getLogger(StaffActionOutboxRecoveryTest.class.getName());
    private static final String TIMESTAMP = "2026-10-11T12:34:56Z";
    private static final String FIRST_ACTION = "first";
    private static final String JOURNAL_PREFIX = "staff-actions-";
    private static final String JOURNAL_EXTENSION = ".jsonl";
    private static final String LEGACY_TIMESTAMP_PREFIX = "{\"ts\":\"";

    @Test
    void aDatabaseOutageRetainsFailedRowAndRecoversWithoutReplayingCheckpointedRecords()
            throws Exception {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        Path journal = journal();
        Files.writeString(journal, record(one, FIRST_ACTION) + "\n"
                + record(two, "second") + "\n", StandardCharsets.UTF_8);
        List<UUID> attempts = new ArrayList<>();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, time) -> {
                    attempts.add(id);
                    if (id.equals(two) && unavailable.get()) {
                        throw new java.sql.SQLException("simulated database outage");
                    }
                });
        recovery.recoverOnce();
        assertEquals(List.of(one, two), attempts);
        unavailable.set(false);
        recovery.recoverOnce();
        assertEquals(List.of(one, two, two), attempts);
        assertTrue(Files.exists(checkpoint()));
        recovery.recoverOnce();
        assertEquals(3, attempts.size());
    }

    @Test
    void databaseOutageStopsThePassBeforeHammeringLaterJournals() throws Exception {
        Path first = oldJournal();
        Path second = directory.resolve(JOURNAL_PREFIX
                + java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(44) + JOURNAL_EXTENSION);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Files.writeString(first, record(firstId, FIRST_ACTION) + "\n", StandardCharsets.UTF_8);
        Files.writeString(second, record(secondId, "second") + "\n", StandardCharsets.UTF_8);
        List<UUID> attempts = new ArrayList<>();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, time) -> {
                    attempts.add(id);
                    if (unavailable.get()) {
                        throw new java.sql.SQLException("simulated database outage");
                    }
                });
        recovery.recoverOnce();
        assertEquals(List.of(firstId), attempts,
                "A failed outbox write must pause later journals until the next pass");
        assertTrue(Files.exists(first));
        assertTrue(Files.exists(second));
        assertFalse(Files.exists(cursorFor(first)));

        unavailable.set(false);
        recovery.recoverOnce();
        assertEquals(List.of(firstId, firstId, secondId), attempts);
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
    }

    @Test
    void malformedJournalDoesNotPreventAnotherJournalFromReplaying() throws Exception {
        Path bad = oldJournal();
        Path good = directory.resolve(JOURNAL_PREFIX
                + java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(44) + JOURNAL_EXTENSION);
        UUID id = UUID.randomUUID();
        Files.writeString(bad, "{invalid-json}\n", StandardCharsets.UTF_8);
        Files.writeString(good, record(id, "good") + "\n", StandardCharsets.UTF_8);
        List<UUID> delivered = new ArrayList<>();
        new StaffActionOutboxRecovery(directory, LOG,
                (eventId, json, when) -> delivered.add(eventId)).recoverOnce();
        assertEquals(List.of(id), delivered);
        assertTrue(Files.exists(bad), "malformed historical evidence must remain");
    }

    @Test
    void crashBeforeCursorUpdateCanReplaySameStableIdWithoutCreatingSecondDbRow()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        Files.writeString(journal(), record(eventId, "cmd") + "\n", StandardCharsets.UTF_8);
        Set<UUID> databaseIds = new HashSet<>();
        List<UUID> attempts = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, timestamp) -> {
                    attempts.add(id);
                    databaseIds.add(id); // Models a unique database message_id.
                });
        recovery.recoverOnce();
        Files.delete(checkpoint()); // Crash before cursor persisted.
        recovery.recoverOnce();
        assertEquals(List.of(eventId, eventId), attempts);
        assertEquals(Set.of(eventId), databaseIds);
    }

    @Test
    void partiallyWrittenLastLineWaitsForNewlineBeforeReplay() throws Exception {
        UUID eventId = UUID.randomUUID();
        Path journal = journal();
        Files.writeString(journal, record(eventId, "held"), StandardCharsets.UTF_8);
        List<UUID> observed = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, when) -> observed.add(id));
        recovery.recoverOnce();
        assertTrue(observed.isEmpty());
        assertFalse(Files.exists(checkpoint()));

        Files.writeString(journal, "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        recovery.recoverOnce();
        assertEquals(List.of(eventId), observed);
    }

    @Test
    void legacyRecordsWithoutEventIdsCannotBeSafelyReplayed() throws Exception {
        UUID eventId = UUID.randomUUID();
        Files.writeString(journal(),
                LEGACY_TIMESTAMP_PREFIX + TIMESTAMP + "\",\"action\":\"old\"}\n"
                        + record(eventId, "new") + "\n", StandardCharsets.UTF_8);
        List<UUID> observed = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, timestamp) -> observed.add(id));
        recovery.recoverOnce();
        assertEquals(List.of(eventId), observed);
        recovery.recoverOnce();
        assertEquals(List.of(eventId), observed);
    }

    @Test
    void preservesUtf8TextDuringRecovery() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(journal(), record(id, "blöck placed") + "\n", StandardCharsets.UTF_8);
        List<String> payloads = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (eventId, json, timestamp) -> {
                    assertEquals(id, eventId);
                    assertEquals(Instant.parse(TIMESTAMP), timestamp);
                    payloads.add(json);
                });
        recovery.recoverOnce();
        assertEquals(1, payloads.size());
        assertTrue(payloads.getFirst().contains("blöck placed"));
    }

    @Test
    void largeBacklogIsDrainedInBoundedBatches() throws Exception {
        int total = 215;
        StringBuilder contents = new StringBuilder();
        for (int i = 0; i < total; i++) {
            contents.append(record(UUID.randomUUID(), "event")).append('\n');
        }
        Files.writeString(journal(), contents.toString(), StandardCharsets.UTF_8);
        List<UUID> observed = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, timestamp) -> observed.add(id));
        recovery.recoverOnce();
        assertEquals(200, observed.size());
        recovery.recoverOnce();
        assertEquals(total, observed.size());
        recovery.recoverOnce();
        assertEquals(total, observed.size());
    }

    @Test
    void oversizedLegacyEntryIsBoundedAndBlocksLaterReplay() throws Exception {
        UUID valid = UUID.randomUUID();
        Files.writeString(journal(), "x".repeat(40_000) + "\n"
                + record(valid, "later") + "\n", StandardCharsets.UTF_8);
        List<UUID> observed = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, timestamp) -> observed.add(id));
        recovery.recoverOnce();
        assertTrue(observed.isEmpty());
        assertFalse(Files.exists(checkpoint()));
    }

    @Test
    void acceptsWindowsCrLfJsonlRows() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(journal(), record(id, "placed") + "\r\n", StandardCharsets.UTF_8);
        List<UUID> observed = new ArrayList<>();
        new StaffActionOutboxRecovery(directory, LOG, (uuid, json, timestamp) ->
                observed.add(uuid)).recoverOnce();
        assertEquals(List.of(id), observed);
    }

    @Test
    void oldJournalSurvivesDatabaseOutageAndIsPrunedAfterAcceptance() throws Exception {
        Path old = oldJournal();
        UUID id = UUID.randomUUID();
        Files.writeString(old, record(id, "delayed") + "\n", StandardCharsets.UTF_8);
        AtomicBoolean databaseAvailable = new AtomicBoolean(false);
        List<UUID> received = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (eventId, json, when) -> {
                    if (!databaseAvailable.get()) {
                        throw new java.sql.SQLException("database unavailable past retention");
                    }
                    received.add(eventId);
                });

        recovery.recoverOnce();
        assertTrue(Files.exists(old)); // Must not age-delete undelivered events.
        assertFalse(Files.exists(cursorFor(old)));
        databaseAvailable.set(true);
        recovery.recoverOnce();
        assertEquals(List.of(id), received);
        assertFalse(Files.exists(old)); // Completed historical backlog can be pruned.
        assertFalse(Files.exists(cursorFor(old)));
    }

    @Test
    void oldLegacyJournalIsRetainedEvenAfterReplayOfLaterIdentifiedRecords() throws Exception {
        Path old = oldJournal();
        UUID id = UUID.randomUUID();
        Files.writeString(old, LEGACY_TIMESTAMP_PREFIX + TIMESTAMP + "\",\"action\":\"legacy\"}\n"
                + record(id, "new") + "\n", StandardCharsets.UTF_8);
        List<UUID> received = new ArrayList<>();
        new StaffActionOutboxRecovery(directory, LOG, (eventId, json, when) ->
                received.add(eventId)).recoverOnce();
        assertEquals(List.of(id), received);
        assertTrue(Files.exists(old)); // Unknown legacy delivery status needs manual review.
    }

    @Test
    void oldMixedJournalPreservesUnidentifiedRecordEvenWhenCursorReachedEof() throws Exception {
        Path old = oldJournal();
        UUID first = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Files.writeString(old, record(first, FIRST_ACTION) + "\n"
                + LEGACY_TIMESTAMP_PREFIX + TIMESTAMP + "\",\"action\":\"legacy\"}\n"
                + record(third, "third") + "\n", StandardCharsets.UTF_8);
        List<UUID> delivered = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, when) -> delivered.add(id));
        recovery.recoverOnce();
        assertEquals(List.of(first, third), delivered);
        assertEquals(Files.size(old),
                Long.parseLong(Files.readString(cursorFor(old), StandardCharsets.UTF_8).strip()));
        assertTrue(Files.exists(old), "a skipped middle record must block pruning");
        recovery.recoverOnce();
        assertTrue(Files.exists(old), "retention must not delete the legacy record on retry");
    }

    @Test
    void oldMixedJournalWithPreexistingEofCheckpointIsNeverPruned() throws Exception {
        Path old = oldJournal();
        Files.writeString(old, record(UUID.randomUUID(), FIRST_ACTION) + "\n"
                + LEGACY_TIMESTAMP_PREFIX + TIMESTAMP + "\",\"action\":\"legacy\"}\n"
                + record(UUID.randomUUID(), "last") + "\n", StandardCharsets.UTF_8);
        Files.writeString(cursorFor(old), Long.toString(Files.size(old)), StandardCharsets.UTF_8);
        new StaffActionOutboxRecovery(directory, LOG, (id, json, when) -> {
            throw new AssertionError("checkpointed records must not be offered again");
        }).recoverOnce();
        assertTrue(Files.exists(old), "preexisting mixed journal must be retained");
        assertTrue(Files.exists(cursorFor(old)));
    }

    @Test
    void largeOldJournalUsesBoundedRetentionValidationBeforeDeletion() throws Exception {
        Path old = oldJournal();
        StringBuilder contents = new StringBuilder();
        for (int i = 0; i < 215; i++) {
            contents.append(record(UUID.randomUUID(), "event")).append('\n');
        }
        Files.writeString(old, contents.toString(), StandardCharsets.UTF_8);
        List<UUID> delivered = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (id, json, when) -> delivered.add(id));
        recovery.recoverOnce(); // 200 delivery records; no scan budget left.
        assertEquals(200, delivered.size());
        assertTrue(Files.exists(old));
        recovery.recoverOnce(); // Remaining 15 delivered; 185 retention records checked.
        assertEquals(215, delivered.size());
        assertTrue(Files.exists(old));
        recovery.recoverOnce(); // Remaining 30 retention records validated; safe to remove.
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(cursorFor(old)));
    }

    @Test
    void oldMalformedLastRecordIsRetainedEvenWithEofCheckpoint() throws Exception {
        Path old = oldJournal();
        Files.writeString(old, record(UUID.randomUUID(), FIRST_ACTION) + "\n"
                + "{\"eventId\":\"not-a-uuid\",\"ts\":\"" + TIMESTAMP + "\"}\n",
                StandardCharsets.UTF_8);
        Files.writeString(cursorFor(old), Long.toString(Files.size(old)), StandardCharsets.UTF_8);
        new StaffActionOutboxRecovery(directory, LOG, (id, json, when) -> {
            throw new AssertionError("checkpointed records must not be offered again");
        }).recoverOnce();
        assertTrue(Files.exists(old));
    }

    @Test
    void oldTruncatedJournalIsRetainedWithoutAdvancingItsCursor() throws Exception {
        Path old = oldJournal();
        Files.writeString(old, record(UUID.randomUUID(), "partial"), StandardCharsets.UTF_8);
        new StaffActionOutboxRecovery(directory, LOG, (eventId, json, when) -> {
            throw new AssertionError("incomplete entry must not be offered");
        }).recoverOnce();
        assertTrue(Files.exists(old));
        assertFalse(Files.exists(cursorFor(old)));
    }

    @Test
    void invalidJsonMustNotSkipFollowingEntries() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(journal(), "{invalid-json}\n"
                + record(id, "later") + "\n", StandardCharsets.UTF_8);
        List<UUID> observed = new ArrayList<>();
        StaffActionOutboxRecovery recovery = new StaffActionOutboxRecovery(
                directory, LOG, (eventId, json, timestamp) -> observed.add(eventId));
        recovery.recoverOnce();
        assertTrue(observed.isEmpty());
        assertFalse(Files.exists(checkpoint()));
    }

    @Test
    void rejectsInvalidCursorWithoutResettingDeliveryPosition() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(journal(), record(id, "cmd") + "\n", StandardCharsets.UTF_8);
        Files.writeString(checkpoint(), "-4", StandardCharsets.UTF_8);
        List<UUID> observed = new ArrayList<>();
        new StaffActionOutboxRecovery(directory, LOG, (uuid, json, timestamp) ->
                observed.add(uuid)).recoverOnce();
        assertTrue(observed.isEmpty());
    }

    private Path journal() {
        // Use today's UTC date for ordinary event replay and cursor tests.
        String day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        return directory.resolve(JOURNAL_PREFIX + day + JOURNAL_EXTENSION);
    }

    private Path oldJournal() {
        String date = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .minusDays(45).toString();
        return directory.resolve(JOURNAL_PREFIX + date + JOURNAL_EXTENSION);
    }

    private Path cursorFor(Path journal) {
        return journal.resolveSibling(journal.getFileName().toString() + ".cursor");
    }

    private Path checkpoint() {
        return directory.resolve(journal().getFileName().toString() + ".cursor");
    }

    private static String record(UUID id, String action) {
        return "{\"eventId\":\"" + id + "\",\"ts\":\"" + TIMESTAMP
                + "\",\"action\":\"" + action + "\",\"forwardChannel\":\"\"}";
    }
}
