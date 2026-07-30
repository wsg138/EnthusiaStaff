package net.enthusia.staff.paper.punishment;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class PunishmentRequestGuiHolder implements InventoryHolder {
    private final PunishmentRequestGuiState state;
    private Inventory inventory;

    PunishmentRequestGuiHolder(PunishmentRequestGuiState state) {
        if (state == null) {
            throw new IllegalArgumentException("punishment request GUI state must be present");
        }
        this.state = state;
    }

    PunishmentRequestGuiState state() {
        return state;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("punishment request GUI inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("punishment request GUI inventory has not been attached");
        }
        return inventory;
    }
}
