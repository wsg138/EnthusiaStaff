package net.enthusia.staff.paper.staff;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

/**
 * Allow rearranging a session-owned staff tool inside the player's inventory,
 * never moving it into a container, crafting grid, armor, or offhand.
 * Unrelated inventory edits retain their existing staff-rank restrictions.
 */
final class StaffToolMovePolicy {
    private StaffToolMovePolicy() { }

    static boolean safeClick(
            String topType, int topSize, int rawSlot,
            ClickType click, InventoryAction action, boolean sessionToolInvolved
    ) {
        if (!sessionToolInvolved || !"CRAFTING".equals(topType)
                && !"PLAYER".equals(topType) && !"CREATIVE".equals(topType)
                || rawSlot < topSize || rawSlot >= topSize + 36) {
            return false;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.NUMBER_KEY && click != ClickType.CREATIVE) {
            return false;
        }
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                    PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR,
                    HOTBAR_SWAP -> true;
            default -> false;
        };
    }

    static boolean safeDrag(String topType, int topSize,
                            java.util.Set<Integer> rawSlots, boolean sessionToolOnCursor) {
        return sessionToolOnCursor
                && ("CRAFTING".equals(topType) || "PLAYER".equals(topType) || "CREATIVE".equals(topType))
                && !rawSlots.isEmpty()
                && rawSlots.stream().allMatch(slot -> slot >= topSize && slot < topSize + 36);
    }
}
