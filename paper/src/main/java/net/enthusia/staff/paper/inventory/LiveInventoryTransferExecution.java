package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import org.bukkit.inventory.ItemStack;

/**
 * Serializes the physical half of one live target/cursor transfer. Mutations are
 * invoked only by the owning Paper entity scheduler supplied by the coordinator.
 */
final class LiveInventoryTransferExecution {
    enum AbortResult {
        NO_TARGET_CHANGE,
        ROLLBACK_TARGET,
        PHYSICAL_TRANSFER_COMPLETE
    }

    @FunctionalInterface
    interface Mutation {
        boolean apply();
    }

    private enum Phase {
        READY,
        TARGET_APPLIED,
        CURSOR_APPLIED,
        COMMITTED,
        ABORTED,
        ROLLED_BACK
    }

    private final UUID operationId;
    private final UUID viewerId;
    private final UUID targetId;
    private final ModerationInventoryHolder.Kind kind;
    private final int logicalSlot;
    private final InventoryImage beforeImage;
    private final InventoryImage replacementImage;
    private final ItemStack expectedCursor;
    private final ItemStack resultingCursor;
    private final LiveInventoryTransferDecision.Action action;
    private volatile InventoryPatch patch;
    private Phase phase = Phase.READY;

    LiveInventoryTransferExecution(
            UUID operationId,
            UUID viewerId,
            UUID targetId,
            ModerationInventoryHolder.Kind kind,
            int logicalSlot,
            InventoryImage beforeImage,
            InventoryImage replacementImage,
            ItemStack expectedCursor,
            ItemStack resultingCursor,
            LiveInventoryTransferDecision.Action action
    ) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.logicalSlot = logicalSlot;
        this.beforeImage = Objects.requireNonNull(beforeImage, "beforeImage");
        this.replacementImage = Objects.requireNonNull(replacementImage, "replacementImage");
        this.expectedCursor = copy(expectedCursor);
        this.resultingCursor = copy(resultingCursor);
        this.action = Objects.requireNonNull(action, "action");
    }

    UUID operationId() {
        return operationId;
    }

    UUID viewerId() {
        return viewerId;
    }

    UUID targetId() {
        return targetId;
    }

    ModerationInventoryHolder.Kind kind() {
        return kind;
    }

    int logicalSlot() {
        return logicalSlot;
    }

    InventoryImage beforeImage() {
        return beforeImage;
    }

    InventoryImage replacementImage() {
        return replacementImage;
    }

    ItemStack expectedCursor() {
        return copy(expectedCursor);
    }

    ItemStack resultingCursor() {
        return copy(resultingCursor);
    }

    LiveInventoryTransferDecision.Action action() {
        return action;
    }

    void patch(InventoryPatch preparedPatch) {
        InventoryPatch next = Objects.requireNonNull(preparedPatch, "preparedPatch");
        synchronized (this) {
            if (patch != null && !patch.patchId().equals(next.patchId())) {
                throw new IllegalStateException("live transfer already owns another durable patch");
            }
            patch = next;
        }
    }

    InventoryPatch patch() {
        return patch;
    }

    synchronized boolean applyTarget(Mutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (phase != Phase.READY || !mutation.apply()) {
            return false;
        }
        phase = Phase.TARGET_APPLIED;
        return true;
    }

    synchronized boolean applyCursor(Mutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (phase != Phase.TARGET_APPLIED || !mutation.apply()) {
            return false;
        }
        phase = Phase.CURSOR_APPLIED;
        return true;
    }

    synchronized AbortResult abort() {
        return switch (phase) {
            case READY -> {
                phase = Phase.ABORTED;
                yield AbortResult.NO_TARGET_CHANGE;
            }
            case TARGET_APPLIED -> {
                phase = Phase.ABORTED;
                yield AbortResult.ROLLBACK_TARGET;
            }
            case CURSOR_APPLIED, COMMITTED -> AbortResult.PHYSICAL_TRANSFER_COMPLETE;
            case ABORTED, ROLLED_BACK -> AbortResult.NO_TARGET_CHANGE;
        };
    }

    synchronized void rolledBack() {
        if (phase != Phase.ABORTED) {
            throw new IllegalStateException("rollback requires an aborted live transfer");
        }
        phase = Phase.ROLLED_BACK;
    }

    synchronized void committed() {
        if (phase != Phase.CURSOR_APPLIED) {
            throw new IllegalStateException("durable commit requires both physical mutations");
        }
        phase = Phase.COMMITTED;
    }

    synchronized boolean physicalTransferComplete() {
        return phase == Phase.CURSOR_APPLIED || phase == Phase.COMMITTED;
    }

    synchronized boolean targetAppliedWithoutCursor() {
        return phase == Phase.TARGET_APPLIED;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }
}
