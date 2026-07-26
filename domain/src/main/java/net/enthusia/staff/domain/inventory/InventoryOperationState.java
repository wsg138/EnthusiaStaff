package net.enthusia.staff.domain.inventory;

public enum InventoryOperationState {
    PREPARED,
    LOCKED,
    SNAPSHOT_SAVED,
    VALIDATED,
    PENDING,
    APPLYING,
    APPLIED,
    COMMITTED,
    CONFLICT,
    QUARANTINED,
    RESTORED
}
