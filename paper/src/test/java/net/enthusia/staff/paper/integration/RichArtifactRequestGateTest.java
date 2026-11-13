package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.enthusia.staff.api.chat.RichChatArtifact;
import org.junit.jupiter.api.Test;

class RichArtifactRequestGateTest {
    @Test
    void closeImmediatelyCompletesQueuedRequestsAsTextOnly() {
        RichArtifactRequestGate gate = new RichArtifactRequestGate();
        CompletableFuture<List<RichChatArtifact>> first = gate.begin();
        CompletableFuture<List<RichChatArtifact>> second = gate.begin();
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        assertTrue(gate.close());
        assertTrue(gate.isClosed());
        assertEquals(List.of(), first.join());
        assertEquals(List.of(), second.join());
        assertFalse(gate.close());
    }

    @Test
    void lateWorkerOrSchedulerCannotOverwriteShutdownFallback() {
        RichArtifactRequestGate gate = new RichArtifactRequestGate();
        CompletableFuture<List<RichChatArtifact>> queued = gate.begin();
        gate.close();

        assertFalse(queued.complete(List.of()));
        assertEquals(List.of(), queued.join());
        assertTrue(gate.begin().isDone());
        assertEquals(List.of(), gate.begin().join());
    }

    @Test
    void completedRequestStaysCompletedAcrossProviderClose() {
        RichArtifactRequestGate gate = new RichArtifactRequestGate();
        CompletableFuture<List<RichChatArtifact>> sent = gate.begin();
        assertTrue(sent.complete(List.of()));
        assertTrue(gate.close());
        assertEquals(List.of(), sent.join());
    }
}
