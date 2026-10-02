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

    record Participants(UUID viewerId, UUID targetId) {
        Participants {
            Objects.requireNonNull(viewerId, "viewerId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    record TargetSlot(ModerationInventoryHolder.Kind kind, int logicalSlot) {
        TargetSlot {
            Objects.requireNonNull(kind, "kind");
            if (logicalSlot < 0) {
                throw new IllegalArgumentException("logicalSlot cannot be negative");
            }
        }
    }

    record Images(InventoryImage before, InventoryImage replacement) {
        Images {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    record CursorChange(
            ItemStack expected,
            ItemStack resulting,
            LiveInventoryTransferDecision.Action action
    ) {
        CursorChange {
            expected = copy(expected);
            resulting = copy(resulting);
            Objects.requireNonNull(action, "action");
        }

        @Override
        public ItemStack expected() {
            return copy(expected);
        }

        @Override
        public ItemStack resulting() {
            return copy(resulting);
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

    private final UUID operationId;
    private final Participants participants;
    private final TargetSlot targetSlot;
    private final Images images;
    private final CursorChange cursor;
    private final Object stateLock = new Object();
    private InventoryPatch patch;
    private Phase phase = Phase.READY;

    LiveInventoryTransferExecution(
            UUID operationId,
            Participants participants,
            TargetSlot targetSlot,
            Images images,
            CursorChange cursor
    ) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.participants = Objects.requireNonNull(participants, "participants");
        this.targetSlot = Objects.requireNonNull(targetSlot, "targetSlot");
        this.images = Objects.requireNonNull(images, "images");
        this.cursor = Objects.requireNonNull(cursor, "cursor");
    }

    UUID operationId() {
        return operationId;
    }

    UUID viewerId() {
        return participants.viewerId();
    }

    UUID targetId() {
        return participants.targetId();
    }

    ModerationInventoryHolder.Kind kind() {
        return targetSlot.kind();
    }

    int logicalSlot() {
        return targetSlot.logicalSlot();
    }

    InventoryImage beforeImage() {
        return images.before();
    }

    InventoryImage replacementImage() {
        return images.replacement();
    }

    ItemStack expectedCursor() {
        return cursor.expected();
    }

    ItemStack resultingCursor() {
        return cursor.resulting();
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
        synchronized (stateLock) {
            return patch;
        }
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
            return abortLocked();
        }
    }

    private AbortResult abortLocked() {
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
        return item == null || item.getAmount() <= 0 ? null : item.clone();
    }
}
