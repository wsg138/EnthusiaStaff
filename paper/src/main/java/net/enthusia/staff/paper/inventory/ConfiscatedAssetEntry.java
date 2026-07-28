package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;

public record ConfiscatedAssetEntry(ItemPath path, String fingerprint, ItemStack item) {
    public ConfiscatedAssetEntry {
        Objects.requireNonNull(path, "path");
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be a lowercase SHA-256 value");
        }
        item = Objects.requireNonNull(item, "item").clone();
        if (item.isEmpty()) {
            throw new IllegalArgumentException("confiscated asset cannot be empty");
        }
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
