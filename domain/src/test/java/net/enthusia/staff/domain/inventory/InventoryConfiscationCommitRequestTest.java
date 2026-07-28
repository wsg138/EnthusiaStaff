package net.enthusia.staff.domain.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InventoryConfiscationCommitRequestTest {
    @Test
    void nestedPathsAreBoundedAndCanonical() {
        assertDoesNotThrow(() -> request(List.of("0", "40/2/5")));
        assertThrows(IllegalArgumentException.class, () -> request(List.of("0//2")));
        assertThrows(
                IllegalArgumentException.class,
                () -> request(List.of("1/" + "0/".repeat(17) + "1"))
        );
    }

    private static InventoryConfiscationCommitRequest request(List<String> paths) {
        return new InventoryConfiscationCommitRequest(
                UUID.randomUUID(),
                1L,
                0L,
                "a".repeat(64),
                "b".repeat(64),
                new byte[]{1},
                List.of(0),
                "c".repeat(64),
                new byte[]{2},
                paths
        );
    }
}
