package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ItemPathTest {
    @Test
    void canonicalEncodingRoundTrips() {
        ItemPath path = new ItemPath(40, List.of(2, 5, 1));

        assertEquals(path, ItemPath.parse(path.encoded()));
    }

    @Test
    void ancestorChecksRespectExactSlotSequence() {
        ItemPath parent = new ItemPath(3, List.of(2));

        assertTrue(parent.ancestorOf(new ItemPath(3, List.of(2, 7))));
        assertTrue(parent.ancestorOf(parent));
        assertFalse(parent.ancestorOf(new ItemPath(3, List.of(1, 7))));
        assertFalse(parent.ancestorOf(new ItemPath(4, List.of(2, 7))));
    }

    @Test
    void excessiveDepthIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ItemPath(0, java.util.Collections.nCopies(17, 0))
        );
    }
}
