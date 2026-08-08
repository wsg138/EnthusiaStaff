package net.enthusia.staff.paper.tester;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Finds a conflict-free placement without loading chunks or mutating real blocks. */
// The safety predicate is intentionally kept as one direct fail-closed review surface.
@SuppressWarnings("PMD.CyclomaticComplexity")
final class FakeBasePlacementPlanner {
    private static final int INTERIOR_FLOOR_RADIUS = 2;
    private static final int[] HORIZONTAL_OFFSETS = {0, 4, -4};
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1, 2, -2};

    Optional<Anchor> find(
            int targetX,
            int targetY,
            int targetZ,
            BlockView blocks,
            FakeBaseTemplate template
    ) {
        int chunkX = targetX >> 4;
        int chunkZ = targetZ >> 4;
        if (!blocks.isChunkLoaded(chunkX, chunkZ)) {
            return Optional.empty();
        }
        Set<Anchor> candidates = candidates(targetX, targetY, targetZ, chunkX, chunkZ);
        for (Anchor candidate : candidates) {
            if (safe(candidate, blocks, template)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    boolean safe(Anchor anchor, BlockView blocks, FakeBaseTemplate template) {
        if (!blocks.isChunkLoaded(anchor.chunkX(), anchor.chunkZ())) {
            return false;
        }
        if (anchor.y() - 1 < blocks.minHeight() || anchor.y() + FakeBaseTemplate.HEIGHT >= blocks.maxHeight()) {
            return false;
        }
        for (FakeBaseTemplate.Cell cell : template.cells()) {
            int x = anchor.x() + cell.x();
            int y = anchor.y() + cell.y();
            int z = anchor.z() + cell.z();
            if ((x >> 4) != anchor.chunkX() || (z >> 4) != anchor.chunkZ() || !blocks.isAir(x, y, z)) {
                return false;
            }
        }
        for (int x = -INTERIOR_FLOOR_RADIUS; x <= INTERIOR_FLOOR_RADIUS; x++) {
            for (int z = -INTERIOR_FLOOR_RADIUS; z <= INTERIOR_FLOOR_RADIUS; z++) {
                if (!blocks.isSafeFloor(anchor.x() + x, anchor.y() - 1, anchor.z() + z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<Anchor> candidates(
            int targetX,
            int targetY,
            int targetZ,
            int chunkX,
            int chunkZ
    ) {
        int minX = (chunkX << 4) + FakeBaseTemplate.RADIUS;
        int maxX = (chunkX << 4) + 15 - FakeBaseTemplate.RADIUS;
        int minZ = (chunkZ << 4) + FakeBaseTemplate.RADIUS;
        int maxZ = (chunkZ << 4) + 15 - FakeBaseTemplate.RADIUS;
        int centerX = clamp(targetX, minX, maxX);
        int centerZ = clamp(targetZ, minZ, maxZ);
        Set<Anchor> candidates = new LinkedHashSet<>();
        for (int yOffset : VERTICAL_OFFSETS) {
            for (int xOffset : HORIZONTAL_OFFSETS) {
                for (int zOffset : HORIZONTAL_OFFSETS) {
                    candidates.add(new Anchor(
                            clamp(centerX + xOffset, minX, maxX),
                            targetY + yOffset,
                            clamp(centerZ + zOffset, minZ, maxZ)
                    ));
                }
            }
        }
        return candidates;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    interface BlockView {
        int minHeight();

        int maxHeight();

        boolean isChunkLoaded(int chunkX, int chunkZ);

        boolean isAir(int x, int y, int z);

        boolean isSafeFloor(int x, int y, int z);
    }

    record Anchor(int x, int y, int z) {
        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }
}
