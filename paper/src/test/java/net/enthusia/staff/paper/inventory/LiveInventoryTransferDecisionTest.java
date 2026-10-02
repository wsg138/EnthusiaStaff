package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class LiveInventoryTransferDecisionTest {
    @Test
    void leftPickupMovesExactTargetStackToEmptyCursor() {
        ItemStack target = stack(Material.DIAMOND, 17);

        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                target,
                null,
                LiveInventoryTransferDecision.Click.LEFT
        );

        assertEquals(LiveInventoryTransferDecision.Action.PICKUP, decision.action());
        assertNull(decision.targetAfter());
        assertEquals(target, decision.cursorAfter());
    }

    @Test
    void leftPlacementMovesEntireCursorIntoEmptyTargetSlot() {
        ItemStack cursor = stack(Material.EMERALD, 11);

        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                null,
                cursor,
                LiveInventoryTransferDecision.Click.LEFT
        );

        assertEquals(LiveInventoryTransferDecision.Action.PLACE, decision.action());
        assertEquals(cursor, decision.targetAfter());
        assertNull(decision.cursorAfter());
    }

    @Test
    void compatibleLeftClickMergesOnlyAvailableCapacity() {
        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                stack(Material.DIAMOND, 60),
                stack(Material.DIAMOND, 10),
                LiveInventoryTransferDecision.Click.LEFT
        );

        assertEquals(LiveInventoryTransferDecision.Action.MERGE, decision.action());
        assertEquals(64, decision.targetAfter().getAmount());
        assertEquals(6, decision.cursorAfter().getAmount());
    }

    @Test
    void incompatibleLeftClickSwapsCursorAndTarget() {
        ItemStack target = stack(Material.DIAMOND, 3);
        ItemStack cursor = stack(Material.EMERALD, 5);

        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                target,
                cursor,
                LiveInventoryTransferDecision.Click.LEFT
        );

        assertEquals(LiveInventoryTransferDecision.Action.SWAP, decision.action());
        assertEquals(cursor, decision.targetAfter());
        assertEquals(target, decision.cursorAfter());
    }

    @Test
    void emptyCursorRightClickSplitsTargetWithLargerHalfOnCursor() {
        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                stack(Material.DIAMOND, 9),
                null,
                LiveInventoryTransferDecision.Click.RIGHT
        );

        assertEquals(LiveInventoryTransferDecision.Action.SPLIT, decision.action());
        assertEquals(4, decision.targetAfter().getAmount());
        assertEquals(5, decision.cursorAfter().getAmount());
    }

    @Test
    void rightClickPlacesOneAndReducesCursor() {
        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                null,
                stack(Material.DIAMOND, 2),
                LiveInventoryTransferDecision.Click.RIGHT
        );

        assertEquals(LiveInventoryTransferDecision.Action.PLACE_ONE, decision.action());
        assertEquals(1, decision.targetAfter().getAmount());
        assertEquals(1, decision.cursorAfter().getAmount());
    }

    @Test
    void rightClickMergesExactlyOneCompatibleItem() {
        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                stack(Material.DIAMOND, 4),
                stack(Material.DIAMOND, 3),
                LiveInventoryTransferDecision.Click.RIGHT
        );

        assertEquals(LiveInventoryTransferDecision.Action.PLACE_ONE, decision.action());
        assertEquals(5, decision.targetAfter().getAmount());
        assertEquals(2, decision.cursorAfter().getAmount());
    }

    @Test
    void fullCompatibleStackDoesNotSwapOrDuplicate() {
        ItemStack target = stack(Material.DIAMOND, 64);
        ItemStack cursor = stack(Material.DIAMOND, 2);

        LiveInventoryTransferDecision.Decision decision = LiveInventoryTransferDecision.decide(
                target,
                cursor,
                LiveInventoryTransferDecision.Click.LEFT
        );

        assertEquals(LiveInventoryTransferDecision.Action.NO_CHANGE, decision.action());
        assertEquals(target, decision.targetAfter());
        assertEquals(cursor, decision.cursorAfter());
        assertTrue(!decision.changed());
    }

    private static ItemStack stack(Material material, int amount) {
        return new RegistryFreeItemStack(material, amount);
    }
}
