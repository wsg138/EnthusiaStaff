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
import net.enthusia.staff.domain.ports.FakeBaseAuditStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterJournalStart;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;
import net.enthusia.staff.domain.tester.FakeBaseAuditEvent;

/**
 * Publishes one asset binding while exposing inventory, cheat-tester recovery, and
 * coordinate-free fake-base audit ports. A durable ACTIVE tester row participates in
 * the inventory lock contract so offline edits cannot race exact tester recovery.
 */
public final class CompositeInventoryTesterJournalStore
        implements InventoryJournalStore, CheatTesterJournalStore, FakeBaseAuditStore {
    private final InventoryJournalStore inventory;
    private final CheatTesterJournalStore testers;
    private final FakeBaseAuditStore fakeBaseAudits;

    public CompositeInventoryTesterJournalStore(
            InventoryJournalStore inventory,
            CheatTesterJournalStore testers,
            FakeBaseAuditStore fakeBaseAudits
    ) {
        this.inventory = java.util.Objects.requireNonNull(inventory, "inventory");
        this.testers = java.util.Objects.requireNonNull(testers, "testers");
        this.fakeBaseAudits = java.util.Objects.requireNonNull(fakeBaseAudits, "fakeBaseAudits");
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
        if (inventory.isLocked(playerId, scopeId, now)) {
            return true;
        }
        return testers.activeForTarget(playerId).isPresent();
    }

    @Override
    public Optional<String> lockedOwningServer(UUID playerId, Instant now) {
        Optional<String> inventoryOwner = inventory.lockedOwningServer(playerId, now);
        if (inventoryOwner.isPresent()) {
            return inventoryOwner;
        }
        return testers.activeForTarget(playerId).map(CheatTesterJournalRecord::serverId);
    }

    @Override
    public CheatTesterJournalRecord start(CheatTesterJournalStart start) {
        return testers.start(start);
    }

    @Override
    public Optional<CheatTesterJournalRecord> activeForTarget(UUID targetId) {
        return testers.activeForTarget(targetId);
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

    @Override
    public void record(FakeBaseAuditEvent event) {
        fakeBaseAudits.record(event);
    }
}
