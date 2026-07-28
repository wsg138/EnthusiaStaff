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
        String validatedCurrent = InventoryObservation.requireChecksum(currentChecksum);
        String validatedExpected = InventoryObservation.requireChecksum(expectedChecksum);
        String validatedReplacement = InventoryObservation.requireChecksum(replacementChecksum);
        if (validatedCurrent.equals(validatedExpected)) {
            return APPLY_REPLACEMENT;
        }
        if (validatedCurrent.equals(validatedReplacement)) {
            return FINALIZE_ALREADY_APPLIED;
        }
        return QUARANTINE_CONFLICT;
    }
}
