package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffWhoCommand implements CommandExecutor {
    private static final String PERMISSION = "enthusiastaff.staffwho";
    private static final int PENDING_LIMIT = 500;

    private final JavaPlugin plugin;
    private final Supplier<PunishmentRequestService> requests;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final ExecutorService workers;

    public StaffWhoCommand(
            JavaPlugin plugin,
            Supplier<PunishmentRequestService> requests,
            StaffModeManager staffMode,
            VanishManager vanish,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandPermissionGate.require(sender, PERMISSION, "You do not have permission to view staff presence.")) {
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(Component.text("Usage: /staffwho"));
            return true;
        }
        collectOnlineStaff(sender);
        return true;
    }

    private void collectOnlineStaff(CommandSender sender) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());
            if (online.isEmpty()) {
                submit(sender, List.of());
                return;
            }
            collectOwnedSnapshots(sender, online);
        });
    }

    private void collectOwnedSnapshots(CommandSender sender, List<Player> online) {
        ConcurrentLinkedQueue<Entry> entries = new ConcurrentLinkedQueue<>();
        AtomicInteger remaining = new AtomicInteger(online.size());
        for (Player player : online) {
            scheduleSnapshot(
                    plugin,
                    player,
                    () -> safeSnapshot(player),
                    entries::add,
                    () -> completeSnapshot(sender, entries, remaining)
            );
        }
    }

    private void completeSnapshot(
            CommandSender sender,
            ConcurrentLinkedQueue<Entry> entries,
            AtomicInteger remaining
    ) {
        if (remaining.decrementAndGet() == 0) {
            submit(sender, entries.stream()
                    .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList());
        }
    }

    private Entry safeSnapshot(Player player) {
        try {
            return snapshot(player);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Staff presence snapshot failed", exception);
            return null;
        }
    }

    private Entry snapshot(Player player) {
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        return new Entry(player.getName(), rank, staffMode.active(playerId), vanish.isVanished(playerId));
    }

    static boolean scheduleSnapshot(
            Plugin plugin,
            Player player,
            Supplier<Entry> snapshot,
            Consumer<Entry> accepted,
            Runnable finished
    ) {
        AtomicBoolean settled = new AtomicBoolean();
        Runnable retired = () -> finishOnce(settled, finished);
        Runnable owned = () -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            try {
                Entry entry = snapshot.get();
                if (entry != null) {
                    accepted.accept(entry);
                }
            } finally {
                finished.run();
            }
        };
        try {
            boolean scheduled = player.getScheduler().execute(plugin, owned, retired, 1L);
            if (!scheduled) {
                retired.run();
            }
            return scheduled;
        } catch (RuntimeException exception) {
            retired.run();
            return false;
        }
    }

    private static void finishOnce(AtomicBoolean settled, Runnable finished) {
        if (settled.compareAndSet(false, true)) {
            finished.run();
        }
    }

    private void submit(CommandSender sender, List<Entry> online) {
        try {
            workers.execute(() -> respond(sender, online, loadPendingLabel()));
        } catch (RejectedExecutionException exception) {
            deliver(sender, () -> sender.sendMessage(Component.text(
                    "The staff status work queue is full; try again shortly."
            )));
        }
    }

    private String loadPendingLabel() {
        try {
            PunishmentRequestService service = requests.get();
            return service == null ? "unavailable" : pendingLabel(service.pending(PENDING_LIMIT).size());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Pending punishment-request count is unavailable", exception);
            return "unavailable";
        }
    }

    private void respond(CommandSender sender, List<Entry> online, String pending) {
        List<String> lines = render(online, pending);
        deliver(sender, () -> lines.forEach(line -> sender.sendMessage(Component.text(line))));
    }

    private void deliver(CommandSender sender, Runnable delivery) {
        if (sender instanceof Player player) {
            player.getScheduler().execute(plugin, delivery, null, 1L);
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, delivery);
    }

    static String pendingLabel(int count) {
        return count >= PENDING_LIMIT ? PENDING_LIMIT + "+" : Integer.toString(Math.max(0, count));
    }

    static List<String> render(List<Entry> entries, String pending) {
        List<String> body = entries.stream()
                .map(entry -> "- " + entry.name() + " [" + entry.rank() + "] staff-mode="
                        + onOff(entry.staffMode()) + " vanished=" + yesNo(entry.vanished()))
                .toList();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Online staff: " + entries.size() + " | pending punishment requests: " + pending);
        lines.addAll(body);
        if (entries.isEmpty()) {
            lines.add("- No staff are currently online.");
        }
        return List.copyOf(lines);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    record Entry(String name, StaffRank rank, boolean staffMode, boolean vanished) {
        Entry {
            if (name == null || name.isBlank() || rank == null) {
                throw new IllegalArgumentException("staffwho entry requires a name and rank");
            }
        }
    }
}
