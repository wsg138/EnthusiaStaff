package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezeCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "enthusiastaff.freeze";
    private static final int LIST_ARGUMENT_COUNT = 1;
    private static final int STATUS_ARGUMENT_COUNT = 2;
    private static final int SECOND_ARGUMENT_INDEX = 1;
    private static final int MAX_REASON_LENGTH = 512;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<FreezeStore> freezes;
    private final FreezeManager manager;
    private final ExecutorService workers;
    private final BiConsumer<CommandSender, List<Component>> responses;
    private final FreezeQueryHandler queries;

    public FreezeCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PlayerDirectory> players,
            Supplier<FreezeStore> freezes,
            FreezeManager manager,
            ExecutorService workers
    ) {
        this(plugin, clock, mode, players, freezes, manager, workers, commandResponses(plugin));
    }

    FreezeCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PlayerDirectory> players,
            Supplier<FreezeStore> freezes,
            FreezeManager manager,
            ExecutorService workers,
            BiConsumer<CommandSender, List<Component>> responses
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.mode = mode;
        this.players = players;
        this.freezes = freezes;
        this.manager = manager;
        this.workers = workers;
        this.responses = responses;
        this.queries = new FreezeQueryHandler(clock, players, freezes, this::respond);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(sender, PERMISSION, "You do not have permission to manage freezes.")) {
            return true;
        }
        boolean release = CommandRoute.canonicalName(command).equals("unfreeze");
        if (!release && routeRead(sender, arguments)) {
            return true;
        }
        OperationalMode currentMode = mode.get();
        if (currentMode != OperationalMode.ACTIVE) {
            sender.sendMessage(Component.text("Freeze changes are disabled while moderation is " + currentMode + '.'));
            return true;
        }
        return routeChange(sender, release, arguments);
    }

    private boolean routeRead(CommandSender sender, String[] arguments) {
        if (arguments.length == 0) {
            return false;
        }
        if (arguments[0].equalsIgnoreCase("status")) {
            if (arguments.length != STATUS_ARGUMENT_COUNT) {
                sender.sendMessage(Component.text("Usage: /freeze status <player|uuid>"));
            } else {
                submit(sender, () -> queries.status(sender, arguments[SECOND_ARGUMENT_INDEX]));
            }
            return true;
        }
        if (arguments[0].equalsIgnoreCase("list")) {
            if (arguments.length != LIST_ARGUMENT_COUNT) {
                sender.sendMessage(Component.text("Usage: /freeze list"));
            } else {
                submit(sender, () -> queries.list(sender));
            }
            return true;
        }
        return false;
    }

    private boolean routeChange(CommandSender sender, boolean release, String[] arguments) {
        boolean keep = !release && arguments.length > 0 && arguments[0].equalsIgnoreCase("keep");
        ChangeArguments parsed = parseChangeArguments(sender, release, keep, arguments);
        if (parsed == null) {
            return true;
        }
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        submit(sender, () -> change(
                sender,
                parsed.target(),
                actorId,
                parsed.reason(),
                release,
                parsed.keep()
        ));
        return true;
    }

    private static ChangeArguments parseChangeArguments(
            CommandSender sender,
            boolean release,
            boolean keep,
            String[] arguments
    ) {
        int targetIndex = keep ? SECOND_ARGUMENT_INDEX : 0;
        int reasonStart = targetIndex + 1;
        if (arguments.length <= reasonStart) {
            sender.sendMessage(Component.text(changeUsage(release)));
            return null;
        }
        boolean confirmed = arguments[arguments.length - 1].equals("CONFIRM");
        if (confirmationMissing(release, keep, confirmed)) {
            sender.sendMessage(Component.text("No change was made. Append the exact word CONFIRM to commit."));
            return null;
        }
        int reasonEnd = confirmed ? arguments.length - 1 : arguments.length;
        String reason = String.join(" ", Arrays.copyOfRange(arguments, reasonStart, reasonEnd)).trim();
        if (invalidReason(reason)) {
            sender.sendMessage(Component.text(
                    "A written reason of at most " + MAX_REASON_LENGTH + " characters is required."
            ));
            return null;
        }
        return new ChangeArguments(arguments[targetIndex], reason, keep);
    }

    private static boolean confirmationMissing(boolean release, boolean keep, boolean confirmed) {
        return (release || keep) && !confirmed;
    }

    private static boolean invalidReason(String reason) {
        return reason.isBlank() || reason.length() > MAX_REASON_LENGTH;
    }

    private static String changeUsage(boolean release) {
        if (release) {
            return "Usage: /unfreeze <player> <reason> CONFIRM";
        }
        return "Usage: /freeze <player> <reason> | /freeze keep <player> <reason> CONFIRM"
                + " | /freeze status <player|uuid> | /freeze list";
    }

    private void change(
            CommandSender sender,
            String targetInput,
            UUID actorId,
            String reason,
            boolean release,
            boolean keep
    ) {
        PlayerDirectory directory = players.get();
        FreezeStore store = freezes.get();
        if (directory == null || store == null) {
            respond(sender, "Freeze storage is not ready; no change was made.");
            return;
        }
        PlayerIdentity target = directory.find(targetInput).orElse(null);
        if (target == null) {
            respond(sender, "That player has never joined the authoritative directory.");
            return;
        }
        if (release) {
            boolean changed = store.release(target.playerId(), actorId, reason, clock.instant());
            if (changed) {
                manager.releaseOnline(target.playerId());
            }
            respond(sender, changed ? "Player freeze released and audited." : "That player is not frozen.");
        } else if (keep) {
            boolean changed = store.keepActive(target.playerId(), actorId, reason, clock.instant());
            respond(sender, changed
                    ? "Freeze will remain active beyond the offline timeout."
                    : "That player is not frozen.");
        } else {
            store.apply(target.playerId(), actorId, reason, clock.instant());
            manager.applyOnline(target.playerId());
            respond(sender, "Player frozen and durable recovery state committed.");
        }
    }

    private void submit(CommandSender sender, Runnable operation) {
        try {
            workers.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Freeze command failed", exception);
                    respond(sender, "Freeze operation failed; inspect the sanitized server log.");
                }
            });
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The bounded work queue is full; no freeze operation started."));
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!CommandPermissionGate.allows(sender::hasPermission, PERMISSION)) {
            return List.of();
        }
        boolean release = CommandRoute.canonicalName(command).equals("unfreeze");
        if (arguments.length == LIST_ARGUMENT_COUNT) {
            if (release) {
                return onlinePlayers(arguments[0]);
            }
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            List<String> candidates = new ArrayList<>(List.of("keep", "list", "status"));
            candidates.addAll(onlinePlayers(arguments[0]));
            return candidates.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(20)
                    .toList();
        }
        if (!release && arguments.length == STATUS_ARGUMENT_COUNT
                && (arguments[0].equalsIgnoreCase("keep") || arguments[0].equalsIgnoreCase("status"))) {
            return onlinePlayers(arguments[SECOND_ARGUMENT_INDEX]);
        }
        return List.of();
    }

    private List<String> onlinePlayers(String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(20)
                .toList();
    }

    private void respond(CommandSender sender, String message) {
        respond(sender, List.of(Component.text(message)));
    }

    private void respond(CommandSender sender, List<Component> messages) {
        responses.accept(sender, List.copyOf(messages));
    }

    private static BiConsumer<CommandSender, List<Component>> commandResponses(JavaPlugin plugin) {
        CommandResponseDispatcher dispatcher = new CommandResponseDispatcher(plugin);
        return dispatcher::send;
    }

    private record ChangeArguments(String target, String reason, boolean keep) {
    }
}
