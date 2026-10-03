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
    private final Map<UUID, UUID> pendingLoads = new ConcurrentHashMap<>();

    AiReviewGuiController(JavaPlugin plugin, AiReviewSubsystem subsystem) {
        this.plugin = plugin;
        this.subsystem = subsystem;
    }

    void openQueue(Player viewer) {
        openQueue(viewer, 0, true);
    }

    void openQueue(Player viewer, int page, boolean refreshIfStale) {
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
                if (loadCurrent(viewer, token) && AiReviewPermissions.queue(viewer)) {
                    openQueueState(viewer, page, subsystem.snapshot());
                }
            }));
        }
    }

    void openEvent(Player viewer, String eventId, int returnPage) {
        if (!AiReviewPermissions.detail(viewer)) {
            deny(viewer, AiReviewPermissions.DETAIL);
            return;
        }
        UUID token = beginLoad(viewer);
        message(viewer, "Loading central review event…", NamedTextColor.GRAY);
        subsystem.loadEvent(
                eventId,
                details -> onEntity(viewer, () -> {
                    if (loadCurrent(viewer, token) && AiReviewPermissions.detail(viewer)) {
                        open(viewer, new AiReviewGuiState.Detail(
                                viewer.getUniqueId(),
                                nextGeneration(viewer),
                                details,
                                returnPage
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
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!AiReviewPermissions.queue(viewer)) {
            viewer.closeInventory();
            return;
        }
        if (state instanceof AiReviewGuiState.Queue queue) {
            queueClick(viewer, queue, slot);
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
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        pendingLoads.remove(viewerId);
        activeGeneration.remove(viewerId);
    }

    private void queueClick(Player viewer, AiReviewGuiState.Queue state, int slot) {
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

    private void detailClick(Player viewer, AiReviewGuiState.Detail state, int slot) {
        if (slot == AiReviewGuiRenderer.CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (slot == AiReviewGuiRenderer.BACK) {
            openQueue(viewer, state.returnPage(), false);
            return;
        }
        if (slot == AiReviewGuiRenderer.REFRESH) {
            openEvent(viewer, state.details().eventId(), state.returnPage());
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
                    0
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
                    state.returnPage()
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
                false
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
                    state.returnPage()
            ));
            return;
        }
        if (slot != AiReviewGuiRenderer.CONFIRM) {
            return;
        }
        if (!AiReviewPermissions.correct(viewer)) {
            deny(viewer, AiReviewPermissions.CORRECT);
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
        prewriteRefresh(viewer, state, authority);
    }

    private void prewriteRefresh(
            Player viewer,
            AiReviewGuiState.Confirm state,
            CorrectionAuthority authority
    ) {
        UUID token = beginLoad(viewer);
        message(viewer, "Revalidating central correction state…", NamedTextColor.GRAY);
        subsystem.loadEvent(
                state.details().eventId(),
                fresh -> onEntity(viewer, () -> {
                    if (!loadCurrent(viewer, token) || !AiReviewPermissions.correct(viewer)) {
                        return;
                    }
                    if (!validAgainstFresh(state, fresh)) {
                        message(viewer, "The central review state changed; no write was made.", NamedTextColor.YELLOW);
                        open(viewer, new AiReviewGuiState.Detail(
                                viewer.getUniqueId(), nextGeneration(viewer), fresh, state.returnPage()
                        ));
                        return;
                    }
                    write(viewer, state, fresh, authority);
                }),
                issue -> loadFailed(viewer, token, issue)
        );
    }

    private boolean validAgainstFresh(AiReviewGuiState.Confirm state, EventDetails fresh) {
        if (state.proposalId() == null) {
            return fresh.acceptedCorrection() == null;
        }
        Correction pending = fresh.pendingCorrection(state.proposalId());
        if (pending == null) {
            return false;
        }
        return state.kind() != WriteKind.CORRECT || pending.corrected().equals(state.decision());
    }

    private void write(
            Player viewer,
            AiReviewGuiState.Confirm state,
            EventDetails fresh,
            CorrectionAuthority authority
    ) {
        String reviewerId = viewer.getUniqueId().toString();
        String note = "EnthusiaStaff in-game review: " + state.description();
        if (state.kind() == WriteKind.REJECT) {
            subsystem.reject(
                    state.proposalId(),
                    reviewerId,
                    authority,
                    note,
                    correction -> writeComplete(viewer, correction, state.returnPage()),
                    issue -> writeFailed(viewer, fresh, state.returnPage(), issue)
            );
            return;
        }
        subsystem.correct(
                fresh.eventId(),
                reviewerId,
                authority,
                state.decision(),
                note,
                correction -> writeComplete(viewer, correction, state.returnPage()),
                issue -> writeFailed(viewer, fresh, state.returnPage(), issue)
        );
    }

    private void writeComplete(Player viewer, Correction correction, int returnPage) {
        onEntity(viewer, () -> {
            message(
                    viewer,
                    "Central correction " + correction.status()
                            + " · approvals=" + correction.approvals()
                            + " rejections=" + correction.rejections() + '.',
                    correction.status() == AiReviewModels.CorrectionStatus.ACCEPTED
                            ? NamedTextColor.GREEN : NamedTextColor.GOLD
            );
            subsystem.refreshQueue(
                    false,
                    () -> onEntity(viewer, () -> openQueue(viewer, returnPage, false))
            );
        });
    }

    private void writeFailed(Player viewer, EventDetails fresh, int returnPage, String issue) {
        onEntity(viewer, () -> {
            message(viewer, "No correction was committed: " + issue + '.', NamedTextColor.YELLOW);
            open(viewer, new AiReviewGuiState.Detail(
                    viewer.getUniqueId(),
                    nextGeneration(viewer),
                    fresh,
                    returnPage
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
                adminRequested
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
                page
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
            if (!viewer.isOnline() || !AiReviewPermissions.queue(viewer)) {
                return;
            }
            if (!(state instanceof AiReviewGuiState.Queue)
                    && !AiReviewPermissions.detail(viewer)) {
                return;
            }
            AiReviewGuiRenderer renderer = new AiReviewGuiRenderer(subsystem.configuration());
            viewer.openInventory(renderer.render(state, clock.instant(), adminAvailable(viewer)));
        });
    }

    private boolean adminAvailable(Player viewer) {
        return subsystem.configuration().adminOverrideEnabled()
                && viewer.hasPermission(AiReviewPermissions.ADMIN);
    }

    private UUID beginLoad(Player viewer) {
        UUID token = UUID.randomUUID();
        pendingLoads.put(viewer.getUniqueId(), token);
        return token;
    }

    private boolean loadCurrent(Player viewer, UUID token) {
        return viewer.isOnline()
                && token.equals(pendingLoads.remove(viewer.getUniqueId()));
    }

    private void loadFailed(Player viewer, UUID token, String issue) {
        onEntity(viewer, () -> {
            if (loadCurrent(viewer, token)) {
                message(viewer, "AI review unavailable: " + issue + ". No write was made.", NamedTextColor.YELLOW);
            }
        });
    }

    private long nextGeneration(Player viewer) {
        long next = generation.incrementAndGet();
        activeGeneration.put(viewer.getUniqueId(), next);
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
