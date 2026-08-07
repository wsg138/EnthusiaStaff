package net.enthusia.staff.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetReservation;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStart;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.ports.CheatTesterJournalStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterJournalStart;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;

/**
 * Publishes the already-existing inventory storage binding while also exposing the dedicated V18
 * cheat-tester journal port. This keeps storage bootstrap atomic without coupling tester rows to
 * inventory patch/confiscation tables.
 */
public final class CompositeInventoryTesterJournalStore implements InventoryJournalStore, CheatTesterJournalStore {
    private final InventoryJournalStore inventory;
    private final CheatTesterJournalStore testers;

    public CompositeInventoryTesterJournalStore(
            InventoryJournalStore inventory,
            CheatTesterJournalStore testers
    ) {
        this.inventory = java.util.Objects.requireNonNull(inventory, "inventory");
        this.testers = java.util.Objects.requireNonNull(testers, "testers");
    }

    @Override
    public InventoryConfiscationStart beginConfiscation(
            InventoryConfiscationStartRequest request,
            Duration leaseDuration,
            Instant now
    ) {
        return inventory.beginConfiscation(request, leaseDuration, now);
    }

    @Override
    public Optional<InventoryConfiscationSession> renewConfiscation(
            UUID operationId,
            long fencingToken,
            Duration leaseDuration,
            Instant now
    ) {
        return inventory.renewConfiscation(operationId, fencingToken, leaseDuration, now);
    }

    @Override
    public InventoryPreparation prepareConfiscation(
            InventoryConfiscationCommitRequest request,
            Instant now
    ) {
        return inventory.prepareConfiscation(request, now);
    }

    @Override
    public boolean cancelConfiscation(
            UUID operationId,
            long fencingToken,
            String reasonCode,
            String detail,
            Instant now
    ) {
        return inventory.cancelConfiscation(operationId, fencingToken, reasonCode, detail, now);
    }

    @Override
    public int cancelAbandonedConfiscations(
            UUID playerId,
            String scopeId,
            String owningServerId,
            Instant now
    ) {
        return inventory.cancelAbandonedConfiscations(playerId, scopeId, owningServerId, now);
    }

    @Override
    public ConfiscatedAssetReservation reserveRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            Instant now
    ) {
        return inventory.reserveRestoration(caseId, restorationOperationId, now);
    }

    @Override
    public boolean cancelRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            Instant now
    ) {
        return inventory.cancelRestoration(caseId, restorationOperationId, now);
    }

    @Override
    public boolean finalizeRestoration(
            CaseId caseId,
            UUID restorationOperationId,
            String restoredChecksum,
            Instant now
    ) {
        return inventory.finalizeRestoration(caseId, restorationOperationId, restoredChecksum, now);
    }

    @Override
    public InventoryObservation recordObservation(
            UUID playerId,
            String scopeId,
            String owningServerId,
            String checksum,
            byte[] snapshot,
            Instant observedAt
    ) {
        return inventory.recordObservation(playerId, scopeId, owningServerId, checksum, snapshot, observedAt);
    }

    @Override
    public Optional<InventoryObservation> latest(UUID playerId, String scopeId) {
        return inventory.latest(playerId, scopeId);
    }

    @Override
    public InventoryPreparation prepare(
            InventoryPrepareRequest request,
            Duration leaseDuration,
            Instant now
    ) {
        return inventory.prepare(request, leaseDuration, now);
    }

    @Override
    public List<InventoryPatch> pending(UUID playerId, String scopeId, String owningServerId, int limit) {
        return inventory.pending(playerId, scopeId, owningServerId, limit);
    }

    @Override
    public Optional<InventoryPatch> claimForApply(
            UUID patchId,
            UUID operationId,
            Duration leaseDuration,
            Instant now
    ) {
        return inventory.claimForApply(patchId, operationId, leaseDuration, now);
    }

    @Override
    public InventoryFinalizeResult finalizeApplied(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            String observedChecksum,
            byte[] observedSnapshot,
            Instant now
    ) {
        return inventory.finalizeApplied(
                patchId,
                operationId,
                fencingToken,
                observedChecksum,
                observedSnapshot,
                now
        );
    }

    @Override
    public void quarantine(
            UUID patchId,
            UUID operationId,
            long fencingToken,
            String reasonCode,
            String detail,
            Instant now
    ) {
        inventory.quarantine(patchId, operationId, fencingToken, reasonCode, detail, now);
    }

    @Override
    public boolean isLocked(UUID playerId, String scopeId, Instant now) {
        return inventory.isLocked(playerId, scopeId, now);
    }

    @Override
    public Optional<String> lockedOwningServer(UUID playerId, Instant now) {
        return inventory.lockedOwningServer(playerId, now);
    }

    @Override
    public CheatTesterJournalRecord start(CheatTesterJournalStart start) {
        return testers.start(start);
    }

    @Override
    public Optional<CheatTesterJournalRecord> activeForTarget(String serverId, UUID targetId) {
        return testers.activeForTarget(serverId, targetId);
    }

    @Override
    public List<CheatTesterJournalRecord> activeForServer(String serverId, int limit) {
        return testers.activeForServer(serverId, limit);
    }

    @Override
    public Optional<CheatTesterJournalRecord> checkpointEvidence(
            UUID sessionId,
            long expectedRevision,
            String evidence,
            Instant now
    ) {
        return testers.checkpointEvidence(sessionId, expectedRevision, evidence, now);
    }

    @Override
    public boolean complete(
            UUID sessionId,
            long expectedRevision,
            CheatTesterSessionState terminalState,
            String reason,
            String evidence,
            Instant now
    ) {
        return testers.complete(sessionId, expectedRevision, terminalState, reason, evidence, now);
    }
}
