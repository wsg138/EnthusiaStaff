package net.enthusia.staff.paper.inventory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.enthusia.staff.domain.inventory.InventoryCursorTransfer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class CursorStackCodec {
    private static final int VERSION = 1;
    private static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;

    InventoryCursorTransfer transfer(ItemStack expected, ItemStack replacement) {
        EncodedCursor before = encodeWithChecksum(expected);
        EncodedCursor after = encodeWithChecksum(replacement);
        return new InventoryCursorTransfer(
                before.checksum(), before.bytes(), after.checksum(), after.bytes()
        );
    }

    EncodedCursor encodeWithChecksum(ItemStack item) {
        byte[] bytes = encode(item);
        return new EncodedCursor(bytes, checksum(bytes));
    }

    byte[] encode(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(VERSION);
                boolean present = usable(item);
                output.writeBoolean(present);
                if (present) {
                    byte[] encoded = item.serializeAsBytes();
                    if (encoded.length < 1 || encoded.length > MAX_ITEM_BYTES) {
                        throw new IllegalArgumentException("cursor item exceeds serialization safety limit");
                    }
                    output.writeInt(encoded.length);
                    output.write(encoded);
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode cursor item", exception);
        }
    }

    ItemStack decode(byte[] encoded) {
        if (encoded == null || encoded.length < Integer.BYTES + 1) {
            throw new IllegalArgumentException("encoded cursor item is invalid");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != VERSION) {
                throw new IllegalArgumentException("unsupported cursor item encoding version");
            }
            ItemStack item = input.readBoolean() ? readItem(input) : null;
            if (input.available() != 0) {
                throw new IllegalArgumentException("encoded cursor item contains trailing bytes");
            }
            return item;
        } catch (IOException exception) {
            throw new IllegalArgumentException("encoded cursor item is invalid", exception);
        }
    }

    boolean matches(ItemStack item, String expectedChecksum) {
        return checksum(encode(item)).equals(expectedChecksum);
    }

    String checksum(byte[] encoded) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ItemStack readItem(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > MAX_ITEM_BYTES || length > input.available()) {
            throw new IllegalArgumentException("encoded cursor item length is invalid");
        }
        ItemStack item = ItemStack.deserializeBytes(input.readNBytes(length));
        return usable(item) ? item : null;
    }

    private static boolean usable(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType() != Material.AIR;
    }

    record EncodedCursor(byte[] bytes, String checksum) {
        EncodedCursor {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
