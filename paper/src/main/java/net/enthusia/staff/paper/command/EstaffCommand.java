package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.paper.RuntimeHealth;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

public final class EstaffCommand implements CommandExecutor, TabCompleter {
    private static final String STATUS_PERMISSION = "enthusiastaff.status";
    private static final String VERIFY_PERMISSION = "enthusiastaff.verify";
    private static final String RELOAD_PERMISSION = "enthusiastaff.reload";
    private static final int MAX_RELOAD_DETAILS = 5;

    private final RuntimeHealth health;
    private final ConfigurationReloadAction reloadAction;

    public EstaffCommand(RuntimeHealth health) {
        this(
                health,
                () -> new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.APPLY_FAILED,
                        "EnthusiaStaff reload is unavailable",
                        List.of(),
                        false
                )
        );
    }

    public EstaffCommand(RuntimeHealth health, ConfigurationReloadAction reloadAction) {
        this.health = Objects.requireNonNull(health, "health");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        String operation = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
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
            reportReload(sender, reloadAction.reload());
            return true;
        }
        reportStatus(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        addCompletion(sender, matches, prefix, "status", STATUS_PERMISSION);
        addCompletion(sender, matches, prefix, "verify", VERIFY_PERMISSION);
        addCompletion(sender, matches, prefix, "reload", RELOAD_PERMISSION);
        return List.copyOf(matches);
    }

    static boolean requirePermission(CommandSender sender, String permission, String denialMessage) {
        if (permission != null && !permission.isBlank() && sender instanceof ConsoleCommandSender) {
            return true;
        }
        return CommandPermissionGate.require(sender, permission, denialMessage);
    }

    private void reportStatus(CommandSender sender) {
        RuntimeHealth.Snapshot snapshot = health.snapshot();
        sender.sendMessage("EnthusiaStaff mode: " + snapshot.mode());
        if (snapshot.issues().isEmpty()) {
            sender.sendMessage("PASS: no active runtime health issues");
            return;
        }
        for (Map.Entry<String, String> issue : snapshot.issues().entrySet()) {
            sender.sendMessage("DISABLED " + issue.getKey() + ": " + issue.getValue());
        }
    }

    private static void reportReload(CommandSender sender, ConfigurationReloadResult result) {
        sender.sendMessage(result.message());
        int shown = Math.min(result.details().size(), MAX_RELOAD_DETAILS);
        for (int index = 0; index < shown; index++) {
            sender.sendMessage("- " + result.details().get(index));
        }
        if (result.details().size() > shown) {
            sender.sendMessage("Additional sanitized reload details were written to the server log.");
        }
        if (result.reasonPoliciesReloaded()) {
            sender.sendMessage("Reason policies were replaced atomically.");
        }
    }

    private static void addCompletion(
            CommandSender sender,
            List<String> completions,
            String prefix,
            String operation,
            String permission
    ) {
        if (operation.startsWith(prefix) && allowedWithoutMessage(sender, permission)) {
            completions.add(operation);
        }
    }

    private static boolean allowedWithoutMessage(CommandSender sender, String permission) {
        return sender instanceof ConsoleCommandSender || sender.hasPermission(permission);
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
