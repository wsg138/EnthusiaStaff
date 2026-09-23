package net.enthusia.staff.paper.command;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
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
        List<Entry> online = snapshotOnlineStaff();
        submit(sender, online);
        return true;
    }

    private List<Entry> snapshotOnlineStaff() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(this::snapshot)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Entry snapshot(Player player) {
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        return new Entry(player.getName(), rank, staffMode.active(playerId), vanish.isVanished(playerId));
    }

    private void submit(CommandSender sender, List<Entry> online) {
        try {
            workers.execute(() -> loadAndRespond(sender, online));
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The staff status work queue is full; try again shortly."));
        }
    }

    private void loadAndRespond(CommandSender sender, List<Entry> online) {
        PunishmentRequestService service = requests.get();
        String pending = service == null ? "unavailable" : pendingLabel(service.pending(PENDING_LIMIT).size());
        List<String> lines = render(online, pending);
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin,
                () -> lines.forEach(line -> sender.sendMessage(Component.text(line)))
        );
    }

    static String pendingLabel(int count) {
        return count >= PENDING_LIMIT ? PENDING_LIMIT + "+" : Integer.toString(Math.max(0, count));
    }

    static List<String> render(List<Entry> entries, String pending) {
        List<String> body = entries.stream()
                .map(entry -> "- " + entry.name() + " [" + entry.rank() + "] staff-mode="
                        + onOff(entry.staffMode()) + " vanished=" + yesNo(entry.vanished()))
                .toList();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
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
