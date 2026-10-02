package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes one target's reconciliation and cursor-transfer work. */
final class LiveInventorySessionGate {
    private final AtomicBoolean working = new AtomicBoolean();
    private volatile LiveInventoryTransferExecution activeTransfer;

    synchronized boolean beginEdit(LiveInventoryTransferExecution transfer) {
        Objects.requireNonNull(transfer, "transfer");
        if (!working.compareAndSet(false, true)) {
            return false;
        }
        activeTransfer = transfer;
        return true;
    }

    boolean beginWork() {
        return working.compareAndSet(false, true);
    }

    synchronized void finishTransfer(LiveInventoryTransferExecution transfer) {
        if (activeTransfer == transfer) {
            activeTransfer = null;
        }
        working.set(false);
    }

    void finishWork() {
        working.set(false);
    }

    LiveInventoryTransferExecution activeTransfer() {
        return activeTransfer;
    }

    boolean working() {
        return working.get();
    }
}
