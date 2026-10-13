package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

class StaffToolMovePolicyTest {
    private static final String CRAFTING = "CRAFTING";
    private static final String CREATIVE = "CREATIVE";

    private static boolean allowed(String type, int top, int raw, ClickType click,
                                   InventoryAction action) {
        return StaffToolMovePolicy.safeClick(type, top, raw, click, action, true);
    }

    @Test
    void helperModAndDeveloperMayRelocateBoundToolsInPersonalSlots() {
        for (int slot : new int[]{5, 7, 9, 35, 40}) {
            assertTrue(allowed(CRAFTING, 5, slot,
                    ClickType.LEFT, InventoryAction.PICKUP_ALL));
            assertTrue(allowed(CRAFTING, 5, slot,
                    ClickType.LEFT, InventoryAction.PLACE_ALL));
            assertTrue(allowed(CRAFTING, 5, slot,
                    ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP));
        }
    }

    @Test
    void creativeStaffCanMoveTheirOwnToolsWithoutAllowingCloneActions() {
        assertTrue(allowed(CREATIVE, 5, 15, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(allowed(CREATIVE, 5, 15, ClickType.CREATIVE, InventoryAction.PLACE_ALL));
        assertFalse(allowed(CREATIVE, 5, 15, ClickType.MIDDLE, InventoryAction.CLONE_STACK));
        assertFalse(allowed(CREATIVE, 5, 15, ClickType.CREATIVE, InventoryAction.CLONE_STACK));
    }

    @Test
    void toolsCannotShiftOutOrMoveIntoChestsCraftingArmorOrOffhand() {
        assertFalse(allowed("CHEST", 27, 28,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertFalse(allowed(CRAFTING, 5, 2,
                ClickType.LEFT, InventoryAction.PLACE_ALL));
        assertFalse(allowed(CRAFTING, 5, 41,
                ClickType.LEFT, InventoryAction.PLACE_ALL));
        assertFalse(allowed(CRAFTING, 5, 14,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertFalse(allowed(CRAFTING, 5, 14,
                ClickType.DROP, InventoryAction.DROP_ONE_SLOT));
        assertFalse(allowed(CRAFTING, 5, 14,
                ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP));
        assertFalse(allowed(CRAFTING, 5, 14,
                ClickType.MIDDLE, InventoryAction.CLONE_STACK));
        assertFalse(StaffToolMovePolicy.safeClick(CRAFTING, 5, 14,
                ClickType.LEFT, InventoryAction.PICKUP_ALL, false));
    }

    @Test
    void dragOnlyWithinPlayerSlotsNeverIntoOtherInventories() {
        assertTrue(StaffToolMovePolicy.safeDrag(CRAFTING, 5,
                Set.of(10, 14, 37), true));
        assertFalse(StaffToolMovePolicy.safeDrag(CRAFTING, 5,
                Set.of(1, 15), true));
        assertFalse(StaffToolMovePolicy.safeDrag("CHEST", 27,
                Set.of(30, 35), true));
        assertFalse(StaffToolMovePolicy.safeDrag(CRAFTING, 5,
                Set.of(10), false));
    }
}
