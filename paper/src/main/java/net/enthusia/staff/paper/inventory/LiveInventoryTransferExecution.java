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

    record Identity(UUID operationId, UUID viewerId, UUID targetId) {
        Identity {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(viewerId, "viewerId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    record TargetMutation(
            ModerationInventoryHolder.Kind kind,
            int logicalSlot,
            InventoryImage beforeImage,
            InventoryImage replacementImage
    ) {
        TargetMutation {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(beforeImage, "beforeImage");
            Objects.requireNonNull(replacementImage, "replacementImage");
        }
    }

    record CursorMutation(
            ItemStack expectedCursor,
            ItemStack resultingCursor,
            LiveInventoryTransferDecision.Action action
    ) {
        CursorMutation {
            expectedCursor = copy(expectedCursor);
            resultingCursor = copy(resultingCursor);
            Objects.requireNonNull(action, "action");
        }

        @Override
        public ItemStack expectedCursor() {
            return copy(expectedCursor);
        }

        @Override
        public ItemStack resultingCursor() {
            return copy(resultingCursor);
        }
    }

    private enum Phase {
        READY,
        TARGET_APPLIED,
        CURSOR_APPLIED,
        COMMITTED,
        ABORTED,
        ROLLED_BACK
    }

    private final Identity identity;
    private final TargetMutation target;
    private final CursorMutation cursor;
    private final Object stateLock = new Object();
    private volatile InventoryPatch patch;
    private Phase phase = Phase.READY;

    LiveInventoryTransferExecution(
            Identity identity,
            TargetMutation target,
            CursorMutation cursor
    ) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.target = Objects.requireNonNull(target, "target");
        this.cursor = Objects.requireNonNull(cursor, "cursor");
    }

    UUID operationId() {
        return identity.operationId();
    }

    UUID viewerId() {
        return identity.viewerId();
    }

    UUID targetId() {
        return identity.targetId();
    }

    ModerationInventoryHolder.Kind kind() {
        return target.kind();
    }

    int logicalSlot() {
        return target.logicalSlot();
    }

    InventoryImage beforeImage() {
        return target.beforeImage();
    }

    InventoryImage replacementImage() {
        return target.replacementImage();
    }

    ItemStack expectedCursor() {
        return cursor.expectedCursor();
    }

    ItemStack resultingCursor() {
        return cursor.resultingCursor();
    }

    LiveInventoryTransferDecision.Action action() {
        return cursor.action();
    }

    void patch(InventoryPatch preparedPatch) {
        InventoryPatch next = Objects.requireNonNull(preparedPatch, "preparedPatch");
        synchronized (stateLock) {
            if (patch != null && !patch.patchId().equals(next.patchId())) {
                throw new IllegalStateException("live transfer already owns another durable patch");
            }
            patch = next;
        }
    }

    InventoryPatch patch() {
        return patch;
    }

    boolean applyTarget(Mutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        synchronized (stateLock) {
            if (phase != Phase.READY || !mutation.apply()) {
                return false;
            }
            phase = Phase.TARGET_APPLIED;
            return true;
        }
    }

    boolean applyCursor(Mutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        synchronized (stateLock) {
            if (phase != Phase.TARGET_APPLIED || !mutation.apply()) {
                return false;
            }
            phase = Phase.CURSOR_APPLIED;
            return true;
        }
    }

    AbortResult abort() {
        synchronized (stateLock) {
            return switch (phase) {
                case READY -> abortReady();
                case TARGET_APPLIED -> abortTargetApplied();
                case CURSOR_APPLIED, COMMITTED -> AbortResult.PHYSICAL_TRANSFER_COMPLETE;
                case ABORTED, ROLLED_BACK -> AbortResult.NO_TARGET_CHANGE;
            };
        }
    }

    private AbortResult abortReady() {
        phase = Phase.ABORTED;
        return AbortResult.NO_TARGET_CHANGE;
    }

    private AbortResult abortTargetApplied() {
        phase = Phase.ABORTED;
        return AbortResult.ROLLBACK_TARGET;
    }

    void rolledBack() {
        synchronized (stateLock) {
            if (phase != Phase.ABORTED) {
                throw new IllegalStateException("rollback requires an aborted live transfer");
            }
            phase = Phase.ROLLED_BACK;
        }
    }

    void committed() {
        synchronized (stateLock) {
            if (phase != Phase.CURSOR_APPLIED) {
                throw new IllegalStateException("durable commit requires both physical mutations");
            }
            phase = Phase.COMMITTED;
        }
    }

    boolean physicalTransferComplete() {
        synchronized (stateLock) {
            return phase == Phase.CURSOR_APPLIED || phase == Phase.COMMITTED;
        }
    }

    boolean targetAppliedWithoutCursor() {
        synchronized (stateLock) {
            return phase == Phase.TARGET_APPLIED;
        }
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }
}
