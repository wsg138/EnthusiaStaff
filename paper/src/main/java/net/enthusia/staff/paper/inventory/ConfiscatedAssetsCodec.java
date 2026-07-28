package net.enthusia.staff.paper.inventory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class ConfiscatedAssetsCodec {
    private static final int MAGIC = 0x45534341;
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 16_384;
    private static final int MAX_ITEM_BYTES = 16 * 1024 * 1024;

    public EncodedAssets encode(List<ConfiscatedAssetEntry> entries) {
        List<ConfiscatedAssetEntry> immutableEntries = List.copyOf(entries);
        if (immutableEntries.isEmpty() || immutableEntries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("confiscated asset entry count is invalid");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(immutableEntries.size());
                for (ConfiscatedAssetEntry entry : immutableEntries) {
                    output.writeUTF(entry.path().encoded());
                    output.writeUTF(entry.fingerprint());
                    byte[] item = entry.item().serializeAsBytes();
                    if (item.length < 1 || item.length > MAX_ITEM_BYTES) {
                        throw new IllegalArgumentException("confiscated item exceeds the safety limit");
                    }
                    output.writeInt(item.length);
                    output.write(item);
                }
            }
            byte[] encoded = bytes.toByteArray();
            return new EncodedAssets(encoded, checksum(encoded));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode confiscated assets", exception);
        }
    }

    public List<ConfiscatedAssetEntry> decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("confiscated asset snapshot is empty");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IllegalArgumentException("confiscated asset snapshot schema is unsupported");
            }
            int count = input.readInt();
            if (count < 1 || count > MAX_ENTRIES) {
                throw new IllegalArgumentException("confiscated asset entry count is invalid");
            }
            List<ConfiscatedAssetEntry> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ItemPath path = ItemPath.parse(input.readUTF());
                String fingerprint = input.readUTF();
                int length = input.readInt();
                if (length < 1 || length > MAX_ITEM_BYTES) {
                    throw new IllegalArgumentException("confiscated item length is invalid");
                }
                byte[] item = input.readNBytes(length);
                if (item.length != length) {
                    throw new IOException("confiscated item ended unexpectedly");
                }
                entries.add(new ConfiscatedAssetEntry(
                        path,
                        fingerprint,
                        ItemStack.deserializeBytes(item)
                ));
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("confiscated asset snapshot contains trailing data");
            }
            return List.copyOf(entries);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unable to decode confiscated assets", exception);
        }
    }

    public String checksum(byte[] encoded) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record EncodedAssets(byte[] bytes, String checksum) {
        public EncodedAssets {
            if (bytes == null || bytes.length == 0
                    || checksum == null || !checksum.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("encoded asset fields are invalid");
            }
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
