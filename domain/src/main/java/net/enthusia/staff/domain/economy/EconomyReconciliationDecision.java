package net.enthusia.staff.domain.economy;

public enum EconomyReconciliationDecision {
    ROLL_BACK_UNAPPLIED,
    COMMIT_ALREADY_APPLIED,
    RESTORE_BEFORE_STATE;

    public static EconomyReconciliationDecision decide(
            String currentChecksum,
            String beforeChecksum,
            String replacementChecksum
    ) {
        String validatedCurrent = EconomyPrepareRequest.requireChecksum(currentChecksum, "currentChecksum");
        String validatedBefore = EconomyPrepareRequest.requireChecksum(beforeChecksum, "beforeChecksum");
        String validatedReplacement = EconomyPrepareRequest.requireChecksum(
                replacementChecksum,
                "replacementChecksum"
        );
        if (validatedCurrent.equals(validatedBefore)) {
            return ROLL_BACK_UNAPPLIED;
        }
        if (validatedCurrent.equals(validatedReplacement)) {
            return COMMIT_ALREADY_APPLIED;
        }
        return RESTORE_BEFORE_STATE;
    }
}
