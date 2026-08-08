package net.enthusia.staff.paper.tester;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
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

    boolean show(Player viewer, UUID worldId, FakeBasePlacementPlanner.Anchor anchor) {
        if (!sameWorld(viewer, worldId)) {
            return false;
        }
        try {
            World world = viewer.getWorld();
            Map<Location, BlockData> changes = new HashMap<>(template.cells().size());
            for (FakeBaseTemplate.Cell cell : template.cells()) {
                changes.put(
                        new Location(world, anchor.x() + cell.x(), anchor.y() + cell.y(), anchor.z() + cell.z()),
                        cell.material().createBlockData()
                );
            }
            viewer.sendMultiBlockChange(changes);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Fake-base virtual render failed", exception);
            return false;
        }
    }

    boolean clear(Player viewer, UUID worldId, FakeBasePlacementPlanner.Anchor anchor) {
        if (!sameWorld(viewer, worldId)) {
            return false;
        }
        World world = plugin.getServer().getWorld(worldId);
        if (world == null) {
            return false;
        }
        try {
            plugin.getServer().getRegionScheduler().execute(
                    plugin,
                    world,
                    anchor.chunkX(),
                    anchor.chunkZ(),
                    () -> captureRealBlocks(viewer, worldId, world, anchor)
            );
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Fake-base restore scheduling retired", exception);
            return false;
        }
    }

    private void captureRealBlocks(
            Player viewer,
            UUID worldId,
            World world,
            FakeBasePlacementPlanner.Anchor anchor
    ) {
        try {
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
            boolean scheduled = viewer.getScheduler().execute(
                    plugin,
                    () -> sendRealBlocks(viewer, worldId, realBlocks),
                    null,
                    1L
            );
            if (!scheduled && viewer.isOnline()) {
                plugin.getLogger().fine("Fake-base viewer restore retired before send; client session cleanup is relied on");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Fake-base authoritative block capture failed during cleanup", exception);
        }
    }

    private void sendRealBlocks(Player viewer, UUID worldId, Map<Location, BlockData> realBlocks) {
        if (!sameWorld(viewer, worldId)) {
            return;
        }
        try {
            viewer.sendMultiBlockChange(realBlocks);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Fake-base authoritative block restore failed", exception);
        }
    }

    private static boolean sameWorld(Player viewer, UUID worldId) {
        return viewer != null && viewer.isOnline() && viewer.getWorld().getUID().equals(worldId);
    }
}
