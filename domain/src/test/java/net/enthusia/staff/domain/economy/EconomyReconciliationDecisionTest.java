package net.enthusia.staff.domain.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class EconomyReconciliationDecisionTest {
    private static final String BEFORE = "a".repeat(64);
    private static final String REPLACEMENT = "b".repeat(64);
    private static final String OTHER = "c".repeat(64);

    @Test
    void unchangedStateRollsBackAsUnapplied() {
        assertEquals(
                EconomyReconciliationDecision.ROLL_BACK_UNAPPLIED,
                EconomyReconciliationDecision.decide(BEFORE, BEFORE, REPLACEMENT)
        );
    }

    @Test
    void exactReplacementCommitsAsAlreadyApplied() {
        assertEquals(
                EconomyReconciliationDecision.COMMIT_ALREADY_APPLIED,
                EconomyReconciliationDecision.decide(REPLACEMENT, BEFORE, REPLACEMENT)
        );
    }

    @Test
    void anyOtherStateRequiresAnExactRestore() {
        assertEquals(
                EconomyReconciliationDecision.RESTORE_BEFORE_STATE,
                EconomyReconciliationDecision.decide(OTHER, BEFORE, REPLACEMENT)
        );
    }
}
