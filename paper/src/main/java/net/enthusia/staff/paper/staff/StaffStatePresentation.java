package net.enthusia.staff.paper.staff;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps staff/vanish state visible to the actor without mutating the actor's own PlayerInfo entry. */
public final class StaffStatePresentation implements Listener {
    private static final long REFRESH_TICKS = 10L;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final Set<UUID> indicatorVisible = ConcurrentHashMap.newKeySet();

    public StaffStatePresentation(JavaPlugin plugin, StaffModeManager staffMode, VanishManager vanish) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
    }

    public void start() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                if (!needsRefresh(playerId)) {
                    continue;
                }
                player.getScheduler().run(plugin, ignoredEntity -> refresh(player), null);
            }
        }, 1L, REFRESH_TICKS);
    }

    private boolean needsRefresh(UUID playerId) {
        return staffMode.active(playerId)
                || vanish.isVanished(playerId)
                || indicatorVisible.contains(playerId);
    }

    private void refresh(Player player) {
        UUID playerId = player.getUniqueId();
        boolean staffActive = staffMode.active(playerId);
        boolean vanished = vanish.isVanished(playerId);

        if (!staffActive && !vanished) {
            if (indicatorVisible.remove(playerId)) {
                player.sendActionBar(Component.empty());
            }
            return;
        }
        indicatorVisible.add(playerId);
        player.sendActionBar(indicator(staffActive, vanished));
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId())) {
            refresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        indicatorVisible.remove(event.getPlayer().getUniqueId());
    }
}
