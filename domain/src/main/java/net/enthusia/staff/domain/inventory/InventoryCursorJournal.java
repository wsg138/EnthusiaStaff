package net.enthusia.staff.domain.inventory;

import java.util.Objects;

/** Durable target-patch plus Staff-cursor escrow state for one online shared-inventory transfer. */
public record InventoryCursorJournal(
        InventoryPatch patch,
        byte[] beforeSnapshot,
        InventoryCursorTransfer cursorTransfer,
        InventoryCursorPhase phase
) {
    public InventoryCursorJournal {
        Objects.requireNonNull(patch, "patch");
        beforeSnapshot = Objects.requireNonNull(beforeSnapshot, "beforeSnapshot").clone();
        Objects.requireNonNull(cursorTransfer, "cursorTransfer");
        Objects.requireNonNull(phase, "phase");
    }

    @Override
    public byte[] beforeSnapshot() {
        return beforeSnapshot.clone();
    }
}
