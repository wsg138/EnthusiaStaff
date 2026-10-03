package net.enthusia.staff.paper.staff;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owner-mandated: double-crouch (two sneak toggles within 500ms) while in staff
 * mode toggles the gamemode.
 *
 * <ul>
 *   <li>Admin/Founder: toggles between Spectator and Creative.</li>
 *   <li>Helper/Mod: sets Survival with fly enabled and invincibility
 *   (their maximum allowed state).</li>
 * </ul>
 *
 * <p>Only triggers on sneak-START (not sneak-stop) to avoid interfering with
 * normal crouching. Single crouches are unaffected.
 */
public final class StaffDoubleCrouchListener implements Listener {
    private static final long DOUBLE_CROUCH_WINDOW_MS = 500L;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final Map<UUID, Long> lastSneakStart = new ConcurrentHashMap<>();

    public StaffDoubleCrouchListener(JavaPlugin plugin, StaffModeManager staffMode) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        // Only sneak-start, not sneak-stop.
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!staffMode.active(playerId)) {
            lastSneakStart.remove(playerId);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSneakStart.put(playerId, now);
        if (last == null || now - last > DOUBLE_CROUCH_WINDOW_MS) {
            return; // First crouch or outside window — wait for the second.
        }
        // Double-crouch detected. Clear so a third quick crouch doesn't retrigger.
        lastSneakStart.remove(playerId);
        // Run on the main thread next tick to avoid interfering with the sneak event.
        plugin.getServer().getScheduler().runTask(plugin, () -> toggleGameMode(player));
    }

    private void toggleGameMode(Player player) {
        if (!player.isOnline() || !staffMode.active(player.getUniqueId())) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (tier == null) {
            return;
        }
        switch (tier) {
            case ADMIN -> {
                GameMode current = player.getGameMode();
                GameMode next = current == GameMode.SPECTATOR ? GameMode.CREATIVE : GameMode.SPECTATOR;
                player.setGameMode(next);
                staffMode.logStaffAction(player, "double-crouch-gamemode",
                        current + " -> " + next);
            }
            case HELPER, MOD -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setInvulnerable(true);
                staffMode.logStaffAction(player, "double-crouch-gamemode",
                        " -> SURVIVAL (fly + invincible)");
            }
        }
    }
}
