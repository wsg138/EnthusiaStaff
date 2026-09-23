package net.enthusia.staff.paper;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Delivers player-facing messages from the player-owned entity scheduler.
 */
public final class PlayerMessageDispatcher {
    private static final long NEXT_TICK = 1L;

    private final Plugin plugin;

    public PlayerMessageDispatcher(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void send(Player player, Component message) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        EntityScheduler scheduler = player.getScheduler();
        try {
            boolean scheduled = scheduler.execute(
                    plugin,
                    () -> player.sendMessage(message),
                    () -> plugin.getLogger().fine(
                            "Player message was discarded because the player disconnected"
                    ),
                    NEXT_TICK
            );
            if (!scheduled) {
                plugin.getLogger().fine("Player message recipient is no longer schedulable");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to schedule player message", exception);
        }
    }
}
