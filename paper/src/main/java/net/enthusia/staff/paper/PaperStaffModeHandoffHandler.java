package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import net.enthusia.staff.protocol.TransferSnapshotMessages;

final class PaperStaffModeHandoffHandler {
    static final String EXIT_REQUEST = "STAFF_MODE_HANDOFF_EXIT";
    static final String PREPARE_RESUME = "STAFF_MODE_HANDOFF_PREPARE";
    static final String ROLLBACK_RESUME = "STAFF_MODE_HANDOFF_ROLLBACK";
    static final String CANCEL_RESUME = "STAFF_MODE_HANDOFF_CANCEL";
    static final String ABORT_SOURCE = "STAFF_MODE_HANDOFF_ABORT_SOURCE";
    static final String READY = "STAFF_MODE_READY";
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(8);
    /**
     * Fail-open bound for the pre-transfer snapshot persist (overnight/cross-server). When the
     * source backend cannot finish persisting the staff snapshot within this bound, the
     * transfer proceeds on the in-memory transfer snapshot that was already uploaded to the
     * proxy, and the persist is left to the background retry machinery. The transfer itself
     * never waits longer than this for a database write.
     */
    private static final Duration EXIT_SNAPSHOT_FAILOPEN_TIMEOUT = Duration.ofSeconds(2);
    private static final String PLAYER_ID_FIELD = "playerId";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String TRANSFER_ID_FIELD = "transferId";

    /**
     * Cross-server transfer snapshot hook (overnight/cross-server). Implemented by
     * {@code StaffTransferSnapshotCoordinator}; may be {@code null} when transfer snapshots
     * are unavailable.
     */
    interface TransferSnapshotHook {
        /** Source side: capture the in-memory snapshot and upload it to the proxy. Never blocks. */
        void captureAndUpload(UUID playerId, UUID transferId);

        /** Destination side: stash a snapshot received inside a prepare payload. */
        void stashReceived(StaffTransferSnapshot snapshot);
    }

    interface Operations {
        CompletableFuture<Boolean> close(UUID playerId, UUID sessionId, long revision, UUID transferId);

        boolean abortSource(UUID playerId, UUID transferId);

        boolean prepare(UUID playerId, UUID transferId);

        boolean cancel(UUID playerId, UUID transferId);

        CompletableFuture<Boolean> rollback(UUID playerId, UUID transferId);
    }

    private final ObjectMapper json;
    private final Operations operations;
    private final TransferSnapshotHook transferSnapshots;
    private final Logger logger;

    PaperStaffModeHandoffHandler(ObjectMapper json, Operations operations) {
        this(json, operations, null, Logger.getLogger(PaperStaffModeHandoffHandler.class.getName()));
    }

