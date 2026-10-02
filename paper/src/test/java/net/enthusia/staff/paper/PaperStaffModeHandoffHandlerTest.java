package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class PaperStaffModeHandoffHandlerTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRANSFER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void exitRequestPassesExpectedSessionFence() {
        RecordingOperations operations = new RecordingOperations();
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(new ObjectMapper(), operations);

        assertTrue(handler.handle(envelope(
                PaperStaffModeHandoffHandler.EXIT_REQUEST,
                "{\"playerId\":\"" + PLAYER + "\",\"sessionId\":\"" + SESSION + "\",\"revision\":7}"
        )));
        assertTrue(operations.closed);
    }

    @Test
    void prepareAndRollbackUseTransferIdentity() {
        RecordingOperations operations = new RecordingOperations();
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(new ObjectMapper(), operations);
        String payload = "{\"playerId\":\"" + PLAYER + "\",\"transferId\":\"" + TRANSFER + "\"}";

        assertTrue(handler.handle(envelope(PaperStaffModeHandoffHandler.PREPARE_RESUME, payload)));
        assertTrue(handler.handle(envelope(PaperStaffModeHandoffHandler.CANCEL_RESUME, payload)));
        assertTrue(handler.handle(envelope(PaperStaffModeHandoffHandler.ROLLBACK_RESUME, payload)));
        assertTrue(operations.prepared.get());
        assertTrue(operations.cancelled.get());
        assertTrue(operations.rolledBack.get());
    }

    @Test
    void malformedOrRejectedCommandsAreNotAcknowledged() {
        RecordingOperations operations = new RecordingOperations();
        operations.accept = false;
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(new ObjectMapper(), operations);

        assertFalse(handler.handle(envelope(PaperStaffModeHandoffHandler.PREPARE_RESUME, "{}")));
        assertFalse(handler.handle(envelope(
                PaperStaffModeHandoffHandler.PREPARE_RESUME,
                "{\"playerId\":\"" + PLAYER + "\",\"transferId\":\"" + TRANSFER + "\"}"
        )));
    }

    @Test
    void unrelatedMessagesAreNotClaimed() {
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(
                new ObjectMapper(), new RecordingOperations());

        assertFalse(handler.handles(envelope("FREEZE_CHANGED", "{}")));
    }

    private static ProtocolEnvelope envelope(String type, String payload) {
        return new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                "VELOCITY",
                type,
                1L,
                "nonce",
                payload,
                "mac"
        );
    }

    private static final class RecordingOperations implements PaperStaffModeHandoffHandler.Operations {
        private boolean accept = true;
        private boolean closed;
        private final AtomicBoolean prepared = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean rolledBack = new AtomicBoolean();

        @Override
        public CompletableFuture<Boolean> close(UUID playerId, UUID sessionId, long revision) {
            closed = PLAYER.equals(playerId) && SESSION.equals(sessionId) && revision == 7L;
            return CompletableFuture.completedFuture(accept && closed);
        }

        @Override
        public boolean prepare(UUID playerId, UUID transferId) {
            prepared.set(PLAYER.equals(playerId) && TRANSFER.equals(transferId));
            return accept && prepared.get();
        }

        @Override
        public boolean cancel(UUID playerId, UUID transferId) {
            cancelled.set(PLAYER.equals(playerId) && TRANSFER.equals(transferId));
            return accept && cancelled.get();
        }

        @Override
        public CompletableFuture<Boolean> rollback(UUID playerId, UUID transferId) {
            rolledBack.set(PLAYER.equals(playerId) && TRANSFER.equals(transferId));
            return CompletableFuture.completedFuture(accept && rolledBack.get());
        }
    }
}
