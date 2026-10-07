package net.enthusia.staff.paper.punishment.policyv2;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class PolicyV2GuiHolder implements InventoryHolder {
    private final PolicyV2GuiState state;
    private Inventory inventory;

    PolicyV2GuiHolder(PolicyV2GuiState state) {
        this.state = java.util.Objects.requireNonNull(state, "state");
    }

    PolicyV2GuiState state() {
        return state;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("Policy v2 GUI inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Policy v2 GUI inventory has not been attached");
        }
        return inventory;
    }
}
