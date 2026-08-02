package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSummary;
import net.enthusia.staff.paper.report.ReportGuiController;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportsCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<ReportStore> reports;
    private final ExecutorService workers;
    private final ReportGuiController gui;

    public ReportsCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<ReportStore> reports,
            ExecutorService workers,
            ReportGuiController gui
    ) {
        if (plugin == null || clock == null || reports == null || workers == null || gui == null) {
            throw new IllegalArgumentException("report command dependencies must be present");
        }
        this.plugin = plugin;
        this.clock = clock;
        this.reports = reports;
        this.workers = workers;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission("enthusiastaff.reports.manage")) {
            sender.sendMessage(Component.text("You do not have permission to manage reports."));
            return true;
        }
        if (arguments.length == 0) {
            if (sender instanceof Player player) {
                gui.openQueue(player, ReportQueue.OPEN);
            } else {
                submit(sender, () -> list(sender, ReportQueue.OPEN));
            }
            return true;
        }
        if (arguments[0].equalsIgnoreCase("note")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only a player can complete a GUI report note."));
                return true;
            }
            if (arguments.length < 2) {
                sender.sendMessage(Component.text("Usage: /reports note <private action note>"));
                return true;
            }
            gui.acceptNote(player, String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length)));
            return true;
        }
        if (arguments[0].equalsIgnoreCase("cancel") && arguments.length == 1) {
            if (sender instanceof Player player) {
                gui.cancelNote(player);
            } else {
                sender.sendMessage(Component.text("Only a player can cancel a GUI report note."));
            }
            return true;
        }
        ReportQueue queue = parseQueue(arguments[0]);
        if (queue != null && arguments.length == 1) {
            submit(sender, () -> list(sender, queue));
            return true;
        }
        if (arguments[0].equalsIgnoreCase("view") && arguments.length == 2) {
            UUID reportId = uuid(sender, arguments[1]);
            if (reportId != null) {
                submit(sender, () -> details(sender, reportId));
            }
            return true;
        }
        ReportAction action = parseAction(arguments[0]);
        if (action == null || arguments.length < 3) {
            usage(sender);
            return true;
        }
        UUID reportId = uuid(sender, arguments[1]);
        if (reportId == null) {
            return true;
        }
        long revision;
        try {
            revision = Long.parseLong(arguments[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("The expected report revision must be a non-negative number."));
            return true;
        }
        boolean claim = action == ReportAction.CLAIM;
        boolean confirmed = arguments[arguments.length - 1].equals("CONFIRM");
        int reasonStart = 3;
        int reasonEnd = confirmed ? arguments.length - 1 : arguments.length;
        String note = reasonStart < reasonEnd
                ? String.join(" ", Arrays.copyOfRange(arguments, reasonStart, reasonEnd)).trim()
                : claim ? "Claimed for investigation" : "";
        if (note.isBlank()) {
            sender.sendMessage(Component.text("A written action note is required."));
            return true;
        }
        if (!claim && !confirmed) {
            sender.sendMessage(Component.text("Review only: " + action + " report " + reportId + '.'));
            sender.sendMessage(Component.text("No change was made. Append the exact word CONFIRM to commit."));
            return true;
        }
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        submit(sender, () -> change(sender, new ReportStateChangeRequest(
                reportId,
                actorId,
                action,
                revision,
                note,
                new IdempotencyKey("report-change:" + UUID.randomUUID()),
                clock.instant()
        )));
        return true;
    }

    private void list(CommandSender sender, ReportQueue queue) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready.");
            return;
        }
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        List<ReportSummary> summaries = store.list(queue, actorId, 50);
        send(sender, queue + " reports: " + summaries.size());
        for (ReportSummary summary : summaries) {
            send(sender, summary.reportId() + " rev=" + summary.revision() + " " + summary.state()
                    + " target=" + summary.targetId() + " reason=" + summary.reasonId()
                    + " server=" + summary.serverId());
        }
    }

    private void details(CommandSender sender, UUID reportId) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready.");
            return;
        }
        ReportDetails details = store.details(reportId).orElse(null);
        if (details == null) {
            send(sender, "That report does not exist.");
            return;
        }
        ReportSummary summary = details.summary();
        send(sender, "Report " + summary.reportId() + " rev=" + summary.revision() + " state=" + summary.state());
        send(sender, "Reporter=" + summary.reporterId() + " target=" + summary.targetId()
                + " assigned=" + summary.assignedTo().map(UUID::toString).orElse("none"));
        send(sender, "Reason=" + summary.reasonId() + " description=" + details.description());
        send(sender, "Server=" + summary.serverId() + " world=" + details.worldId().orElse("unavailable")
                + " reporter-coordinates=" + details.reporterCoordinates().orElse("unavailable")
                + " target-coordinates=" + details.targetCoordinates().orElse("unavailable"));
        send(sender, "Evidence snapshots: public-chat=" + details.publicChatSnapshots().size()
                + ", private-message=" + details.privateMessageSnapshots().size()
                + ", client=" + details.clientEvidenceSnapshots().size());
    }

    private void change(CommandSender sender, ReportStateChangeRequest request) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready; no change was made.");
            return;
        }
        ReportStateChangeResult result = store.changeState(request);
        if (result instanceof ReportStateChangeResult.Applied applied) {
            send(sender, "Report is now " + applied.state() + " at revision " + applied.revision()
                    + (applied.replayed() ? " (idempotent replay)" : "") + '.');
        } else {
            ReportStateChangeResult.Rejected rejected = (ReportStateChangeResult.Rejected) result;
            send(sender, rejected.code() + ": " + rejected.message());
        }
    }

    private void submit(CommandSender sender, Runnable work) {
        try {
            workers.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Report management operation failed", exception);
                    send(sender, "Report operation failed; inspect the sanitized server log.");
                }
            });
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The bounded work queue is full; no report operation started."));
        }
    }

    private void send(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(Component.text(message)));
    }

    private static ReportQueue parseQueue(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "open" -> ReportQueue.OPEN;
            case "mine" -> ReportQueue.CLAIMED_BY_ME;
            case "claimed" -> ReportQueue.ALL_CLAIMED;
            case "review" -> ReportQueue.AWAITING_REVIEW;
            case "closed" -> ReportQueue.RECENTLY_CLOSED;
            default -> null;
        };
    }

    private static ReportAction parseAction(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "claim" -> ReportAction.CLAIM;
            case "awaitreview" -> ReportAction.AWAIT_REVIEW;
            case "close" -> ReportAction.CLOSE;
            case "noviolation" -> ReportAction.NO_VIOLATION;
            default -> null;
        };
    }

    private static UUID uuid(CommandSender sender, String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Report IDs use UUID format."));
            return null;
        }
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /reports (opens the staff report GUI for players)"));
        sender.sendMessage(Component.text("       /reports note <private action note> | /reports cancel"));
        sender.sendMessage(Component.text("       /reports <open|mine|claimed|review|closed>"));
        sender.sendMessage(Component.text("       /reports view <report-id>"));
        sender.sendMessage(Component.text(
                "       /reports <claim|awaitreview|close|noviolation> <report-id> <revision> <note> [CONFIRM]"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        return arguments.length == 1
                ? List.of("note", "cancel", "open", "mine", "claimed", "review", "closed", "view", "claim",
                        "awaitreview", "close", "noviolation")
                : List.of();
    }
}
