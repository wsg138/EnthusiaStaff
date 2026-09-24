package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.util.ArrayList;
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
import net.enthusia.staff.paper.report.ReportEvidenceFormatter;
import net.enthusia.staff.paper.report.ReportEvidenceFormatter.EvidenceKind;
import net.enthusia.staff.paper.report.ReportEvidenceFormatter.EvidencePage;
import net.enthusia.staff.paper.report.ReportGuiController;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportsCommand implements CommandExecutor, TabCompleter {
    public static final String MANAGE_PERMISSION = "enthusiastaff.reports.manage";
    public static final String EVIDENCE_PERMISSION = "enthusiastaff.reports.evidence";
    private static final String UNAVAILABLE = "unavailable";
    private static final int SINGLE_ARGUMENT = 1;
    private static final int MIN_EVIDENCE_ARGUMENTS = 3;
    private static final int MAX_EVIDENCE_ARGUMENTS = 5;
    private static final int STATE_CHANGE_MIN_ARGUMENTS = 3;
    private static final int EVIDENCE_KIND_TAB_ARGUMENTS = 3;
    private static final int MIN_POSITIVE_INTEGER = 1;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<ReportStore> reports;
    private final ExecutorService workers;
    private final ReportGuiController gui;
    private final ReportEvidenceFormatter evidenceFormatter;

    public ReportsCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<ReportStore> reports,
            ExecutorService workers,
            ReportGuiController gui
    ) {
        this(plugin, clock, reports, workers, gui, new ReportEvidenceFormatter());
    }

    ReportsCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<ReportStore> reports,
            ExecutorService workers,
            ReportGuiController gui,
            ReportEvidenceFormatter evidenceFormatter
    ) {
        if (plugin == null || clock == null || reports == null || workers == null || gui == null
                || evidenceFormatter == null) {
            throw new IllegalArgumentException("report command dependencies must be present");
        }
        this.plugin = plugin;
        this.clock = clock;
        this.reports = reports;
        this.workers = workers;
        this.gui = gui;
        this.evidenceFormatter = evidenceFormatter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to manage reports."));
            return true;
        }
        if (arguments.length == 0) {
            if (sender instanceof Player player) {
                gui.openQueue(player, ReportQueue.OPEN);
            } else {
                submit(sender, () -> list(sender, ReportQueue.OPEN, consoleActor()));
            }
            return true;
        }
        if (arguments[0].equalsIgnoreCase("note")) {
            return note(sender, arguments);
        }
        if (arguments[0].equalsIgnoreCase("cancel") && arguments.length == SINGLE_ARGUMENT) {
            if (sender instanceof Player player) {
                gui.cancelNote(player);
            } else {
                sender.sendMessage(Component.text("Only a player can cancel a GUI report note."));
            }
            return true;
        }
        if (arguments[0].equalsIgnoreCase("evidence")) {
            return evidence(sender, arguments);
        }
        ReportQueue queue = parseQueue(arguments[0]);
        if (queue != null && arguments.length == SINGLE_ARGUMENT) {
            UUID actorId = actorId(sender);
            submit(sender, () -> list(sender, queue, actorId));
            return true;
        }
        if (arguments[0].equalsIgnoreCase("view") && arguments.length == 2) {
            UUID reportId = uuid(sender, arguments[1]);
            if (reportId != null) {
                submit(sender, () -> details(sender, reportId));
            }
            return true;
        }
        return stateChange(sender, arguments);
    }

    private boolean note(CommandSender sender, String[] arguments) {
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

    private boolean evidence(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(EVIDENCE_PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to inspect sensitive report evidence."));
            return true;
        }
        EvidenceRequest request = evidenceRequest(sender, arguments);
        if (request != null) {
            submit(sender, () -> renderEvidence(
                    sender,
                    request.reportId(),
                    request.kind(),
                    request.snapshot(),
                    request.page()
            ));
        }
        return true;
    }

    private EvidenceRequest evidenceRequest(CommandSender sender, String[] arguments) {
        if (arguments.length < MIN_EVIDENCE_ARGUMENTS || arguments.length > MAX_EVIDENCE_ARGUMENTS) {
            sender.sendMessage(Component.text(
                    "Usage: /reports evidence <report-id> <public|private|client> [snapshot] [page]"
            ));
            return null;
        }
        UUID reportId = uuid(sender, arguments[1]);
        if (reportId == null) {
            return null;
        }
        EvidenceKind kind = evidenceFormatter.parseKind(arguments[2]).orElse(null);
        if (kind == null) {
            sender.sendMessage(Component.text("Evidence kind must be public, private, or client."));
            return null;
        }
        Integer snapshot = optionalPositiveInteger(sender, arguments, 3, "snapshot", 0);
        if (snapshot == null) {
            return null;
        }
        Integer page = optionalPositiveInteger(sender, arguments, 4, "page", 1);
        if (page == null) {
            return null;
        }
        return new EvidenceRequest(reportId, kind, snapshot, page);
    }

    private boolean stateChange(CommandSender sender, String[] arguments) {
        ReportAction action = parseAction(arguments[0]);
        if (action == null || arguments.length < STATE_CHANGE_MIN_ARGUMENTS) {
            usage(sender);
            return true;
        }
        StateChangeInput input = stateChangeInput(sender, arguments, action);
        if (input == null) {
            return true;
        }
        if (input.reviewOnly()) {
            sender.sendMessage(Component.text("Review only: " + action + " report " + input.reportId() + '.'));
            sender.sendMessage(Component.text("No change was made. Append the exact word CONFIRM to commit."));
            return true;
        }
        UUID actorId = actorId(sender);
        submit(sender, () -> change(sender, new ReportStateChangeRequest(
                input.reportId(),
                actorId,
                action,
                input.revision(),
                input.note(),
                new IdempotencyKey("report-change:" + UUID.randomUUID()),
                clock.instant()
        )));
        return true;
    }

    private static StateChangeInput stateChangeInput(
            CommandSender sender,
            String[] arguments,
            ReportAction action
    ) {
        UUID reportId = uuid(sender, arguments[1]);
        if (reportId == null) {
            return null;
        }
        Long revision = revision(sender, arguments[2]);
        if (revision == null) {
            return null;
        }
        boolean claim = action == ReportAction.CLAIM;
        boolean confirmed = arguments[arguments.length - 1].equals("CONFIRM");
        String note = stateChangeNote(arguments, claim, confirmed);
        if (note.isBlank()) {
            sender.sendMessage(Component.text("A written action note is required."));
            return null;
        }
        return new StateChangeInput(reportId, revision, note, claim, confirmed);
    }

    private static Long revision(CommandSender sender, String input) {
        try {
            long revision = Long.parseLong(input);
            if (revision < 0) {
                sender.sendMessage(Component.text("The expected report revision must be a non-negative number."));
                return null;
            }
            return revision;
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("The expected report revision must be a non-negative number."));
            return null;
        }
    }

    private static String stateChangeNote(String[] arguments, boolean claim, boolean confirmed) {
        int reasonStart = STATE_CHANGE_MIN_ARGUMENTS;
        int reasonEnd = confirmed ? arguments.length - 1 : arguments.length;
        if (reasonStart < reasonEnd) {
            return String.join(" ", Arrays.copyOfRange(arguments, reasonStart, reasonEnd)).trim();
        }
        return claim ? "Claimed for investigation" : "";
    }

    private void list(CommandSender sender, ReportQueue queue, UUID actorId) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready.");
            return;
        }
        List<ReportSummary> summaries;
        try {
            summaries = store.list(queue, actorId, 50);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to load report queue: " + exception.getClass().getSimpleName());
            send(sender, "Reports are temporarily unavailable.");
            return;
        }
        if (summaries.isEmpty()) {
            send(sender, "No reports are currently in the " + queue.name().toLowerCase(Locale.ROOT) + " queue.");
            return;
        }
        send(sender, queue.name() + " reports:");
        for (ReportSummary summary : summaries) {
            send(sender, "- " + summary.reportId() + " | " + summary.reporterName()
                    + " -> " + summary.targetName() + " | " + summary.category()
                    + " | rev " + summary.revision());
        }
    }

    private void details(CommandSender sender, UUID reportId) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready.");
            return;
        }
        ReportDetails details;
        try {
            details = store.details(reportId).orElse(null);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to load report details: " + exception.getClass().getSimpleName());
            send(sender, "Reports are temporarily unavailable.");
            return;
        }
        if (details == null) {
            send(sender, "That report does not exist.");
            return;
        }
        send(sender, "Report " + details.reportId() + " | " + details.reporterName()
                + " -> " + details.targetName() + " | " + details.category()
                + " | state " + details.state() + " | rev " + details.revision());
        send(sender, "Reason: " + details.reason());
    }

    private void renderEvidence(
            CommandSender sender,
            UUID reportId,
            EvidenceKind kind,
            int snapshot,
            int page
    ) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready.");
            return;
        }
        ReportDetails details;
        try {
            details = store.details(reportId).orElse(null);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to load report evidence: " + exception.getClass().getSimpleName());
            send(sender, "Reports are temporarily unavailable.");
            return;
        }
        if (details == null) {
            send(sender, "That report does not exist.");
            return;
        }
        EvidencePage evidence = evidenceFormatter.page(details.evidence(), kind, snapshot, page);
        send(sender, evidence.title());
        for (String line : evidence.lines()) {
            send(sender, line);
        }
    }

    private void change(CommandSender sender, ReportStateChangeRequest request) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready.");
            return;
        }
        ReportStateChangeResult result;
        try {
            result = store.changeState(request);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to change report state: " + exception.getClass().getSimpleName());
            send(sender, "Reports are temporarily unavailable.");
            return;
        }
        send(sender, switch (result.status()) {
            case APPLIED -> "Report updated to " + result.state() + " (rev " + result.revision() + ").";
            case CONFLICT -> "Report changed before your action could be saved. Refresh and retry.";
            case NOT_FOUND -> "That report no longer exists.";
            case REJECTED -> "That report action is not allowed from its current state.";
        });
    }

    private void submit(CommandSender sender, Runnable work) {
        try {
            workers.execute(work);
        } catch (RejectedExecutionException exception) {
            send(sender, "Reports are temporarily unavailable.");
        }
    }

    private static ReportQueue parseQueue(String input) {
        try {
            return ReportQueue.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
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

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }

    private static UUID actorId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : consoleActor();
    }

    private static UUID consoleActor() {
        return new UUID(0L, 0L);
    }

    private static UUID uuid(CommandSender sender, String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Report IDs use UUID format."));
            return null;
        }
    }

    private static Integer optionalPositiveInteger(
            CommandSender sender,
            String[] arguments,
            int index,
            String name,
            int defaultValue
    ) {
        return arguments.length > index ? positiveInteger(sender, arguments[index], name) : defaultValue;
    }

    private static Integer positiveInteger(CommandSender sender, String input, String name) {
        try {
            int value = Integer.parseInt(input);
            if (value < MIN_POSITIVE_INTEGER) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("Evidence " + name + " must be a positive number."));
            return null;
        }
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /reports (opens the staff report GUI for players)"));
        sender.sendMessage(Component.text("       /reports note <private action note> | /reports cancel"));
        sender.sendMessage(Component.text("       /reports <open|mine|claimed|review|closed>"));
        sender.sendMessage(Component.text("       /reports view <report-id>"));
        sender.sendMessage(Component.text(
                "       /reports evidence <report-id> <public|private|client> [snapshot] [page]"
        ));
        sender.sendMessage(Component.text(
                "       /reports <claim|awaitreview|close|noviolation> <report-id> <revision> <note> [CONFIRM]"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            return List.of();
        }
        if (arguments.length == SINGLE_ARGUMENT) {
            List<String> suggestions = new ArrayList<>(List.of(
                    "note", "cancel", "open", "mine", "claimed", "review", "closed", "view",
                    "claim", "awaitreview", "close", "noviolation"
            ));
            if (!sender.hasPermission(EVIDENCE_PERMISSION)) {
                return List.copyOf(suggestions);
            }
            suggestions.add(2, "evidence");
            return List.copyOf(suggestions);
        }
        if (arguments.length == EVIDENCE_KIND_TAB_ARGUMENTS
                && arguments[0].equalsIgnoreCase("evidence")
                && sender.hasPermission(EVIDENCE_PERMISSION)) {
            return List.of("public", "private", "client");
        }
        return List.of();
    }

    private record EvidenceRequest(UUID reportId, EvidenceKind kind, int snapshot, int page) {
    }

    private record StateChangeInput(UUID reportId, long revision, String note, boolean claim, boolean confirmed) {
        private boolean reviewOnly() {
            return !claim && !confirmed;
        }
    }
}
