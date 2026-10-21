package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
    private static final Instant NOW = Instant.parse("2026-10-15T01:01:00Z");
    private static final CaseReceiver.Peer PEER =
            new CaseReceiver.Peer(true, "minecraft", "smp", 12345L);
    private final ObjectMapper json = new ObjectMapper();

    private ReceiverJournal journal() throws IOException {
        return new ReceiverJournal(dir.resolve("synthetic-receiver.json"));
    }

    private CaseReceiver receiver(ReceiverJournal journal) {
        return new CaseReceiver(journal, "minecraft", "smp", 12345L);
    }

    private static CaseEvent event(String operation, String type, String caseId, long revision,
                                   String scope, String state) {
        return new CaseEvent(1, operation.repeat(64), type, caseId, revision,
                "minecraft", scope, state, "NORMAL",
                "2026-10-15T01:00:00+00:00", "2026-10-15T01:10:00+00:00",
                List.of("private:synthetic-source"));
    }

    private String wire(CaseEvent e) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("contract_version", e.contractVersion());
        fields.put("operation_id", e.operationId());
        fields.put("event_type", e.eventType());
        fields.put("case_id", e.caseId());
        fields.put("revision", e.revision());
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
        return event("a", "CASE_CREATED", CASE, 0, "smp", "OPEN");
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
        assertEquals("ACCEPTED", ack.status());
        assertEquals(first.operationId(), ack.operationId());
        assertEquals(first.eventType(), ack.eventType());
        assertEquals(first.caseId(), ack.caseId());
        assertEquals(0, ack.revision());
        assertEquals("minecraft", ack.originPlatform());
        assertEquals("smp", ack.scopeId());
        assertEquals(2, store.ready("minecraft", "smp", 4, NOW).size());
        assertEquals("DUPLICATE", accept(service, first));
    }

    @Test
    void malformedUnknownAndUnexpectedFieldsNeverAck() throws Exception {
        CaseReceiver service = receiver(journal());
        String valid = wire(birth());
        assertTrue(service.receive(PEER, valid.replace("\"contract_version\":1",
                "\"contract_version\":2")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"contract_version\":1",
                "\"contract_version\":4294967297")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"revision\":0",
                "\"revision\":\"0\"")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"revision\":0",
                "\"revision\":-1")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"revision\":0",
                "\"revision\":0.0")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"revision\":0",
                "\"revision\":false")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("}", ",\"chat_text\":\"SECRET\"}")).isEmpty());
        assertTrue(service.receive(PEER, valid.replace("\"scope_id\":\"smp\"",
                "\"scope_id\":\"smp\",\"scope_id\":\"other\"")).isEmpty());
        assertTrue(service.receive(PEER, " ".repeat(8193)).isEmpty());
        assertTrue(service.receive(PEER, "{bad-json").isEmpty());
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
        assertTrue(service.receive(new CaseReceiver.Peer(false, "minecraft", "smp", 12345),
                wire(birth())).isEmpty());
        assertTrue(service.receive(new CaseReceiver.Peer(true, "minecraft", "smp", 999),
                wire(birth())).isEmpty());
        assertTrue(service.receive(new CaseReceiver.Peer(true, "discord", "smp", 12345),
                wire(birth())).isEmpty());
        assertTrue(service.receive(new CaseReceiver.Peer(true, "minecraft", "other", 12345),
                wire(birth())).isEmpty());
        assertTrue(service.receive(PEER, wire(event("b", "CASE_CREATED", CASE, 0,
                "other", "OPEN"))).isEmpty());
    }

    @Test
    void operationCollisionChangedPayloadAndCaseScopeCollisionReject() throws Exception {
        CaseReceiver service = receiver(journal());
        assertEquals("ACCEPTED", accept(service, birth()));
        assertEquals("REJECTED", accept(service, event("a", "CASE_CREATED",
                OTHER, 0, "smp", "OPEN")));
        assertEquals("REJECTED", accept(service, event("b", "CASE_CREATED",
                CASE, 0, "smp", "OPEN")));
        assertEquals("DUPLICATE", accept(service, birth()));
        CaseReceiver otherScope = new CaseReceiver(journal(), "minecraft", "other", 777);
        CaseReceiver.Peer otherPeer = new CaseReceiver.Peer(true, "minecraft", "other", 777);
        assertEquals("REJECTED", otherScope.receive(otherPeer,
                wire(event("c", "CASE_CREATED", CASE, 0, "other", "OPEN")))
                .orElseThrow().status());
    }

    @Test
    void revisionsAdvanceMonotonicallyAndClosedNeverReopens() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        CaseEvent claim = event("b", "CASE_STATE", CASE, 1, "smp", "CLAIMED");
        CaseEvent closed = event("c", "CASE_STATE", CASE, 2, "smp", "CLOSED");
        assertEquals("ACCEPTED", accept(service, birth()));
        assertEquals("ACCEPTED", accept(service, claim));
        assertEquals("ACCEPTED", accept(service, closed));
        assertEquals("CLOSED", store.cursor(CASE).caseState());
        assertEquals("REJECTED", accept(service, event("d", "CASE_REVISION",
                CASE, 3, "smp", "OPEN")));
        assertEquals("REJECTED", accept(service, event("e", "CASE_REVISION",
                CASE, 1, "smp", "OPEN")));
        assertEquals(2, store.ready("minecraft", "smp", 10, NOW).size());
    }

    @Test
    void claimedCaseCannotRegressToOpenEvenWithHigherRevision() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals("ACCEPTED", accept(service, birth()));
        CaseEvent claim = event("b", "CASE_STATE", CASE, 1, "smp", "CLAIMED");
        assertEquals("ACCEPTED", accept(service, claim));
        CaseEvent regress = event("c", "CASE_REVISION", CASE, 2, "smp", "OPEN");
        assertEquals("REJECTED", accept(service, regress));
        assertEquals(1, store.cursor(CASE).revision());
        assertEquals("CLAIMED", store.cursor(CASE).caseState());
        assertFalse(store.cursor(CASE).needsResync());
        assertEquals(2, journal().ready("minecraft", "smp", 10, NOW).size());
        assertEquals("ACCEPTED", accept(service,
                event("d", "CASE_STATE", CASE, 2, "smp", "CLOSED")));
        assertEquals("CLOSED", store.cursor(CASE).caseState());
    }

    @Test
    void revisionEventCannotForgeAClosureState() {
        assertThrows(IllegalArgumentException.class,
                () -> event("c", "CASE_REVISION", CASE, 2, "smp", "CLOSED"));
        assertDoesNotThrow(() -> event("c", "CASE_STATE", CASE, 2, "smp", "CLOSED"));
    }

    @Test
    void missedBirthAndOutOfOrderNeedAuthenticatedCurrentSnapshot() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        CaseEvent late = event("b", "CASE_REVISION", CASE, 2, "smp", "OPEN");
        assertEquals("ACCEPTED", accept(service, late));
        assertTrue(store.cursor(CASE).needsResync());
        assertTrue(store.ready("minecraft", "smp", 10, NOW).isEmpty());
        assertFalse(service.reconcile(new CaseReceiver.Peer(false, "minecraft", "smp", 12345),
                late));
        assertFalse(service.reconcile(PEER, event("c", "CASE_REVISION", CASE, 2,
                "smp", "OPEN")));
        assertTrue(service.reconcile(PEER, late));
        assertEquals(2, store.ready("minecraft", "smp", 10, NOW).size());
        assertEquals("REJECTED", accept(service, birth()));
    }

    @Test
    void journalRestartDuplicatesRemainStableAndScopePollIsNotStarved() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals("ACCEPTED", accept(service, birth()));
        CaseReceiver external = new CaseReceiver(store, "minecraft", "other", 777);
        CaseReceiver.Peer other = new CaseReceiver.Peer(true, "minecraft", "other", 777);
        assertEquals("ACCEPTED", external.receive(other,
                wire(event("d", "CASE_CREATED", OTHER, 0, "other", "OPEN")))
                .orElseThrow().status());
        ReceiverJournal restarted = journal();
        assertEquals("DUPLICATE", accept(receiver(restarted), birth()));
        assertEquals(1, restarted.ready("minecraft", "smp", 1, NOW).size());
        assertEquals(2, restarted.ready("minecraft", "other", 10, NOW).size());
    }

    @Test
    void crashBetweenPersistSendAndAckFencesReplayIndependently() throws Exception {
        ReceiverJournal store = journal();
        accept(receiver(store), birth());
        List<ReceiverJournal.Job> jobs = store.ready("minecraft", "smp", 10, NOW);
        ReceiverJournal.Job discord = jobs.stream()
                .filter(x -> x.surface() == ReceiverJournal.Surface.DISCORD).findFirst().orElseThrow();
        assertTrue(store.markPossiblySent(discord.id(), NOW));
        ReceiverJournal afterCrash = journal();
        assertEquals(1, afterCrash.ready("minecraft", "smp", 10, NOW).size());
        assertFalse(afterCrash.confirm(discord.id(), "f".repeat(64), "discord:123"));
        assertTrue(afterCrash.confirm(discord.id(), birth().operationId(), "discord:123"));
        assertTrue(afterCrash.confirm(discord.id(), birth().operationId(), "discord:123"));
        assertFalse(afterCrash.confirm(discord.id(), birth().operationId(), "discord:999"));
        assertEquals(1, journal().ready("minecraft", "smp", 10, NOW).size());
    }

    @Test
    void fakeKnownNotSentUsesBoundedBackoffAndNoFalseRepost() throws Exception {
        ReceiverJournal store = journal();
        accept(receiver(store), birth());
        ReceiverJournal.Job item = store.ready("minecraft", "smp", 10, NOW).getFirst();
        assertFalse(store.confirm(item.id(), item.operationId(), "discord:123"));
        assertTrue(store.markPossiblySent(item.id(), NOW));
        assertFalse(store.markPossiblySent(item.id(), NOW));
        assertTrue(journal().ready("minecraft", "smp", 10, NOW).stream()
                .noneMatch(x -> x.id().equals(item.id())));
        assertTrue(store.verifiedNotSent(item.id(), NOW));
        assertTrue(store.ready("minecraft", "smp", 10, NOW).stream()
                .noneMatch(x -> x.id().equals(item.id())));
        assertEquals(2, store.ready("minecraft", "smp", 10, NOW.plusSeconds(5)).size());
    }

    @Test
    void caseAdvanceAfterSelectionCannotSendStaleRevision() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals("ACCEPTED", accept(service, birth()));
        ReceiverJournal.Job selected = store.ready("minecraft", "smp", 10, NOW).getFirst();
        assertEquals("ACCEPTED", accept(service,
                event("b", "CASE_STATE", CASE, 1, "smp", "CLAIMED")));
        assertFalse(store.markPossiblySent(selected.id(), NOW));
        assertEquals(2, store.ready("minecraft", "smp", 10, NOW).size());
    }

    @Test
    void acknowledgementHasExactPublicWireAllowlist() throws Exception {
        CaseAck ack = receiver(journal()).receive(PEER, wire(birth())).orElseThrow();
        assertEquals(java.util.Set.of("status", "operation_id", "event_type", "case_id",
                "revision", "origin_platform", "scope_id"), ack.toWire().keySet());
        assertEquals(0L, ack.toWire().get("revision"));
        assertFalse(ack.toWire().containsKey("evidence_refs"));
    }

    @Test
    void concurrentIngressAndExactDestinationAckAreIdempotent() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<String> submit = () -> {
                go.await();
                return service.receive(PEER, wire(birth())).orElseThrow().status();
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            go.countDown();
            assertEquals(java.util.Set.of("ACCEPTED", "DUPLICATE"),
                    java.util.Set.of(first.get(), second.get()));
            ReceiverJournal.Job job = store.ready("minecraft", "smp", 10, NOW).getFirst();
            assertTrue(store.markPossiblySent(job.id(), NOW));
            var a = pool.submit(() -> store.confirm(job.id(), job.operationId(), "mock:1"));
            var b = pool.submit(() -> store.confirm(job.id(), job.operationId(), "mock:1"));
            assertTrue(a.get());
            assertTrue(b.get());
            assertFalse(store.confirm(job.id(), job.operationId(), "mock:2"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiryBlocksOpenButStillPermitsCanonicalClose() throws Exception {
        ReceiverJournal store = journal();
        CaseReceiver service = receiver(store);
        assertEquals("ACCEPTED", accept(service, birth()));
        assertTrue(store.ready("minecraft", "smp", 10, NOW.plusSeconds(1000)).isEmpty());
        assertEquals("ACCEPTED", accept(service,
                event("e", "CASE_STATE", CASE, 1, "smp", "CLOSED")));
        assertEquals(2, store.ready("minecraft", "smp", 10, NOW.plusSeconds(1000)).size());
    }
}
