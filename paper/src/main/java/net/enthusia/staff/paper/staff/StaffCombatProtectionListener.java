package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.paper.visibility.VanishManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Keeps protected on-duty or vanished staff clear of CombatLogX combat state.
 *
 * <p>Developer is intentionally exempt while on duty so technical combat testing is not
 * suppressed; those actions are audit-logged by Staff Mode. Damage edges for protected ranks
 * are rechecked one entity tick after the event so CombatLogX has completed its
 * own tagging work. A Folia-safe periodic reconciliation remains as recovery for integrations
 * that tag outside Bukkit damage events.</p>
 */
public final class StaffCombatProtectionListener implements Listener {
    private static final long SWEEP_PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final CombatStatusAdapter combat;

    public StaffCombatProtectionListener(
            JavaPlugin plugin,
            StaffModeManager staffMode,
            VanishManager vanish,
            CombatStatusAdapter combat
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> sweep(),
                SWEEP_PERIOD_TICKS,
                SWEEP_PERIOD_TICKS
        );
    }

    /** Schedules a protected-state reconciliation on the player's owning entity scheduler. */
    public void protect(Player player) {
        scheduleProtection(player, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleProtection(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            scheduleProtection(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event);
        if (attacker != null) {
            scheduleProtection(attacker, true);
        }
    }

    private Player attackingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }

    private void sweep() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (protectedState(player.getUniqueId())) {
                scheduleProtection(player, true);
            }
        }
    }

    private void scheduleProtection(Player player, boolean logRemoval) {
        if (player == null || !protectedState(player.getUniqueId())) {
            return;
        }
        if (staffMode.active(player.getUniqueId())
                && staffMode.dutyTier(player) == StaffDutyTier.DEVELOPER) {
            return;
        }
        try {
            player.getScheduler().execute(
                    plugin,
                    () -> reconcile(player, logRemoval),
                    null,
                    1L
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().fine("Combat protection scheduling failed for "
                    + player.getUniqueId() + ": " + exception.getMessage());
        }
    }

    private void reconcile(Player player, boolean logRemoval) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || !protectedState(playerId)) {
            return;
        }
        CombatStatusAdapter.Status status = combat.status(player);
        if (status != CombatStatusAdapter.Status.TAGGED) {
            return;
        }
        if (combat.untag(player) && logRemoval) {
            staffMode.logStaffAction(
                    player,
                    "combat-untag",
                    "CombatLogX tag removed while staff protection was active"
            );
        }
    }

    private boolean protectedState(UUID playerId) {
        return playerId != null && (staffMode.active(playerId) || vanish.isVanished(playerId));
    }
}
