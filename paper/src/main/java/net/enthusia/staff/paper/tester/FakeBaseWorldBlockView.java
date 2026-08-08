package net.enthusia.staff.paper.tester;

import org.bukkit.Material;
import org.bukkit.World;

final class FakeBaseWorldBlockView implements FakeBasePlacementPlanner.BlockView {
    private final World world;

    FakeBaseWorldBlockView(World world) {
        this.world = java.util.Objects.requireNonNull(world, "world");
    }

    @Override
    public int minHeight() {
        return world.getMinHeight();
    }

    @Override
    public int maxHeight() {
        return world.getMaxHeight();
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isAir();
    }

    @Override
    public boolean isSafeFloor(int x, int y, int z) {
        Material material = world.getBlockAt(x, y, z).getType();
        return material.isSolid() && material != Material.MAGMA_BLOCK
                && material != Material.CAMPFIRE && material != Material.SOUL_CAMPFIRE
                && material != Material.CACTUS && material != Material.POWDER_SNOW;
    }
}
