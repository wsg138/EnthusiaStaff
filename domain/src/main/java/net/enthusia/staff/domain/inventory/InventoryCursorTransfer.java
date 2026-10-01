package net.enthusia.staff.domain.inventory;

import java.util.Arrays;
import java.util.Objects;

/** Durable, Bukkit-independent before/after state for the Staff cursor side of a live transfer. */
public record InventoryCursorTransfer(
        String expectedChecksum,
        byte[] expectedSnapshot,
        String replacementChecksum,
        byte[] replacementSnapshot
) {
    private static final int MAX_CURSOR_SNAPSHOT_BYTES = 16 * 1024 * 1024;

    public InventoryCursorTransfer {
        expectedChecksum = InventoryObservation.requireChecksum(expectedChecksum);
        replacementChecksum = InventoryObservation.requireChecksum(replacementChecksum);
        expectedSnapshot = checkedCopy(expectedSnapshot, "expectedSnapshot");
        replacementSnapshot = checkedCopy(replacementSnapshot, "replacementSnapshot");
    }

    @Override
    public byte[] expectedSnapshot() {
        return expectedSnapshot.clone();
    }

    @Override
    public byte[] replacementSnapshot() {
        return replacementSnapshot.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InventoryCursorTransfer value)) {
            return false;
        }
        return expectedChecksum.equals(value.expectedChecksum)
                && replacementChecksum.equals(value.replacementChecksum)
                && Arrays.equals(expectedSnapshot, value.expectedSnapshot)
                && Arrays.equals(replacementSnapshot, value.replacementSnapshot);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(expectedChecksum, replacementChecksum);
        result = 31 * result + Arrays.hashCode(expectedSnapshot);
        return 31 * result + Arrays.hashCode(replacementSnapshot);
    }

    private static byte[] checkedCopy(byte[] value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length == 0 || value.length > MAX_CURSOR_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException(field + " is outside the cursor snapshot safety limit");
        }
        return value.clone();
    }
}
