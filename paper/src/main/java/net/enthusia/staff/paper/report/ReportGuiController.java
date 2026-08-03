package net.enthusia.staff.paper.report;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSummary;
import net.enthusia.staff.paper.config.ReportConfigurationSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportGuiController implements Listener {
    private static final String MANAGE_PERMISSION = "enthusiastaff.reports.manage";

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<ReportStore> reports;
    private final Supplier<ReportConfigurationSnapshot> configuration;
    private final ExecutorService workers;
    private final ReportGuiRenderer renderer = new ReportGuiRenderer();
    private final Map<UUID, InputCapture> inputCaptures = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingLoads = new ConcurrentHashMap<>();
    private final Set<UUID> confirmations = ConcurrentHashMap.newKeySet();

    public ReportGuiController(
            JavaPlugin plugin,
            Clock clock,
            Supplier<ReportStore> reports,
            Supplier<ReportConfigurationSnapshot> configuration,
            ExecutorService workers
    ) {
        if (plugin == null || clock == null || reports == null || configuration == null || workers == null) {
            throw new IllegalArgumentException("report GUI dependencies must be present");
        }
        this.plugin = plugin;
        this.clock = clock;
        this.reports = reports;
        this.configuration = configuration;
        this.workers = workers;
    }

    public void openQueue(Player viewer, ReportQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("report queue must be present");
        }
        if (authorized(viewer)) {
            inputCaptures.remove(viewer.getUniqueId());
            loadQueue(viewer, queue, 0);
        }
    }

    public void openDetails(Player viewer, UUID reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("reportId must be present");
        }
        if (authorized(viewer)) {
            inputCaptures.remove(viewer.getUniqueId());
            loadDetails(viewer, ReportQueue.OPEN, 0, reportId);
        }
    }

    public void acceptNote(Player viewer, String input) {
        if (!authorized(viewer)) {
            inputCaptures.remove(viewer.getUniqueId());
            return;
        }
        InputCapture capture = inputCaptures.remove(viewer.getUniqueId());
        if (capture == null) {
            viewer.sendMessage(Component.text("No report action is waiting for a note."));
            return;
        }
        String note = input == null ? "" : input.trim();
        if (note.isBlank() || note.length() > 2_000) {
            viewer.sendMessage(Component.text("The private action note must contain 1 to 2000 characters."));
            openState(viewer, capture.state(), capture.configuration());
            return;
        }
        openState(viewer, new ReportGuiState.Review(
                viewer.getUniqueId(),
                capture.state().queue(),
                capture.state().queuePage(),
                capture.state().details(),
                capture.action(),
                note,
                UUID.randomUUID()
        ), capture.configuration());
    }

    public void cancelNote(Player viewer) {
        if (!authorized(viewer)) {
            inputCaptures.remove(viewer.getUniqueId());
            return;
        }
        InputCapture capture = inputCaptures.remove(viewer.getUniqueId());
        if (capture == null) {
            viewer.sendMessage(Component.text("No report action is waiting for a note."));
            return;
        }
        openState(viewer, capture.state(), capture.configuration());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof ReportGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        ReportGuiState state = holder.state();
        if (!state.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!authorized(viewer)) {
            viewer.closeInventory();
            return;
        }
        ReportGuiConfiguration gui = holder.configuration();
        if (state instanceof ReportGuiState.Queue queue) {
            queueClick(viewer, queue, gui, slot);
        } else if (state instanceof ReportGuiState.Detail detail) {
            detailClick(viewer, detail, gui, slot);
        } else if (state instanceof ReportGuiState.Review review) {
            reviewClick(viewer, review, gui, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ReportGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        inputCaptures.remove(viewerId);
        pendingLoads.remove(viewerId);
        confirmations.remove(viewerId);
    }

    private void queueClick(
            Player viewer,
            ReportGuiState.Queue state,
            ReportGuiConfiguration gui,
            int slot
    ) {
        ReportQueue selected = queueForSlot(slot, gui);
        if (selected != null) {
            loadQueue(viewer, selected, 0);
            return;
        }
        if (slot == gui.slot("refresh")) {
            loadQueue(viewer, state.queue(), state.queuePage());
            return;
        }
        if (slot == gui.slot("close")) {
            viewer.closeInventory();
            return;
        }
        if (slot == gui.slot("previous") && state.queuePage() > 0) {
            openState(viewer, new ReportGuiState.Queue(
                    state.viewerId(), state.queue(), state.reports(), state.queuePage() - 1
            ), gui);
            return;
        }
        if (slot == gui.slot("next")
                && (state.queuePage() + 1) * gui.pageSize() < state.reports().size()) {
            openState(viewer, new ReportGuiState.Queue(
                    state.viewerId(), state.queue(), state.reports(), state.queuePage() + 1
            ), gui);
            return;
        }
        int slotIndex = gui.contentSlots().indexOf(slot);
        int index = state.queuePage() * gui.pageSize() + slotIndex;
        if (slotIndex >= 0 && index < state.reports().size()) {
            loadDetails(viewer, state.queue(), state.queuePage(), state.reports().get(index).reportId());
        }
    }

    private void detailClick(
            Player viewer,
            ReportGuiState.Detail state,
            ReportGuiConfiguration gui,
            int slot
    ) {
        ReportQueue selected = queueForSlot(slot, gui);
        if (selected != null) {
            loadQueue(viewer, selected, 0);
            return;
        }
        if (slot == gui.slot("refresh")) {
            loadDetails(viewer, state.queue(), state.queuePage(), state.details().summary().reportId());
            return;
        }
        if (slot == gui.slot("back")) {
            loadQueue(viewer, state.queue(), state.queuePage());
            return;
        }
        if (slot == gui.slot("close")) {
            viewer.closeInventory();
            return;
        }
        List<ReportAction> actions = ReportGuiAccess.actions(state.details().summary(), viewer.getUniqueId());
        int actionIndex = gui.actionSlots().indexOf(slot);
        if (actionIndex >= 0 && actionIndex < actions.size()) {
            beginInput(viewer, state, actions.get(actionIndex), gui);
        }
    }

    private void reviewClick(
            Player viewer,
            ReportGuiState.Review state,
            ReportGuiConfiguration gui,
            int slot
    ) {
        if (slot == gui.slot("back")) {
            openState(viewer, detailState(state), gui);
            return;
        }
        if (slot == gui.slot("close")) {
            viewer.closeInventory();
            return;
        }
        if (slot == gui.slot("confirm")) {
            confirm(viewer, state, gui);
        }
    }

    private void beginInput(
            Player viewer,
            ReportGuiState.Detail state,
            ReportAction action,
            ReportGuiConfiguration gui
    ) {
        inputCaptures.put(viewer.getUniqueId(), new InputCapture(state, action, gui));
        viewer.closeInventory();
        Component command = Component.text("/reports note ", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand("/reports note "))
                .hoverEvent(HoverEvent.showText(Component.text("Click to prepare the private note command")));
        viewer.sendMessage(Component.text("Enter the private report action note with ")
                .append(command)
                .append(Component.text("<note>, or run /reports cancel. The note is not public chat.")));
    }

    private void confirm(
            Player viewer,
            ReportGuiState.Review state,
            ReportGuiConfiguration gui
    ) {
        UUID actorId = state.viewerId();
        if (!confirmations.add(actorId)) {
            viewer.sendMessage(Component.text("That report action is already being confirmed."));
            return;
        }
        boolean submitted = submit(viewer, () -> {
            try {
                ReportStore store = reports.get();
                if (store == null) {
                    message(viewer, "Report storage is not ready; no change was made.");
                    return;
                }
                ReportSummary summary = state.details().summary();
                ReportStateChangeResult result = store.changeState(new ReportStateChangeRequest(
                        summary.reportId(),
                        actorId,
                        state.action(),
                        summary.revision(),
                        state.note(),
                        new IdempotencyKey("report-gui:" + state.operationId()),
                        clock.instant()
                ));
                ReportDetails fresh = loadFreshDetails(store, summary.reportId());
                if (result instanceof ReportStateChangeResult.Applied applied) {
                    String replay = applied.replayed() ? " (idempotent replay)" : "";
                    showResult(viewer, state, gui, fresh,
                            "Report is now " + applied.state() + " at revision " + applied.revision() + replay + '.');
                    return;
                }
                ReportStateChangeResult.Rejected rejected = (ReportStateChangeResult.Rejected) result;
                showResult(viewer, state, gui, fresh, rejected.code() + ": " + rejected.message());
            } finally {
                confirmations.remove(actorId);
            }
        });
        if (!submitted) {
            confirmations.remove(actorId);
        }
    }

    private ReportDetails loadFreshDetails(ReportStore store, UUID reportId) {
        try {
            return store.details(reportId).orElse(null);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Report state outcome is known, but refreshed details could not be loaded",
                    exception
            );
            return null;
        }
    }

    private void showResult(
            Player viewer,
            ReportGuiState.Review state,
            ReportGuiConfiguration gui,
            ReportDetails fresh,
            String message
    ) {
        onEntity(viewer, () -> {
            viewer.sendMessage(Component.text(message));
            if (fresh == null) {
                loadQueue(viewer, state.queue(), state.queuePage());
            } else {
                openState(viewer, new ReportGuiState.Detail(
                        state.viewerId(), state.queue(), state.queuePage(), fresh
                ), gui);
            }
        });
    }

    private void loadQueue(Player viewer, ReportQueue queue, int requestedPage) {
        UUID viewerId = viewer.getUniqueId();
        UUID loadId = beginLoad(viewerId);
        ReportConfigurationSnapshot snapshot = currentConfiguration();
        submitLoad(viewer, viewerId, loadId, () -> {
            ReportStore store = reports.get();
            if (store == null) {
                failLoad(viewer, viewerId, loadId, "Report storage is not ready.");
                return;
            }
            List<ReportSummary> summaries = store.list(queue, viewerId, snapshot.policy().queryLimit());
            int pageSize = snapshot.gui().pageSize();
            int lastPage = summaries.isEmpty() ? 0 : (summaries.size() - 1) / pageSize;
            int page = Math.min(requestedPage, lastPage);
            openLoadedState(
                    viewer,
                    loadId,
                    new ReportGuiState.Queue(viewerId, queue, summaries, page),
                    snapshot.gui()
            );
        });
    }

    private void loadDetails(Player viewer, ReportQueue queue, int queuePage, UUID reportId) {
        UUID viewerId = viewer.getUniqueId();
        UUID loadId = beginLoad(viewerId);
        ReportGuiConfiguration gui = currentConfiguration().gui();
        submitLoad(viewer, viewerId, loadId, () -> {
            ReportStore store = reports.get();
            if (store == null) {
                failLoad(viewer, viewerId, loadId, "Report storage is not ready.");
                return;
            }
            ReportDetails details = store.details(reportId).orElse(null);
            if (details == null) {
                onCurrentLoad(viewer, viewerId, loadId, () -> {
                    viewer.sendMessage(Component.text("That report does not exist."));
                    loadQueue(viewer, queue, queuePage);
                });
                return;
            }
            openLoadedState(
                    viewer,
                    loadId,
                    new ReportGuiState.Detail(viewerId, queue, queuePage, details),
                    gui
            );
        });
    }

    private UUID beginLoad(UUID viewerId) {
        UUID loadId = UUID.randomUUID();
        pendingLoads.put(viewerId, loadId);
        return loadId;
    }

    private void failLoad(Player viewer, UUID viewerId, UUID loadId, String body) {
        onCurrentLoad(viewer, viewerId, loadId, () -> viewer.sendMessage(Component.text(body)));
    }

    private void openLoadedState(
            Player viewer,
            UUID loadId,
            ReportGuiState state,
            ReportGuiConfiguration gui
    ) {
        onCurrentLoad(viewer, state.viewerId(), loadId, () -> renderState(viewer, state, gui));
    }

    private void onCurrentLoad(
            Player viewer,
            UUID viewerId,
            UUID loadId,
            Runnable task
    ) {
        onEntity(viewer, () -> {
            if (pendingLoads.remove(viewerId, loadId)) {
                task.run();
            }
        });
    }

    private void openState(Player viewer, ReportGuiState state, ReportGuiConfiguration gui) {
        pendingLoads.remove(state.viewerId());
        onEntity(viewer, () -> renderState(viewer, state, gui));
    }

    private void renderState(
            Player viewer,
            ReportGuiState state,
            ReportGuiConfiguration gui
    ) {
        if (authorized(viewer)) {
            viewer.openInventory(renderer.render(state, gui));
        }
    }

    private boolean authorized(Player viewer) {
        if (viewer == null || !viewer.hasPermission(MANAGE_PERMISSION)) {
            if (viewer != null) {
                viewer.sendMessage(Component.text("You do not have permission to manage reports."));
            }
            return false;
        }
        return true;
    }

    private boolean submit(Player viewer, Runnable work) {
        try {
            workers.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Report GUI action outcome was not confirmed", exception);
                    message(viewer, "The report action outcome was not confirmed. Reopen the report before retrying.");
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            viewer.sendMessage(Component.text("The bounded work queue is full; no report operation started."));
            return false;
        }
    }

    private void submitLoad(
            Player viewer,
            UUID viewerId,
            UUID loadId,
            Runnable work
    ) {
        try {
            workers.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Report GUI load failed", exception);
                    failLoad(viewer, viewerId, loadId, "The report could not be loaded; inspect the sanitized log.");
                }
            });
        } catch (RejectedExecutionException exception) {
            if (pendingLoads.remove(viewerId, loadId)) {
                viewer.sendMessage(Component.text("The bounded work queue is full; no report load started."));
            }
        }
    }

    private void message(Player viewer, String body) {
        onEntity(viewer, () -> viewer.sendMessage(Component.text(body)));
    }

    private void onEntity(Player viewer, Runnable task) {
        viewer.getScheduler().execute(plugin, task, null, 1L);
    }

    private ReportConfigurationSnapshot currentConfiguration() {
        return Objects.requireNonNull(configuration.get(), "active report configuration");
    }

    private static ReportGuiState.Detail detailState(ReportGuiState.Review state) {
        return new ReportGuiState.Detail(
                state.viewerId(), state.queue(), state.queuePage(), state.details()
        );
    }

    private static ReportQueue queueForSlot(int slot, ReportGuiConfiguration gui) {
        if (slot == gui.slot("queue-open")) {
            return ReportQueue.OPEN;
        }
        if (slot == gui.slot("queue-mine")) {
            return ReportQueue.CLAIMED_BY_ME;
        }
        if (slot == gui.slot("queue-claimed")) {
            return ReportQueue.ALL_CLAIMED;
        }
        if (slot == gui.slot("queue-review")) {
            return ReportQueue.AWAITING_REVIEW;
        }
        if (slot == gui.slot("queue-closed")) {
            return ReportQueue.RECENTLY_CLOSED;
        }
        return null;
    }

    private record InputCapture(
            ReportGuiState.Detail state,
            ReportAction action,
            ReportGuiConfiguration configuration
    ) {
        private InputCapture {
            if (state == null || action == null || configuration == null) {
                throw new IllegalArgumentException("report GUI input capture fields must be present");
            }
        }
    }
}
