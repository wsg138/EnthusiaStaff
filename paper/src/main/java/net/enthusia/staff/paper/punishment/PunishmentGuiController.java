package net.enthusia.staff.paper.punishment;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PreparePunishmentDraftRequest;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentDraftCleanupException;
import net.enthusia.staff.domain.application.PunishmentDraftConfirmation;
import net.enthusia.staff.domain.application.PunishmentDraftEvaluation;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentRequestDraftCleanupException;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.auth.LuckPermsStaffTargetGuard;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.auth.StaffTargetGuard;
import net.enthusia.staff.paper.command.HistoryCommand;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentGuiController implements Listener {
    private static final int OVERVIEW_HISTORY_LIMIT = 1;
    private static final int OVERVIEW_CASE_LIMIT = 100;
    private static final int OVERVIEW_REPORT_LIMIT = 100;
    private static final int HISTORY_PAGE_SIZE = 36;
    private static final ZoneId FALLBACK_TIMEZONE = ZoneId.of("UTC");

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentDraftWorkflow> workflows;
    private final Supplier<PlayerDirectory> players;
    private final AuthorizationPolicy authorization;
    private final ReasonPolicyRepository policies;
    private final Supplier<ModerationHistoryStore> histories;
    private final Supplier<CaseReviewStore> caseReviews;
    private final Supplier<SanctionLookup> sanctions;
    private final Supplier<ReportStore> reports;
    private final Supplier<ModerationFeatureSettings> settings;
    private final ExecutorService workers;
    private final StaffTargetGuard targetGuard;
    private final PunishmentGuiCatalog catalog;
    private final PunishmentGuiRenderer renderer;
    private final Set<UUID> suppressedClosures = ConcurrentHashMap.newKeySet();
    private final Set<UUID> confirmations = ConcurrentHashMap.newKeySet();
    private final Map<UUID, NoteCapture> noteCaptures = new ConcurrentHashMap<>();

    public PunishmentGuiController(Dependencies dependencies) {
        this(
                dependencies,
                LuckPermsStaffTargetGuard.discover(
                        java.util.Objects.requireNonNull(dependencies, "dependencies").plugin()
                )
        );
    }

    PunishmentGuiController(Dependencies dependencies, StaffTargetGuard targetGuard) {
        Dependencies checked = java.util.Objects.requireNonNull(dependencies, "dependencies");
        this.plugin = checked.plugin();
        this.clock = checked.clock();
        this.mode = checked.mode();
        this.workflows = checked.workflows();
        this.players = checked.players();
        this.authorization = checked.authorization();
        this.policies = checked.policies();
        this.histories = checked.histories();
        this.caseReviews = checked.caseReviews();
        this.sanctions = checked.sanctions();
        this.reports = checked.reports();
        this.settings = checked.settings();
        this.workers = checked.workers();
        this.targetGuard = java.util.Objects.requireNonNull(targetGuard, "targetGuard");
        this.catalog = new PunishmentGuiCatalog(this.policies, this.authorization);
        this.renderer = new PunishmentGuiRenderer(catalog);
    }

    public void openTargetPicker(Player viewer, String commandName) {
        if (authorizedActor(viewer) == null) {
            return;
        }
        String normalizedCommand = normalizeCommand(commandName);
        onEntity(viewer, () -> openTargetPickerPage(viewer, normalizedCommand, 0));
    }

    public void open(Player viewer, String targetQuery, String commandName) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        String normalizedCommand = normalizeCommand(commandName);
        resolveTarget(viewer, targetQuery, target -> {
            if (!targetAllowed(viewer, actor, target.playerId())) {
                return;
            }
            PunishmentGuiOverview overview = loadOverview(target.playerId());
            openState(viewer, new PunishmentGuiState.Categories(
                    viewer.getUniqueId(),
                    target,
                    normalizedCommand,
                    overview,
                    0
            ));
        });
    }

    public void resume(Player viewer, String targetQuery, String invokedCommand) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        resolveTarget(viewer, targetQuery, target -> {
            if (!targetAllowed(viewer, actor, target.playerId())) {
                return;
            }
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                message(viewer, "Moderation storage is not ready; no draft was opened.");
                return;
            }
            PunishmentDraft draft = workflow.resume(actor.id(), target.playerId()).orElse(null);
            if (draft == null) {
                message(viewer, "No unexpired punishment draft exists for " + targetName(target) + '.');
                return;
            }
            if (!"punish".equalsIgnoreCase(invokedCommand)
                    && !draft.commandName().equalsIgnoreCase(invokedCommand)) {
                message(viewer, "That draft belongs to /" + draft.commandName() + ". Resume it with /punish.");
                return;
            }
            PunishmentGuiOverview overview = loadOverview(target.playerId());
            openState(viewer, new PunishmentGuiState.Review(
                    viewer.getUniqueId(),
                    target,
                    draft.commandName(),
                    overview,
                    draft,
                    Optional.empty()
            ));
        });
    }

    public void showPreparedDraft(
            Player viewer,
            PlayerIdentity target,
            String commandName,
            Actor actor,
            PunishmentDraftEvaluation.Prepared prepared
    ) {
        PunishmentGuiOverview overview = loadOverview(target.playerId());
        showPrepared(viewer, target, commandName, actor, overview, prepared);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!punishmentHolder(holder)) {
            return;
        }
        event.setCancelled(true);
        int slot = topInventorySlot(event, top);
        if (slot < 0) {
            return;
        }
        dispatchClick(viewer, holder, slot);
    }

    private static boolean punishmentHolder(InventoryHolder holder) {
        return holder instanceof PunishmentGuiHolder
                || holder instanceof PunishmentTargetPickerHolder;
    }

    private static int topInventorySlot(InventoryClickEvent event, Inventory top) {
        int slot = event.getRawSlot();
        return slot >= 0 && slot < top.getSize() ? slot : -1;
    }

    private void dispatchClick(Player viewer, InventoryHolder holder, int slot) {
        if (holder instanceof PunishmentTargetPickerHolder picker) {
            targetPickerClick(viewer, picker, slot);
            return;
        }
        punishmentStateClick(viewer, ((PunishmentGuiHolder) holder).state(), slot);
    }

    private void punishmentStateClick(Player viewer, PunishmentGuiState state, int slot) {
        if (!state.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            closeWithoutResume(viewer);
            return;
        }
        dispatchStateClick(viewer, actor, state, slot);
    }

    private void dispatchStateClick(Player viewer, Actor actor, PunishmentGuiState state, int slot) {
        if (state instanceof PunishmentGuiState.Categories categories) {
            categoryClick(viewer, actor, categories, slot);
            return;
        }
        if (state instanceof PunishmentGuiState.Reasons reasons) {
            reasonClick(viewer, actor, reasons, slot);
            return;
        }
        if (state instanceof PunishmentGuiState.Review review) {
            reviewClick(viewer, actor, review, slot);
            return;
        }
        if (state instanceof PunishmentGuiState.History history) {
            historyClick(viewer, history, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof PunishmentGuiHolder || holder instanceof PunishmentTargetPickerHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)
                || !(event.getInventory().getHolder(false) instanceof PunishmentGuiHolder holder)) {
            return;
        }
        if (suppressedClosures.remove(viewer.getUniqueId())) {
            return;
        }
        if (holder.state() instanceof PunishmentGuiState.Review review) {
            Component resume = Component.text("Punishment draft saved. ", NamedTextColor.GREEN)
                    .append(Component.text("[Resume]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand(
                                    "/punish resume " + review.target().playerId()
                            ))
                            .hoverEvent(HoverEvent.showText(Component.text("Open the saved review"))));
            viewer.sendMessage(resume);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player viewer = event.getPlayer();
        NoteCapture capture = noteCaptures.remove(viewer.getUniqueId());
        if (capture == null) {
            return;
        }
        event.setCancelled(true);
        String note = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        onEntity(viewer, () -> {
            if (note.equalsIgnoreCase("cancel")) {
                openState(viewer, capture.review());
                return;
            }
            if (note.isBlank() || note.length() > 4_000) {
                viewer.sendMessage(StaffMessageStyle.style(Component.text(
                        "The internal explanation must contain 1 to 4000 characters; the prior draft remains saved."
                )));
                openState(viewer, capture.review());
                return;
            }
            Actor actor = authorizedActor(viewer);
            if (actor != null) {
                reprepare(viewer, actor, capture.review(), note, capture.review().draft().visibility());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        noteCaptures.remove(viewerId);
        suppressedClosures.remove(viewerId);
        confirmations.remove(viewerId);
    }

    private void targetPickerClick(Player viewer, PunishmentTargetPickerHolder picker, int slot) {
        if (!targetPickerAuthorized(viewer, picker)) {
            return;
        }
        if (handleTargetPickerControl(viewer, picker, slot)) {
            return;
        }
        int index = targetPickerIndex(picker, slot);
        if (index < 0 || index >= picker.targetIds().size()) {
            return;
        }
        open(viewer, picker.targetIds().get(index).toString(), picker.commandName());
    }

    private boolean targetPickerAuthorized(Player viewer, PunishmentTargetPickerHolder picker) {
        if (!picker.viewerId().equals(viewer.getUniqueId())) {
            return false;
        }
        return authorizedActor(viewer) != null;
    }

    private void openTargetPickerOffset(Player viewer, PunishmentTargetPickerHolder picker, int offset) {
        openTargetPickerPage(viewer, picker.commandName(), picker.page() + offset);
    }

    private boolean handleTargetPickerControl(
            Player viewer,
            PunishmentTargetPickerHolder picker,
            int slot
    ) {
        if (slot == PunishmentGuiRenderer.TARGET_CLOSE_SLOT) {
            viewer.closeInventory();
            return true;
        }
        if (slot == PunishmentGuiRenderer.TARGET_REFRESH_SLOT) {
            openTargetPickerOffset(viewer, picker, 0);
            return true;
        }
        if (slot == PunishmentGuiRenderer.PREVIOUS_SLOT && picker.page() > 0) {
            openTargetPickerOffset(viewer, picker, -1);
            return true;
        }
        if (slot == PunishmentGuiRenderer.NEXT_SLOT) {
            openTargetPickerOffset(viewer, picker, 1);
            return true;
        }
        return false;
    }

    private static int targetPickerIndex(PunishmentTargetPickerHolder picker, int slot) {
        int localIndex = slot - PunishmentGuiRenderer.CONTENT_START;
        if (localIndex < 0 || localIndex >= PunishmentGuiRenderer.CONTENT_SIZE) {
            return -1;
        }
        return picker.page() * PunishmentGuiRenderer.CONTENT_SIZE + localIndex;
    }

    private void categoryClick(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Categories state,
            int slot
    ) {
        List<String> categories = catalog.categories(actor, state.commandName());
        if (openHistoryFromControl(viewer, state, slot)) {
            return;
        }
        if (slot == PunishmentGuiRenderer.PREVIOUS_SLOT && state.page() > 0) {
            openState(viewer, new PunishmentGuiState.Categories(
                    state.viewerId(),
                    state.target(),
                    state.commandName(),
                    state.overview(),
                    state.page() - 1
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.NEXT_SLOT
                && (state.page() + 1) * PunishmentGuiRenderer.CONTENT_SIZE < categories.size()) {
            openState(viewer, new PunishmentGuiState.Categories(
                    state.viewerId(),
                    state.target(),
                    state.commandName(),
                    state.overview(),
                    state.page() + 1
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        int index = contentIndex(state.page(), slot);
        if (index >= 0 && index < categories.size()) {
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(),
                    state.target(),
                    state.commandName(),
                    state.overview(),
                    categories.get(index),
                    0
            ));
        }
    }

    private void reasonClick(Player viewer, Actor actor, PunishmentGuiState.Reasons state, int slot) {
        List<ReasonPolicy> reasons = catalog.reasons(actor, state.commandName(), state.family());
        if (handleReasonNavigation(viewer, state, reasons.size(), slot)) {
            return;
        }
        int index = contentIndex(state.page(), slot);
        if (index >= 0 && index < reasons.size()) {
            prepare(viewer, actor, state, reasons.get(index));
        }
    }

    private boolean handleReasonNavigation(
            Player viewer,
            PunishmentGuiState.Reasons state,
            int reasonCount,
            int slot
    ) {
        if (openHistoryFromControl(viewer, state, slot)) {
            return true;
        }
        if (slot == PunishmentGuiRenderer.PREVIOUS_SLOT && state.page() > 0) {
            openReasonPage(viewer, state, state.page() - 1);
            return true;
        }
        if (slot == PunishmentGuiRenderer.NEXT_SLOT
                && (state.page() + 1) * PunishmentGuiRenderer.CONTENT_SIZE < reasonCount) {
            openReasonPage(viewer, state, state.page() + 1);
            return true;
        }
        if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            openState(viewer, categoriesState(state));
            return true;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return true;
        }
        return false;
    }

    private void openReasonPage(Player viewer, PunishmentGuiState.Reasons state, int page) {
        openState(viewer, new PunishmentGuiState.Reasons(
                state.viewerId(),
                state.target(),
                state.commandName(),
                state.overview(),
                state.family(),
                page
        ));
    }

    private void reviewClick(Player viewer, Actor actor, PunishmentGuiState.Review state, int slot) {
        if (openHistoryFromControl(viewer, state, slot)) {
            return;
        }
        if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            String family = policies.find(state.draft().reasonId())
                    .map(ReasonPolicy::family)
                    .orElse(state.draft().reasonId());
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(),
                    state.target(),
                    state.commandName(),
                    state.overview(),
                    family,
                    0
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        if (slot == PunishmentGuiRenderer.VISIBILITY_SLOT) {
            CaseVisibility next = state.draft().visibility() == CaseVisibility.PUBLIC
                    ? CaseVisibility.PRIVATE
                    : CaseVisibility.PUBLIC;
            reprepare(viewer, actor, state, state.draft().internalExplanation(), next);
            return;
        }
        if (slot == PunishmentGuiRenderer.NOTE_SLOT) {
            noteCaptures.put(viewer.getUniqueId(), new NoteCapture(state));
            suppressedClosures.add(viewer.getUniqueId());
            viewer.closeInventory();
            viewer.sendMessage(StaffMessageStyle.style(Component.text(
                    "Type the private internal explanation in chat, or type cancel. It will not be broadcast."
            )));
            return;
        }
        if (slot == PunishmentGuiRenderer.CONFIRM_SLOT) {
            confirm(viewer, actor, state);
        }
    }

    private void historyClick(Player viewer, PunishmentGuiState.History state, int slot) {
        if (slot == PunishmentGuiRenderer.PREVIOUS_SLOT && state.history().page() > 1) {
            openHistory(viewer, state.returnState(), state.history().page() - 1);
            return;
        }
        if (slot == PunishmentGuiRenderer.NEXT_SLOT
                && state.history().page() < state.history().totalPages()) {
            openHistory(viewer, state.returnState(), state.history().page() + 1);
            return;
        }
        if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            openState(viewer, state.returnState());
            return;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
        }
    }

    private boolean openHistoryFromControl(Player viewer, PunishmentGuiState state, int slot) {
        if (slot != PunishmentGuiRenderer.HISTORY_SLOT
                && slot != PunishmentGuiRenderer.SUMMARY_HISTORY_SLOT) {
            return false;
        }
        openHistory(viewer, state, 1);
        return true;
    }

    private void openHistory(Player viewer, PunishmentGuiState returnState, int page) {
        boolean sensitiveHistory = viewer.hasPermission(HistoryCommand.SENSITIVE_PERMISSION);
        submit(viewer, () -> {
            ModerationHistoryStore store = histories.get();
            ModerationFeatureSettings active = settings.get();
            if (store == null || active == null) {
                openUnavailableHistory(viewer, returnState, sensitiveHistory);
                return;
            }
            HistoryQueryOptions options = historyOptions(active, sensitiveHistory);
            try {
                ModerationHistoryPage result = store.page(
                        returnState.target().playerId(),
                        page,
                        HISTORY_PAGE_SIZE,
                        options
                );
                openState(viewer, new PunishmentGuiState.History(
                        returnState.viewerId(),
                        returnState.target(),
                        returnState.commandName(),
                        returnState.overview(),
                        result,
                        sensitiveHistory,
                        true,
                        returnState
                ));
            } catch (IllegalArgumentException exception) {
                message(viewer, "That history page is no longer available.");
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Punishment GUI history lookup failed", exception);
                openUnavailableHistory(viewer, returnState, sensitiveHistory);
            }
        });
    }

    private void openUnavailableHistory(
            Player viewer,
            PunishmentGuiState returnState,
            boolean sensitiveHistory
    ) {
        ModerationHistoryPage empty = new ModerationHistoryPage(
                returnState.target().playerId(),
                1,
                HISTORY_PAGE_SIZE,
                0,
                0,
                List.of()
        );
        openState(viewer, new PunishmentGuiState.History(
                returnState.viewerId(),
                returnState.target(),
                returnState.commandName(),
                returnState.overview(),
                empty,
                sensitiveHistory,
                false,
                returnState
        ));
    }

    private void prepare(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Reasons state,
            ReasonPolicy policy
    ) {
        submit(viewer, () -> {
            if (!targetAllowed(viewer, actor, state.target().playerId())) {
                return;
            }
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                message(viewer, "Moderation storage is not ready; no draft was created.");
                return;
            }
            PunishmentDraftEvaluation evaluation = workflow.prepare(
                    new PreparePunishmentDraftRequest(
                            state.target().playerId(),
                            actor,
                            policy.id(),
                            "Issued through the central punishment GUI",
                            CaseVisibility.PUBLIC,
                            state.commandName()
                    ),
                    mode.get()
            );
            showPrepared(
                    viewer,
                    state.target(),
                    state.commandName(),
                    actor,
                    state.overview(),
                    evaluation
            );
        });
    }

    private void reprepare(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Review state,
            String explanation,
            CaseVisibility visibility
    ) {
        submit(viewer, () -> {
            if (!targetAllowed(viewer, actor, state.target().playerId())) {
                return;
            }
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                message(viewer, "Moderation storage is not ready; the existing draft remains saved.");
                return;
            }
            PunishmentDraftEvaluation evaluation = workflow.prepare(
                    new PreparePunishmentDraftRequest(
                            state.target().playerId(),
                            actor,
                            state.draft().reasonId(),
                            explanation,
                            visibility,
                            state.commandName()
                    ),
                    mode.get()
            );
            showPrepared(
                    viewer,
                    state.target(),
                    state.commandName(),
                    actor,
                    state.overview(),
                    evaluation
            );
        });
    }

    private void showPrepared(
            Player viewer,
            PlayerIdentity target,
            String commandName,
            Actor actor,
            PunishmentGuiOverview overview,
            PunishmentDraftEvaluation evaluation
    ) {
        if (evaluation instanceof PunishmentDraftEvaluation.Rejected rejected) {
            message(viewer, rejected.code() + ": " + rejected.message());
            return;
        }
        PunishmentDraftEvaluation.Prepared prepared = (PunishmentDraftEvaluation.Prepared) evaluation;
        PunishmentAssessment assessment = prepared.assessment();
        if (!PunishmentCommandFilter.matches(commandName, assessment.sanctions())) {
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow != null) {
                workflow.discard(prepared.draft().draftId(), actor.id());
            }
            message(viewer, "The current recommendation does not contain the sanction type selected by /"
                    + commandName + ".");
            return;
        }
        openState(viewer, new PunishmentGuiState.Review(
                viewer.getUniqueId(),
                target,
                commandName,
                overview,
                prepared.draft(),
                Optional.of(assessment)
        ));
    }

    private void confirm(Player viewer, Actor actor, PunishmentGuiState.Review state) {
        UUID viewerId = viewer.getUniqueId();
        if (!confirmations.add(viewerId)) {
            viewer.sendMessage(StaffMessageStyle.style(
                    Component.text("That punishment confirmation is already in progress.")
            ));
            return;
        }
        boolean submitted = submit(viewer, () -> runConfirmation(viewer, actor, state, viewerId));
        if (!submitted) {
            confirmations.remove(viewerId);
        }
    }

    private void runConfirmation(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Review state,
            UUID viewerId
    ) {
        try {
            confirmOnce(viewer, actor, state);
        } finally {
            confirmations.remove(viewerId);
        }
    }

    private void confirmOnce(Player viewer, Actor actor, PunishmentGuiState.Review state) {
        if (!targetAllowed(viewer, actor, state.target().playerId())) {
            return;
        }
        PunishmentDraftWorkflow workflow = workflows.get();
        if (workflow == null) {
            message(viewer, "Moderation storage is not ready; no action was taken.");
            return;
        }
        Optional<PunishmentDraftConfirmation> result = confirmDraft(viewer, actor, state, workflow);
        result.ifPresent(value -> handleConfirmation(viewer, actor, state, value));
    }

    private Optional<PunishmentDraftConfirmation> confirmDraft(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Review state,
            PunishmentDraftWorkflow workflow
    ) {
        try {
            return Optional.of(workflow.confirmRouted(state.draft().draftId(), actor, mode.get()));
        } catch (PunishmentDraftCleanupException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Punishment GUI draft cleanup failed after case commit " + exception.accepted().caseId(),
                    exception
            );
            finish(viewer, "Punishment committed as case " + exception.accepted().caseId()
                    + ", but draft cleanup failed. Reconfirming is idempotent.");
        } catch (PunishmentRequestDraftCleanupException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Punishment GUI draft cleanup failed after request submission "
                            + exception.submitted().request().requestId(),
                    exception
            );
            finish(viewer, "Punishment request submitted, but draft cleanup failed. Reconfirming is idempotent.");
        }
        return Optional.empty();
    }

    private void handleConfirmation(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Review state,
            PunishmentDraftConfirmation result
    ) {
        if (result instanceof PunishmentDraftConfirmation.Applied applied) {
            finish(viewer, "Punishment committed as case " + applied.accepted().caseId()
                    + (applied.accepted().replayed() ? " (idempotent replay)" : "") + '.');
            return;
        }
        if (result instanceof PunishmentDraftConfirmation.Requested requested) {
            finish(viewer, "Punishment request "
                    + (requested.submitted().replayed() ? "replayed" : "submitted")
                    + "; expires " + requested.submitted().request().expiresAt() + '.');
            return;
        }
        handleRejected(viewer, actor, state, (PunishmentDraftConfirmation.Rejected) result);
    }

    private void handleRejected(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Review state,
            PunishmentDraftConfirmation.Rejected rejected
    ) {
        if ("RECOMMENDATION_CHANGED".equals(rejected.code())) {
            message(viewer, "The recommendation changed. A fresh review is being opened; "
                    + "no punishment or request was created.");
            reprepare(
                    viewer,
                    actor,
                    state,
                    state.draft().internalExplanation(),
                    state.draft().visibility()
            );
            return;
        }
        message(viewer, rejected.code() + ": " + rejected.message());
    }

    private PunishmentGuiOverview loadOverview(UUID targetId) {
        ModerationFeatureSettings active = settings.get();
        ZoneId timezone = active == null ? FALLBACK_TIMEZONE : active.historyTimezone();
        Instant now = clock.instant();
        HistorySummary history = loadHistorySummary(targetId, active);
        CaseSummary cases = loadCaseSummary(targetId);
        SanctionSummary activeSanctions = loadSanctions(targetId, now);
        ReportSummary reportsForTarget = loadReports(targetId);
        return new PunishmentGuiOverview(
                now,
                timezone,
                history.totalEntries(),
                history.available(),
                cases.cases(),
                cases.truncated(),
                cases.available(),
                activeSanctions.sanctions(),
                activeSanctions.available(),
                reportsForTarget.count(),
                reportsForTarget.truncated(),
                reportsForTarget.available()
        );
    }

    private HistorySummary loadHistorySummary(
            UUID targetId,
            ModerationFeatureSettings active
    ) {
        ModerationHistoryStore store = histories.get();
        if (store == null || active == null) {
            return HistorySummary.unavailable();
        }
        try {
            ModerationHistoryPage page = store.page(
                    targetId,
                    1,
                    OVERVIEW_HISTORY_LIMIT,
                    historyOptions(active, false)
            );
            return new HistorySummary(page.totalEntries(), true);
        } catch (RuntimeException exception) {
            contextFailure("history", exception);
            return HistorySummary.unavailable();
        }
    }

    private CaseSummary loadCaseSummary(UUID targetId) {
        CaseReviewStore store = caseReviews.get();
        if (store == null) {
            return CaseSummary.unavailable();
        }
        try {
            List<CaseReview> recent = store.recent(targetId, OVERVIEW_CASE_LIMIT);
            List<PunishmentGuiOverview.RecentCase> summaries = recent.stream()
                    .map(PunishmentGuiController::recentCase)
                    .toList();
            return new CaseSummary(summaries, recent.size() == OVERVIEW_CASE_LIMIT, true);
        } catch (RuntimeException exception) {
            contextFailure("recent cases", exception);
            return CaseSummary.unavailable();
        }
    }

    private static PunishmentGuiOverview.RecentCase recentCase(CaseReview review) {
        boolean warning = review.sanctions().stream()
                .anyMatch(sanction -> sanction.type() == SanctionType.WARNING);
        return new PunishmentGuiOverview.RecentCase(
                review.exactReasonId(),
                review.sanctionFamily(),
                review.publicReason(),
                review.issuedAt(),
                warning
        );
    }

    private SanctionSummary loadSanctions(UUID targetId, Instant now) {
        SanctionLookup store = sanctions.get();
        if (store == null) {
            return SanctionSummary.unavailable();
        }
        try {
            List<ActiveSanction> active = store.activeFor(
                    targetId,
                    EnumSet.allOf(SanctionType.class),
                    now
            );
            return new SanctionSummary(active, true);
        } catch (RuntimeException exception) {
            contextFailure("active sanctions", exception);
            return SanctionSummary.unavailable();
        }
    }

    private ReportSummary loadReports(UUID targetId) {
        ReportStore store = reports.get();
        if (store == null) {
            return ReportSummary.unavailable();
        }
        try {
            int count = store.listActiveForTarget(targetId, OVERVIEW_REPORT_LIMIT).size();
            return new ReportSummary(count, count == OVERVIEW_REPORT_LIMIT, true);
        } catch (RuntimeException exception) {
            contextFailure("active reports", exception);
            return ReportSummary.unavailable();
        }
    }

    private void contextFailure(String context, RuntimeException exception) {
        plugin.getLogger().log(
                Level.FINE,
                "Punishment GUI could not load optional " + context + " context",
                exception
        );
    }

    private void resolveTarget(
            Player viewer,
            String targetQuery,
            java.util.function.Consumer<PlayerIdentity> continuation
    ) {
        submit(viewer, () -> {
            PlayerDirectory directory = players.get();
            if (directory == null) {
                message(viewer, "Moderation storage is not ready; no player was resolved.");
                return;
            }
            PlayerIdentity target = directory.find(targetQuery).orElse(null);
            if (target == null) {
                message(
                        viewer,
                        "Player is not present in the authoritative directory. "
                                + "UUIDs and historical names are accepted."
                );
                return;
            }
            continuation.accept(target);
        });
    }

    private boolean targetAllowed(Player viewer, Actor actor, UUID targetId) {
        StaffTargetGuard.Result result = targetGuard.check(actor, targetId, false);
        if (result.allowed()) {
            return true;
        }
        message(viewer, result.message());
        return false;
    }

    private void openTargetPickerPage(Player viewer, String commandName, int requestedPage) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        List<Player> targets = plugin.getServer().getOnlinePlayers().stream()
                .map(Player.class::cast)
                .filter(target -> !target.getUniqueId().equals(viewer.getUniqueId()))
                .filter(viewer::canSee)
                .filter(target -> targetGuard.check(actor, target.getUniqueId(), false).allowed())
                .toList();
        int maxPage = targets.isEmpty()
                ? 0
                : (targets.size() - 1) / PunishmentGuiRenderer.CONTENT_SIZE;
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inventory = renderer.renderTargetPicker(
                viewer.getUniqueId(),
                targets,
                page,
                commandName
        );
        viewer.openInventory(inventory);
    }

    private void openState(Player viewer, PunishmentGuiState state) {
        onEntity(viewer, () -> {
            Actor actor = authorizedActor(viewer);
            if (actor == null) {
                return;
            }
            if (state instanceof PunishmentGuiState.History history
                    && history.sensitiveHistory()
                    && !viewer.hasPermission(HistoryCommand.SENSITIVE_PERMISSION)) {
                openHistory(viewer, history.returnState(), history.history().page());
                return;
            }
            Inventory inventory = renderer.render(state, actor);
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof PunishmentGuiHolder) {
                suppressedClosures.add(viewer.getUniqueId());
            }
            viewer.openInventory(inventory);
        });
    }

    private Actor authorizedActor(Player viewer) {
        Actor actor = PaperActorResolver.resolve(viewer).orElse(null);
        if (actor == null || !actor.id().equals(viewer.getUniqueId())
                || (!authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                && !authorization.permits(actor, ModerationAction.REQUEST_POLICY_SANCTION))) {
            viewer.sendMessage(StaffMessageStyle.style(Component.text("You do not have punishment authority.")));
            return null;
        }
        return actor;
    }

    private void closeWithoutResume(Player viewer) {
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof PunishmentGuiHolder) {
            suppressedClosures.add(viewer.getUniqueId());
        }
        viewer.closeInventory();
    }

    private void finish(Player viewer, String result) {
        onEntity(viewer, () -> {
            closeWithoutResume(viewer);
            viewer.sendMessage(StaffMessageStyle.style(Component.text(result)));
        });
    }

    private boolean submit(Player viewer, Runnable work) {
        try {
            workers.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Punishment GUI workflow failed", exception);
                    message(
                            viewer,
                            "The punishment workflow failed and its outcome was not confirmed. "
                                    + "Check case history before retrying."
                    );
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            viewer.sendMessage(StaffMessageStyle.style(
                    Component.text("The moderation work queue is full; no action was taken.")
            ));
            return false;
        }
    }

    private void message(Player viewer, String body) {
        onEntity(viewer, () -> viewer.sendMessage(StaffMessageStyle.style(Component.text(body))));
    }

    private void onEntity(Player player, Runnable task) {
        player.getScheduler().execute(plugin, task, null, 1L);
    }

    private static PunishmentGuiState.Categories categoriesState(PunishmentGuiState state) {
        return new PunishmentGuiState.Categories(
                state.viewerId(),
                state.target(),
                state.commandName(),
                state.overview(),
                0
        );
    }

    private static int contentIndex(int page, int slot) {
        int localIndex = slot - PunishmentGuiRenderer.CONTENT_START;
        if (localIndex < 0 || localIndex >= PunishmentGuiRenderer.CONTENT_SIZE) {
            return -1;
        }
        return page * PunishmentGuiRenderer.CONTENT_SIZE + localIndex;
    }

    private static HistoryQueryOptions historyOptions(
            ModerationFeatureSettings active,
            boolean sensitiveHistory
    ) {
        return new HistoryQueryOptions(
                active.includeRequestEvents(),
                active.includeAppealEvents(),
                sensitiveHistory
        );
    }

    private static String normalizeCommand(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return "punish";
        }
        return commandName.toLowerCase(Locale.ROOT);
    }

    private static String targetName(PlayerIdentity target) {
        return target.currentUsername().orElse(target.playerId().toString());
    }

    public record Dependencies(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ReasonPolicyRepository policies,
            Supplier<ModerationHistoryStore> histories,
            Supplier<CaseReviewStore> caseReviews,
            Supplier<SanctionLookup> sanctions,
            Supplier<ReportStore> reports,
            Supplier<ModerationFeatureSettings> settings,
            ExecutorService workers
    ) {
        public Dependencies {
            plugin = java.util.Objects.requireNonNull(plugin, "plugin");
            clock = java.util.Objects.requireNonNull(clock, "clock");
            mode = java.util.Objects.requireNonNull(mode, "mode");
            workflows = java.util.Objects.requireNonNull(workflows, "workflows");
            players = java.util.Objects.requireNonNull(players, "players");
            authorization = java.util.Objects.requireNonNull(authorization, "authorization");
            policies = java.util.Objects.requireNonNull(policies, "policies");
            histories = java.util.Objects.requireNonNull(histories, "histories");
            caseReviews = java.util.Objects.requireNonNull(caseReviews, "caseReviews");
            sanctions = java.util.Objects.requireNonNull(sanctions, "sanctions");
            reports = java.util.Objects.requireNonNull(reports, "reports");
            settings = java.util.Objects.requireNonNull(settings, "settings");
            workers = java.util.Objects.requireNonNull(workers, "workers");
        }
    }

    private record NoteCapture(PunishmentGuiState.Review review) {
        private NoteCapture {
            if (review == null) {
                throw new IllegalArgumentException("punishment note capture requires a review");
            }
        }
    }

    private record HistorySummary(long totalEntries, boolean available) {
        private static HistorySummary unavailable() {
            return new HistorySummary(0, false);
        }
    }

    private record CaseSummary(
            List<PunishmentGuiOverview.RecentCase> cases,
            boolean truncated,
            boolean available
    ) {
        private CaseSummary {
            cases = List.copyOf(cases);
        }

        private static CaseSummary unavailable() {
            return new CaseSummary(List.of(), false, false);
        }
    }

    private record SanctionSummary(List<ActiveSanction> sanctions, boolean available) {
        private SanctionSummary {
            sanctions = List.copyOf(sanctions);
        }

        private static SanctionSummary unavailable() {
            return new SanctionSummary(List.of(), false);
        }
    }

    private record ReportSummary(int count, boolean truncated, boolean available) {
        private static ReportSummary unavailable() {
            return new ReportSummary(0, false, false);
        }
    }
}
