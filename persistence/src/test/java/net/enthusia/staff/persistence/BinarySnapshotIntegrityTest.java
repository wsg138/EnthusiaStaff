package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

final class BinarySnapshotIntegrityTest {
    @Test
    void bindsChecksumsToTheExactSnapshotBytes() {
        byte[] snapshot = {1, 2, 3, 4};

        assertDoesNotThrow(() -> BinarySnapshotIntegrity.requireMatch(
                checksum(snapshot),
                snapshot,
                "inventory"
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> BinarySnapshotIntegrity.requireMatch(
                        checksum(snapshot),
                        new byte[]{4, 3, 2, 1},
                        "inventory"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BinarySnapshotIntegrity.requireMatch(
                        checksum(snapshot).toUpperCase(java.util.Locale.ROOT),
                        snapshot,
                        "inventory"
                )
        );
    }

    private static String checksum(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
