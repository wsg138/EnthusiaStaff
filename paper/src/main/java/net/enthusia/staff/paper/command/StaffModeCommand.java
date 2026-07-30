package net.enthusia.staff.paper.command;

import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StaffModeCommand implements CommandExecutor {
    private final Supplier<OperationalMode> mode;
    private final StaffModeManager manager;

    public StaffModeCommand(Supplier<OperationalMode> mode, StaffModeManager manager) {
        this.mode = mode;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can enter staff mode."));
            return true;
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            player.sendMessage(Component.text("Staff-mode transitions are disabled while moderation is " + mode.get() + '.'));
            return true;
        }
        if (manager.active(player.getUniqueId())) {
            manager.exit(player);
            return true;
        }
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            player.sendMessage(Component.text("An explicit EnthusiaStaff rank is required before entering staff mode."));
            return true;
        }
        manager.enter(player, rank);
        return true;
    }
}
