package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.protocol.ProtocolEnvelope;

final class PaperStaffModeHandoffHandler {
    static final String EXIT_REQUEST = "STAFF_MODE_HANDOFF_EXIT";
    static final String PREPARE_RESUME = "STAFF_MODE_HANDOFF_PREPARE";
    static final String ROLLBACK_RESUME = "STAFF_MODE_HANDOFF_ROLLBACK";
    static final String READY = "STAFF_MODE_READY";
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(8);

    interface Operations {
        CompletableFuture<Boolean> close(UUID playerId, UUID sessionId, long revision);

        boolean prepare(UUID playerId, UUID transferId);

        CompletableFuture<Boolean> rollback(UUID playerId, UUID transferId);
    }

    private final ObjectMapper json;
    private final Operations operations;

    PaperStaffModeHandoffHandler(ObjectMapper json, Operations operations) {
        this.json = java.util.Objects.requireNonNull(json, "json");
        this.operations = java.util.Objects.requireNonNull(operations, "operations");
    }

    static PaperStaffModeHandoffHandler forManager(ObjectMapper json, StaffModeManager manager) {
        java.util.Objects.requireNonNull(manager, "manager");
        return new PaperStaffModeHandoffHandler(json, new Operations() {
            @Override
            public CompletableFuture<Boolean> close(UUID playerId, UUID sessionId, long revision) {
                return manager.closeForBackendHandoff(playerId, sessionId, revision);
            }

            @Override
            public boolean prepare(UUID playerId, UUID transferId) {
                return manager.prepareBackendHandoffResume(playerId, transferId);
            }

            @Override
            public CompletableFuture<Boolean> rollback(UUID playerId, UUID transferId) {
                return manager.rollbackBackendHandoff(playerId, transferId);
            }
        });
    }

    boolean handles(ProtocolEnvelope envelope) {
        return switch (envelope.messageType()) {
            case EXIT_REQUEST, PREPARE_RESUME, ROLLBACK_RESUME -> true;
            default -> false;
        };
    }

    boolean handle(ProtocolEnvelope envelope) {
        try {
            JsonNode payload = json.readTree(envelope.payloadJson());
            return switch (envelope.messageType()) {
                case EXIT_REQUEST -> await(operations.close(
                        uuid(payload, "playerId"), uuid(payload, "sessionId"), payload.path("revision").asLong(-1L)));
                case PREPARE_RESUME -> operations.prepare(uuid(payload, "playerId"), uuid(payload, "transferId"));
                case ROLLBACK_RESUME -> await(operations.rollback(
                        uuid(payload, "playerId"), uuid(payload, "transferId")));
                default -> false;
            };
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    static String readyPayload(UUID playerId, UUID sessionId) {
        return "{\"playerId\":\"" + playerId + "\",\"sessionId\":\"" + sessionId + "\"}";
    }

    private static UUID uuid(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            throw new IllegalArgumentException("missing handoff field");
        }
        return UUID.fromString(payload.path(field).asText());
    }

    private static boolean await(CompletableFuture<Boolean> future) {
        try {
            return future.get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            return false;
        }
    }
}
