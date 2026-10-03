package net.enthusia.staff.paper.visibility;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Tracks container blocks opened by vanished staff so that the
 * {@link ProtocolLibSilentContainerPacketAdapter} can suppress the
 * {@code BLOCK_ACTION} open/close animation (and its client-side sound)
 * for everyone except players who currently have the container open.
 *
 * <p>The container GUI itself is unaffected: the vanished opener still receives
 * the normal inventory window. Only the lid animation and open/close sound
 * broadcast to nearby players is suppressed.
 *
 * <p>Suppression applies when every player currently viewing the container is
 * vanished. If a non-vanished player opens the same container, animations play
 * normally. The close animation is suppressed for a short window after a
 * vanished player closes a container that nobody else is viewing.
 *
 * <p>All staff ranks are covered: suppression is based on vanish state, not rank.
 */
public final class SilentContainerTracker implements Listener {
    /** How long after a vanished close the close animation stays suppressed. */
    static final long CLOSE_SUPPRESSION_MILLIS = 5_000L;

    private final Predicate<UUID> vanishedCheck;
    private final Clock clock;
    /** Block positions with at least one recorded viewer. */
    private final Map<BlockKey, Set<UUID>> viewers = new ConcurrentHashMap<>();
    /** Positions whose close animation should stay silent until the timestamp. */
    private final Map<BlockKey, Long> silentUntilMillis = new ConcurrentHashMap<>();

    public SilentContainerTracker(Predicate<UUID> vanishedCheck, Clock clock) {
        this.vanishedCheck = Objects.requireNonNull(vanishedCheck, "vanishedCheck");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        List<BlockKey> positions = containerPositions(event.getInventory());
        if (positions.isEmpty()) {
            return;
        }
        onOpen(player.getUniqueId(), positions, clock.millis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        List<BlockKey> positions = containerPositions(event.getInventory());
        if (positions.isEmpty()) {
            return;
        }
        onClose(player.getUniqueId(), positions, clock.millis());
    }

    /**
     * Records a container open. Package-private for tests.
     */
    void onOpen(UUID playerId, List<BlockKey> positions, long nowMillis) {
        Objects.requireNonNull(playerId, "playerId");
        if (positions.isEmpty()) {
            return;
        }
        boolean vanished = vanishedCheck.test(playerId);
        for (BlockKey key : positions) {
            viewers.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(playerId);
            if (!vanished) {
                // A visible player opened it: the open animation must play.
                silentUntilMillis.remove(key);
            }
        }
        pruneExpired(nowMillis);
    }

    /**
     * Records a container close. Package-private for tests.
     */
    void onClose(UUID playerId, List<BlockKey> positions, long nowMillis) {
        Objects.requireNonNull(playerId, "playerId");
        if (positions.isEmpty()) {
            return;
        }
        boolean vanished = vanishedCheck.test(playerId);
        for (BlockKey key : positions) {
            Set<UUID> viewing = viewers.get(key);
            if (viewing != null) {
                viewing.remove(playerId);
                if (viewing.isEmpty()) {
                    viewers.remove(key);
                }
            }
            Set<UUID> remaining = viewers.get(key);
            if (remaining == null || remaining.isEmpty()) {
                if (vanished) {
                    // Nobody left viewing and the closer was vanished:
                    // keep the close animation silent briefly.
                    silentUntilMillis.put(key, nowMillis + CLOSE_SUPPRESSION_MILLIS);
                } else {
                    silentUntilMillis.remove(key);
                }
            } else if (remaining.stream().noneMatch(vanishedCheck::test)) {
                // A visible player is still viewing: animations must play.
                silentUntilMillis.remove(key);
            }
        }
        pruneExpired(nowMillis);
    }

    /**
     * Whether the {@code BLOCK_ACTION} animation/sound for {@code key} should be
     * hidden from {@code recipientId}.
     *
     * <p>Players who currently have the container open always see the animation.
     * Everyone else sees it only when at least one current viewer is not vanished.
     * After the last viewer closes, a recent vanished close keeps it silent briefly.
     */
    public boolean shouldSuppressFor(BlockKey key, UUID recipientId, long nowMillis) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(recipientId, "recipientId");
        pruneExpired(nowMillis);
        Set<UUID> viewing = viewers.get(key);
        if (viewing != null && !viewing.isEmpty()) {
            if (viewing.contains(recipientId)) {
                return false;
            }
            return viewing.stream().allMatch(vanishedCheck::test);
        }
        Long until = silentUntilMillis.get(key);
        return until != null && until > nowMillis;
    }

    private void pruneExpired(long nowMillis) {
        silentUntilMillis.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
    }

    /**
     * Extracts the animated container block positions for an inventory.
     * Double chests report both halves since each half animates independently.
     * Returns an empty list for non-block holders (player inventories, etc.).
     */
    static List<BlockKey> containerPositions(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            List<BlockKey> keys = new ArrayList<>(2);
            addHolderPosition(keys, doubleChest.getLeftSide());
            addHolderPosition(keys, doubleChest.getRightSide());
            return List.copyOf(keys);
        }
        if (holder instanceof BlockState state) {
            BlockKey key = blockKey(state.getLocation());
            return key == null ? List.of() : List.of(key);
        }
        return List.of();
    }

    private static void addHolderPosition(List<BlockKey> keys, InventoryHolder holder) {
        if (holder instanceof BlockState state) {
            BlockKey key = blockKey(state.getLocation());
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
        }
    }

    private static BlockKey blockKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new BlockKey(
                location.getWorld().getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    /**
     * Identifies a single animated container block.
     */
    public record BlockKey(UUID worldId, int x, int y, int z) {
        public BlockKey {
            Objects.requireNonNull(worldId, "worldId");
        }
    }
}
