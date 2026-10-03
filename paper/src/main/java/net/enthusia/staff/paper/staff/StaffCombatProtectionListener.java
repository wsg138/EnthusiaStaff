package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.enthusia.staff.paper.visibility.VanishManager;

/**
 * Owner-mandated: no staff member may ever be put in combat while on duty or
 * vanished, regardless of rank (including Admin and Founder).
 *
 * <p>CombatLogX tags players on damage dealt/received. Staff in protected mode
 * already have damage cancelled, but tags can still land via edge cases (splash
 * potions, environmental triggers, pre-cancellation tag events). This listener
 * grants CombatLogX bypass by actively untagging protected staff:
 *
 * <ul>
 *   <li>On staff-mode enter and vanish enable: immediate untag.</li>
 *   <li>Periodic sweep (every 5 seconds): untag any on-duty or vanished player
 *   who is somehow tagged.</li>
 * </ul>
 */
public final class StaffCombatProtectionListener implements Listener {
    private static final long SWEEP_PERIOD_TICKS = 100L; // 5 seconds

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
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Untag a player immediately (e.g. on staff-mode enter or vanish enable). */
    public void protect(Player player) {
        if (player != null && player.isOnline()) {
            combat.untag(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (staffMode.active(playerId) || vanish.isVanished(playerId)) {
            // Defer one tick so CombatLogX has finished its own join handling.
            plugin.getServer().getScheduler().runTask(plugin, () -> protect(player));
        }
    }

    private void sweep() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!staffMode.active(playerId) && !vanish.isVanished(playerId)) {
                continue;
            }
            if (combat.status(player) == CombatStatusAdapter.Status.TAGGED) {
                combat.untag(player);
                staffMode.logStaffAction(player, "combat-untag",
                        "CombatLogX tag removed (staff combat bypass)");
            }
        }
    }
}
