package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class ConfiscationInventoryHolder implements InventoryHolder {
    private final UUID operationId;
    private final UUID viewerId;
    private final Optional<ItemPath> containerPath;
    private final int page;
    private Inventory inventory;

    ConfiscationInventoryHolder(
            UUID operationId,
            UUID viewerId,
            Optional<ItemPath> containerPath,
            int page
    ) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.containerPath = Objects.requireNonNull(containerPath, "containerPath");
        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative");
        }
        this.page = page;
    }

    void attach(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("confiscation holder is already attached");
        }
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("confiscation holder is not attached");
        }
        return inventory;
    }

    UUID operationId() {
        return operationId;
    }

    UUID viewerId() {
        return viewerId;
    }

    Optional<ItemPath> containerPath() {
        return containerPath;
    }

    int page() {
        return page;
    }
}
