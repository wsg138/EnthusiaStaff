package net.enthusia.staff.persistence;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class BinarySnapshotIntegrity {
    private BinarySnapshotIntegrity() {
    }

    static void requireMatch(String checksum, byte[] snapshot, String field) {
        if (checksum == null || snapshot == null) {
            throw new IllegalArgumentException(field + " checksum and snapshot must be present");
        }
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(checksum);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " checksum must be a lowercase SHA-256 value", exception);
        }
        if (expected.length != 32 || !checksum.equals(checksum.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(field + " checksum must be a lowercase SHA-256 value");
        }
        byte[] actual;
        try {
            actual = MessageDigest.getInstance("SHA-256").digest(snapshot);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException(field + " checksum does not match its snapshot bytes");
        }
    }
}
