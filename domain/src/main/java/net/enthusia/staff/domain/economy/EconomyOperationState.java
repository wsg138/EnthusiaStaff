package net.enthusia.staff.domain.economy;

public enum EconomyOperationState {
    PREPARED,
    LOCKED,
    SNAPSHOT_SAVED,
    VALIDATED,
    APPLYING,
    COMMITTED,
    ROLLED_BACK,
    QUARANTINED,
    UNLOCKED;

    public boolean terminalBeforeUnlock() {
        return this == COMMITTED || this == ROLLED_BACK || this == QUARANTINED;
    }

    public boolean released() {
        return this == UNLOCKED;
    }
}
