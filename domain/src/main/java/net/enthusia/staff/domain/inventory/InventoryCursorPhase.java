package net.enthusia.staff.domain.inventory;

/** Durable progress for the Staff-cursor half of an online shared-inventory transfer. */
public enum InventoryCursorPhase {
    PREPARED,
    SOURCE_ESCROWED,
    TARGET_APPLIED,
    CURSOR_APPLIED;

    public boolean canAdvanceTo(InventoryCursorPhase next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case PREPARED -> next == SOURCE_ESCROWED;
            case SOURCE_ESCROWED -> next == TARGET_APPLIED;
            case TARGET_APPLIED -> next == CURSOR_APPLIED;
            case CURSOR_APPLIED -> false;
        };
    }
}
