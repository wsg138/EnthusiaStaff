package net.enthusia.staff.domain.inventory;

public enum InventoryPatchDecision {
    APPLY_REPLACEMENT,
    FINALIZE_ALREADY_APPLIED,
    QUARANTINE_CONFLICT;

    public static InventoryPatchDecision decide(
            String currentChecksum,
            String expectedChecksum,
            String replacementChecksum
    ) {
        currentChecksum = InventoryObservation.requireChecksum(currentChecksum);
        expectedChecksum = InventoryObservation.requireChecksum(expectedChecksum);
        replacementChecksum = InventoryObservation.requireChecksum(replacementChecksum);
        if (currentChecksum.equals(expectedChecksum)) {
            return APPLY_REPLACEMENT;
        }
        if (currentChecksum.equals(replacementChecksum)) {
            return FINALIZE_ALREADY_APPLIED;
        }
        return QUARANTINE_CONFLICT;
    }
}
