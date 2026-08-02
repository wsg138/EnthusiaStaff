package net.enthusia.staff.paper.report;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class ReportGuiHolder implements InventoryHolder {
    private final ReportGuiState state;
    private final ReportGuiConfiguration configuration;
    private Inventory inventory;

    ReportGuiHolder(ReportGuiState state, ReportGuiConfiguration configuration) {
        if (state == null || configuration == null) {
            throw new IllegalArgumentException("report GUI state and configuration must be present");
        }
        this.state = state;
        this.configuration = configuration;
    }

    ReportGuiState state() {
        return state;
    }

    ReportGuiConfiguration configuration() {
        return configuration;
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
