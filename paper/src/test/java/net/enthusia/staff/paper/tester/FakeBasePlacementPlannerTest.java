package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FakeBasePlacementPlannerTest {
    private final FakeBasePlacementPlanner planner = new FakeBasePlacementPlanner();
    private final FakeBaseTemplate template = FakeBaseTemplate.standard();

    @Test
    void placementStaysInsideAlreadyLoadedTargetChunk() {
        var result = planner.find(15, 64, 15, new FakeBlocks(-64, 320, true, true, true), template);
        assertTrue(result.isPresent());
        var anchor = result.orElseThrow();
        assertEquals(0, anchor.chunkX());
        assertEquals(0, anchor.chunkZ());
        for (FakeBaseTemplate.Cell cell : template.cells()) {
            assertEquals(anchor.chunkX(), (anchor.x() + cell.x()) >> 4);
            assertEquals(anchor.chunkZ(), (anchor.z() + cell.z()) >> 4);
        }
    }

    @Test
    void rejectsUnloadedChunkWithoutGeneratingOrLoadingIt() {
        assertTrue(planner.find(8, 64, 8, new FakeBlocks(-64, 320, false, true, true), template).isEmpty());
    }

    @Test
    void rejectsRealBlockConflicts() {
        assertTrue(planner.find(8, 64, 8, new FakeBlocks(-64, 320, true, false, true), template).isEmpty());
    }

    @Test
    void rejectsUnsafeInteriorFloor() {
        assertTrue(planner.find(8, 64, 8, new FakeBlocks(-64, 320, true, true, false), template).isEmpty());
    }

    @Test
    void rejectsPlacementOutsideWorldHeight() {
        assertTrue(planner.find(8, 64, 8, new FakeBlocks(64, 68, true, true, true), template).isEmpty());
    }

    private record FakeBlocks(
            int minHeight,
            int maxHeight,
            boolean loaded,
            boolean air,
            boolean safeFloor
    ) implements FakeBasePlacementPlanner.BlockView {
        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return loaded;
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return air;
        }

        @Override
        public boolean isSafeFloor(int x, int y, int z) {
            return safeFloor;
        }
    }
}
