package net.enthusia.staff.paper.auth;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers the Staff Mode session context with LuckPerms.
 *
 * <p>This context is a permission-inheritance input, not the final authorization gate for a
 * destructive Staff action. Sensitive mutations must also revalidate the authoritative Staff
 * session immediately before commit.</p>
 */
public final class LuckPermsStaffDutyContext implements AutoCloseable, Listener {
    private static final long RECONCILE_TICKS = 2L;

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final StaffModeManager staffMode;
    private final StaffDutyContextCalculator calculator;
    private final StaffDutyOwnerReconciler reconciler = new StaffDutyOwnerReconciler();
    private final AtomicBoolean closed = new AtomicBoolean();

    private LuckPermsStaffDutyContext(JavaPlugin plugin, LuckPerms luckPerms, StaffModeManager staffMode) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        this.calculator = new StaffDutyContextCalculator(
                staffMode::authorityActive,
                staffMode::unrestricted
        );
    }

    public static LuckPermsStaffDutyContext install(JavaPlugin plugin, StaffModeManager staffMode) {
        LuckPermsStaffDutyContext registration = new LuckPermsStaffDutyContext(
                plugin,
                LuckPermsProvider.get(),
                staffMode
        );
        registration.luckPerms.getContextManager().registerCalculator(registration.calculator);
        plugin.getServer().getPluginManager().registerEvents(registration, plugin);
        registration.reconcileOnlinePlayers();
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> registration.reconcileOnlinePlayers(),
                1L,
                RECONCILE_TICKS
        );
        return registration;
    }

    private boolean scheduleOwner(Player player, Runnable operation, Runnable retired) {
        return player.getScheduler().execute(plugin, operation, retired, 1L);
    }

    private boolean liveDutyActive(Player player) {
        // Always refresh unrestricted identity cache, even if Staff Mode is active.
        boolean unrestricted = staffMode.isUnrestricted(player);
        return staffMode.authorityActive(player.getUniqueId()) || unrestricted;
    }

    private void reconcileOnlinePlayers() {
        if (closed.get()) {
            return;
        }
        // This global-region callback only enumerates UUIDs and queues entity tasks.
        // All live permission reads and LuckPerms signals execute on the player owner.
        reconciler.reconcile(
                plugin.getServer().getOnlinePlayers(),
                this::scheduleOwner,
                this::liveDutyActive,
                player -> luckPerms.getContextManager().signalContextUpdate(player),
                failure -> plugin.getLogger().log(
                        Level.WARNING, "Staff Mode LuckPerms duty context owner task failed", failure)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        reconciler.forget(event.getPlayer());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        reconciler.close();
        luckPerms.getContextManager().unregisterCalculator(calculator);
        // Plugin disable can run on the global region. Never call LuckPerms
        // signalContextUpdate(Player) directly from that thread.
        // Shutdown scheduling may be rejected: log rather than violating owner safety.
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                if (!scheduleOwner(player, () -> {
                    if (player.isOnline()) {
                        luckPerms.getContextManager().signalContextUpdate(player);
                    }
                }, () -> { })) {
                    plugin.getLogger().warning(
                            "LuckPerms duty context shutdown refresh rejected for " + player.getUniqueId());
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().log(
                        Level.WARNING, "LuckPerms duty context shutdown refresh unavailable", failure);
            }
        }
    }
}
