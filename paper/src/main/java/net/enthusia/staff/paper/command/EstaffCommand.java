package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.enthusia.staff.paper.RuntimeHealth;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class EstaffCommand implements CommandExecutor, TabCompleter {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(
            EstaffCommand.class.getName()
    );
    private static final String STATUS_PERMISSION = "enthusiastaff.status";
    private static final String VERIFY_PERMISSION = "enthusiastaff.verify";
    private static final String RELOAD_PERMISSION = "enthusiastaff.reload";
    private static final int MAX_RELOAD_DETAILS = 5;

    private final RuntimeHealth health;
    private final ConfigurationReloadAction reloadAction;
    private final ReloadDispatcher reloadDispatcher;
    private final CopyOnWriteArrayList<Runnable> successfulReloadHooks = new CopyOnWriteArrayList<>();
    private volatile SanctionLifecycleCommand sanctionLifecycle;

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
        this(health, reloadAction, ReloadDispatcher.immediate());
    }

    public EstaffCommand(
            JavaPlugin plugin,
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction
    ) {
        this(health, reloadAction, ReloadDispatcher.folia(plugin));
    }

    EstaffCommand(
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction,
            ReloadDispatcher reloadDispatcher
    ) {
        this.health = Objects.requireNonNull(health, "health");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.reloadDispatcher = Objects.requireNonNull(reloadDispatcher, "reloadDispatcher");
    }

    public void configureSanctionLifecycle(SanctionLifecycleCommand lifecycle) {
        sanctionLifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public void addSuccessfulReloadHook(Runnable hook) {
        successfulReloadHooks.add(Objects.requireNonNull(hook, "hook"));
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        SanctionLifecycleCommand lifecycle = sanctionLifecycle;
        if (args.length > 0 && args[0].equalsIgnoreCase("sanction") && lifecycle != null) {
            return lifecycle.execute(sender, label, args);
        }

        String operation = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        String permission = permissionFor(operation);
        if (permission == null) {
            if (requirePermission(
                    sender,
                    STATUS_PERMISSION,
                    "You do not have permission to view EnthusiaStaff status."
            )) {
                sender.sendMessage("Usage: /" + label + " <status|verify|reload|sanction>");
            }
            return true;
        }
        if (!requirePermission(sender, permission, denialMessage(operation))) {
            return true;
        }
        if (operation.equals("reload")) {
            dispatchReload(sender);
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
        SanctionLifecycleCommand lifecycle = sanctionLifecycle;
        if (args.length > 0 && args[0].equalsIgnoreCase("sanction") && lifecycle != null) {
            return lifecycle.complete(sender, args);
        }
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        addCompletion(sender, matches, prefix, "status", STATUS_PERMISSION);
        addCompletion(sender, matches, prefix, "verify", VERIFY_PERMISSION);
        addCompletion(sender, matches, prefix, "reload", RELOAD_PERMISSION);
        if (lifecycle != null && "sanction".startsWith(prefix) && hasAnySanctionPermission(sender)) {
            matches.add("sanction");
        }
        return List.copyOf(matches);
    }

    static boolean requirePermission(CommandSender sender, String permission, String denialMessage) {
        if (permission != null && !permission.isBlank() && sender instanceof ConsoleCommandSender) {
            return true;
        }
        return CommandPermissionGate.require(sender, permission, denialMessage);
    }

    private void dispatchReload(CommandSender sender) {
        ReloadDispatch dispatch = reloadDispatcher.dispatch(
                sender,
                reloadAction,
                result -> reportReload(sender, result)
        );
        if (dispatch == ReloadDispatch.SCHEDULED) {
            sender.sendMessage("EnthusiaStaff reload scheduled on the global region thread.");
        } else if (dispatch == ReloadDispatch.REJECTED) {
            sender.sendMessage("EnthusiaStaff reload could not be scheduled; no configuration was changed.");
        }
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

    private void reportReload(CommandSender sender, ConfigurationReloadResult result) {
        if (result.successful()) {
            for (Runnable hook : successfulReloadHooks) {
                try {
                    hook.run();
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.WARNING, "Successful reload hook failed", exception);
                    sender.sendMessage(
                            "Reload applied, but a presentation-settings hook failed; previous values remain active."
                    );
                }
            }
        }
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

    private static boolean hasAnySanctionPermission(CommandSender sender) {
        return sender instanceof ConsoleCommandSender
                || sender.hasPermission(SanctionLifecycleCommand.REDUCE_PERMISSION)
                || sender.hasPermission(SanctionLifecycleCommand.END_PERMISSION)
                || sender.hasPermission(SanctionLifecycleCommand.REVOKE_PERMISSION)
                || sender.hasPermission(SanctionLifecycleCommand.OVERTURN_PERMISSION);
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

    enum ReloadDispatch {
        COMPLETED,
        SCHEDULED,
        REJECTED
    }

    @FunctionalInterface
    interface ReloadDispatcher {
        ReloadDispatch dispatch(
                CommandSender sender,
                ConfigurationReloadAction action,
                Consumer<ConfigurationReloadResult> reporter
        );

        static ReloadDispatcher immediate() {
            return (sender, action, reporter) -> {
                reporter.accept(action.reload());
                return ReloadDispatch.COMPLETED;
            };
        }

        static ReloadDispatcher folia(JavaPlugin plugin) {
            Objects.requireNonNull(plugin, "plugin");
            return (sender, action, reporter) -> {
                if (!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) {
                    return ReloadDispatch.REJECTED;
                }
                try {
                    plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                        ConfigurationReloadResult result = safeReload(plugin, action);
                        if (sender instanceof Player player) {
                            dispatchPlayerResult(plugin, player, reporter, result);
                        } else {
                            reporter.accept(result);
                        }
                    });
                    return ReloadDispatch.SCHEDULED;
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(
                            Level.WARNING,
                            "EnthusiaStaff reload could not be scheduled on the global region thread",
                            exception
                    );
                    return ReloadDispatch.REJECTED;
                }
            };
        }

        private static ConfigurationReloadResult safeReload(
                JavaPlugin plugin,
                ConfigurationReloadAction action
        ) {
            try {
                return action.reload();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "EnthusiaStaff reload failed unexpectedly", exception);
                return new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.APPLY_FAILED,
                        "Reload failed unexpectedly; previous runtime state was retained where possible",
                        List.of("See the sanitized server log for the failure category"),
                        false
                );
            }
        }

        private static void dispatchPlayerResult(
                JavaPlugin plugin,
                Player player,
                Consumer<ConfigurationReloadResult> reporter,
                ConfigurationReloadResult result
        ) {
            try {
                boolean scheduled = player.getScheduler().execute(
                        plugin,
                        () -> reporter.accept(result),
                        () -> plugin.getLogger().fine(
                                "Reload result was not delivered because the command sender disconnected"
                        ),
                        1L
                );
                if (!scheduled) {
                    plugin.getLogger().fine(
                            "Reload result was not delivered because the command sender is no longer schedulable"
                    );
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                        Level.FINE,
                        "Reload result could not be returned to the command sender",
                        exception
                );
            }
        }
    }
}
