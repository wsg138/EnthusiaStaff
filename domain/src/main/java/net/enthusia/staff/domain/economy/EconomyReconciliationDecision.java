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
        currentChecksum = EconomyPrepareRequest.requireChecksum(currentChecksum, "currentChecksum");
        beforeChecksum = EconomyPrepareRequest.requireChecksum(beforeChecksum, "beforeChecksum");
        replacementChecksum = EconomyPrepareRequest.requireChecksum(
                replacementChecksum,
                "replacementChecksum"
        );
        if (currentChecksum.equals(beforeChecksum)) {
            return ROLL_BACK_UNAPPLIED;
        }
        if (currentChecksum.equals(replacementChecksum)) {
            return COMMIT_ALREADY_APPLIED;
        }
        return RESTORE_BEFORE_STATE;
    }
}
