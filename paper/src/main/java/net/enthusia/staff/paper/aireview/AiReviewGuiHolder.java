package net.enthusia.staff.paper.aireview;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class AiReviewGuiHolder implements InventoryHolder {
    private final AiReviewGuiState state;
    private Inventory inventory;

    AiReviewGuiHolder(AiReviewGuiState state) {
        if (state == null) {
            throw new IllegalArgumentException("AI review GUI state must be present");
        }
        this.state = state;
    }

    AiReviewGuiState state() {
        return state;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("AI review inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("AI review inventory has not been attached");
        }
        return inventory;
    }
}
