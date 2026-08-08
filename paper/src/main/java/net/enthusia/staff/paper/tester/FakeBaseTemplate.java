package net.enthusia.staff.paper.tester;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/** Fixed, reviewable fake-base schematic. No cell represents a real-world mutation. */
final class FakeBaseTemplate {
    static final int RADIUS = 3;
    static final int HEIGHT = 4;
    static final int MAX_BLOCKS = 160;
    private static final FakeBaseTemplate STANDARD = buildStandard();

    private final List<Cell> cells;

    private FakeBaseTemplate(List<Cell> cells) {
        if (cells.isEmpty() || cells.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("fake-base template must be non-empty and bounded");
        }
        this.cells = List.copyOf(cells);
    }

    static FakeBaseTemplate standard() {
        return STANDARD;
    }

    List<Cell> cells() {
        return cells;
    }

    private static FakeBaseTemplate buildStandard() {
        List<Cell> cells = new ArrayList<>();
        addWalls(cells);
        addRoof(cells);
        return new FakeBaseTemplate(cells);
    }

    private static void addWalls(List<Cell> cells) {
        for (int y = 1; y < HEIGHT; y++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (wallCell(x, z) && !doorway(x, y, z)) {
                        cells.add(new Cell(x, y, z, wallMaterial(x, y, z)));
                    }
                }
            }
        }
    }

    private static void addRoof(List<Cell> cells) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                cells.add(new Cell(x, HEIGHT, z, Material.POLISHED_BLACKSTONE_BRICKS));
            }
        }
    }

    private static boolean wallCell(int x, int z) {
        return Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
    }

    private static boolean doorway(int x, int y, int z) {
        return z == -RADIUS && x == 0 && y <= 2;
    }

    private static Material wallMaterial(int x, int y, int z) {
        return window(x, y, z) ? Material.TINTED_GLASS : Material.DEEPSLATE_TILES;
    }

    private static boolean window(int x, int y, int z) {
        return y == 2 && ((Math.abs(x) == RADIUS && z == 0) || (Math.abs(z) == RADIUS && x == 0));
    }

    record Cell(int x, int y, int z, Material material) {
        Cell {
            if (Math.abs(x) > RADIUS || y < 1 || y > HEIGHT || Math.abs(z) > RADIUS || material == null) {
                throw new IllegalArgumentException("fake-base template cell is outside approved bounds");
            }
        }
    }
}
