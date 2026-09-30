package net.enthusia.staff.paper.staff;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps staff/vanish state visible and closes unsafe staff-mode death sessions. */
public final class StaffStatePresentation implements Listener {
    private static final long REFRESH_TICKS = 10L;
    private static final long DEATH_EXIT_RETRY_TICKS = 40L;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final NamespacedKey deathExitKey;
    private final Set<UUID> indicatorVisible = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deathExitRequested = ConcurrentHashMap.newKeySet();

    public StaffStatePresentation(JavaPlugin plugin, StaffModeManager staffMode, VanishManager vanish) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
        this.deathExitKey = new NamespacedKey(plugin, "staff_death_exit");
    }

    public void start() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!needsRefresh(player)) {
                    continue;
                }
                player.getScheduler().run(plugin, ignoredEntity -> refresh(player), null);
            }
        }, 1L, REFRESH_TICKS);
    }

    private boolean needsRefresh(Player player) {
        UUID playerId = player.getUniqueId();
        return staffMode.active(playerId)
                || vanish.isVanished(playerId)
                || indicatorVisible.contains(playerId)
                || deathExitRequested.contains(playerId)
                || hasDeathExitMarker(player);
    }

    private void refresh(Player player) {
        UUID playerId = player.getUniqueId();
        boolean staffActive = staffMode.active(playerId);
        boolean vanished = vanish.isVanished(playerId);

        reconcileDeathExit(player, staffActive);
        if (vanished) {
            unlistSelf(player);
        }
        if (!staffActive && !vanished) {
            if (indicatorVisible.remove(playerId)) {
                player.sendActionBar(Component.empty());
            }
            return;
        }
        indicatorVisible.add(playerId);
        player.sendActionBar(indicator(staffActive, vanished));
    }

    private void reconcileDeathExit(Player player, boolean staffActive) {
        UUID playerId = player.getUniqueId();
        if (!hasDeathExitMarker(player)) {
            deathExitRequested.remove(playerId);
            return;
        }
        if (!staffActive) {
            if (deathExitRequested.remove(playerId)) {
                clearDeathExitMarker(player);
            }
            return;
        }
        if (!deathExitRequested.add(playerId)) {
            return;
        }
        staffMode.exit(player);
        player.getScheduler().runDelayed(plugin, ignored -> {
            if (staffMode.active(playerId) && hasDeathExitMarker(player)) {
                deathExitRequested.remove(playerId);
            }
        }, null, DEATH_EXIT_RETRY_TICKS);
    }

    private boolean hasDeathExitMarker(Player player) {
        return player.getPersistentDataContainer().has(deathExitKey, PersistentDataType.BYTE);
    }

    private void markDeathExit(Player player) {
        player.getPersistentDataContainer().set(deathExitKey, PersistentDataType.BYTE, (byte) 1);
        deathExitRequested.remove(player.getUniqueId());
    }

    private void clearDeathExitMarker(Player player) {
        player.getPersistentDataContainer().remove(deathExitKey);
    }

    private static void unlistSelf(Player player) {
        try {
            player.unlistPlayer(player);
        } catch (IllegalStateException ignored) {
            // A disconnect can race a presentation refresh; the next session reconciles normally.
        }
    }

    static Component indicator(boolean staffActive, boolean vanished) {
        if (!staffActive) {
            return Component.text("VANISH", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD));
        }
        return Component.text("STAFF MODE", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("VANISH ", NamedTextColor.GRAY))
                .append(Component.text(
                        vanished ? "ON" : "OFF",
                        vanished ? NamedTextColor.GREEN : NamedTextColor.GOLD,
                        TextDecoration.BOLD
                ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!staffMode.active(player.getUniqueId())) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        markDeathExit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (hasDeathExitMarker(player)) {
            player.getScheduler().run(plugin, ignored -> refresh(player), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId())) {
            refresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        indicatorVisible.remove(playerId);
        deathExitRequested.remove(playerId);
    }
}
