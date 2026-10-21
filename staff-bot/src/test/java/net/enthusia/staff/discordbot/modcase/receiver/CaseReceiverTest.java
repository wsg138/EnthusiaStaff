package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaseReceiverTest {
    @TempDir Path dir;
    private static final String CASE = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String OTHER = "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String JOURNAL_NAME = "synthetic-receiver.json";
    private static final String REVISION_FIELD = "revision";
    private static final String CASES_FIELD = "cases";
    private static final String ORIGIN = "minecraft";
    private static final String SCOPE = "smp";
    private static final String CREATED_EVENT = "CASE_CREATED";
    private static final String OPEN_STATE = "OPEN";
    private static final String ACCEPTED_RESULT = "ACCEPTED";
    private static final String DUPLICATE_RESULT = "DUPLICATE";
    private static final String OTHER_SCOPE = "other";
    private static final String REJECTED_RESULT = "REJECTED";
    private static final String STATE_EVENT = "CASE_STATE";
    private static final String CLAIMED_STATE = "CLAIMED";
    private static final String CLOSED_STATE = "CLOSED";
    private static final String REVISION_EVENT = "CASE_REVISION";
    private static final String DISCORD_RECEIPT = "discord:123";
    private static final String REVISION_ZERO_JSON = "\"revision\":0";
    private static final Instant NOW = Instant.parse("2026-10-15T01:01:00Z");
    private static final CaseReceiver.Peer PEER =
            new CaseReceiver.Peer(true, ORIGIN, SCOPE, 12345L);
    private final ObjectMapper json = new ObjectMapper();

    private ReceiverJournal journal() throws IOException {
        return new ReceiverJournal(dir.resolve(JOURNAL_NAME));
    }

    private CaseReceiver receiver(ReceiverJournal journal) {
        return new CaseReceiver(journal, ORIGIN, SCOPE, 12345L);
    }

    private static CaseEvent event(String operation, String type, String caseId, long revision,
                                   String scope, String state) {
        return new CaseEvent(1, operation.repeat(64), type, caseId, revision,
                ORIGIN, scope, state, "NORMAL",
                "2026-10-15T01:00:00+00:00", "2026-10-15T01:10:00+00:00",
                List.of("private:synthetic-source"));
    }

    private String wire(CaseEvent e) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("contract_version", e.contractVersion());
        fields.put("operation_id", e.operationId());
        fields.put("event_type", e.eventType());
        fields.put("case_id", e.caseId());
        fields.put(REVISION_FIELD, e.revision());
        fields.put("origin_platform", e.originPlatform());
        fields.put("scope_id", e.scopeId());
        fields.put("case_state", e.caseState());
        fields.put("review_priority", e.reviewPriority());
        fields.put("created_at", e.createdAt());
        fields.put("expires_at", e.expiresAt());
        fields.put("evidence_refs", e.evidenceRefs());
        return json.writeValueAsString(fields);
    }

    private CaseEvent birth() {
        return event("a", CREATED_EVENT, CASE, 0, SCOPE, OPEN_STATE);
    }

    private String accept(CaseReceiver receiver, CaseEvent event) throws IOException {
        return receiver.receive(PEER, wire(event)).orElseThrow().status();
    }

    @Test
    void initialRevisionZeroAndExactAckFields() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        CaseEvent first = birth();
        CaseAck ack = service.receive(PEER, wire(first)).orElseThrow();
        assertEquals(ACCEPTED_RESULT, ack.status());
        assertEquals(first.operationId(), ack.operationId());
        assertEquals(first.eventType(), ack.eventType());
        assertEquals(first.caseId(), ack.caseId());
        assertEquals(0, ack.revision());
        assertEquals(ORIGIN, ack.originPlatform());
        assertEquals(SCOPE, ack.scopeId());
        assertEquals(2, store.ready(ORIGIN, SCOPE, 4, NOW).size());
        assertEquals(DUPLICATE_RESULT, accept(service, first));
    }

    @Test
    void malformedUnknownAndUnexpectedFieldsNeverAck() throws Exception {
        CaseReceiver service = receiver(journal());
        String valid = wire(birth());
        assertTrue(service.receive(PEER, valid.replace("\"contract_version\":1",
                "\"contract_version\":2")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"contract_version\":1",
                "\"contract_version\":4294967297")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace(REVISION_ZERO_JSON,
                "\"revision\":\"0\"")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace(REVISION_ZERO_JSON,
                "\"revision\":-1")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace(REVISION_ZERO_JSON,
                "\"revision\":0.0")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace(REVISION_ZERO_JSON,
                "\"revision\":false")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("}", ",\"chat_text\":\"SECRET\"}")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"scope_id\":\"smp\"",
                "\"scope_id\":\"smp\",\"scope_id\":\"other\"")).isEmpty());
        assertTrue(service.receive(PEER, " ".repeat(8193)).isEmpty());
        assertTrue(service.receive(PEER, "{bad-json").isEmpty());
        assertNull(journal().cursor(CASE));
    }

    @Test
    void strictSchemaRejectsWrongTextAndEvidenceShape() throws Exception {
        CaseReceiver service = receiver(journal());
        String valid = wire(birth());
        assertTrue(service.receive(PEER, valid.replace("\"scope_id\":\"smp\"",
                "\"scope_id\":12")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"evidence_refs\":[",
                "\"evidence_refs\":\"not-an-array\"")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"evidence_refs\":[",
                "\"evidence_refs\":[false,")).isEmpty());
        assertNull(journal().cursor(CASE));
    }

    @Test
    void canonicalCaseUuidIsRequiredInStrictWireParser() throws Exception {
        CaseReceiver service = receiver(journal());
        String valid = wire(birth());
        assertTrue(service.receive(PEER, valid.replace(CASE, CASE.toUpperCase(
                java.util.Locale.ROOT))).isEmpty());
        assertTrue(service.receive(PEER,
                valid.replace("\"case_id\":\"" + CASE + "\"", "\"case_id\":null")).isEmpty());
        assertNull(journal().cursor(CASE));
    }

    @Test
    void scopeGuildAndPeerAuthenticationFailClosed() throws Exception {
        CaseReceiver service = receiver(journal());
        assertTrue(service.receive(null, wire(birth())).isEmpty());
        assertTrue(service.receive(new CaseReceiver.Peer(false, ORIGIN, SCOPE, 12345),
                wire(birth())).isEmpty());
        assertTrue(service.receive(new CaseReceiver.Peer(true, ORIGIN, SCOPE, 999),
                wire(birth())).isEmpty());
        assertTrue(service.receive(new CaseReceiver.Peer(true, "discord", SCOPE, 12345),
                wire(birth())).isEmpty());
        assertTrue(service.receive(new CaseReceiver.Peer(true, ORIGIN, OTHER_SCOPE, 12345),
                wire(birth())).isEmpty());
        assertTrue(service.receive(PEER, wire(event("b", CREATED_EVENT, CASE, 0,
                OTHER_SCOPE, OPEN_STATE))).isEmpty());
    }

    @Test
    void operationCollisionChangedPayloadAndCaseScopeCollisionReject() throws Exception {
        CaseReceiver service = receiver(journal());
        assertEquals(ACCEPTED_RESULT, accept(service, birth()));
        assertEquals(REJECTED_RESULT, accept(service, event("a", CREATED_EVENT,
                OTHER, 0, SCOPE, OPEN_STATE)));
        assertEquals(REJECTED_RESULT, accept(service, event("b", CREATED_EVENT,
                CASE, 0, SCOPE, OPEN_STATE)));
        assertEquals(DUPLICATE_RESULT, accept(service, birth()));
        CaseReceiver otherScope = new CaseReceiver(journal(), ORIGIN, OTHER_SCOPE, 777);
        CaseReceiver.Peer otherPeer = new CaseReceiver.Peer(true, ORIGIN, OTHER_SCOPE, 777);
        assertEquals(REJECTED_RESULT, otherScope.receive(otherPeer,
                wire(event("c", CREATED_EVENT, CASE, 0, OTHER_SCOPE, OPEN_STATE)))
                .orElseThrow().status());
    }

    @Test
    void revisionsAdvanceMonotonicallyAndClosedNeverReopens() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        CaseEvent claim = event("b", STATE_EVENT, CASE, 1, SCOPE, CLAIMED_STATE);
        CaseEvent closed = event("c", STATE_EVENT, CASE, 2, SCOPE, CLOSED_STATE);
        assertEquals(ACCEPTED_RESULT, accept(service, birth()));
        assertEquals(ACCEPTED_RESULT, accept(service, claim));
        assertEquals(ACCEPTED_RESULT, accept(service, closed));
        assertEquals(CLOSED_STATE, store.cursor(CASE).caseState());
        assertEquals(REJECTED_RESULT, accept(service, event("d", REVISION_EVENT,
                CASE, 3, SCOPE, OPEN_STATE)));
        assertEquals(REJECTED_RESULT, accept(service, event("e", REVISION_EVENT,
                CASE, 1, SCOPE, OPEN_STATE)));
        assertEquals(2, store.ready(ORIGIN, SCOPE, 10, NOW).size());
    }

    @Test
    void claimedCaseCannotRegressToOpenEvenWithHigherRevision() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals(ACCEPTED_RESULT, accept(service, birth()));
        CaseEvent claim = event("b", STATE_EVENT, CASE, 1, SCOPE, CLAIMED_STATE);
        assertEquals(ACCEPTED_RESULT, accept(service, claim));
        CaseEvent regress = event("c", REVISION_EVENT, CASE, 2, SCOPE, OPEN_STATE);
        assertEquals(REJECTED_RESULT, accept(service, regress));
        assertEquals(1, store.cursor(CASE).revision());
        assertEquals(CLAIMED_STATE, store.cursor(CASE).caseState());
        assertFalse(store.cursor(CASE).needsResync());
        assertEquals(2, journal().ready(ORIGIN, SCOPE, 10, NOW).size());
        assertEquals(ACCEPTED_RESULT, accept(service,
                event("d", STATE_EVENT, CASE, 2, SCOPE, CLOSED_STATE)));
        assertEquals(CLOSED_STATE, store.cursor(CASE).caseState());
    }

    @Test
    void revisionEventCannotForgeAClosureState() {
        assertThrows(IllegalArgumentException.class,
                () -> event("c", REVISION_EVENT, CASE, 2, SCOPE, CLOSED_STATE));
        assertDoesNotThrow(() -> event("c", STATE_EVENT, CASE, 2, SCOPE, CLOSED_STATE));
    }

    @Test
    void missedBirthAndOutOfOrderNeedAuthenticatedCurrentSnapshot() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        CaseEvent late = event("b", REVISION_EVENT, CASE, 2, SCOPE, OPEN_STATE);
        assertEquals(ACCEPTED_RESULT, accept(service, late));
        assertTrue(store.cursor(CASE).needsResync());
        assertTrue(store.ready(ORIGIN, SCOPE, 10, NOW).isEmpty());
        assertFalse(service.reconcile(new CaseReceiver.Peer(false, ORIGIN, SCOPE, 12345),
                late));
        assertFalse(service.reconcile(PEER, event("c", REVISION_EVENT, CASE, 2,
                SCOPE, OPEN_STATE)));
        assertTrue(service.reconcile(PEER, late));
        assertEquals(2, store.ready(ORIGIN, SCOPE, 10, NOW).size());
        assertEquals(REJECTED_RESULT, accept(service, birth()));
    }

    @Test
    void journalRestartDuplicatesRemainStableAndScopePollIsNotStarved() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals(ACCEPTED_RESULT, accept(service, birth()));
        CaseReceiver external = new CaseReceiver(store, ORIGIN, OTHER_SCOPE, 777);
        CaseReceiver.Peer other = new CaseReceiver.Peer(true, ORIGIN, OTHER_SCOPE, 777);
        assertEquals(ACCEPTED_RESULT, external.receive(other,
                wire(event("d", CREATED_EVENT, OTHER, 0, OTHER_SCOPE, OPEN_STATE)))
                .orElseThrow().status());
        ReceiverJournal restarted = journal();
        assertEquals(DUPLICATE_RESULT, accept(receiver(restarted), birth()));
        assertEquals(1, restarted.ready(ORIGIN, SCOPE, 1, NOW).size());
        assertEquals(2, restarted.ready(ORIGIN, OTHER_SCOPE, 10, NOW).size());
    }

    @Test
    void crashBetweenPersistSendAndAckFencesReplayIndependently() throws Exception {
        ReceiverJournal store = journal();
        accept(receiver(store), birth());
        List<ReceiverJournal.Job> jobs = store.ready(ORIGIN, SCOPE, 10, NOW);
        ReceiverJournal.Job discord = jobs.stream()
                .filter(x -> x.surface() == ReceiverJournal.Surface.DISCORD).findFirst().orElseThrow();
        assertTrue(store.markPossiblySent(discord.id(), NOW));
        ReceiverJournal afterCrash = journal();
        assertEquals(1, afterCrash.ready(ORIGIN, SCOPE, 10, NOW).size());
        assertFalse(afterCrash.confirm(discord.id(), "f".repeat(64), DISCORD_RECEIPT));
        assertTrue(afterCrash.confirm(discord.id(), birth().operationId(), DISCORD_RECEIPT));
        assertTrue(afterCrash.confirm(discord.id(), birth().operationId(), DISCORD_RECEIPT));
        assertFalse(afterCrash.confirm(discord.id(), birth().operationId(), "discord:999"));
        assertEquals(1, journal().ready(ORIGIN, SCOPE, 10, NOW).size());
    }

    @Test
    void fakeKnownNotSentUsesBoundedBackoffAndNoFalseRepost() throws Exception {
        ReceiverJournal store = journal();
        accept(receiver(store), birth());
        ReceiverJournal.Job item = store.ready(ORIGIN, SCOPE, 10, NOW).getFirst();
        assertFalse(store.confirm(item.id(), item.operationId(), DISCORD_RECEIPT));
        assertTrue(store.markPossiblySent(item.id(), NOW));
        assertFalse(store.markPossiblySent(item.id(), NOW));
        assertTrue(journal().ready(ORIGIN, SCOPE, 10, NOW).stream()
                .noneMatch(x -> x.id().equals(item.id())));
        assertTrue(store.verifiedNotSent(item.id(), NOW));
        assertTrue(store.ready(ORIGIN, SCOPE, 10, NOW).stream()
                .noneMatch(x -> x.id().equals(item.id())));
        assertEquals(2, store.ready(ORIGIN, SCOPE, 10, NOW.plusSeconds(5)).size());
    }

    @Test
    void caseAdvanceAfterSelectionCannotSendStaleRevision() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals(ACCEPTED_RESULT, accept(service, birth()));
        ReceiverJournal.Job selected = store.ready(ORIGIN, SCOPE, 10, NOW).getFirst();
        assertEquals(ACCEPTED_RESULT, accept(service,
                event("b", STATE_EVENT, CASE, 1, SCOPE, CLAIMED_STATE)));
        assertFalse(store.markPossiblySent(selected.id(), NOW));
        assertEquals(2, store.ready(ORIGIN, SCOPE, 10, NOW).size());
    }

    @Test
    void acknowledgementHasExactPublicWireAllowlist() throws Exception {
        CaseAck ack = receiver(journal()).receive(PEER, wire(birth())).orElseThrow();
        assertEquals(java.util.Set.of("status", "operation_id", "event_type", "case_id",
                REVISION_FIELD, "origin_platform", "scope_id"), ack.toWire().keySet());
        assertEquals(0L, ack.toWire().get(REVISION_FIELD));
        assertFalse(ack.toWire().containsKey("evidence_refs"));
    }

    private java.util.concurrent.Callable<String> receiveAfterLatch(
            CaseReceiver receiver, java.util.concurrent.CountDownLatch gate) {
        return () -> {
            gate.await();
            return receiver.receive(PEER, wire(birth())).orElseThrow().status();
        };
    }

    @Test
    void concurrentIngressAndExactDestinationAckAreIdempotent() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        try (java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            var first = pool.submit(receiveAfterLatch(service, go));
            var second = pool.submit(receiveAfterLatch(service, go));
            go.countDown();
            assertEquals(java.util.Set.of(ACCEPTED_RESULT, DUPLICATE_RESULT),
                    java.util.Set.of(first.get(), second.get()));
            ReceiverJournal.Job job = store.ready(ORIGIN, SCOPE, 10, NOW).getFirst();
            assertTrue(store.markPossiblySent(job.id(), NOW));
            var a = pool.submit(() -> store.confirm(job.id(), job.operationId(), "mock:1"));
            var b = pool.submit(() -> store.confirm(job.id(), job.operationId(), "mock:1"));
            assertTrue(a.get());
            assertTrue(b.get());
            assertFalse(store.confirm(job.id(), job.operationId(), "mock:2"));
        }
    }

    private ObjectNode savedJournal() throws IOException {
        return (ObjectNode) json.readTree(Files.readString(
                dir.resolve(JOURNAL_NAME)));
    }

    private void replaceSavedJournal(ObjectNode value) throws IOException {
        Files.writeString(dir.resolve(JOURNAL_NAME),
                json.writeValueAsString(value));
    }

    @Test
    void restartRejectsOperationKeyThatDoesNotMatchPersistedEvent() throws Exception {
        accept(receiver(journal()), birth());
        ObjectNode state = savedJournal();
        ObjectNode operations = (ObjectNode) state.path("operations");
        operations.set("f".repeat(64), operations.remove(birth().operationId()));
        replaceSavedJournal(state);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void restartRejectsDifferentOperationAtAnExistingRevision() throws Exception {
        accept(receiver(journal()), birth());
        ObjectNode state = savedJournal();
        ObjectNode operations = (ObjectNode) state.path("operations");
        ObjectNode duplicate = ((ObjectNode) operations.path(birth().operationId())).deepCopy();
        duplicate.put("operationId", "f".repeat(64));
        operations.set("f".repeat(64), duplicate);
        replaceSavedJournal(state);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void restartRejectsCursorWithFalseCurrentRevision() throws Exception {
        accept(receiver(journal()), birth());
        ObjectNode state = savedJournal();
        ((ObjectNode) state.path(CASES_FIELD).path(CASE)).put(REVISION_FIELD, 99);
        replaceSavedJournal(state);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void restartRejectsCursorRollbackAfterAClosedCase() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals(ACCEPTED_RESULT, accept(service, birth()));
        assertEquals(ACCEPTED_RESULT, accept(service,
                event("b", STATE_EVENT, CASE, 1, SCOPE, CLAIMED_STATE)));
        assertEquals(ACCEPTED_RESULT, accept(service,
                event("c", STATE_EVENT, CASE, 2, SCOPE, CLOSED_STATE)));

        ObjectNode state = savedJournal();
        ObjectNode cursor = (ObjectNode) state.path(CASES_FIELD).path(CASE);
        cursor.put("operationId", birth().operationId());
        cursor.put(REVISION_FIELD, 0);
        cursor.put("caseState", OPEN_STATE);
        replaceSavedJournal(state);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void restartRejectsOrphanedEventWithMissingCaseCursor() throws Exception {
        accept(receiver(journal()), birth());
        ObjectNode state = savedJournal();
        ((ObjectNode) state.path(CASES_FIELD)).remove(CASE);
        replaceSavedJournal(state);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void restartRejectsJobWithUnownedOperation() throws Exception {
        accept(receiver(journal()), birth());
        ObjectNode state = savedJournal();
        ObjectNode jobs = (ObjectNode) state.path("jobs");
        ObjectNode job = (ObjectNode) jobs.elements().next();
        job.put("operationId", "f".repeat(64));
        replaceSavedJournal(state);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void restartRejectsDuplicatePersistedMapKeys() throws Exception {
        accept(receiver(journal()), birth());
        Path file = dir.resolve(JOURNAL_NAME);
        String original = Files.readString(file);
        String duplicate = original.replace("\"operations\":{",
                "\"operations\":{},\"operations\":{");
        assertNotEquals(original, duplicate);
        Files.writeString(file, duplicate);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void restartRejectsNullCursorInsteadOfCrashingLater() throws Exception {
        accept(receiver(journal()), birth());
        ObjectNode state = savedJournal();
        ((ObjectNode) state.path(CASES_FIELD)).putNull(CASE);
        replaceSavedJournal(state);
        assertThrows(IOException.class, this::journal);
    }

    @Test
    void expiryBlocksOpenButStillPermitsCanonicalClose() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals(ACCEPTED_RESULT, accept(service, birth()));
        assertTrue(store.ready(ORIGIN, SCOPE, 10, NOW.plusSeconds(1000)).isEmpty());
        assertEquals(ACCEPTED_RESULT, accept(service,
                event("e", STATE_EVENT, CASE, 1, SCOPE, CLOSED_STATE)));
        assertEquals(2, store.ready(ORIGIN, SCOPE, 10, NOW.plusSeconds(1000)).size());
    }
}
