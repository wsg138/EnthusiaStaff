package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
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
    private static final String DIAGNOSTICS_PERMISSION = "enthusiastaff.diagnostics";
    private static final String RELOAD_PERMISSION = "enthusiastaff.reload";
    private static final String STATUS_OPERATION = "status";
    private static final String VERIFY_OPERATION = "verify";
    private static final String FULL_VERIFICATION_ARGUMENT = "full";
    private static final String RELOAD_OPERATION = "reload";
    private static final String SANCTION_OPERATION = "sanction";
    private static final int MAX_RELOAD_DETAILS = 5;

    private final RuntimeHealth health;
    private final ConfigurationReloadAction reloadAction;
    private final ReloadDispatcher reloadDispatcher;
    private final JavaPlugin runtimePlugin;
    private final FullVerificationAction fallbackFullVerification;
    private final CopyOnWriteArrayList<Runnable> successfulReloadHooks = new CopyOnWriteArrayList<>();
    private volatile BooleanSupplier storagePublished = () -> false;
    private volatile SanctionLifecycleCommand sanctionLifecycle;

    public EstaffCommand(RuntimeHealth health) {
        this(
                health,
                () -> new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.APPLY_FAILED,
                        "EnthusiaStaff reload is unavailable",
                        List.of(),
                        false
                ),
                ReloadDispatcher.immediate(),
                null,
                () -> List.of("WARNING full verification is unavailable without the Paper runtime.")
        );
    }

    public EstaffCommand(RuntimeHealth health, ConfigurationReloadAction reloadAction) {
        this(
                health,
                reloadAction,
                ReloadDispatcher.immediate(),
                null,
                () -> List.of("WARNING full verification is unavailable without the Paper runtime.")
        );
    }

    public EstaffCommand(JavaPlugin plugin, RuntimeHealth health) {
        this(
                plugin,
                health,
                () -> new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.APPLY_FAILED,
                        "EnthusiaStaff reload is unavailable",
                        List.of(),
                        false
                )
        );
    }

    public EstaffCommand(
            JavaPlugin plugin,
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction
    ) {
        this(health, reloadAction, ReloadDispatcher.folia(plugin), plugin, List::of);
    }

    EstaffCommand(
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction,
            ReloadDispatcher reloadDispatcher
    ) {
        this(
                health,
                reloadAction,
                reloadDispatcher,
                null,
                () -> List.of("WARNING full verification is unavailable without the Paper runtime.")
        );
    }

    EstaffCommand(
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction,
            ReloadDispatcher reloadDispatcher,
            FullVerificationAction fallbackFullVerification
    ) {
        this(health, reloadAction, reloadDispatcher, null, fallbackFullVerification);
    }

    private EstaffCommand(
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction,
            ReloadDispatcher reloadDispatcher,
            JavaPlugin runtimePlugin,
            FullVerificationAction fallbackFullVerification
    ) {
        this.health = Objects.requireNonNull(health, "health");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.reloadDispatcher = Objects.requireNonNull(reloadDispatcher, "reloadDispatcher");
        this.runtimePlugin = runtimePlugin;
        this.fallbackFullVerification = Objects.requireNonNull(fallbackFullVerification, "fallbackFullVerification");
    }

    public void configureSanctionLifecycle(SanctionLifecycleCommand lifecycle) {
        sanctionLifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public void addSuccessfulReloadHook(Runnable hook) {
        successfulReloadHooks.add(Objects.requireNonNull(hook, "hook"));
    }

    public void configureStorageAvailability(BooleanSupplier storagePublished) {
        this.storagePublished = Objects.requireNonNull(storagePublished, "storagePublished");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        SanctionLifecycleCommand lifecycle = sanctionLifecycle;
        if (args.length > 0 && args[0].equalsIgnoreCase(SANCTION_OPERATION) && lifecycle != null) {
            return lifecycle.execute(sender, label, args);
        }

        String operation = args.length == 0 ? STATUS_OPERATION : args[0].toLowerCase(Locale.ROOT);
        String permission = permissionFor(operation);
        if (permission == null) {
            if (requirePermission(
                    sender,
                    STATUS_PERMISSION,
                    "You do not have permission to view EnthusiaStaff status."
            )) {
                reportUsage(sender, label);
            }
            return true;
        }
        if (!requirePermission(sender, permission, denialMessage(operation))) {
            return true;
        }
        if (operation.equals(RELOAD_OPERATION)) {
            if (args.length != 1) {
                reportUsage(sender, label);
                return true;
            }
            dispatchReload(sender);
            return true;
        }
        if (operation.equals(VERIFY_OPERATION)) {
            if (args.length == 1) {
                reportStatus(sender);
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase(FULL_VERIFICATION_ARGUMENT)) {
                if (requirePermission(
                        sender,
                        DIAGNOSTICS_PERMISSION,
                        "You do not have permission to run full EnthusiaStaff diagnostics."
                )) {
                    reportFullVerification(sender);
                }
                return true;
            }
            reportUsage(sender, label);
            return true;
        }
        if (args.length > 1) {
            reportUsage(sender, label);
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
        if (args.length > 0 && args[0].equalsIgnoreCase(SANCTION_OPERATION) && lifecycle != null) {
            return lifecycle.complete(sender, args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase(VERIFY_OPERATION)) {
            if (allowedWithoutMessage(sender, VERIFY_PERMISSION)
                    && allowedWithoutMessage(sender, DIAGNOSTICS_PERMISSION)
                    && FULL_VERIFICATION_ARGUMENT.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                return List.of(FULL_VERIFICATION_ARGUMENT);
            }
            return List.of();
        }
        if (args.length > 1) {
            return List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        addCompletion(sender, matches, prefix, STATUS_OPERATION, STATUS_PERMISSION);
        addCompletion(sender, matches, prefix, VERIFY_OPERATION, VERIFY_PERMISSION);
        addCompletion(sender, matches, prefix, RELOAD_OPERATION, RELOAD_PERMISSION);
        if (lifecycle != null && SANCTION_OPERATION.startsWith(prefix)
                && hasAnySanctionPermission(sender)) {
            matches.add(SANCTION_OPERATION);
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

    private void reportFullVerification(CommandSender sender) {
        try {
            List<String> messages = runtimePlugin == null
                    ? fallbackFullVerification.verify()
                    : new FullRuntimeVerifier(runtimePlugin, health, storagePublished).verify();
            messages.forEach(sender::sendMessage);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Full EnthusiaStaff verification failed", exception);
            sender.sendMessage("CRITICAL full verification failed; see the sanitized server log.");
        }
    }

    private static void reportUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " <status|verify [full]|reload|sanction>");
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
            case STATUS_OPERATION -> STATUS_PERMISSION;
            case VERIFY_OPERATION -> VERIFY_PERMISSION;
            case RELOAD_OPERATION -> RELOAD_PERMISSION;
            default -> null;
        };
    }

    private static String denialMessage(String operation) {
        return switch (operation) {
            case VERIFY_OPERATION -> "You do not have permission to verify EnthusiaStaff runtime state.";
            case RELOAD_OPERATION -> "You do not have permission to reload EnthusiaStaff configuration.";
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

    @FunctionalInterface
    interface FullVerificationAction {
        List<String> verify();
    }
}
