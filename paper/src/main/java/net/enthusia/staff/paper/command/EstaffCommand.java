package net.enthusia.staff.paper.command;

import java.util.Map;
import net.enthusia.staff.paper.RuntimeHealth;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

public final class EstaffCommand implements CommandExecutor {
    private static final String STATUS_PERMISSION = "enthusiastaff.status";
    private static final String VERIFY_PERMISSION = "enthusiastaff.verify";
    private static final String RELOAD_PERMISSION = "enthusiastaff.reload";

    private final RuntimeHealth health;

    public EstaffCommand(RuntimeHealth health) {
        this.health = health;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        String operation = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        String permission = permissionFor(operation);
        if (permission == null) {
            if (requirePermission(
                    sender,
                    STATUS_PERMISSION,
                    "You do not have permission to view EnthusiaStaff status."
            )) {
                sender.sendMessage("Usage: /" + label + " <status|verify|reload>");
            }
            return true;
        }
        if (!requirePermission(sender, permission, denialMessage(operation))) {
            return true;
        }
        if (operation.equals("reload")) {
            sender.sendMessage("EnthusiaStaff reload is unavailable until the modular configuration validator is active.");
            return true;
        }
        RuntimeHealth.Snapshot snapshot = health.snapshot();
        sender.sendMessage("EnthusiaStaff mode: " + snapshot.mode());
        if (snapshot.issues().isEmpty()) {
            sender.sendMessage("PASS: no active runtime health issues");
        } else {
            for (Map.Entry<String, String> issue : snapshot.issues().entrySet()) {
                sender.sendMessage("DISABLED " + issue.getKey() + ": " + issue.getValue());
            }
        }
        return true;
    }

    static boolean requirePermission(CommandSender sender, String permission, String denialMessage) {
        if (permission != null && !permission.isBlank() && sender instanceof ConsoleCommandSender) {
            return true;
        }
        return CommandPermissionGate.require(sender, permission, denialMessage);
    }

    private static String permissionFor(String operation) {
        return switch (operation) {
            case "status" -> STATUS_PERMISSION;
            case "verify" -> VERIFY_PERMISSION;
            case "reload" -> RELOAD_PERMISSION;
            default -> null;
        };
    }

    private static String denialMessage(String operation) {
        return switch (operation) {
            case "verify" -> "You do not have permission to verify EnthusiaStaff runtime state.";
            case "reload" -> "You do not have permission to reload EnthusiaStaff configuration.";
            default -> "You do not have permission to view EnthusiaStaff status.";
        };
    }
}
