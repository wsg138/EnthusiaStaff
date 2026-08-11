package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezeCommand implements CommandExecutor {
    private static final String PERMISSION = "enthusiastaff.freeze";

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<FreezeStore> freezes;
    private final FreezeManager manager;
    private final ExecutorService workers;

    public FreezeCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PlayerDirectory> players,
            Supplier<FreezeStore> freezes,
            FreezeManager manager,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.mode = mode;
        this.players = players;
        this.freezes = freezes;
        this.manager = manager;
        this.workers = workers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(sender, PERMISSION, "You do not have permission to manage freezes.")) {
            return true;
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            sender.sendMessage(Component.text("Freeze changes are disabled while moderation is " + mode.get() + '.'));
            return true;
        }
        boolean release = CommandRoute.canonicalName(command).equals("unfreeze");
        boolean keep = !release && arguments.length > 0 && arguments[0].equalsIgnoreCase("keep");
        int targetIndex = keep ? 1 : 0;
        int reasonStart = targetIndex + 1;
        if (arguments.length <= reasonStart) {
            sender.sendMessage(Component.text(release
                    ? "Usage: /unfreeze <player> <reason> CONFIRM"
                    : "Usage: /freeze <player> <reason> | /freeze keep <player> <reason> CONFIRM"));
            return true;
        }
        boolean confirmed = arguments[arguments.length - 1].equals("CONFIRM");
        if ((release || keep) && !confirmed) {
            sender.sendMessage(Component.text("No change was made. Append the exact word CONFIRM to commit."));
            return true;
        }
        int reasonEnd = confirmed ? arguments.length - 1 : arguments.length;
        String reason = String.join(" ", Arrays.copyOfRange(arguments, reasonStart, reasonEnd)).trim();
        if (reason.isBlank() || reason.length() > 512) {
            sender.sendMessage(Component.text("A written reason of at most 512 characters is required."));
            return true;
        }
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        String target = arguments[targetIndex];
        submit(sender, () -> change(sender, target, actorId, reason, release, keep));
        return true;
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
            send(sender, "Freeze storage is not ready; no change was made.");
            return;
        }
        PlayerIdentity target = directory.find(targetInput).orElse(null);
        if (target == null) {
            send(sender, "That player has never joined the authoritative directory.");
            return;
        }
        if (release) {
            boolean changed = store.release(target.playerId(), actorId, reason, clock.instant());
            if (changed) {
                manager.releaseOnline(target.playerId());
            }
            send(sender, changed ? "Player freeze released and audited." : "That player is not frozen.");
        } else if (keep) {
            boolean changed = store.keepActive(target.playerId(), actorId, reason, clock.instant());
            send(sender, changed ? "Freeze will remain active beyond the offline timeout." : "That player is not frozen.");
        } else {
            store.apply(target.playerId(), actorId, reason, clock.instant());
            manager.applyOnline(target.playerId());
            send(sender, "Player frozen and durable recovery state committed.");
        }
    }

    private void submit(CommandSender sender, Runnable operation) {
        try {
            workers.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Freeze command failed", exception);
                    send(sender, "Freeze operation failed; inspect the sanitized server log.");
                }
            });
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The bounded work queue is full; no freeze operation started."));
        }
    }

    private void send(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(Component.text(message)));
    }
}
