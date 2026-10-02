package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Serializes one target's reconciliation and cursor-transfer work. */
final class LiveInventorySessionGate {
    private final AtomicBoolean working = new AtomicBoolean();
    private final AtomicReference<LiveInventoryTransferExecution> activeTransfer = new AtomicReference<>();

    boolean beginEdit(LiveInventoryTransferExecution transfer) {
        LiveInventoryTransferExecution next = Objects.requireNonNull(transfer, "transfer");
        if (!working.compareAndSet(false, true)) {
            return false;
        }
        activeTransfer.set(next);
        return true;
    }

    boolean beginWork() {
        return working.compareAndSet(false, true);
    }

    void finishTransfer(LiveInventoryTransferExecution transfer) {
        if (activeTransfer.compareAndSet(transfer, null)) {
            working.set(false);
        }
    }

    void finishWork() {
        working.set(false);
    }

    LiveInventoryTransferExecution activeTransfer() {
        return activeTransfer.get();
    }

    boolean working() {
        return working.get();
    }
}
