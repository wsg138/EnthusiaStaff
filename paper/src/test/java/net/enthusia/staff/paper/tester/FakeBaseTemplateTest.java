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
        assertFalse(hasCell(template, 0, 1, -FakeBaseTemplate.RADIUS));
        assertFalse(hasCell(template, 0, 2, -FakeBaseTemplate.RADIUS));
    }

    @Test
    void standardTemplateRoofAndWallsTrackConfiguredHeight() {
        FakeBaseTemplate template = FakeBaseTemplate.standard();
        int roofCells = (FakeBaseTemplate.RADIUS * 2 + 1) * (FakeBaseTemplate.RADIUS * 2 + 1);
        assertEquals(roofCells, template.cells().stream()
                .filter(cell -> cell.y() == FakeBaseTemplate.HEIGHT)
                .count());

        for (int y = 1; y < FakeBaseTemplate.HEIGHT; y++) {
            for (int x = -FakeBaseTemplate.RADIUS; x <= FakeBaseTemplate.RADIUS; x++) {
                for (int z = -FakeBaseTemplate.RADIUS; z <= FakeBaseTemplate.RADIUS; z++) {
                    if (Math.abs(x) != FakeBaseTemplate.RADIUS && Math.abs(z) != FakeBaseTemplate.RADIUS) {
                        continue;
                    }
                    boolean doorway = x == 0 && z == -FakeBaseTemplate.RADIUS && y <= 2;
                    assertEquals(!doorway, hasCell(template, x, y, z));
                }
            }
        }
    }

    private static boolean hasCell(FakeBaseTemplate template, int x, int y, int z) {
        return template.cells().stream().anyMatch(cell -> cell.x() == x && cell.y() == y && cell.z() == z);
    }
}
