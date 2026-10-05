package net.enthusia.staff.paper.staff;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.paper.visibility.VanishManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Admin/Founder double-crouch gameplay-mode selector.
 *
 * <p>Staff Mode, vanish, and game mode are independent. This listener toggles the real
 * Admin/Founder gameplay mode between Creative and Spectator without changing vanish. Lower
 * ranks keep their separate Staff Mode game-mode policy.</p>
 */
public final class StaffDoubleCrouchListener implements Listener {
    private static final long DOUBLE_CROUCH_WINDOW_MILLIS = 500L;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final Map<UUID, Long> lastSneakStart = new ConcurrentHashMap<>();

    public StaffDoubleCrouchListener(
            JavaPlugin plugin,
            StaffModeManager staffMode,
            VanishManager vanish
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!staffMode.active(playerId) || staffMode.dutyTier(player) != StaffDutyTier.ADMIN) {
            lastSneakStart.remove(playerId);
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSneakStart.put(playerId, now);
        if (previous == null || now - previous > DOUBLE_CROUCH_WINDOW_MILLIS) {
            return;
        }
        lastSneakStart.remove(playerId);
        player.getScheduler().execute(plugin, () -> toggleSelectedMode(player), null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastSneakStart.remove(event.getPlayer().getUniqueId());
    }

    private void toggleSelectedMode(Player player) {
        if (!player.isOnline()
                || !staffMode.active(player.getUniqueId())
                || staffMode.dutyTier(player) != StaffDutyTier.ADMIN) {
            return;
        }
        GameMode current = vanish.transferSelectedGameMode(player.getUniqueId())
                .orElse(GameMode.SPECTATOR);
        GameMode next = current == GameMode.CREATIVE ? GameMode.SPECTATOR : GameMode.CREATIVE;
        if (!vanish.selectGameplayMode(player, next)) {
            return;
        }
        staffMode.logStaffAction(
                player,
                "double-crouch-gamemode",
                "selected " + current + " -> " + next
        );
    }
}
