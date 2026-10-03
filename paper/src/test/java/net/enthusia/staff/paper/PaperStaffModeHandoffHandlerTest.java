package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class PaperStaffModeHandoffHandlerTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRANSFER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final long REVISION = 7L;

    @Test
    void exitRequestPassesExpectedSessionFence() {
        RecordingOperations operations = new RecordingOperations();
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(new ObjectMapper(), operations);

        assertTrue(handler.handle(envelope(
                PaperStaffModeHandoffHandler.EXIT_REQUEST,
                exitPayload()
        )));
        assertTrue(operations.closed);
    }

    @Test
    void prepareAndRollbackUseTransferIdentity() {
        RecordingOperations operations = new RecordingOperations();
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(new ObjectMapper(), operations);
        String payload = transferPayload();

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
                transferPayload()
        )));
    }

    @Test
    void timedOutExitIsFailOpen() {
        RecordingOperations operations = new RecordingOperations();
        ImmediateTimeoutFuture future = new ImmediateTimeoutFuture();
        operations.closeFuture = future;
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(new ObjectMapper(), operations);

        assertTrue(handler.handle(envelope(
                PaperStaffModeHandoffHandler.EXIT_REQUEST,
                exitPayload()
        )));
        assertFalse(future.isCancelled());
    }

    @Test
    void unrelatedMessagesAreNotClaimed() {
        PaperStaffModeHandoffHandler handler = new PaperStaffModeHandoffHandler(
                new ObjectMapper(), new RecordingOperations());

        assertFalse(handler.handles(envelope("FREEZE_CHANGED", "{}")));
    }

    private static String transferPayload() {
        return "{\"playerId\":\"" + PLAYER + "\",\"transferId\":\"" + TRANSFER + "\"}";
    }

    private static String exitPayload() {
        return "{\"playerId\":\"" + PLAYER + "\",\"sessionId\":\"" + SESSION
                + "\",\"revision\":" + REVISION + ",\"transferId\":\"" + TRANSFER + "\"}";
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

    private static final class ImmediateTimeoutFuture extends CompletableFuture<Boolean> {
        @Override
        public Boolean get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new TimeoutException("synthetic timeout");
        }
    }

    private static final class RecordingOperations implements PaperStaffModeHandoffHandler.Operations {
        private boolean accept = true;
        private boolean closed;
        private CompletableFuture<Boolean> closeFuture;
        private final AtomicBoolean prepared = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean rolledBack = new AtomicBoolean();

        @Override
        public CompletableFuture<Boolean> close(
                UUID playerId,
                UUID sessionId,
                long revision,
                UUID transferId
        ) {
            closed = PLAYER.equals(playerId) && SESSION.equals(sessionId)
                    && revision == REVISION && TRANSFER.equals(transferId);
            return closeFuture == null
                    ? CompletableFuture.completedFuture(accept && closed)
                    : closeFuture;
        }

        @Override
        public boolean abortSource(UUID playerId, UUID transferId) {
            return accept && PLAYER.equals(playerId) && TRANSFER.equals(transferId);
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
