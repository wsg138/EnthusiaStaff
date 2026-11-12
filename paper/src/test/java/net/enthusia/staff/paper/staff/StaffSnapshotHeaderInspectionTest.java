package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.jupiter.api.Test;

class StaffSnapshotHeaderInspectionTest {
    private final StaffStateCodec codec = new StaffStateCodec();

    @Test
    void boundedHeaderCanBeCheckedWithoutItemDeserialization() throws Exception {
        byte[] bytes = header(0x45535331, 1, "HUB");
        assertEquals("HUB", codec.inspectedBackend(1, bytes));
    }

    @Test
    void corruptUnsupportedTruncatedOrUnsafeHeaderFailsClosed() throws Exception {
        for (byte[] bytes : new byte[][]{new byte[]{1, 2}, header(0, 1, "TEST"),
                header(0x45535331, 2, "TEST"), header(0x45535331, 1, "bad\nbackend"),
                header(0x45535331, 1, "__network_detached__")}) {
            assertThrows(IllegalArgumentException.class,
                    () -> codec.inspectedBackend(1, bytes));
        }
        byte[] bytes = header(0x45535331, 1, "TEST");
        assertThrows(IllegalArgumentException.class, () -> codec.inspectedBackend(2, bytes));
        assertThrows(IllegalArgumentException.class, () -> codec.inspectedBackend(1, new byte[75]));
    }

    private static byte[] header(int magic, int version, String backend) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(magic); out.writeInt(version); out.writeUTF(backend);
        }
        return bytes.toByteArray();
    }
}
