package net.enthusia.staff.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class StaffModeCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "enthusiastaff.staffmode";
    private static final int SINGLE_ARGUMENT = 1;
    private static final List<String> ENTRY_OPTIONS = List.of("recover", "-v", "vanish", "-nv", "visible");

    private final Supplier<OperationalMode> mode;
    private final StaffModeManager manager;
    private final StaffModeVanishEntryCoordinator entry;

    public StaffModeCommand(
            Supplier<OperationalMode> mode,
            StaffModeManager manager,
            StaffModeVanishEntryCoordinator entry
    ) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
        this.entry = java.util.Objects.requireNonNull(entry, "entry");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(
                sender,
                PERMISSION,
                "You do not have permission to use staff mode."
        )) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can enter staff mode."));
            return true;
        }
        if (recoveryRequested(arguments)) {
            player.sendMessage(StaffMessageStyle.style(
                    "Checking your durable staff-mode snapshot and repairing local state..."
            ));
            manager.recover(player);
            return true;
        }
        StaffModeVanishEntryOption option = StaffModeVanishEntryOption.parse(arguments).orElse(null);
        if (option == null) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Usage: /staff [recover|-v|vanish|-nv|visible]"
            )));
            return true;
        }

        boolean activeSession = manager.active(player.getUniqueId());
        OperationalMode currentMode = mode.get();
        if (!StaffOperationalModeGate.staffModeTransitionAllowed(currentMode, activeSession)) {
            player.sendMessage(StaffMessageStyle.style(
                    "Staff-mode entry is disabled while moderation is " + currentMode + '.'
            ));
            return true;
        }
        if (activeSession) {
            if (option != StaffModeVanishEntryOption.REMEMBERED) {
                player.sendMessage(StaffMessageStyle.style(Component.text(
                        "Staff mode is already active. Use /vanish to change visibility before exiting."
                )));
                return true;
            }
            manager.exit(player);
            return true;
        }
        entry.enter(player, option);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (arguments.length != SINGLE_ARGUMENT) {
            return List.of();
        }
        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        return ENTRY_OPTIONS.stream()
                .filter(option -> option.startsWith(prefix))
                .toList();
    }

    private static boolean recoveryRequested(String[] arguments) {
        return arguments.length == SINGLE_ARGUMENT && "recover".equalsIgnoreCase(arguments[0]);
    }
}
