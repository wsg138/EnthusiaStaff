package net.enthusia.staff.domain.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryCursorJournal;
import net.enthusia.staff.domain.inventory.InventoryCursorPhase;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetReservation;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStart;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.common.CaseId;

public interface InventoryJournalStore {
    InventoryConfiscationStart beginConfiscation(
            InventoryConfiscationStartRequest request,
            Duration leaseDuration,
            Instant now
    );

    Optional<InventoryConfiscationSession> renewConfiscation(
            UUID operationId,
            long fencingToken,
            Duration leaseDuration,
            Instant now
    );

    InventoryPreparation prepareConfiscation(
            InventoryConfiscationCommitRequest request,
            Instant now
    );

    boolean cancelConfiscation(
            UUID operationId,
            long fencingToken,
            String reasonCode,
            String detail,
            Instant now
    );

    int cancelAbandonedConfiscations(
            UUID playerId,
            String scopeId,
            String owningServerId,
            Instant now
    );

    ConfiscatedAssetReservation reserveRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            Instant now
    );

    boolean cancelRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            Instant now
    );

    boolean finalizeRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            String restoredChecksum,
            Instant now
    );

    InventoryObservation recordObservation(
            UUID playerId,
            String scopeId,
            String owningServerId,
            String checksum,
            byte[] snapshot,
            Instant observedAt
    );

    Optional<InventoryObservation> latest(UUID playerId, String scopeId);

    InventoryPreparation prepare(
            InventoryPrepareRequest request,
            Duration leaseDuration,
            Instant now
    );

    List<InventoryPatch> pending(UUID playerId, String scopeId, String owningServerId, int limit);

    default List<InventoryCursorJournal> pendingCursorTransfersByActor(
            UUID actorId,
            String owningServerId,
            int limit
    ) {
        throw new UnsupportedOperationException("cursor escrow lookup is not supported by this store");
    }

    default Optional<InventoryCursorJournal> cursorTransfer(UUID operationId) {
        throw new UnsupportedOperationException("cursor escrow lookup is not supported by this store");
    }

    Optional<InventoryPatch> claimForApply(
            UUID patchId,
            UUID operationId,
            Duration leaseDuration,
            Instant now
    );

    default boolean advanceCursorPhase(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            InventoryCursorPhase expected,
            InventoryCursorPhase next,
            Instant now
    ) {
        throw new UnsupportedOperationException("cursor escrow transitions are not supported by this store");
    }

    default boolean resolveCursorRollback(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            Instant now
    ) {
        throw new UnsupportedOperationException("cursor rollback resolution is not supported by this store");
    }

    InventoryFinalizeResult finalizeApplied(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            String observedChecksum,
            byte[] observedSnapshot,
            Instant now
    );

    void quarantine(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            String reasonCode,
            String detail,
            Instant now
    );

    boolean isLocked(UUID playerId, String scopeId, Instant now);

    Optional<String> lockedOwningServer(UUID playerId, Instant now);
}
