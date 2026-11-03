package net.enthusia.staff.paper.command;

import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Schedule delayed command replies on the thread that owns the recipient.
 * Bukkit's legacy sync scheduler is not supported as a player owner thread
 * on regionized servers.
 */
final class PunishmentReplyScheduler {
    private PunishmentReplyScheduler() {
    }

    static void onSenderThread(Plugin plugin, CommandSender sender, Runnable action) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(action, "action");
        if (sender instanceof Player player) {
            player.getScheduler().execute(plugin, () -> {
                if (player.isOnline()) {
                    action.run();
                }
            }, null, 1L);
        } else {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, action);
        }
    }
}
