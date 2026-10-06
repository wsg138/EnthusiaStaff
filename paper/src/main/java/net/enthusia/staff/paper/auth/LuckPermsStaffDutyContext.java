package net.enthusia.staff.paper.auth;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
    private final Set<UUID> activeSnapshot = ConcurrentHashMap.newKeySet();
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

    private void reconcileOnlinePlayers() {
        if (closed.get()) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            online.add(playerId);
            boolean unrestricted = staffMode.isUnrestricted(player);
            boolean active = staffMode.authorityActive(playerId) || unrestricted;
            boolean wasActive = activeSnapshot.contains(playerId);
            if (active == wasActive) {
                continue;
            }
            if (active) {
                activeSnapshot.add(playerId);
            } else {
                activeSnapshot.remove(playerId);
            }
            signalContextUpdate(player);
        }
        activeSnapshot.retainAll(online);
    }

    private void signalContextUpdate(Player player) {
        if (!closed.get()) {
            luckPerms.getContextManager().signalContextUpdate(player);
        }
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
        luckPerms.getContextManager().unregisterCalculator(calculator);
        activeSnapshot.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPerms.getContextManager().signalContextUpdate(player);
        }
    }
}
