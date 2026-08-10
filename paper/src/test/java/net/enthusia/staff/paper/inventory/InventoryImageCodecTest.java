package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class InventoryImageCodecTest {
    private final InventoryImageCodec codec = new InventoryImageCodec();

    @Test
    void emptyInventoryRoundTripsWithStableChecksum() {
        InventoryImage image = new InventoryImage(
                new ItemStack[InventoryImage.STORAGE_SIZE],
                new ItemStack[InventoryImage.ARMOR_SIZE],
                null,
                new ItemStack[InventoryImage.ENDER_SIZE],
                6
        );

        InventoryImageCodec.EncodedImage encoded = codec.encodeWithChecksum(image);
        InventoryImage decoded = codec.decode(encoded.bytes());

        assertEquals(6, decoded.heldSlot());
        assertArrayEquals(image.storage(), decoded.storage());
        assertArrayEquals(image.armor(), decoded.armor());
        assertEquals(image.offhand(), decoded.offhand());
        assertArrayEquals(image.enderChest(), decoded.enderChest());
        assertEquals(codec.checksum(encoded.bytes()), encoded.checksum());
        assertEquals(64, encoded.checksum().length());

        byte[] first = encoded.bytes();
        byte[] second = encoded.bytes();
        assertNotSame(first, second);
        first[0] ^= 0x01;
        assertArrayEquals(second, encoded.bytes());
    }

    @Test
    void malformedSnapshotsAreRejected() {
        byte[] valid = codec.encode(emptyImage());

        byte[] badMagic = valid.clone();
        badMagic[0] ^= 0x01;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(badMagic));

        byte[] wrongStorageSize = valid.clone();
        ByteBuffer.wrap(wrongStorageSize).putInt(12, InventoryImage.STORAGE_SIZE - 1);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongStorageSize));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(trailing));

        assertThrows(IllegalArgumentException.class, () -> codec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(null));
        assertThrows(IllegalArgumentException.class, () -> codec.checksum(null));
    }

    @Test
    void dirtySlotValidationRejectsTheWholeSetBeforeApply() {
        assertEquals(Set.of(0, InventoryImage.OFFHAND_SLOT),
                InventoryImageCodec.validatedSlots(List.of(0, InventoryImage.OFFHAND_SLOT, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> InventoryImageCodec.validatedSlots(List.of(0, InventoryImage.TOTAL_SLOTS))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InventoryImageCodec.validatedSlots(Arrays.asList(0, null))
        );
    }

    @Test
    void aggregateSnapshotSafetyLimitIsEnforcedBeforeDecodeOrStorage() {
        byte[] oversized = new byte[InventoryImageCodec.MAX_SNAPSHOT_BYTES + 1];

        assertThrows(IllegalArgumentException.class, () -> codec.decode(oversized));
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImageCodec.EncodedImage(oversized, "checksum")
        );
    }

    @Test
    void encodedImageRequiresCompleteFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImageCodec.EncodedImage(null, "checksum")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImageCodec.EncodedImage(new byte[0], "checksum")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryImageCodec.EncodedImage(new byte[]{1}, null)
        );
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
}
