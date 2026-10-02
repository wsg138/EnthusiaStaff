package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

final class LiveInventoryTransferDecisionTest {
    private static final int STACK_MAX = 64;

    @Test
    void leftPickupMovesExactTargetStackToEmptyCursor() {
        LiveInventoryTransferRule.Stack target = stack(17);

        LiveInventoryTransferRule.Decision decision = decide(
                target, null, false, LiveInventoryTransferRule.Click.LEFT
        );

        assertEquals(LiveInventoryTransferRule.Action.PICKUP, decision.action());
        assertNull(decision.targetAfter());
        assertEquals(target, decision.cursorAfter());
        assertConserved(target, null, decision);
    }

    @Test
    void leftPlacementMovesEntireCursorIntoEmptyTargetSlot() {
        LiveInventoryTransferRule.Stack cursor = stack(11);

        LiveInventoryTransferRule.Decision decision = decide(
                null, cursor, false, LiveInventoryTransferRule.Click.LEFT
        );

        assertEquals(LiveInventoryTransferRule.Action.PLACE, decision.action());
        assertEquals(cursor, decision.targetAfter());
        assertNull(decision.cursorAfter());
        assertConserved(null, cursor, decision);
    }

    @Test
    void compatibleLeftClickMergesOnlyAvailableCapacity() {
        LiveInventoryTransferRule.Stack target = stack(60);
        LiveInventoryTransferRule.Stack cursor = stack(10);

        LiveInventoryTransferRule.Decision decision = decide(
                target, cursor, true, LiveInventoryTransferRule.Click.LEFT
        );

        assertEquals(LiveInventoryTransferRule.Action.MERGE, decision.action());
        assertEquals(64, decision.targetAfter().amount());
        assertEquals(6, decision.cursorAfter().amount());
        assertConserved(target, cursor, decision);
    }

    @Test
    void incompatibleLeftClickSwapsCursorAndTarget() {
        LiveInventoryTransferRule.Stack target = stack(3);
        LiveInventoryTransferRule.Stack cursor = stack(5);

        LiveInventoryTransferRule.Decision decision = decide(
                target, cursor, false, LiveInventoryTransferRule.Click.LEFT
        );

        assertEquals(LiveInventoryTransferRule.Action.SWAP, decision.action());
        assertEquals(cursor, decision.targetAfter());
        assertEquals(target, decision.cursorAfter());
        assertConserved(target, cursor, decision);
    }

    @Test
    void emptyCursorRightClickSplitsTargetWithLargerHalfOnCursor() {
        LiveInventoryTransferRule.Stack target = stack(9);

        LiveInventoryTransferRule.Decision decision = decide(
                target, null, false, LiveInventoryTransferRule.Click.RIGHT
        );

        assertEquals(LiveInventoryTransferRule.Action.SPLIT, decision.action());
        assertEquals(4, decision.targetAfter().amount());
        assertEquals(5, decision.cursorAfter().amount());
        assertConserved(target, null, decision);
    }

    @Test
    void rightClickPlacesOneAndReducesCursor() {
        LiveInventoryTransferRule.Stack cursor = stack(2);

        LiveInventoryTransferRule.Decision decision = decide(
                null, cursor, false, LiveInventoryTransferRule.Click.RIGHT
        );

        assertEquals(LiveInventoryTransferRule.Action.PLACE_ONE, decision.action());
        assertEquals(1, decision.targetAfter().amount());
        assertEquals(1, decision.cursorAfter().amount());
        assertConserved(null, cursor, decision);
    }

    @Test
    void rightClickMergesExactlyOneCompatibleItem() {
        LiveInventoryTransferRule.Stack target = stack(4);
        LiveInventoryTransferRule.Stack cursor = stack(3);

        LiveInventoryTransferRule.Decision decision = decide(
                target, cursor, true, LiveInventoryTransferRule.Click.RIGHT
        );

        assertEquals(LiveInventoryTransferRule.Action.PLACE_ONE, decision.action());
        assertEquals(5, decision.targetAfter().amount());
        assertEquals(2, decision.cursorAfter().amount());
        assertConserved(target, cursor, decision);
    }

    @Test
    void incompatibleRightClickSwapsCursorAndTarget() {
        LiveInventoryTransferRule.Stack target = stack(3);
        LiveInventoryTransferRule.Stack cursor = stack(5);

        LiveInventoryTransferRule.Decision decision = decide(
                target, cursor, false, LiveInventoryTransferRule.Click.RIGHT
        );

        assertEquals(LiveInventoryTransferRule.Action.SWAP, decision.action());
        assertEquals(cursor, decision.targetAfter());
        assertEquals(target, decision.cursorAfter());
        assertConserved(target, cursor, decision);
    }

    @Test
    void fullCompatibleStackDoesNotSwapOrDuplicate() {
        LiveInventoryTransferRule.Stack target = stack(64);
        LiveInventoryTransferRule.Stack cursor = stack(2);

        LiveInventoryTransferRule.Decision decision = decide(
                target, cursor, true, LiveInventoryTransferRule.Click.LEFT
        );

        assertEquals(LiveInventoryTransferRule.Action.NO_CHANGE, decision.action());
        assertEquals(target, decision.targetAfter());
        assertEquals(cursor, decision.cursorAfter());
        assertFalse(decision.changed());
        assertConserved(target, cursor, decision);
    }

    @Test
    void oversizedCompatibleTargetRejectsMergeWithoutChangingEitherSide() {
        LiveInventoryTransferRule.Stack target = new LiveInventoryTransferRule.Stack(70, STACK_MAX);
        LiveInventoryTransferRule.Stack cursor = stack(2);

        LiveInventoryTransferRule.Decision decision = decide(
                target, cursor, true, LiveInventoryTransferRule.Click.LEFT
        );

        assertEquals(LiveInventoryTransferRule.Action.NO_CHANGE, decision.action());
        assertEquals(target, decision.targetAfter());
        assertEquals(cursor, decision.cursorAfter());
        assertFalse(decision.changed());
        assertConserved(target, cursor, decision);
    }

    private static LiveInventoryTransferRule.Decision decide(
            LiveInventoryTransferRule.Stack target,
            LiveInventoryTransferRule.Stack cursor,
            boolean compatible,
            LiveInventoryTransferRule.Click click
    ) {
        return LiveInventoryTransferRule.decide(target, cursor, compatible, click);
    }

    private static LiveInventoryTransferRule.Stack stack(int amount) {
        return new LiveInventoryTransferRule.Stack(amount, STACK_MAX);
    }

    private static void assertConserved(
            LiveInventoryTransferRule.Stack target,
            LiveInventoryTransferRule.Stack cursor,
            LiveInventoryTransferRule.Decision decision
    ) {
        assertEquals(
                amount(target) + amount(cursor),
                amount(decision.targetAfter()) + amount(decision.cursorAfter())
        );
    }

    private static int amount(LiveInventoryTransferRule.Stack stack) {
        return stack == null ? 0 : stack.amount();
    }
}
