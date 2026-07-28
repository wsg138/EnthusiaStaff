package net.enthusia.staff.paper.punishment;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class PunishmentGuiHolder implements InventoryHolder {
    private final PunishmentGuiState state;
    private Inventory inventory;

    PunishmentGuiHolder(PunishmentGuiState state) {
        if (state == null) {
            throw new IllegalArgumentException("punishment GUI state must be present");
        }
        this.state = state;
    }

    PunishmentGuiState state() {
        return state;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("punishment GUI inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("punishment GUI inventory has not been attached");
        }
        return inventory;
    }
}