    PaperStaffModeHandoffHandler(
            ObjectMapper json,
            Operations operations,
            TransferSnapshotHook transferSnapshots,
            Logger logger
    ) {
        this.json = java.util.Objects.requireNonNull(json, "json");
        this.operations = java.util.Objects.requireNonNull(operations, "operations");
        this.transferSnapshots = transferSnapshots;
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    static PaperStaffModeHandoffHandler forManager(ObjectMapper json, StaffModeManager manager) {
        java.util.Objects.requireNonNull(manager, "manager");
        return forManager(json, manager, null, Logger.getLogger(PaperStaffModeHandoffHandler.class.getName()));
    }

    static PaperStaffModeHandoffHandler forManager(
            ObjectMapper json,
            StaffModeManager manager,
            TransferSnapshotHook transferSnapshots,
            Logger logger
    ) {
        java.util.Objects.requireNonNull(manager, "manager");
        java.util.Objects.requireNonNull(logger, "logger");
        return new PaperStaffModeHandoffHandler(json, new Operations() {
            @Override
            public CompletableFuture<Boolean> close(
                    UUID playerId,
                    UUID sessionId,
                    long revision,
                    UUID transferId
            ) {
                return manager.closeForBackendHandoff(playerId, sessionId, revision, transferId);
            }

            @Override
            public boolean abortSource(UUID playerId, UUID transferId) {
                return manager.abortBackendHandoffSource(playerId, transferId);
            }

            @Override
            public boolean prepare(UUID playerId, UUID transferId) {
                return manager.prepareBackendHandoffResume(playerId, transferId);
            }

            @Override
            public boolean cancel(UUID playerId, UUID transferId) {
                return manager.cancelBackendHandoffResume(playerId, transferId);
            }

            @Override
            public CompletableFuture<Boolean> rollback(UUID playerId, UUID transferId) {
                return manager.rollbackBackendHandoff(playerId, transferId);
            }
        }, transferSnapshots, logger);
    }

    boolean handles(ProtocolEnvelope envelope) {
        return switch (envelope.messageType()) {
            case EXIT_REQUEST, PREPARE_RESUME, ROLLBACK_RESUME, CANCEL_RESUME, ABORT_SOURCE -> true;
            default -> false;
        };
    }

    boolean handle(ProtocolEnvelope envelope) {
        try {
            JsonNode payload = json.readTree(envelope.payloadJson());
            return switch (envelope.messageType()) {
                case EXIT_REQUEST -> handleExitRequest(payload);
                case ABORT_SOURCE -> operations.abortSource(
                        uuid(payload, PLAYER_ID_FIELD), uuid(payload, TRANSFER_ID_FIELD));
                case PREPARE_RESUME -> handlePrepareResume(payload);
                case CANCEL_RESUME -> operations.cancel(uuid(payload, PLAYER_ID_FIELD), uuid(payload, TRANSFER_ID_FIELD));
                case ROLLBACK_RESUME -> await(operations.rollback(
                        uuid(payload, PLAYER_ID_FIELD), uuid(payload, TRANSFER_ID_FIELD)));
                default -> false;
            };
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean handleExitRequest(JsonNode payload) {
        UUID playerId = uuid(payload, PLAYER_ID_FIELD);
        UUID transferId = uuid(payload, TRANSFER_ID_FIELD);
        if (transferSnapshots != null) {
            // Capture the in-memory snapshot BEFORE any database write, and upload it to the
            // proxy without blocking: the transfer must never wait for persistence.
            try {
                transferSnapshots.captureAndUpload(playerId, transferId);
            } catch (RuntimeException exception) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "Transfer snapshot capture threw for " + playerId + "; continuing with the close",
                            exception);
                }
            }
        }
        return awaitExitFailOpen(operations.close(
                playerId,
                uuid(payload, SESSION_ID_FIELD),
                payload.path("revision").asLong(-1L),
                transferId
        ), playerId);
    }

    private boolean handlePrepareResume(JsonNode payload) {
        UUID playerId = uuid(payload, PLAYER_ID_FIELD);
        UUID transferId = uuid(payload, TRANSFER_ID_FIELD);
        boolean prepared = operations.prepare(playerId, transferId);
        if (prepared && transferSnapshots != null && payload.has(TransferSnapshotMessages.PAYLOAD_FIELD)) {
            try {
                StaffTransferSnapshot snapshot = TransferSnapshotMessages.decodeNode(
                        payload.path(TransferSnapshotMessages.PAYLOAD_FIELD));
                if (snapshot != null) {
                    transferSnapshots.stashReceived(snapshot);
                }
            } catch (RuntimeException exception) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "Ignoring invalid transfer snapshot in prepare payload for " + playerId, exception);
                }
            }
        }
        return prepared;
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
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(false);
            return false;
        } catch (java.util.concurrent.ExecutionException exception) {
            return false;
        }
    }

    /**
     * Waits for the source-side close with the fail-open bound (overnight/cross-server).
     * If the snapshot persist cannot complete within
     * {@link #EXIT_SNAPSHOT_FAILOPEN_TIMEOUT}, the close is acknowledged anyway so the
     * transfer proceeds on the in-memory snapshot already uploaded to the proxy; the
     * close future is deliberately <em>not</em> cancelled so its persist keeps running in
     * the background retry machinery. Loud logging marks every fail-open occurrence.
     */
    private boolean awaitExitFailOpen(CompletableFuture<Boolean> future, UUID playerId) {
        try {
            return future.get(EXIT_SNAPSHOT_FAILOPEN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.TimeoutException exception) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE,
                        "CROSS-SERVER TRANSFER FAIL-OPEN for player " + playerId
                                + ": the staff snapshot persist did not complete within "
                                + EXIT_SNAPSHOT_FAILOPEN_TIMEOUT.toSeconds()
                                + "s, so the transfer is proceeding on the in-memory transfer snapshot. "
                                + "The persist was NOT cancelled and continues in the background retry queue; "
                                + "verify staff state on both backends if this repeats.");
            }
            return true;
        } catch (java.util.concurrent.ExecutionException exception) {
            return false;
        }
    }
}
