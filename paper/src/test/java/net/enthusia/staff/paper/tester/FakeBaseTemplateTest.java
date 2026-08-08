package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FakeBaseTemplateTest {
    @Test
    void standardTemplateIsBoundedAndHasNoDuplicateCells() {
        FakeBaseTemplate template = FakeBaseTemplate.standard();
        Set<String> coordinates = new HashSet<>();

        assertFalse(template.cells().isEmpty());
        assertTrue(template.cells().size() <= FakeBaseTemplate.MAX_BLOCKS);
        for (FakeBaseTemplate.Cell cell : template.cells()) {
            assertTrue(Math.abs(cell.x()) <= FakeBaseTemplate.RADIUS);
            assertTrue(Math.abs(cell.z()) <= FakeBaseTemplate.RADIUS);
            assertTrue(cell.y() >= 1 && cell.y() <= FakeBaseTemplate.HEIGHT);
            assertTrue(coordinates.add(cell.x() + ":" + cell.y() + ":" + cell.z()));
        }
        assertEquals(template.cells().size(), coordinates.size());
    }

    @Test
    void standardTemplateKeepsTwoBlockDoorwayOpen() {
        FakeBaseTemplate template = FakeBaseTemplate.standard();
        assertFalse(template.cells().stream().anyMatch(cell -> cell.x() == 0 && cell.z() == -3 && cell.y() == 1));
        assertFalse(template.cells().stream().anyMatch(cell -> cell.x() == 0 && cell.z() == -3 && cell.y() == 2));
    }
}
