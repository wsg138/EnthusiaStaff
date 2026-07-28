package net.enthusia.staff.paper.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

public final class InventoryImage {
    public static final int STORAGE_SIZE = 36;
    public static final int ARMOR_SIZE = 4;
    public static final int OFFHAND_SLOT = 40;
    public static final int ENDER_OFFSET = 41;
    public static final int ENDER_SIZE = 27;
    public static final int TOTAL_SLOTS = ENDER_OFFSET + ENDER_SIZE;

    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final ItemStack[] enderChest;
    private final int heldSlot;

    public InventoryImage(
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack offhand,
            ItemStack[] enderChest,
            int heldSlot
    ) {
        this.storage = copyExact(storage, STORAGE_SIZE, "storage");
        this.armor = copyExact(armor, ARMOR_SIZE, "armor");
        this.offhand = copy(offhand);
        this.enderChest = copyExact(enderChest, ENDER_SIZE, "enderChest");
        if (heldSlot < 0 || heldSlot > 8) {
            throw new IllegalArgumentException("heldSlot must be between 0 and 8");
        }
        this.heldSlot = heldSlot;
    }

    public ItemStack[] storage() {
        return copy(storage);
    }

    public ItemStack[] armor() {
        return copy(armor);
    }

    public ItemStack offhand() {
        return copy(offhand);
    }

    public ItemStack[] enderChest() {
        return copy(enderChest);
    }

    public int heldSlot() {
        return heldSlot;
    }

    public ItemStack item(int logicalSlot) {
        requireSlot(logicalSlot);
        if (logicalSlot < STORAGE_SIZE) {
            return copy(storage[logicalSlot]);
        }
        if (logicalSlot < OFFHAND_SLOT) {
            return copy(armor[logicalSlot - STORAGE_SIZE]);
        }
        if (logicalSlot == OFFHAND_SLOT) {
            return copy(offhand);
        }
        return copy(enderChest[logicalSlot - ENDER_OFFSET]);
    }

    public InventoryImage withItem(int logicalSlot, ItemStack replacement) {
        requireSlot(logicalSlot);
        ItemStack[] nextStorage = storage();
        ItemStack[] nextArmor = armor();
        ItemStack nextOffhand = offhand();
        ItemStack[] nextEnder = enderChest();
        if (logicalSlot < STORAGE_SIZE) {
            nextStorage[logicalSlot] = copy(replacement);
        } else if (logicalSlot < OFFHAND_SLOT) {
            nextArmor[logicalSlot - STORAGE_SIZE] = copy(replacement);
        } else if (logicalSlot == OFFHAND_SLOT) {
            nextOffhand = copy(replacement);
        } else {
            nextEnder[logicalSlot - ENDER_OFFSET] = copy(replacement);
        }
        return new InventoryImage(nextStorage, nextArmor, nextOffhand, nextEnder, heldSlot);
    }

    public List<Integer> changedSlots(InventoryImage replacement) {
        Objects.requireNonNull(replacement, "replacement");
        List<Integer> changed = new ArrayList<>();
        for (int slot = 0; slot < TOTAL_SLOTS; slot++) {
            if (!Objects.equals(item(slot), replacement.item(slot))) {
                changed.add(slot);
            }
        }
        return List.copyOf(changed);
    }

    private static ItemStack[] copyExact(ItemStack[] source, int expected, String field) {
        if (source == null || source.length != expected) {
            throw new IllegalArgumentException(field + " must contain exactly " + expected + " slots");
        }
        return copy(source);
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = copy(source[index]);
        }
        return result;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private static void requireSlot(int logicalSlot) {
        if (logicalSlot < 0 || logicalSlot >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("logical inventory slot is out of range");
        }
    }
}
