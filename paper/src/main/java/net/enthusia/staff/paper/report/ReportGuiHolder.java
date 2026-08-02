package net.enthusia.staff.paper.report;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class ReportGuiHolder implements InventoryHolder {
    private final ReportGuiState state;
    private Inventory inventory;

    ReportGuiHolder(ReportGuiState state) {
        if (state == null) {
            throw new IllegalArgumentException("report GUI state must be present");
        }
        this.state = state;
    }

    ReportGuiState state() {
        return state;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("report GUI inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("report GUI inventory has not been attached");
        }
        return inventory;
    }
}
