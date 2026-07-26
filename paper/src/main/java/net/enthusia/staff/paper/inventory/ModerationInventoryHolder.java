package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class ModerationInventoryHolder implements InventoryHolder {
    enum Kind {
        PLAYER,
        ENDER_CHEST
    }

    private final UUID viewerId;
    private final UUID targetId;
    private final String targetName;
    private final Kind kind;
    private final boolean offline;
    private final InventoryObservation base;
    private Inventory inventory;
    private InventoryImage image;
    private boolean dirty;
    private boolean closed;

    ModerationInventoryHolder(
            UUID viewerId,
            UUID targetId,
            String targetName,
            Kind kind,
            boolean offline,
            InventoryObservation base,
            InventoryImage image
    ) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.targetName = Objects.requireNonNull(targetName, "targetName");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.offline = offline;
        this.base = Objects.requireNonNull(base, "base");
        this.image = Objects.requireNonNull(image, "image");
    }

    void attach(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("inventory holder is already attached");
        }
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("inventory holder has not been attached");
        }
        return inventory;
    }

    UUID viewerId() {
        return viewerId;
    }

    UUID targetId() {
        return targetId;
    }

    String targetName() {
        return targetName;
    }

    Kind kind() {
        return kind;
    }

    boolean offline() {
        return offline;
    }

    InventoryObservation base() {
        return base;
    }

    synchronized InventoryImage image() {
        return image;
    }

    synchronized void image(InventoryImage replacement, boolean markDirty) {
        image = Objects.requireNonNull(replacement, "replacement");
        dirty |= markDirty;
    }

    synchronized boolean dirty() {
        return dirty;
    }

    synchronized boolean closeOnce() {
        if (closed) {
            return false;
        }
        closed = true;
        return true;
    }

    int logicalSlot(int rawSlot) {
        if (kind == Kind.ENDER_CHEST) {
            return rawSlot >= 0 && rawSlot < InventoryImage.ENDER_SIZE
                    ? InventoryImage.ENDER_OFFSET + rawSlot
                    : -1;
        }
        return rawSlot >= 0 && rawSlot <= InventoryImage.OFFHAND_SLOT ? rawSlot : -1;
    }

    int guiSlot(int logicalSlot) {
        if (kind == Kind.ENDER_CHEST) {
            int slot = logicalSlot - InventoryImage.ENDER_OFFSET;
            return slot >= 0 && slot < InventoryImage.ENDER_SIZE ? slot : -1;
        }
        return logicalSlot >= 0 && logicalSlot <= InventoryImage.OFFHAND_SLOT ? logicalSlot : -1;
    }
}
