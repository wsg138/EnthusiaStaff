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

    @Test
    void aDatabaseOutageRetainsFailedRowAndRecoversWithoutReplayingCheckpointedRecords()
            throws Exception {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        Path journal = journal();
        Files.writeString(journal, record(one, "first") + "\n"
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
                "{\"ts\":\"" + TIMESTAMP + "\",\"action\":\"old\"}\n"
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
        // Use today's UTC date: the replay service only reads retained journals.
        String day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        return directory.resolve("staff-actions-" + day + ".jsonl");
    }

    private Path checkpoint() {
        return directory.resolve(journal().getFileName().toString() + ".cursor");
    }

    private static String record(UUID id, String action) {
        return "{\"eventId\":\"" + id + "\",\"ts\":\"" + TIMESTAMP
                + "\",\"action\":\"" + action + "\",\"forwardChannel\":\"\"}";
    }
}
