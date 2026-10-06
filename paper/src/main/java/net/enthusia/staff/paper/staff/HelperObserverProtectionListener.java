package net.enthusia.staff.paper.staff;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps the Helper staff-mode profile observational instead of allowing normal survival participation.
 * Existing StaffModeManager and world-interaction guards continue to own damage, inventory, block,
 * pickup/drop and staff-tool session protections; this listener fills the Helper-specific gaps.
 */
public final class HelperObserverProtectionListener implements Listener {
    private static final long TARGET_RECONCILE_TICKS = 20L;
    private static final double TARGET_RECONCILE_RADIUS = 64.0;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;

    public HelperObserverProtectionListener(StaffModeManager staffMode) {
        this.plugin = JavaPlugin.getProvidingPlugin(HelperObserverProtectionListener.class);
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        startRetainedTargetReconciliation();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAirItemUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (HelperObserverPolicy.blocksAirItemUse(
                activeHelper(event.getPlayer()),
                event.getAction(),
                item != null && !item.getType().isAir()
        )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && activeHelper(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player player)) {
            return;
        }
        if (!HelperObserverPolicy.blocksProjectileCollision(activeHelper(player), true)) {
            return;
        }
        event.setCancelled(true);
        clearMobTarget(event.getEntity(), player);
    }

    /**
     * Paper's modern ProjectileHitEvent has a documented firework exception. This deprecated event is
     * intentionally isolated to that one compatibility case because cancelling it explicitly lets the
     * firework continue flying instead of colliding with the Helper observer.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkCollision(ProjectileCollideEvent event) {
        if (!(event.getEntity() instanceof Firework)
                || !(event.getCollidedWith() instanceof Player player)) {
            return;
        }
        if (HelperObserverPolicy.blocksProjectileCollision(activeHelper(player), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && activeHelper(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && activeHelper(player)) {
            clearMobTarget(event.getDamager(), player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExperiencePickup(PlayerPickupExperienceEvent event) {
        if (activeHelper(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExperienceChange(PlayerExpChangeEvent event) {
        event.setAmount(HelperObserverPolicy.experienceAmount(
                activeHelper(event.getPlayer()),
                event.getAmount()
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        if (activeHelper(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        if (activeHelper(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && activeHelper(player)) {
            event.setCancelled(true);
        }
    }

    private void startRetainedTargetReconciliation() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!staffMode.active(player.getUniqueId())) {
                    continue;
                }
                player.getScheduler().run(plugin, ignoredPlayer -> reconcileRetainedTargets(player), null);
            }
        }, 1L, TARGET_RECONCILE_TICKS);
    }

    private void reconcileRetainedTargets(Player player) {
        if (!activeHelper(player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(
                TARGET_RECONCILE_RADIUS,
                TARGET_RECONCILE_RADIUS,
                TARGET_RECONCILE_RADIUS
        )) {
            if (!(nearby instanceof Mob mob)) {
                continue;
            }
            clearMobTarget(mob, playerId);
        }
    }

    private boolean activeHelper(Player player) {
        return !staffMode.isUnrestricted(player)
                && staffMode.helperObserverActive(player.getUniqueId());
    }

    private void clearMobTarget(Object damager, Player target) {
        clearMobTarget(damager, target.getUniqueId());
    }

    private void clearMobTarget(Object damager, UUID targetId) {
        Mob mob = null;
        if (damager instanceof Mob direct) {
            mob = direct;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Mob shooter) {
            mob = shooter;
        }
        if (mob == null) {
            return;
        }
        Mob targetMob = mob;
        targetMob.getScheduler().run(plugin, ignoredMob -> {
            if (!staffMode.helperObserverActive(targetId)
                    || staffMode.authorityActiveOrUnrestricted(targetId)
                            && !staffMode.authorityActive(targetId)) {
                return;
            }
            var currentTarget = targetMob.getTarget();
            if (currentTarget != null && targetId.equals(currentTarget.getUniqueId())) {
                targetMob.setTarget(null);
            }
        }, null);
    }
}
