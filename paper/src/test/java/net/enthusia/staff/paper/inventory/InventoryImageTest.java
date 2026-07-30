package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class InventoryImageTest {
    @Test
    void emptyInventoryAccessorsReturnIndependentArrayCopies() {
        InventoryImage image = emptyImage(4);

        ItemStack[] firstStorage = image.storage();
        ItemStack[] secondStorage = image.storage();
        ItemStack[] firstArmor = image.armor();
        ItemStack[] secondArmor = image.armor();
        ItemStack[] firstEnder = image.enderChest();
        ItemStack[] secondEnder = image.enderChest();

        assertNotSame(firstStorage, secondStorage);
        assertNotSame(firstArmor, secondArmor);
        assertNotSame(firstEnder, secondEnder);
        assertEquals(InventoryImage.STORAGE_SIZE, firstStorage.length);
        assertEquals(InventoryImage.ARMOR_SIZE, firstArmor.length);
        assertEquals(InventoryImage.ENDER_SIZE, firstEnder.length);
        assertNull(image.offhand());
        assertEquals(4, image.heldSlot());
        assertNull(image.item(0));
        assertNull(image.item(InventoryImage.STORAGE_SIZE));
        assertNull(image.item(InventoryImage.OFFHAND_SLOT));
        assertNull(image.item(InventoryImage.ENDER_OFFSET));
    }

    @Test
    void nullReplacementsExerciseEveryLogicalInventoryRegionWithoutMutation() {
        InventoryImage original = emptyImage(0);
        InventoryImage replacement = original
                .withItem(0, null)
                .withItem(InventoryImage.STORAGE_SIZE, null)
                .withItem(InventoryImage.OFFHAND_SLOT, null)
                .withItem(InventoryImage.ENDER_OFFSET, null);

        assertEquals(List.of(), original.changedSlots(replacement));
        assertEquals(0, replacement.heldSlot());
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

        InventoryImage image = emptyImage(0);
        assertThrows(IllegalArgumentException.class, () -> image.item(-1));
        assertThrows(IllegalArgumentException.class, () -> image.item(InventoryImage.TOTAL_SLOTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> image.withItem(InventoryImage.TOTAL_SLOTS, null)
        );
        assertThrows(NullPointerException.class, () -> image.changedSlots(null));
    }

    private static InventoryImage emptyImage(int heldSlot) {
        return new InventoryImage(
                new ItemStack[InventoryImage.STORAGE_SIZE],
                new ItemStack[InventoryImage.ARMOR_SIZE],
                null,
                new ItemStack[InventoryImage.ENDER_SIZE],
                heldSlot
        );
    }
}
