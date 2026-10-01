package net.enthusia.staff.paper.visibility;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.Plugin;

/** Prevents full-vanish state from leaking through AI or homing projectile targets. */
public final class VanishTargetingGuard implements Listener {
    private static final double RECONCILIATION_RADIUS = 128.0D;

    private final Plugin plugin;
    private final Predicate<UUID> vanished;
    private final Map<UUID, TrackedTarget> trackedTargets = new ConcurrentHashMap<>();

    public VanishTargetingGuard(Plugin plugin, Predicate<UUID> vanished) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.vanished = Objects.requireNonNull(vanished, "vanished");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetAcquisition(EntityTargetEvent event) {
        if (!isVanishedPlayer(event.getTarget())) {
            return;
        }
        event.setTarget(null);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetCommitted(EntityTargetEvent event) {
        track(event.getEntity(), event.getTarget());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShulkerBulletLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ShulkerBullet bullet) || !isVanishedPlayer(bullet.getTarget())) {
            return;
        }
        bullet.setTarget(null);
        trackedTargets.remove(bullet.getUniqueId());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShulkerBulletLaunched(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof ShulkerBullet bullet) {
            track(bullet, bullet.getTarget());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveEvent event) {
        trackedTargets.remove(event.getEntity().getUniqueId());
    }

    /**
     * Reconciles targets when a player enters or restores full vanish. Call this from the
     * player's entity-owned thread; candidate entities are mutated only on their own schedulers.
     */
    public void reconcile(Player player) {
        UUID targetId = player.getUniqueId();
        if (!vanished.test(targetId)) {
            return;
        }
        Set<UUID> scheduled = new HashSet<>();
        for (TrackedTarget tracked : trackedTargets.values()) {
            if (tracked.targetId().equals(targetId)) {
                scheduleClear(tracked.entity(), targetId, scheduled);
            }
        }
        for (Entity entity : player.getNearbyEntities(
                RECONCILIATION_RADIUS, RECONCILIATION_RADIUS, RECONCILIATION_RADIUS)) {
            scheduleClear(entity, targetId, scheduled);
        }
    }

    private void scheduleClear(Entity entity, UUID targetId, Set<UUID> scheduled) {
        if (!targetCapable(entity)) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        if (!scheduled.add(entityId)) {
            return;
        }
        entity.getScheduler().execute(
                plugin,
                () -> clearTarget(entity, targetId),
                () -> trackedTargets.remove(entityId),
                1L
        );
    }

    private void clearTarget(Entity entity, UUID targetId) {
        if (!vanished.test(targetId)) {
            return;
        }
        if (entity instanceof Mob mob && targets(mob.getTarget(), targetId)) {
            mob.setTarget(null);
            removeTrackedTarget(entity.getUniqueId(), targetId);
        }
        if (entity instanceof ShulkerBullet bullet && targets(bullet.getTarget(), targetId)) {
            bullet.remove();
            removeTrackedTarget(entity.getUniqueId(), targetId);
        }
    }

    private void track(Entity entity, Entity target) {
        if (!targetCapable(entity)) {
            return;
        }
        if (target instanceof Player player) {
            trackedTargets.put(entity.getUniqueId(), new TrackedTarget(entity, player.getUniqueId()));
        } else {
            trackedTargets.remove(entity.getUniqueId());
        }
    }

    private void removeTrackedTarget(UUID entityId, UUID targetId) {
        trackedTargets.computeIfPresent(
                entityId,
                (ignored, tracked) -> tracked.targetId().equals(targetId) ? null : tracked
        );
    }

    private boolean isVanishedPlayer(Entity target) {
        return target instanceof Player player && vanished.test(player.getUniqueId());
    }

    private static boolean targetCapable(Entity entity) {
        return entity instanceof Mob || entity instanceof ShulkerBullet;
    }

    private static boolean targets(Entity target, UUID playerId) {
        return target instanceof Player player && player.getUniqueId().equals(playerId);
    }

    private record TrackedTarget(Entity entity, UUID targetId) {
    }
}
