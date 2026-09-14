package net.enthusia.staff.paper.staff;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Typed inventory holder that keeps staff-menu routing state off client-controlled item metadata. */
final class StaffToolsMenuHolder implements InventoryHolder {
    private final StaffToolsMenuView view;
    private Inventory inventory;

    StaffToolsMenuHolder(StaffToolsMenuView view) {
        if (view == null) {
            throw new IllegalArgumentException("staff tools menu view must be present");
        }
        this.view = view;
    }

    StaffToolsMenuView view() {
        return view;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("staff tools menu inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("staff tools menu inventory has not been attached");
        }
        return inventory;
    }
}
