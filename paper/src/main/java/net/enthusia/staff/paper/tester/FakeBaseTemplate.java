package net.enthusia.staff.paper.tester;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/** Fixed, reviewable fake-base schematic. No cell represents a real-world mutation. */
// Keep the tiny approved schematic generator direct so every cell rule is reviewable in one place.
@SuppressWarnings({"PMD.AvoidLiteralsInIfCondition", "PMD.CyclomaticComplexity"})
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
        for (int y = 1; y < HEIGHT; y++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (Math.abs(x) != RADIUS && Math.abs(z) != RADIUS) {
                        continue;
                    }
                    if (z == -RADIUS && x == 0 && y <= 2) {
                        continue;
                    }
                    Material material = window(x, y, z) ? Material.TINTED_GLASS : Material.DEEPSLATE_TILES;
                    cells.add(new Cell(x, y, z, material));
                }
            }
        }
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                cells.add(new Cell(x, HEIGHT, z, Material.POLISHED_BLACKSTONE_BRICKS));
            }
        }
        return new FakeBaseTemplate(cells);
    }

    private static boolean window(int x, int y, int z) {
        if (y != 2) {
            return false;
        }
        return (Math.abs(x) == RADIUS && z == 0) || (Math.abs(z) == RADIUS && x == 0);
    }

    record Cell(int x, int y, int z, Material material) {
        Cell {
            if (Math.abs(x) > RADIUS || y < 1 || y > HEIGHT || Math.abs(z) > RADIUS || material == null) {
                throw new IllegalArgumentException("fake-base template cell is outside approved bounds");
            }
        }
    }
}
