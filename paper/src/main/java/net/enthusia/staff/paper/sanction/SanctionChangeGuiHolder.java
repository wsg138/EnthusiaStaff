package net.enthusia.staff.paper.sanction;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SanctionChangeGuiHolder implements InventoryHolder {
    private final SanctionChangeGuiState state;
    private Inventory inventory;

    SanctionChangeGuiHolder(SanctionChangeGuiState state) {
        if (state == null) {
            throw new IllegalArgumentException("sanction change GUI state must be present");
        }
        this.state = state;
    }

    SanctionChangeGuiState state() {
        return state;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("sanction change inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("sanction change inventory has not been attached");
        }
        return inventory;
    }
}
