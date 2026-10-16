package net.enthusia.staff.paper.command;

import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportMessage;
import net.enthusia.staff.domain.report.ReportMessagesPage;
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
    private static final int MIN_NOTE_ARGUMENTS = 2;
    private static final int VIEW_ARGUMENTS = 2;
    private static final int MESSAGE_MIN_ARGUMENTS = 2;
    private static final int MESSAGE_WITH_PAGE_ARGUMENTS = 3;
    private static final int MESSAGE_MAX_PAGE = 10_000;
    private static final int MESSAGE_CHUNK_SIZE = 180;
    private static final int MAX_LEGACY_BODY_CHARS = 4_000;
    private static final int MIN_EVIDENCE_ARGUMENTS = 3;
    private static final int MAX_EVIDENCE_ARGUMENTS = 5;
    private static final int STATE_CHANGE_MIN_ARGUMENTS = 3;
    private static final int EVIDENCE_KIND_TAB_ARGUMENTS = 3;
    private static final int MIN_POSITIVE_INTEGER = 1;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<ReportStore> reports;
    private final Supplier<PlayerDirectory> players;
    private final CommandResponseDispatcher responses;
    private final ExecutorService workers;
    private final ReportGuiController gui;
    private final ReportEvidenceFormatter evidenceFormatter;

    public ReportsCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<ReportStore> reports,
            Supplier<PlayerDirectory> players,
            ExecutorService workers,
            ReportGuiController gui
    ) {
        this(plugin, clock, reports, players, workers, gui, new ReportEvidenceFormatter());
    }

    ReportsCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<ReportStore> reports,
            Supplier<PlayerDirectory> players,
            ExecutorService workers,
            ReportGuiController gui,
            ReportEvidenceFormatter evidenceFormatter
    ) {
        if (plugin == null || clock == null || reports == null || players == null
                || workers == null || gui == null || evidenceFormatter == null) {
            throw new IllegalArgumentException("report command dependencies must be present");
        }
        this.plugin = plugin;
        this.clock = clock;
        this.reports = reports;
        this.players = players;
        this.responses = new CommandResponseDispatcher(plugin);
        this.workers = workers;
        this.gui = gui;
        this.evidenceFormatter = evidenceFormatter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("You do not have permission to manage reports.")));
            return true;
        }
        if (arguments.length == 0) {
            openDefaultQueue(sender);
            return true;
        }
        return route(sender, arguments);
    }

    private void openDefaultQueue(CommandSender sender) {
        if (sender instanceof Player player) {
            gui.openQueue(player, ReportQueue.OPEN);
            return;
        }
        submit(sender, () -> list(sender, ReportQueue.OPEN, consoleActor()));
    }

    private boolean route(CommandSender sender, String[] arguments) {
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "note" -> note(sender, arguments);
            case "cancel" -> cancel(sender, arguments);
            case "evidence" -> evidence(sender, arguments);
            case "view" -> view(sender, arguments);
            case "messages" -> messages(sender, arguments);
            default -> queueOrStateChange(sender, arguments);
        };
    }

    private boolean cancel(CommandSender sender, String[] arguments) {
        if (arguments.length != SINGLE_ARGUMENT) {
            return stateChange(sender, arguments);
        }
        if (sender instanceof Player player) {
            gui.cancelNote(player);
        } else {
            sender.sendMessage(StaffMessageStyle.style(Component.text("Only a player can cancel a GUI report note.")));
        }
        return true;
    }

    private boolean view(CommandSender sender, String[] arguments) {
        if (arguments.length != VIEW_ARGUMENTS) {
            return stateChange(sender, arguments);
        }
        UUID reportId = uuid(sender, arguments[1]);
        if (Objects.nonNull(reportId)) {
            submit(sender, () -> details(sender, reportId));
        }
        return true;
    }

    private boolean messages(CommandSender sender, String[] arguments) {
        if (arguments.length < MESSAGE_MIN_ARGUMENTS || arguments.length > MESSAGE_WITH_PAGE_ARGUMENTS) {
            send(sender, "Usage: /reports messages <report-id> [page]");
            return true;
        }
        UUID reportId = uuid(sender, arguments[1]);
        if (reportId == null) {
            return true;
        }
        int page = 1;
        if (arguments.length == MESSAGE_WITH_PAGE_ARGUMENTS) {
            try {
                page = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                send(sender, "Report message page must be a positive integer.");
                return true;
            }
        }
        if (page < 1 || page > MESSAGE_MAX_PAGE) {
            send(sender, "Report message page must be from 1 to " + MESSAGE_MAX_PAGE + '.');
            return true;
        }
        int requested = page;
        submit(sender, () -> renderMessages(sender, reportId, requested));
        return true;
    }

    private boolean queueOrStateChange(CommandSender sender, String[] arguments) {
        ReportQueue queue = parseQueue(arguments[0]);
        if (queue == null || arguments.length != SINGLE_ARGUMENT) {
            return stateChange(sender, arguments);
        }
        UUID actorId = actorId(sender);
        submit(sender, () -> list(sender, queue, actorId));
        return true;
    }

    private boolean note(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("Only a player can complete a GUI report note.")));
            return true;
        }
        if (arguments.length < MIN_NOTE_ARGUMENTS) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("Usage: /reports note <private action note>")));
            return true;
        }
        gui.acceptNote(player, String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length)));
        return true;
    }

    private boolean evidence(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(EVIDENCE_PERMISSION)) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("You do not have permission to inspect sensitive report evidence.")));
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
            sender.sendMessage(StaffMessageStyle.style(Component.text(
                    "Usage: /reports evidence <report-id> <public|private|client> [snapshot] [page]"
            )));
            return null;
        }
        UUID reportId = uuid(sender, arguments[1]);
        if (reportId == null) {
            return null;
        }
        EvidenceKind kind = evidenceFormatter.parseKind(arguments[2]).orElse(null);
        if (kind == null) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("Evidence kind must be public, private, or client.")));
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
            sender.sendMessage(StaffMessageStyle.style(Component.text("Review only: " + action + " report " + input.reportId() + '.')));
            sender.sendMessage(StaffMessageStyle.style(Component.text("No change was made. Append the exact word CONFIRM to commit.")));
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
            sender.sendMessage(StaffMessageStyle.style(Component.text("A written action note is required.")));
            return null;
        }
        return new StateChangeInput(reportId, revision, note, claim, confirmed);
    }

    private static Long revision(CommandSender sender, String input) {
        try {
            long revision = Long.parseLong(input);
            if (revision < 0) {
                sender.sendMessage(StaffMessageStyle.style(Component.text("The expected report revision must be a non-negative number.")));
                return null;
            }
            return revision;
        } catch (NumberFormatException exception) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("The expected report revision must be a non-negative number.")));
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
        List<ReportSummary> summaries = store.list(queue, actorId, 50);
        send(sender, queue + " reports: " + summaries.size());
        Map<UUID, String> names = new HashMap<>();
        for (ReportSummary summary : summaries) {
            send(sender, summary.reportId() + " rev=" + summary.revision() + " " + summary.state()
                    + " target=" + names.computeIfAbsent(summary.targetId(), this::playerName)
                    + " reporter=" + names.computeIfAbsent(summary.reporterId(), this::playerName)
                    + " reason=" + net.enthusia.staff.domain.report.ReportReasonLabel.display(summary.reasonId())
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
        send(sender, "Reporter=" + playerName(summary.reporterId())
                + " target=" + playerName(summary.targetId())
                + " assigned=" + summary.assignedTo().map(this::playerName).orElse("none"));
        send(sender, "Reason=" + net.enthusia.staff.domain.report.ReportReasonLabel.display(summary.reasonId()) + " description=" + details.description());
        sendEvidenceAware(
                sender,
                () -> "Server=" + summary.serverId() + " world=" + details.worldId().orElse(UNAVAILABLE)
                        + " reporter-coordinates=" + details.reporterCoordinates().orElse(UNAVAILABLE)
                        + " target-coordinates=" + details.targetCoordinates().orElse(UNAVAILABLE),
                "Server=" + summary.serverId() + " world=" + details.worldId().orElse(UNAVAILABLE)
                        + " reporter-coordinates=restricted target-coordinates=restricted"
        );
        send(sender, "Case history: /reports messages " + reportId
                + " [page] (reporter follow-ups, internal actions and unverified legacy entries).");
        send(sender, "Evidence snapshots: public-chat=" + details.publicChatSnapshots().size()
                + ", private-message=" + details.privateMessageSnapshots().size()
                + ", client=" + details.clientEvidenceSnapshots().size());
        sendEvidenceAware(
                sender,
                () -> "Inspect retained contents with /reports evidence <report-id> <public|private|client> [snapshot] [page].",
                "Sensitive evidence contents and exact coordinates require " + EVIDENCE_PERMISSION + '.'
        );
    }

    private void renderMessages(CommandSender sender, UUID reportId, int requestedPage) {
        ReportStore store = reports.get();
        if (store == null) {
            send(sender, "Report storage is not ready.");
            return;
        }
        if (!store.exists(reportId)) {
            send(sender, "That report does not exist.");
            return;
        }
        ReportMessagesPage page = store.messages(reportId, requestedPage);
        sendReportMessages(sender, reportMessageLines(reportId, page, false),
                reportMessageLines(reportId, page, true));
    }

    private List<String> reportMessageLines(UUID reportId, ReportMessagesPage page,
                                            boolean revealPrivateNotes) {
        List<String> output = new ArrayList<>();
        output.add("Report " + reportId + " case messages, page " + page.page()
                + " (original description is shown in /reports view):");
        for (ReportMessage entry : page.messages()) {
            appendCaseMessage(output, entry, revealPrivateNotes);
        }
        if (page.messages().isEmpty()) {
            output.add("No case messages on this page.");
        }
        if (page.hasMore()) {
            output.add("Next: /reports messages " + reportId + " " + (page.page() + 1));
        }
        return List.copyOf(output);
    }

    static boolean restrictedOrigin(ReportMessage.Origin origin) {
        return origin != ReportMessage.Origin.REPORTER_FOLLOWUP;
    }

    private void appendCaseMessage(List<String> output, ReportMessage entry,
                                   boolean revealPrivateNotes) {
        if (restrictedOrigin(entry.origin()) && !revealPrivateNotes) {
            output.add("Restricted case note at " + entry.createdAt()
                    + " (requires " + EVIDENCE_PERMISSION + ").");
            return;
        }
        String kind = switch (entry.origin()) {
            case REPORTER_FOLLOWUP -> "Reporter follow-up";
            case STAFF_ACTION -> "Staff action note";
            case UNKNOWN -> "Historical message (origin unverified)";
        };
        output.add(kind + " - " + entry.createdAt() + " - " + playerName(entry.actorId())
                + " (" + entry.actorId() + "):");
        appendMessageBody(output, entry.body());
    }

    private void sendReportMessages(CommandSender sender, List<String> normalOutput,
                                    List<String> evidenceAuthorizedOutput) {
        // Async IO can complete after a player's staff permission is revoked.
        // Keep the entire staff-only message history behind a fresh, entity-owned check.
        responses.execute(sender, () -> {
            if (!sender.hasPermission(MANAGE_PERMISSION)) {
                sender.sendMessage(StaffMessageStyle.style(Component.text(
                        "Report access was revoked before the response could be delivered.")));
                return;
            }
            List<String> output = sender.hasPermission(EVIDENCE_PERMISSION)
                    ? evidenceAuthorizedOutput : normalOutput;
            for (String line : output) {
                sender.sendMessage(StaffMessageStyle.style(Component.text(line)));
            }
        });
    }

    static void appendMessageBody(List<String> output, String body) {
        String compact = body.replace('\r', ' ').replace('\n', ' ');
        int displayed = Math.min(compact.length(), MAX_LEGACY_BODY_CHARS);
        if (displayed < compact.length() && Character.isHighSurrogate(compact.charAt(displayed - 1))) {
            displayed--;
        }
        for (int start = 0; start < displayed;) {
            int end = Math.min(start + MESSAGE_CHUNK_SIZE, displayed);
            if (end < displayed && Character.isHighSurrogate(compact.charAt(end - 1))) {
                end--;
            }
            output.add(compact.substring(start, end));
            start = end;
        }
        if (compact.length() > displayed) {
            output.add("Historical message exceeds the safe chat display limit; remaining text omitted.");
        }
    }

    private void renderEvidence(
            CommandSender sender,
            UUID reportId,
            EvidenceKind kind,
            int requestedSnapshot,
            int requestedPage
    ) {
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
        try {
            EvidencePage page = evidenceFormatter.render(details, kind, requestedSnapshot, requestedPage);
            List<String> messages = new ArrayList<>();
            if (page.totalSnapshots() == 0) {
                messages.add("Report " + reportId + " has no retained " + kind.commandName() + " evidence.");
            } else {
                messages.add("Report " + reportId + " " + kind.commandName() + " evidence: snapshot "
                        + page.snapshot() + '/' + page.totalSnapshots() + ", page " + page.page() + '/'
                        + page.totalPages());
                messages.addAll(page.lines());
                if (page.page() < page.totalPages()) {
                    messages.add("Next: /reports evidence " + reportId + ' ' + kind.commandName() + ' '
                            + page.snapshot() + ' ' + (page.page() + 1));
                }
            }
            sendSensitive(sender, messages);
        } catch (IllegalArgumentException exception) {
            sendSensitive(sender, List.of("Invalid evidence page: " + exception.getMessage()));
        }
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
            sender.sendMessage(StaffMessageStyle.style(Component.text("The bounded work queue is full; no report operation started.")));
        }
    }

    private String playerName(UUID playerId) {
        try {
            PlayerDirectory directory = players.get();
            if (directory != null) {
                String name = directory.find(playerId.toString())
                        .flatMap(identity -> identity.currentUsername()).orElse(null);
                if (name != null && name.matches("\\*?[A-Za-z0-9_]{1,32}")) {
                    return name;
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().fine("A current report player name could not be resolved");
        }
        return "Unknown player";
    }

    private void send(CommandSender sender, String message) {
        responses.send(sender, Component.text(message));
    }

    private void sendEvidenceAware(CommandSender sender, Supplier<String> authorizedMessage, String deniedMessage) {
        responses.execute(sender, () -> {
            String message = sender.hasPermission(EVIDENCE_PERMISSION) ? authorizedMessage.get() : deniedMessage;
            sender.sendMessage(StaffMessageStyle.style(Component.text(message)));
        });
    }

    private void sendSensitive(CommandSender sender, List<String> messages) {
        List<String> output = List.copyOf(messages);
        responses.execute(sender, () -> {
            if (!sender.hasPermission(EVIDENCE_PERMISSION)) {
                sender.sendMessage(StaffMessageStyle.style(Component.text(
                        "Sensitive report evidence access is no longer permitted.")));
                return;
            }
            for (String message : output) {
                sender.sendMessage(StaffMessageStyle.style(Component.text(message)));
            }
        });
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
            sender.sendMessage(StaffMessageStyle.style(Component.text("Report IDs use UUID format.")));
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
            sender.sendMessage(StaffMessageStyle.style(Component.text("Evidence " + name + " must be a positive number.")));
            return null;
        }
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(StaffMessageStyle.style(Component.text("Usage: /reports (opens the staff report GUI for players)")));
        sender.sendMessage(StaffMessageStyle.style(Component.text("       /reports note <private action note> | /reports cancel")));
        sender.sendMessage(StaffMessageStyle.style(Component.text("       /reports <open|mine|claimed|review|closed>")));
        sender.sendMessage(StaffMessageStyle.style(Component.text("       /reports view <report-id>")));
        sender.sendMessage(StaffMessageStyle.style(Component.text(
                "       /reports messages <report-id> [page]")));
        sender.sendMessage(StaffMessageStyle.style(Component.text(
                "       /reports evidence <report-id> <public|private|client> [snapshot] [page]"
        )));
        sender.sendMessage(StaffMessageStyle.style(Component.text(
                "       /reports <claim|awaitreview|close|noviolation> <report-id> <revision> <note> [CONFIRM]"
        )));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            return List.of();
        }
        if (arguments.length == SINGLE_ARGUMENT) {
            List<String> suggestions = new ArrayList<>(List.of(
                    "note", "cancel", "open", "mine", "claimed", "review", "closed", "view", "messages",
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
