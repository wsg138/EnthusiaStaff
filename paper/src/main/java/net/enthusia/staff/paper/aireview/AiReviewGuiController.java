package net.enthusia.staff.paper.aireview;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.enthusia.staff.paper.aireview.AiReviewGuiState.WriteKind;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class AiReviewGuiController implements Listener {
    static final List<String> SEMANTIC_LABELS = List.of(
            "SAFE",
            "GAMEPLAY_VIOLENCE",
            "LOW_LEVEL_HARASSMENT",
            "SEVERE_HARASSMENT",
            "STAFF_TARGETED_ABUSE",
            "REAL_WORLD_THREAT",
            "SELF_HARM_INSTRUCTION",
            "SELF_HARM_INTENT",
            "THIRD_PARTY_SELF_HARM_CONCERN",
            "HATE",
            "SLUR_USE",
            "SEXUAL_CONTENT",
            "SEXUAL_MINOR",
            "DOXXING",
            "BLACKMAIL",
            "GROOMING",
            "DANGEROUS_REAL_WORLD_INSTRUCTIONS",
            "AMBIGUOUS_REVIEW"
    );

    private final JavaPlugin plugin;
    private final AiReviewSubsystem subsystem;
    private final Clock clock = Clock.systemUTC();
    private final AtomicLong generation = new AtomicLong();
    private final Map<UUID, Long> activeGeneration = new ConcurrentHashMap<>();
    private final AiReviewLoadFence loadFence = new AiReviewLoadFence();

    AiReviewGuiController(JavaPlugin plugin, AiReviewSubsystem subsystem) {
        this.plugin = plugin;
        this.subsystem = subsystem;
    }

    void openQueue(Player viewer) {
        openQueue(viewer, 0, true);
    }

    void openQueue(Player viewer, int page, boolean refreshIfStale) {
        if (!subsystem.activeDuty(viewer)) {
            message(viewer, "AI review requires active staff mode.", NamedTextColor.RED);
            viewer.closeInventory();
            return;
        }
        if (!AiReviewPermissions.queue(viewer)) {
            deny(viewer, AiReviewPermissions.QUEUE);
            return;
        }
        if (!subsystem.enabled()) {
            message(viewer, subsystem.disabledReason(), NamedTextColor.GRAY);
            return;
        }
        AiReviewPollState.Snapshot snapshot = subsystem.snapshot();
        if (snapshot.fresh(clock.instant(), subsystem.configuration().cacheStaleAfter())) {
            openQueueState(viewer, page, snapshot);
            return;
        }
        openQueueState(viewer, page, snapshot);
        if (refreshIfStale) {
            UUID token = beginLoad(viewer);
            subsystem.refreshQueue(true, () -> onEntity(viewer, () -> {
                if (loadCurrent(viewer, token)
                        && subsystem.activeDuty(viewer)
                        && AiReviewPermissions.queue(viewer)) {
                    openQueueState(viewer, page, subsystem.snapshot());
                }
            }));
        }
    }

    void openHistory(Player viewer) {
        openHistory(viewer, null, List.of(), AiReviewHistoryFilter.ALL);
    }

    private void openHistory(
            Player viewer, String cursor, List<String> previousCursors,
            AiReviewHistoryFilter filter
    ) {
        if (!subsystem.activeDuty(viewer) || !AiReviewPermissions.queue(viewer)) {
            deny(viewer, AiReviewPermissions.QUEUE);
            viewer.closeInventory();
            return;
        }
        if (!subsystem.enabled()) {
            message(viewer, subsystem.disabledReason(), NamedTextColor.GRAY);
            return;
        }
        UUID token = beginLoad(viewer);
        message(viewer, "Loading saved AI decisions…", NamedTextColor.GRAY);
        int limit = Math.min(subsystem.configuration().pageSize(), AiReviewGuiRenderer.CONTENT_SLOTS.size());
        subsystem.loadDecisions(
                limit, cursor, filter,
                page -> onEntity(viewer, () -> {
                    if (loadCurrent(viewer, token)
                            && subsystem.activeDuty(viewer)
                            && AiReviewPermissions.queue(viewer)) {
                        open(viewer, new AiReviewGuiState.History(
                                viewer.getUniqueId(), nextGeneration(viewer),
                                page.items(), cursor, page.nextCursor(), previousCursors, filter
                        ));
                    }
                }),
                issue -> loadFailed(viewer, token, issue)
        );
    }

    void openEvent(Player viewer, String eventId, int returnPage) {
        openEvent(viewer, eventId, returnPage, null);
    }

    private void openEvent(
            Player viewer, String eventId, int returnPage, AiReviewGuiState.History historyOrigin
    ) {
        if (!subsystem.activeDuty(viewer)) {
            message(viewer, "AI review requires active staff mode.", NamedTextColor.RED);
            viewer.closeInventory();
            return;
        }
        if (!AiReviewPermissions.detail(viewer)) {
            deny(viewer, AiReviewPermissions.DETAIL);
            return;
        }
        UUID token = beginLoad(viewer);
        message(viewer, "Loading central review event…", NamedTextColor.GRAY);
        subsystem.loadEvent(
                eventId,
                details -> onEntity(viewer, () -> {
                    if (loadCurrent(viewer, token)
                            && subsystem.activeDuty(viewer)
                            && AiReviewPermissions.detail(viewer)) {
                        open(viewer, new AiReviewGuiState.Detail(
                                viewer.getUniqueId(),
                                nextGeneration(viewer),
                                details,
                                returnPage,
                                historyOrigin
                        ));
                    }
                }),
                issue -> loadFailed(viewer, token, issue)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof AiReviewGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        AiReviewGuiState state = holder.state();
        if (!state.viewerId().equals(viewer.getUniqueId())
                || activeGeneration.getOrDefault(viewer.getUniqueId(), -1L) != state.generation()) {
            return;
        }
        if (!subsystem.activeDuty(viewer)) {
            viewer.closeInventory();
            activeGeneration.remove(viewer.getUniqueId());
            loadFence.retire(viewer.getUniqueId());
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!AiReviewPermissions.queue(viewer)) {
            viewer.closeInventory();
            return;
        }
        if (!(state instanceof AiReviewGuiState.Queue)
                && !(state instanceof AiReviewGuiState.History)
                && !AiReviewPermissions.detail(viewer)) {
            viewer.closeInventory();
            return;
        }
        if ((state instanceof AiReviewGuiState.LabelPicker
                || state instanceof AiReviewGuiState.Confirm)
                && !AiReviewPermissions.correct(viewer)) {
            viewer.closeInventory();
            return;
        }
        if (state instanceof AiReviewGuiState.Queue queue) {
            queueClick(viewer, queue, slot);
        } else if (state instanceof AiReviewGuiState.History history) {
            historyClick(viewer, history, slot);
        } else if (state instanceof AiReviewGuiState.Detail detail) {
            detailClick(viewer, detail, slot);
        } else if (state instanceof AiReviewGuiState.LabelPicker picker) {
            labelClick(viewer, picker, slot);
        } else if (state instanceof AiReviewGuiState.Confirm confirm) {
            confirmClick(viewer, confirm, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AiReviewGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)
                || !(event.getInventory().getHolder(false) instanceof AiReviewGuiHolder holder)) {
            return;
        }
        AiReviewGuiState state = holder.state();
        UUID viewerId = viewer.getUniqueId();
        if (state.viewerId().equals(viewerId)
                && activeGeneration.remove(viewerId, state.generation())) {
            loadFence.retire(viewerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        loadFence.retire(viewerId);
        activeGeneration.remove(viewerId);
    }

    private void queueClick(Player viewer, AiReviewGuiState.Queue state, int slot) {
        if (slot == AiReviewGuiRenderer.HISTORY_TOGGLE) {
            openHistory(viewer);
            return;
        }
        if (slot == AiReviewGuiRenderer.CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (slot == AiReviewGuiRenderer.REFRESH) {
            openQueue(viewer, state.page(), true);
            return;
        }
        int pageSize = Math.min(subsystem.configuration().pageSize(), AiReviewGuiRenderer.CONTENT_SLOTS.size());
        if (slot == AiReviewGuiRenderer.PREVIOUS && state.page() > 0) {
            openQueue(viewer, state.page() - 1, false);
            return;
        }
        if (slot == AiReviewGuiRenderer.NEXT
                && (state.page() + 1) * pageSize < state.items().size()) {
            openQueue(viewer, state.page() + 1, false);
            return;
        }
        int slotIndex = AiReviewGuiRenderer.CONTENT_SLOTS.indexOf(slot);
        int index = state.page() * pageSize + slotIndex;
        if (slotIndex >= 0 && index >= 0 && index < state.items().size()) {
            openEvent(viewer, state.items().get(index).eventId(), state.page());
        }
    }

    private void historyClick(Player viewer, AiReviewGuiState.History state, int slot) {
        if (slot == AiReviewGuiRenderer.CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (slot == AiReviewGuiRenderer.HISTORY_TOGGLE) {
            openQueue(viewer);
            return;
        }
        if (slot == AiReviewGuiRenderer.HISTORY_FILTER) {
            openHistory(viewer, null, List.of(), state.filter().next());
            return;
        }
        if (slot == AiReviewGuiRenderer.REFRESH) {
            openHistory(viewer, state.cursor(), state.previousCursors(), state.filter());
            return;
        }
        if (slot == AiReviewGuiRenderer.PREVIOUS && !state.previousCursors().isEmpty()) {
            var previous = AiReviewHistoryNavigation.previous(state);
            openHistory(viewer, previous.cursor(), previous.previousCursors(), state.filter());
            return;
        }
        if (slot == AiReviewGuiRenderer.NEXT && state.nextCursor() != null
                && state.previousCursors().size() < AiReviewHistoryNavigation.MAX_PREVIOUS_PAGES) {
            var next = AiReviewHistoryNavigation.next(state);
            openHistory(viewer, next.cursor(), next.previousCursors(), state.filter());
            return;
        }
        int slotIndex = AiReviewGuiRenderer.CONTENT_SLOTS.indexOf(slot);
        if (slotIndex >= 0 && slotIndex < state.items().size()) {
            if (!AiReviewPermissions.detail(viewer)) {
                deny(viewer, AiReviewPermissions.DETAIL);
                return;
            }
            openEvent(viewer, state.items().get(slotIndex).eventId(), 0, state);
        }
    }

    private void detailClick(Player viewer, AiReviewGuiState.Detail state, int slot) {
        if (slot == AiReviewGuiRenderer.CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (slot == AiReviewGuiRenderer.BACK) {
            if (state.historyOrigin() != null) {
                AiReviewGuiState.History h = state.historyOrigin();
                open(viewer, new AiReviewGuiState.History(
                        viewer.getUniqueId(), nextGeneration(viewer), h.items(),
                        h.cursor(), h.nextCursor(), h.previousCursors(), h.filter()
                ));
            } else {
                openQueue(viewer, state.returnPage(), false);
            }
            return;
        }
        if (slot == AiReviewGuiRenderer.REFRESH) {
            openEvent(viewer, state.details().eventId(), state.returnPage(), state.historyOrigin());
            return;
        }
        if (!AiReviewPermissions.correct(viewer)) {
            if (List.of(
                    AiReviewGuiRenderer.ALLOW,
                    AiReviewGuiRenderer.BLOCK,
                    AiReviewGuiRenderer.REVIEW,
                    AiReviewGuiRenderer.LABEL,
                    AiReviewGuiRenderer.APPROVE,
                    AiReviewGuiRenderer.REJECT,
                    AiReviewGuiRenderer.ADMIN_APPROVE
            ).contains(slot)) {
                deny(viewer, AiReviewPermissions.CORRECT);
            }
            return;
        }
        CorrectionDecision current = CorrectionDecision.from(state.details().decision());
        if (slot == AiReviewGuiRenderer.ALLOW) {
            openConfirm(viewer, state, current.withAction(MessageAction.ALLOW),
                    null, WriteKind.CORRECT, "Change message visibility to ALLOW", false);
        } else if (slot == AiReviewGuiRenderer.BLOCK) {
            openConfirm(viewer, state, current.withAction(MessageAction.BLOCK),
                    null, WriteKind.CORRECT, "Change message visibility to BLOCK", false);
        } else if (slot == AiReviewGuiRenderer.REVIEW) {
            ReviewPriority priority = current.reviewPriority() == ReviewPriority.NONE
                    ? ReviewPriority.NORMAL : current.reviewPriority();
            openConfirm(viewer, state, current.withReviewPriority(priority),
                    null, WriteKind.CORRECT, "Require central review", false);
        } else if (slot == AiReviewGuiRenderer.LABEL) {
            open(viewer, new AiReviewGuiState.LabelPicker(
                    viewer.getUniqueId(),
                    nextGeneration(viewer),
                    state.details(),
                    state.returnPage(),
                    SEMANTIC_LABELS,
                    0,
                    state.historyOrigin()
            ));
        } else if (slot == AiReviewGuiRenderer.APPROVE) {
            Correction pending = state.details().latestPendingCorrection();
            if (pending != null) {
                openConfirm(viewer, state, pending.corrected(), pending.proposalId(),
                        WriteKind.CORRECT, "Approve pending correction " + pending.proposalId(), false);
            }
        } else if (slot == AiReviewGuiRenderer.REJECT) {
            Correction pending = state.details().latestPendingCorrection();
            if (pending != null) {
                openConfirm(viewer, state, null, pending.proposalId(),
                        WriteKind.REJECT, "Reject pending correction " + pending.proposalId(), false);
            }
        } else if (slot == AiReviewGuiRenderer.ADMIN_APPROVE) {
            Correction pending = state.details().latestPendingCorrection();
            if (pending != null && adminAvailable(viewer)) {
                openConfirm(viewer, state, pending.corrected(), pending.proposalId(),
                        WriteKind.CORRECT, "ADMIN accept pending correction " + pending.proposalId(), true);
            }
        }
    }

    private void labelClick(Player viewer, AiReviewGuiState.LabelPicker state, int slot) {
        if (slot == AiReviewGuiRenderer.CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (slot == AiReviewGuiRenderer.BACK) {
            open(viewer, new AiReviewGuiState.Detail(
                    viewer.getUniqueId(),
                    nextGeneration(viewer),
                    state.details(),
                    state.returnPage(),
                    state.historyOrigin()
            ));
            return;
        }
        int pageSize = AiReviewGuiRenderer.CONTENT_SLOTS.size();
        if (slot == AiReviewGuiRenderer.PREVIOUS && state.page() > 0) {
            openLabelPage(viewer, state, state.page() - 1);
            return;
        }
        if (slot == AiReviewGuiRenderer.NEXT
                && (state.page() + 1) * pageSize < state.labels().size()) {
            openLabelPage(viewer, state, state.page() + 1);
            return;
        }
        int slotIndex = AiReviewGuiRenderer.CONTENT_SLOTS.indexOf(slot);
        int index = state.page() * pageSize + slotIndex;
        if (slotIndex < 0 || index < 0 || index >= state.labels().size()) {
            return;
        }
        if (!AiReviewPermissions.correct(viewer)) {
            deny(viewer, AiReviewPermissions.CORRECT);
            return;
        }
        String label = state.labels().get(index);
        CorrectionDecision decision = CorrectionDecision.from(state.details().decision())
                .withSemanticLabel(label);
        open(viewer, new AiReviewGuiState.Confirm(
                viewer.getUniqueId(),
                nextGeneration(viewer),
                state.details(),
                state.returnPage(),
                WriteKind.CORRECT,
                decision,
                null,
                "Change semantic label to " + label,
                false,
                state.historyOrigin()
        ));
    }

    private void confirmClick(Player viewer, AiReviewGuiState.Confirm state, int slot) {
        if (slot == AiReviewGuiRenderer.CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (slot == AiReviewGuiRenderer.BACK) {
            open(viewer, new AiReviewGuiState.Detail(
                    viewer.getUniqueId(),
                    nextGeneration(viewer),
                    state.details(),
                    state.returnPage(),
                    state.historyOrigin()
            ));
            return;
        }
        if (slot != AiReviewGuiRenderer.CONFIRM) {
            return;
        }
        if (!subsystem.activeDuty(viewer) || !AiReviewPermissions.correct(viewer)) {
            message(viewer, "Active staff mode is required; no write was made.", NamedTextColor.RED);
            viewer.closeInventory();
            return;
        }
        CorrectionAuthority authority;
        try {
            authority = AiReviewPermissions.authority(
                    viewer,
                    subsystem.configuration(),
                    state.adminRequested()
            );
        } catch (SecurityException exception) {
            deny(viewer, AiReviewPermissions.ADMIN);
            viewer.closeInventory();
            return;
        }
        prewriteRefresh(viewer, state);
    }

    private void prewriteRefresh(
            Player viewer,
            AiReviewGuiState.Confirm state
    ) {
        UUID token = beginLoad(viewer);
        message(viewer, "Revalidating central correction state…", NamedTextColor.GRAY);
        subsystem.loadEvent(
                state.details().eventId(),
                fresh -> onEntity(viewer, () -> {
                    if (!loadCurrent(viewer, token)
                            || !subsystem.activeDuty(viewer)
                            || !AiReviewPermissions.queue(viewer)
                            || !AiReviewPermissions.detail(viewer)
                            || !AiReviewPermissions.correct(viewer)) {
                        return;
                    }
                    CorrectionAuthority currentAuthority;
                    try {
                        currentAuthority = AiReviewPermissions.authority(
                                viewer,
                                subsystem.configuration(),
                                state.adminRequested()
                        );
                    } catch (SecurityException exception) {
                        message(
                                viewer,
                                "AI review authority changed; no write was made.",
                                NamedTextColor.RED
                        );
                        return;
                    }
                    if (!validAgainstFresh(state, fresh)) {
                        message(viewer, "The central review state changed; no write was made.", NamedTextColor.YELLOW);
                        open(viewer, new AiReviewGuiState.Detail(
                                viewer.getUniqueId(), nextGeneration(viewer), fresh,
                                state.returnPage(), state.historyOrigin()
                        ));
                        return;
                    }
                    write(viewer, state, fresh, currentAuthority);
                }),
                issue -> loadFailed(viewer, token, issue)
        );
    }

    private boolean validAgainstFresh(AiReviewGuiState.Confirm state, EventDetails fresh) {
        return AiReviewWriteFence.valid(
                state.kind(),
                state.proposalId(),
                state.decision(),
                fresh
        );
    }

    private void write(
            Player viewer,
            AiReviewGuiState.Confirm state,
            EventDetails fresh,
            CorrectionAuthority authority
    ) {
        if (!subsystem.activeDuty(viewer) || !AiReviewPermissions.correct(viewer)) {
            message(viewer, "Active staff mode is required; no write was made.", NamedTextColor.RED);
            viewer.closeInventory();
            return;
        }
        activeGeneration.remove(viewer.getUniqueId(), state.generation());
        viewer.closeInventory();
        String reviewerId = viewer.getUniqueId().toString();
        String note = "EnthusiaStaff in-game review: " + state.description();
        if (state.kind() == WriteKind.REJECT) {
            subsystem.reject(
                    state.proposalId(),
                    reviewerId,
                    authority,
                    note,
                    correction -> writeComplete(viewer, correction, state.returnPage(),
                            state.historyOrigin()),
                    issue -> writeFailed(viewer, fresh, state.returnPage(), issue,
                            state.historyOrigin())
            );
            return;
        }
        subsystem.correct(
                fresh.eventId(),
                reviewerId,
                authority,
                state.decision(),
                note,
                correction -> writeComplete(viewer, correction, state.returnPage(),
                        state.historyOrigin()),
                issue -> writeFailed(viewer, fresh, state.returnPage(), issue,
                        state.historyOrigin())
        );
    }

    private void writeComplete(
            Player viewer, Correction correction, int returnPage,
            AiReviewGuiState.History historyOrigin
    ) {
        onEntity(viewer, () -> {
            if (!subsystem.activeDuty(viewer)) {
                viewer.closeInventory();
                return;
            }
            message(
                    viewer,
                    "Central correction " + correction.status()
                            + " · approvals=" + correction.approvals()
                            + " rejections=" + correction.rejections() + '.',
                    correction.status() == AiReviewModels.CorrectionStatus.ACCEPTED
                            ? NamedTextColor.GREEN : NamedTextColor.GOLD
            );
            if (historyOrigin != null) {
                openHistory(viewer, historyOrigin.cursor(), historyOrigin.previousCursors(),
                        historyOrigin.filter());
            } else {
                subsystem.refreshQueue(
                        false,
                        () -> onEntity(viewer, () -> openQueue(viewer, returnPage, false))
                );
            }
        });
    }

    private void writeFailed(
            Player viewer, EventDetails fresh, int returnPage, String issue,
            AiReviewGuiState.History historyOrigin
    ) {
        onEntity(viewer, () -> {
            message(viewer, "No correction was committed: " + issue + '.', NamedTextColor.YELLOW);
            if ("central review conflict".equals(issue)) {
                openEvent(viewer, fresh.eventId(), returnPage, historyOrigin);
                return;
            }
            open(viewer, new AiReviewGuiState.Detail(
                    viewer.getUniqueId(),
                    nextGeneration(viewer),
                    fresh,
                    returnPage,
                    historyOrigin
            ));
        });
    }

    private void openConfirm(
            Player viewer,
            AiReviewGuiState.Detail state,
            CorrectionDecision decision,
            String proposalId,
            WriteKind kind,
            String description,
            boolean adminRequested
    ) {
        open(viewer, new AiReviewGuiState.Confirm(
                viewer.getUniqueId(),
                nextGeneration(viewer),
                state.details(),
                state.returnPage(),
                kind,
                decision,
                proposalId,
                description,
                adminRequested,
                state.historyOrigin()
        ));
    }

    private void openLabelPage(
            Player viewer,
            AiReviewGuiState.LabelPicker state,
            int page
    ) {
        open(viewer, new AiReviewGuiState.LabelPicker(
                viewer.getUniqueId(),
                nextGeneration(viewer),
                state.details(),
                state.returnPage(),
                state.labels(),
                page,
                state.historyOrigin()
        ));
    }

    private void openQueueState(Player viewer, int page, AiReviewPollState.Snapshot snapshot) {
        int pageSize = Math.min(subsystem.configuration().pageSize(), AiReviewGuiRenderer.CONTENT_SLOTS.size());
        int maximumPage = snapshot.items().isEmpty()
                ? 0 : (snapshot.items().size() - 1) / pageSize;
        open(viewer, new AiReviewGuiState.Queue(
                viewer.getUniqueId(),
                nextGeneration(viewer),
                snapshot.items(),
                Math.min(Math.max(0, page), maximumPage),
                snapshot.authoritative(),
                snapshot.issue()
        ));
    }

    private void open(Player viewer, AiReviewGuiState state) {
        onEntity(viewer, () -> {
            if (!viewer.isOnline()
                    || !subsystem.activeDuty(viewer)
                    || !AiReviewPermissions.queue(viewer)
                    || activeGeneration.getOrDefault(viewer.getUniqueId(), -1L) != state.generation()) {
                return;
            }
            if (!(state instanceof AiReviewGuiState.Queue)
                    && !(state instanceof AiReviewGuiState.History)
                    && !AiReviewPermissions.detail(viewer)) {
                return;
            }
            if ((state instanceof AiReviewGuiState.LabelPicker
                    || state instanceof AiReviewGuiState.Confirm)
                    && !AiReviewPermissions.correct(viewer)) {
                return;
            }
            AiReviewGuiRenderer renderer = new AiReviewGuiRenderer(subsystem.configuration());
            viewer.openInventory(renderer.render(state, clock.instant(), adminAvailable(viewer)));
        });
    }

    private boolean adminAvailable(Player viewer) {
        return AiReviewPermissions.admin(viewer, subsystem.configuration());
    }

    private UUID beginLoad(Player viewer) {
        return loadFence.begin(viewer.getUniqueId());
    }

    private boolean loadCurrent(Player viewer, UUID token) {
        return viewer.isOnline()
                && loadFence.consume(viewer.getUniqueId(), token);
    }

    private void loadFailed(Player viewer, UUID token, String issue) {
        onEntity(viewer, () -> {
            if (loadCurrent(viewer, token)) {
                message(viewer, "AI review unavailable: " + issue + ". No write was made.", NamedTextColor.YELLOW);
            }
        });
    }

    private long nextGeneration(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        loadFence.retire(viewerId);
        long next = generation.incrementAndGet();
        activeGeneration.put(viewerId, next);
        return next;
    }

    private void deny(Player viewer, String permission) {
        message(viewer, "AI review permission denied: " + permission, NamedTextColor.RED);
    }

    private void message(Player viewer, String text, NamedTextColor color) {
        onEntity(viewer, () -> {
            if (viewer.isOnline()) {
                viewer.sendMessage(StaffMessageStyle.style(Component.text(text, color)));
            }
        });
    }

    private void onEntity(Player viewer, Runnable operation) {
        viewer.getScheduler().execute(plugin, operation, null, 1L);
    }
}
