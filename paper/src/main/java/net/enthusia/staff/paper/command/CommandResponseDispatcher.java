package net.enthusia.staff.paper.command;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class CommandResponseDispatcher {
    private final JavaPlugin plugin;

    CommandResponseDispatcher(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    void send(CommandSender sender, Component message) {
        send(sender, List.of(message));
    }

    void send(CommandSender sender, List<Component> messages) {
        List<Component> immutable = List.copyOf(messages);
        Runnable delivery = () -> immutable.forEach(sender::sendMessage);
        try {
            if (sender instanceof Player player) {
                boolean scheduled = player.getScheduler().execute(
                        plugin,
                        delivery,
                        () -> plugin.getLogger().fine(
                                "Command response was discarded because the sender disconnected"
                        ),
                        1L
                );
                if (!scheduled) {
                    plugin.getLogger().fine("Command response sender is no longer schedulable");
                }
                return;
            }
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, delivery);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to deliver command response", exception);
        }
    }
}
