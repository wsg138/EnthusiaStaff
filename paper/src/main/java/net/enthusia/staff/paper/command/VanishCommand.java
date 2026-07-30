package net.enthusia.staff.paper.command;

import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class VanishCommand implements CommandExecutor {
    private final Supplier<OperationalMode> mode;
    private final VanishManager vanish;

    public VanishCommand(Supplier<OperationalMode> mode, VanishManager vanish) {
        this.mode = mode;
        this.vanish = vanish;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can change vanish or spectator tab visibility."));
            return true;
        }
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("tab")) {
            if (arguments[1].equalsIgnoreCase("show")) {
                vanish.configureSpectatorTab(player, true);
                return true;
            }
            if (arguments[1].equalsIgnoreCase("hide")) {
                vanish.configureSpectatorTab(player, false);
                return true;
            }
        }
        if (arguments.length != 0) {
            player.sendMessage(Component.text("Usage: /" + label + " | /" + label + " tab <show|hide>"));
            return true;
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            player.sendMessage(Component.text("Vanish changes are disabled while moderation is " + mode.get() + '.'));
            return true;
        }
        vanish.toggle(player);
        return true;
    }
}
