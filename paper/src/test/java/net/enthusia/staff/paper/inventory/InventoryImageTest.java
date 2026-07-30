package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class InventoryImageTest {
    @Test
    void constructorAndAccessorsDefensivelyCopyInventoryContents() {
        ItemStack[] storage = new ItemStack[InventoryImage.STORAGE_SIZE];
        ItemStack[] armor = new ItemStack[InventoryImage.ARMOR_SIZE];
        ItemStack[] ender = new ItemStack[InventoryImage.ENDER_SIZE];
        storage[0] = item(Material.STONE, 2);
        armor[0] = item(Material.IRON_BOOTS, 1);
        ItemStack offhand = item(Material.SHIELD, 1);
        ender[0] = item(Material.DIAMOND, 3);

        InventoryImage image = new InventoryImage(storage, armor, offhand, ender, 4);

        storage[0].setAmount(9);
        armor[0] = null;
        offhand.setAmount(2);
        ender[0].setAmount(8);

        assertEquals(2, image.item(0).getAmount());
        assertEquals(Material.IRON_BOOTS, image.item(InventoryImage.STORAGE_SIZE).getType());
        assertEquals(Material.SHIELD, image.item(InventoryImage.OFFHAND_SLOT).getType());
        assertEquals(Material.DIAMOND, image.item(InventoryImage.ENDER_OFFSET).getType());
        assertEquals(4, image.heldSlot());

        ItemStack[] returnedStorage = image.storage();
        assertNotSame(returnedStorage[0], image.item(0));
        returnedStorage[0].setAmount(7);
        assertEquals(2, image.item(0).getAmount());

        ItemStack returnedOffhand = image.offhand();
        returnedOffhand.setAmount(4);
        assertEquals(1, image.offhand().getAmount());
    }

    @Test
    void replacementsMapAcrossAllLogicalInventoryRegions() {
        InventoryImage original = emptyImage();
        InventoryImage replacement = original
                .withItem(0, item(Material.STONE, 1))
                .withItem(InventoryImage.STORAGE_SIZE, item(Material.IRON_HELMET, 1))
                .withItem(InventoryImage.OFFHAND_SLOT, item(Material.SHIELD, 1))
                .withItem(InventoryImage.ENDER_OFFSET, item(Material.EMERALD, 5));

        assertEquals(Material.STONE, replacement.item(0).getType());
        assertEquals(Material.IRON_HELMET, replacement.item(InventoryImage.STORAGE_SIZE).getType());
        assertEquals(Material.SHIELD, replacement.item(InventoryImage.OFFHAND_SLOT).getType());
        assertEquals(Material.EMERALD, replacement.item(InventoryImage.ENDER_OFFSET).getType());
        assertNull(original.item(0));
        assertEquals(
                List.of(0, InventoryImage.STORAGE_SIZE, InventoryImage.OFFHAND_SLOT, InventoryImage.ENDER_OFFSET),
                original.changedSlots(replacement)
        );
    }

    @Test
    void invalidShapesSlotsAndHeldSelectionAreRejected() {
        ItemStack[] storage = new ItemStack[InventoryImage.STORAGE_SIZE];
        ItemStack[] armor = new ItemStack[InventoryImage.ARMOR_SIZE];
        ItemStack[] ender = new ItemStack[InventoryImage.ENDER_SIZE];

        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImage(new ItemStack[1], armor, null, ender, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImage(storage, new ItemStack[1], null, ender, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImage(storage, armor, null, new ItemStack[1], 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImage(storage, armor, null, ender, -1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImage(storage, armor, null, ender, 9)
        );

        InventoryImage image = emptyImage();
        assertThrows(IllegalArgumentException.class, () -> image.item(-1));
        assertThrows(IllegalArgumentException.class, () -> image.item(InventoryImage.TOTAL_SLOTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> image.withItem(InventoryImage.TOTAL_SLOTS, null)
        );
        assertThrows(NullPointerException.class, () -> image.changedSlots(null));
    }

    private static InventoryImage emptyImage() {
        return new InventoryImage(
                new ItemStack[InventoryImage.STORAGE_SIZE],
                new ItemStack[InventoryImage.ARMOR_SIZE],
                null,
                new ItemStack[InventoryImage.ENDER_SIZE],
                0
        );
    }

    private static ItemStack item(Material material, int amount) {
        return new ItemStack(material, amount);
    }
}
