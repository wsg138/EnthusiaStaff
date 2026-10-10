package net.enthusia.staff.paper.audit;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Logs every staff command and sensitive action while a staffer is vanished or on duty, for every
 * rank: command executions, game-mode changes, and teleports. (Inventory inspections, punishments,
 * and freeze actions are covered because they are commands; item/container interactions are
 * logged by StaffModeManager's tiered handlers.)
 */
public final class StaffActionAuditListener implements Listener {
    private final StaffActionLogger logger;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;

    public StaffActionAuditListener(
            StaffActionLogger logger,
            StaffModeManager staffMode,
            VanishManager vanish
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!shouldAudit(player)) {
            return;
        }
        record(player, "command", StaffAuditCommandSummary.summarize(event.getMessage()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!shouldAudit(player)) {
            return;
        }
        record(player, "teleport",
                summarize(event.getFrom()) + " -> " + summarize(event.getTo())
                        + " cause=" + event.getCause());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!shouldAudit(player)) {
            return;
        }
        record(player, "gamemode-change",
                player.getGameMode() + " -> " + event.getNewGameMode());
    }

    private boolean shouldAudit(Player player) {
        UUID playerId = player.getUniqueId();
        return staffMode.active(playerId) || vanish.isVanished(playerId);
    }

    private void record(Player player, String action, String detail) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        boolean vanished = vanish.isVanished(playerId);
        boolean onDuty = staffMode.authorityActive(playerId);
        logger.log(playerId, playerName, rank, vanished, onDuty, action, detail);
    }

    private static String summarize(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + " " + location.getBlockX()
                + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
