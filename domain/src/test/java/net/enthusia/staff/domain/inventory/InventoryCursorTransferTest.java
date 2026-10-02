package net.enthusia.staff.domain.inventory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

final class InventoryCursorTransferTest {
    @Test
    void acceptsSnapshotsWhoseChecksumsMatchExactly() {
        byte[] before = {1, 2, 3};
        byte[] after = {4, 5, 6};

        InventoryCursorTransfer transfer = new InventoryCursorTransfer(
                checksum(before), before, checksum(after), after
        );

        assertArrayEquals(before, transfer.expectedSnapshot());
        assertArrayEquals(after, transfer.replacementSnapshot());
    }

    @Test
    void rejectsCursorSnapshotChecksumMismatch() {
        byte[] before = {1, 2, 3};
        byte[] after = {4, 5, 6};

        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryCursorTransfer(
                        checksum(after), before, checksum(after), after
                )
        );
    }

    private static String checksum(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
