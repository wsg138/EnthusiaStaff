package net.enthusia.staff.paper.command;

import java.util.Objects;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.enthusia.staff.paper.staff.StaffModeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StaffInventoryCommand implements CommandExecutor {
    private static final String PERMISSION = "enthusiastaff.staffmode";

    private final StaffModeManager staffMode;

    public StaffInventoryCommand(StaffModeManager staffMode) {
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(StaffMessageStyle.error("You do not have permission to change Staff inventory mode."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(StaffMessageStyle.error("Only a player can change Staff inventory mode."));
            return true;
        }
        if (arguments.length != 0) {
            sender.sendMessage(StaffMessageStyle.usage("Usage: /staffinv"));
            return true;
        }
        staffMode.toggleToolInventory(player);
        return true;
    }
}
