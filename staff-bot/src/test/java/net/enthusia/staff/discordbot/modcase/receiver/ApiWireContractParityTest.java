package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline-only API-to-Java contract fixture. The JSONL is generated from the
 * Python ContextMonitor and CaseEventDeliveryCoordinator (not a hand-built
 * Java event) at API commit 7f91df3b7ff067793e6758c4ca457e7eb0b38fcc,
 * using synthetic CASE_CREATED, CLAIMED and CLOSED transitions.
 * The test peer is fake; this does NOT prove live transport authentication.
 */
class ApiWireContractParityTest {
    private static final String FIXTURE = "/modcase/api-generated-case-events.jsonl";
    private static final String CASE_ID = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final List<String> EXPECTED_STATES = List.of("OPEN", "CLAIMED", "CLOSED");
    private static final List<String> EXPECTED_TYPES = List.of(
            "CASE_CREATED", "CASE_STATE", "CASE_STATE");
    private static final Set<String> ACK_KEYS = Set.of(
            "status", "operation_id", "event_type", "case_id", "revision",
            "origin_platform", "scope_id");
    @TempDir Path directory;

    private List<String> apiWires() throws IOException {
        try (InputStream source = getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull(source, "synthetic API fixture is required");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> !line.isBlank()).toList();
        }
    }

    @Test
    void realApiEnvelopeAcceptsBirthClaimClosureAndExactAcks() throws Exception {
        List<String> lines = apiWires();
        assertEquals(3, lines.size());
        assertFalse(String.join("", lines).contains("SYNTHETIC CHAT NEVER DELIVER"));
        assertFalse(String.join("", lines).contains("staff:alice"));

        ReceiverJournal journal = new ReceiverJournal(directory.resolve("api-parity.json"));
        CaseReceiver receiver = new CaseReceiver(journal, "minecraft", "smp", 777L);
        CaseReceiver.Peer syntheticPeer = new CaseReceiver.Peer(
                true, "minecraft", "smp", 777L);
        for (int index = 0; index < lines.size(); index++) {
            String wire = lines.get(index);
            CaseEvent event = CaseEvent.parse(wire);
            assertEquals(CASE_ID, event.caseId());
            assertEquals(index, event.revision());
            assertEquals(EXPECTED_TYPES.get(index), event.eventType());
            assertEquals(EXPECTED_STATES.get(index), event.caseState());
            assertEquals(List.of("first"), event.evidenceRefs());

            CaseAck ack = receiver.receive(syntheticPeer, wire).orElseThrow();
            assertEquals("ACCEPTED", ack.status());
            assertEquals(ACK_KEYS, ack.toWire().keySet());
            assertEquals(event.operationId(), ack.operationId());
            assertEquals(event.revision(), ack.revision());
            assertEquals(event.caseId(), ack.caseId());
            assertEquals(event.originPlatform(), ack.originPlatform());
            assertEquals(event.scopeId(), ack.scopeId());
        }
        assertEquals("CLOSED", journal.cursor(CASE_ID).caseState());
        assertEquals(2L, journal.cursor(CASE_ID).revision());

        assertEquals("DUPLICATE",
                receiver.receive(syntheticPeer, lines.getFirst()).orElseThrow().status());
        assertTrue(receiver.receive(syntheticPeer, lines.getFirst()
                .replace("\"scope_id\":\"smp\"", "\"scope_id\":\"foreign\""))
                .isEmpty());
        assertEquals("CLOSED", journal.cursor(CASE_ID).caseState());
    }
}
