package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.enthusia.staff.domain.inventory.InventoryCursorPhase;
import org.junit.jupiter.api.Test;

final class LiveCursorRecoveryPolicyTest {
    @Test
    void settledCursorPhaseNeverRedeliversAnUnmarkedResult() {
        assertEquals(
                LiveCursorEscrow.UnmarkedResultDisposition.ALREADY_SETTLED,
                LiveCursorEscrow.unmarkedResultDisposition(
                        InventoryCursorPhase.CURSOR_APPLIED,
                        false,
                        true
                )
        );
    }

    @Test
    void targetAppliedPhaseCanDeliverOnlyFromTheRecordedExpectedCursor() {
        assertEquals(
                LiveCursorEscrow.UnmarkedResultDisposition.DELIVER_RESULT,
                LiveCursorEscrow.unmarkedResultDisposition(
                        InventoryCursorPhase.TARGET_APPLIED,
                        false,
                        true
                )
        );
        assertEquals(
                LiveCursorEscrow.UnmarkedResultDisposition.CONFLICT,
                LiveCursorEscrow.unmarkedResultDisposition(
                        InventoryCursorPhase.TARGET_APPLIED,
                        false,
                        false
                )
        );
    }

    @Test
    void replacementAlreadyOnCursorIsAlwaysTreatedAsSettled() {
        assertEquals(
                LiveCursorEscrow.UnmarkedResultDisposition.ALREADY_SETTLED,
                LiveCursorEscrow.unmarkedResultDisposition(
                        InventoryCursorPhase.TARGET_APPLIED,
                        true,
                        false
                )
        );
    }
}
