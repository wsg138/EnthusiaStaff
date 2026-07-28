package net.enthusia.staff.paper.inventory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryImageCodec {
    private static final int MAGIC = 0x4553494D;
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_ITEM_BYTES = 16 * 1024 * 1024;

    public InventoryImage capture(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player must be present");
        }
        PlayerInventory inventory = player.getInventory();
        return new InventoryImage(
                inventory.getStorageContents(),
                inventory.getArmorContents(),
                inventory.getItemInOffHand(),
                player.getEnderChest().getStorageContents(),
                inventory.getHeldItemSlot()
        );
    }

    public void apply(Player player, InventoryImage image) {
        if (player == null || image == null) {
            throw new IllegalArgumentException("player and image must be present");
        }
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(image.storage());
        inventory.setArmorContents(image.armor());
        inventory.setItemInOffHand(image.offhand());
        inventory.setHeldItemSlot(image.heldSlot());
        player.getEnderChest().setStorageContents(image.enderChest());
        player.updateInventory();
    }

    public byte[] encode(InventoryImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image must be present");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(SCHEMA_VERSION);
                output.writeInt(image.heldSlot());
                writeItems(output, image.storage());
                writeItems(output, image.armor());
                writeItem(output, image.offhand());
                writeItems(output, image.enderChest());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode inventory image", exception);
        }
    }

    public InventoryImage decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("encoded inventory image must be present");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC || input.readInt() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("inventory image schema is unsupported");
            }
            int heldSlot = input.readInt();
            ItemStack[] storage = readItems(input, InventoryImage.STORAGE_SIZE);
            ItemStack[] armor = readItems(input, InventoryImage.ARMOR_SIZE);
            ItemStack offhand = readItem(input);
            ItemStack[] ender = readItems(input, InventoryImage.ENDER_SIZE);
            if (input.available() != 0) {
                throw new IllegalArgumentException("inventory image contains trailing data");
            }
            return new InventoryImage(storage, armor, offhand, ender, heldSlot);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unable to decode inventory image", exception);
        }
    }

    public String checksum(byte[] encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded inventory image must be present");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public EncodedImage encodeWithChecksum(InventoryImage image) {
        byte[] encoded = encode(image);
        return new EncodedImage(encoded, checksum(encoded));
    }

    private static void writeItems(DataOutputStream output, ItemStack[] items) throws IOException {
        output.writeInt(items.length);
        for (ItemStack item : items) {
            writeItem(output, item);
        }
    }

    private static void writeItem(DataOutputStream output, ItemStack item) throws IOException {
        if (item == null || item.isEmpty()) {
            output.writeInt(-1);
            return;
        }
        byte[] encoded = item.serializeAsBytes();
        if (encoded.length > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("serialized item exceeds the safety limit");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static ItemStack[] readItems(DataInputStream input, int expectedSize) throws IOException {
        int size = input.readInt();
        if (size != expectedSize) {
            throw new IllegalArgumentException("inventory image has an unexpected slot count");
        }
        ItemStack[] items = new ItemStack[size];
        for (int index = 0; index < size; index++) {
            items[index] = readItem(input);
        }
        return items;
    }

    private static ItemStack readItem(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 1 || length > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("serialized item length is invalid");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new IOException("serialized item ended unexpectedly");
        }
        return ItemStack.deserializeBytes(encoded);
    }

    public record EncodedImage(byte[] bytes, String checksum) {
        public EncodedImage {
            if (bytes == null || bytes.length == 0 || checksum == null) {
                throw new IllegalArgumentException("encoded image fields must be present");
            }
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
