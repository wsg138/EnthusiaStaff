package net.enthusia.staff.paper.tester;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Sends and restores client-only fake blocks. The real world is never modified. */
final class FakeBaseRenderer {
    private final JavaPlugin plugin;
    private final FakeBaseTemplate template;

    FakeBaseRenderer(JavaPlugin plugin, FakeBaseTemplate template) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.template = java.util.Objects.requireNonNull(template, "template");
    }

    void show(Player viewer, UUID worldId, FakeBasePlacementPlanner.Anchor anchor) {
        if (!sameWorld(viewer, worldId)) {
            return;
        }
        World world = viewer.getWorld();
        Map<Location, BlockData> changes = new HashMap<>(template.cells().size());
        for (FakeBaseTemplate.Cell cell : template.cells()) {
            changes.put(
                    new Location(world, anchor.x() + cell.x(), anchor.y() + cell.y(), anchor.z() + cell.z()),
                    cell.material().createBlockData()
            );
        }
        viewer.sendMultiBlockChange(changes);
    }

    void clear(Player viewer, UUID worldId, FakeBasePlacementPlanner.Anchor anchor) {
        if (!sameWorld(viewer, worldId)) {
            return;
        }
        World world = plugin.getServer().getWorld(worldId);
        if (world == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().execute(
                plugin,
                world,
                anchor.chunkX(),
                anchor.chunkZ(),
                () -> captureRealBlocks(viewer, worldId, world, anchor)
        );
    }

    private void captureRealBlocks(
            Player viewer,
            UUID worldId,
            World world,
            FakeBasePlacementPlanner.Anchor anchor
    ) {
        Map<Location, BlockData> realBlocks = new HashMap<>(template.cells().size());
        for (FakeBaseTemplate.Cell cell : template.cells()) {
            int x = anchor.x() + cell.x();
            int y = anchor.y() + cell.y();
            int z = anchor.z() + cell.z();
            realBlocks.put(
                    new Location(world, x, y, z),
                    world.getBlockAt(x, y, z).getBlockData().clone()
            );
        }
        viewer.getScheduler().execute(
                plugin,
                () -> {
                    if (sameWorld(viewer, worldId)) {
                        viewer.sendMultiBlockChange(realBlocks);
                    }
                },
                () -> {
                    // Disconnect/retirement clears client-only block state by itself.
                },
                1L
        );
    }

    private static boolean sameWorld(Player viewer, UUID worldId) {
        return viewer != null && viewer.isOnline() && viewer.getWorld().getUID().equals(worldId);
    }
}
