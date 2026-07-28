package net.enthusia.staff.domain.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class InventoryPatchDecisionTest {
    private static final String BEFORE = "a".repeat(64);
    private static final String AFTER = "b".repeat(64);

    @Test
    void appliesOnlyAgainstThePreparedBeforeState() {
        assertEquals(
                InventoryPatchDecision.APPLY_REPLACEMENT,
                InventoryPatchDecision.decide(BEFORE, BEFORE, AFTER)
        );
    }

    @Test
    void recognizesCrashAfterExternalApplyBeforeJournalCommit() {
        assertEquals(
                InventoryPatchDecision.FINALIZE_ALREADY_APPLIED,
                InventoryPatchDecision.decide(AFTER, BEFORE, AFTER)
        );
    }

    @Test
    void quarantinesAThirdStateAndRejectsMalformedChecksums() {
        assertEquals(
                InventoryPatchDecision.QUARANTINE_CONFLICT,
                InventoryPatchDecision.decide("c".repeat(64), BEFORE, AFTER)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InventoryPatchDecision.decide("not-a-checksum", BEFORE, AFTER)
        );
    }
}
