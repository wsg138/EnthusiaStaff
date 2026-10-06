package net.enthusia.staff.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class StaffModeCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "enthusiastaff.staffmode";
    private static final String RECOVER = "recover";
    private static final String TAB = "tab";
    private static final String INVENTORY = "inventory";
    private static final String INVENTORY_SHORT = "inv";
    private static final String TOGGLE_VANISH = "togglevanish";
    private static final String SHOW = "show";
    private static final String HIDE = "hide";
    private static final int SINGLE_ARGUMENT = 1;
    private static final int TARGETED_RECOVERY_ARGUMENTS = 2;
    private static final int TAB_ARGUMENTS = 2;
    private static final List<String> ENTRY_OPTIONS =
            List.of("recover", "-v", "vanish", "-nv", "visible", "tab", INVENTORY, INVENTORY_SHORT);
    private static final List<String> TAB_OPTIONS = List.of(SHOW, HIDE);

    private final Supplier<OperationalMode> mode;
    private final StaffModeManager manager;
    private final StaffModeVanishEntryCoordinator entry;
    private final VanishManager vanish;

    public StaffModeCommand(
            Supplier<OperationalMode> mode,
            StaffModeManager manager,
            StaffModeVanishEntryCoordinator entry,
            VanishManager vanish
    ) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
        this.entry = java.util.Objects.requireNonNull(entry, "entry");
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!allowed(sender)) {
            return true;
        }
        if (targetedRecovery(arguments)) {
            return recoverTarget(sender, arguments[1]);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can enter staff mode."));
            return true;
        }
        if (spectatorTabRequested(arguments)) {
            return configureSpectatorTab(player, arguments[1]);
        }
        if (inventoryToggleRequested(arguments)) {
            manager.toggleToolInventory(player);
            return true;
        }
        if (vanishToggleRequested(arguments)) {
            vanish.toggle(player);
            return true;
        }
        return handlePlayer(player, arguments);
    }

    private static boolean allowed(CommandSender sender) {
        return CommandPermissionGate.require(
                sender,
                PERMISSION,
                "You do not have permission to use staff mode."
        );
    }

    private boolean recoverTarget(CommandSender sender, String targetName) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(StaffMessageStyle.error(
                    "Targeted snapshot recovery is available only from the server console."
            ));
            return true;
        }
        Player target = sender.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(StaffMessageStyle.error(
                    "That player must be online on the snapshot's owning backend."
            ));
            return true;
        }
        manager.recover(target);
        sender.sendMessage(StaffMessageStyle.info(
                "Durable snapshot recovery requested for " + target.getName() + '.'
        ));
        return true;
    }

    private boolean handlePlayer(Player player, String[] arguments) {
        if (recoveryRequested(arguments)) {
            player.sendMessage(StaffMessageStyle.style(
                    "Checking your durable staff-mode snapshot and repairing local state..."
            ));
            manager.recover(player);
            return true;
        }
        StaffModeVanishEntryOption option = StaffModeVanishEntryOption.parse(arguments).orElse(null);
        if (option == null) {
            player.sendMessage(StaffMessageStyle.usage(
                    "Usage: /staff [recover|-v|vanish|-nv|visible|inventory|tab <show|hide>]"
            ));
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
                player.sendMessage(StaffMessageStyle.style(
                        "Staff mode is already active. Use the vanish control to change visibility before exiting."
                ));
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
        if (arguments.length == SINGLE_ARGUMENT) {
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            return ENTRY_OPTIONS.stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (arguments.length == TAB_ARGUMENTS && TAB.equalsIgnoreCase(arguments[0])) {
            String prefix = arguments[1].toLowerCase(Locale.ROOT);
            return TAB_OPTIONS.stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private boolean configureSpectatorTab(Player player, String option) {
        if (SHOW.equalsIgnoreCase(option)) {
            vanish.configureSpectatorTab(player, true);
            return true;
        }
        if (HIDE.equalsIgnoreCase(option)) {
            vanish.configureSpectatorTab(player, false);
            return true;
        }
        player.sendMessage(StaffMessageStyle.usage("Usage: /staff tab <show|hide>"));
        return true;
    }

    private static boolean spectatorTabRequested(String[] arguments) {
        return arguments.length == TAB_ARGUMENTS && TAB.equalsIgnoreCase(arguments[0]);
    }

    private static boolean inventoryToggleRequested(String[] arguments) {
        return arguments.length == SINGLE_ARGUMENT
                && (INVENTORY.equalsIgnoreCase(arguments[0])
                        || INVENTORY_SHORT.equalsIgnoreCase(arguments[0]));
    }

    private static boolean vanishToggleRequested(String[] arguments) {
        return arguments.length == SINGLE_ARGUMENT && TOGGLE_VANISH.equalsIgnoreCase(arguments[0]);
    }

    private static boolean targetedRecovery(String[] arguments) {
        return arguments.length == TARGETED_RECOVERY_ARGUMENTS
                && RECOVER.equalsIgnoreCase(arguments[0]);
    }

    private static boolean recoveryRequested(String[] arguments) {
        return arguments.length == SINGLE_ARGUMENT && RECOVER.equalsIgnoreCase(arguments[0]);
    }
}
