package net.enthusia.staff.paper.punishment;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.time.Duration;
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
import net.enthusia.staff.domain.guided.GuidedIncidentWizard;
import net.enthusia.staff.domain.guided.GuidedPolicyResolution;
import net.enthusia.staff.domain.application.PunishmentEvaluation;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PreparePunishmentDraftRequest;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentDraftCleanupException;
import net.enthusia.staff.domain.application.PunishmentDraftConfirmation;
import net.enthusia.staff.domain.application.PunishmentDraftEvaluation;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentDraftReprepareOverrides;
import net.enthusia.staff.domain.application.PunishmentRequestDraftCleanupException;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
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
    private final Map<UUID, SearchCapture> searchCaptures = new ConcurrentHashMap<>();
    private final PunishmentGuiHistoryFence historyFence = new PunishmentGuiHistoryFence();
    // Independent single-use tokens prevent delayed draft results reopening old pages.
    private final PunishmentGuiHistoryFence preparationFence = new PunishmentGuiHistoryFence();
    private final PunishmentGuiTargetResolutionFence targetResolutionFence =
            new PunishmentGuiTargetResolutionFence();
    private static final String PUNISH_COMMAND = "punish";
    private final Map<UUID, GuidedReturn> guidedReturns = new ConcurrentHashMap<>();

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
        noteCaptures.remove(viewer.getUniqueId());
        searchCaptures.remove(viewer.getUniqueId());
        String normalizedCommand = normalizeCommand(commandName);
        onEntity(viewer, () -> openTargetPickerPage(viewer, normalizedCommand, 0));
    }

    public void open(Player viewer, String targetQuery, String commandName) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        String normalizedCommand = normalizeCommand(commandName);
        var ticket = beginTargetResolution(viewer);
        resolveTarget(viewer, targetQuery, ticket, target -> {
            if (!targetAllowed(viewer, actor, target.playerId(), ticket)) {
                return;
            }
            PunishmentGuiOverview overview = loadOverview(target.playerId());
            presentResolvedState(viewer, new PunishmentGuiState.Categories(
                    viewer.getUniqueId(),
                    target,
                    normalizedCommand,
                    overview,
                    0
            ), ticket);
        });
    }

    public void resume(Player viewer, String targetQuery, String invokedCommand) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        var ticket = beginTargetResolution(viewer);
        resolveTarget(viewer, targetQuery, ticket, target -> {
            if (!targetAllowed(viewer, actor, target.playerId(), ticket)) {
                return;
            }
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                resolvedMessage(viewer, ticket, "Moderation storage is not ready; no draft was opened.");
                return;
            }
            PunishmentDraft draft = workflow.resume(actor.id(), target.playerId()).orElse(null);
            if (draft == null) {
                resolvedMessage(viewer, ticket, "No unexpired punishment draft exists for " + targetName(target) + '.');
                return;
            }
            if (isGuidedDraft(draft.internalExplanation()) && !guidedModeEnabled()) {
                resolvedMessage(viewer, ticket, "Guided punishment mode is disabled. This saved draft is not "
                        + "available for submission; use Expert Reasons instead.");
                return;
            }
            if (!"punish".equalsIgnoreCase(invokedCommand)
                    && !draft.commandName().equalsIgnoreCase(invokedCommand)) {
                resolvedMessage(viewer, ticket, "That draft belongs to /" + draft.commandName() + ". Resume it with /punish.");
                return;
            }
            PunishmentGuiOverview overview = loadOverview(target.playerId());
            presentResolvedState(viewer, new PunishmentGuiState.Review(
                    viewer.getUniqueId(),
                    target,
                    draft.commandName(),
                    overview,
                    draft,
                    Optional.empty()
            ), ticket);
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
        // A new interaction supersedes pending history, draft and target lookups.
        historyFence.invalidate(viewer.getUniqueId());
        preparationFence.invalidate(viewer.getUniqueId());
        targetResolutionFence.invalidate(viewer.getUniqueId());
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
        if (state instanceof PunishmentGuiState.Guided guided) {
            guidedClick(viewer, actor, guided, slot);
            return;
        }
        if (state instanceof PunishmentGuiState.Reasons reasons) {
            reasonClick(viewer, actor, reasons, slot);
            return;
        }
        if (state instanceof PunishmentGuiState.Search search) {
            searchClick(viewer, actor, search, slot);
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
            historyFence.invalidate(event.getWhoClicked().getUniqueId());
            preparationFence.invalidate(event.getWhoClicked().getUniqueId());
            targetResolutionFence.invalidate(event.getWhoClicked().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)
                || !punishmentHolder(event.getInventory().getHolder(false))) {
            return;
        }
        // Closing either a target picker or a punishment page cancels delayed lookups.
        historyFence.invalidate(viewer.getUniqueId());
        preparationFence.invalidate(viewer.getUniqueId());
        targetResolutionFence.invalidate(viewer.getUniqueId());
        if (!(event.getInventory().getHolder(false) instanceof PunishmentGuiHolder holder)) {
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
            SearchCapture search = searchCaptures.remove(viewer.getUniqueId());
            if (search != null) {
                event.setCancelled(true);
                String query = PlainTextComponentSerializer.plainText().serialize(event.message());
                // Async chat may precede a newer /punish page or target lookup.
                // Consume only on the player's entity thread after navigation
                // has had a chance to invalidate this pending response.
                Object searchTicket = preparationFence.begin(viewer.getUniqueId());
                onEntity(viewer, () -> {
                    if (!preparationFence.consume(viewer.getUniqueId(), searchTicket)) {
                        return;
                    }
                    finishSearch(viewer, search, query);
                });
            }
            return;
        }
        event.setCancelled(true);
        // A delayed 60-second scheduler callback can run late under load.
        // Never let an already-expired private prompt edit an old draft.
        if (noteCaptureExpired(capture.startedAt(), clock.instant())) {
            message(viewer, "Punishment note entry expired. Reopen the saved review to edit it.");
            return;
        }
        String note = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Object noteTicket = preparationFence.begin(viewer.getUniqueId());
        onEntity(viewer, () -> {
            if (!preparationFence.consume(viewer.getUniqueId(), noteTicket)) {
                return;
            }
            if (note.equalsIgnoreCase("cancel")) {
                openState(viewer, capture.review());
                return;
            }
            int maxNoteLength = isGuidedDraft(capture.review().draft().internalExplanation())
                    ? 2_000 : 4_000;
            if (note.isBlank() || note.length() > maxNoteLength) {
                viewer.sendMessage(StaffMessageStyle.style(Component.text(
                        "The internal explanation must contain 1 to " + maxNoteLength
                                + " characters; the prior draft remains saved."
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
        searchCaptures.remove(viewerId);
        guidedReturns.remove(viewerId);
        suppressedClosures.remove(viewerId);
        confirmations.remove(viewerId);
        historyFence.invalidate(viewerId);
        preparationFence.invalidate(viewerId);
        targetResolutionFence.invalidate(viewerId);
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
        List<PunishmentGuiCategory> categories = catalog.categories(actor, state.commandName());
        if (openHistoryFromControl(viewer, state, slot)) {
            return;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        if (slot == PunishmentGuiRenderer.SEARCH_SLOT) {
            startSearch(viewer, state);
            return;
        }
        int index = PunishmentGuiRenderer.categoryIndex(slot, categories.size());
        if (index >= 0 && index < categories.size()) {
            if (PUNISH_COMMAND.equalsIgnoreCase(state.commandName())
                    && guidedModeEnabled()) {
                String categoryId = categories.get(index).id();
                openState(viewer, new PunishmentGuiState.Guided(
                        state.viewerId(), state.target(), state.commandName(),
                        state.overview(), categoryId,
                        GuidedIncidentWizard.start(categoryId), 0));
                return;
            }
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(),
                    state.target(),
                    state.commandName(),
                    state.overview(),
                    categories.get(index).id(),
                    0
            ));
        }
    }

    /** The flag is checked on every click, not only when entering the questionnaire. */
    private void guidedClick(Player viewer, Actor actor, PunishmentGuiState.Guided state, int slot) {
        if (!guidedModeEnabled()) {
            message(viewer, "Guided punishment mode was disabled. Use Expert Reasons instead.");
            openState(viewer, categoriesState(state));
            return;
        }
        if (openHistoryFromControl(viewer, state, slot)) {
            return;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
        } else if (slot == PunishmentGuiRenderer.SEARCH_SLOT) {
            openGuidedExpertReasons(viewer, actor, state);
        } else if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            backGuided(viewer, state);
        } else if (state.interview().nextQuestion().isPresent()) {
            chooseGuidedAnswer(viewer, state, slot);
        } else {
            chooseGuidedResult(viewer, actor, state, slot);
        }
    }

    private void openGuidedExpertReasons(Player viewer, Actor actor,
                                         PunishmentGuiState.Guided state) {
        if (!catalog.reasons(actor, state.commandName(), state.categoryId()).isEmpty()) {
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(), state.target(), state.commandName(),
                    state.overview(), state.categoryId(), 0));
        }
    }

    private void backGuided(Player viewer, PunishmentGuiState.Guided state) {
        if (guidedBackGoesToCategories(state.interview())) {
            openState(viewer, categoriesState(state));
            return;
        }
        // One answered question must return to that question, not the category picker.
        openGuidedPage(viewer, state, state.interview().back(), 0);
    }

    /** Only an untouched interview should navigate out of the questionnaire. */
    static boolean guidedBackGoesToCategories(GuidedIncidentWizard interview) {
        return interview.answers().isEmpty();
    }

    private void chooseGuidedAnswer(Player viewer, PunishmentGuiState.Guided state, int slot) {
        GuidedIncidentWizard.Question prompt = state.interview().nextQuestion().orElseThrow();
        for (int index = 0; index < prompt.choices().size()
                && index < PunishmentGuiRenderer.GUIDED_CHOICE_SLOTS.length; index++) {
            if (slot == PunishmentGuiRenderer.GUIDED_CHOICE_SLOTS[index]) {
                var choice = prompt.choices().get(index);
                openGuidedPage(viewer, state,
                        state.interview().answer(prompt.id(), choice.id()), 0);
                return;
            }
        }
    }

    private void chooseGuidedResult(Player viewer, Actor actor,
                                    PunishmentGuiState.Guided state, int slot) {
        if (slot == PunishmentGuiRenderer.CONFIRM_SLOT) {
            prepareGuided(viewer, actor, state);
            return;
        }
        int updated = switch (slot) {
            case PunishmentGuiRenderer.GUIDED_SHORTER_SLOT -> -1;
            case PunishmentGuiRenderer.GUIDED_SUGGESTED_SLOT -> 0;
            case PunishmentGuiRenderer.GUIDED_LONGER_SLOT -> 1;
            default -> state.durationChoice();
        };
        if (updated != state.durationChoice()) {
            openGuidedPage(viewer, state, state.interview(), updated);
        }
    }

    private void openGuidedPage(Player viewer, PunishmentGuiState.Guided state,
                                GuidedIncidentWizard interview, int durationChoice) {
        openState(viewer, new PunishmentGuiState.Guided(
                state.viewerId(), state.target(), state.commandName(),
                state.overview(), state.categoryId(), interview, durationChoice));
    }

    /** An incident can only become a draft through the current policy gates. */
    private void prepareGuided(Player viewer, Actor actor, PunishmentGuiState.Guided state) {
        if (!guidedModeEnabled()) {
            message(viewer, "Guided punishment mode was disabled. No draft was created.");
            return;
        }
        if (state.interview().nextQuestion().isPresent()) {
            message(viewer, "Finish the incident questionnaire before calculating.");
            return;
        }
        GuidedIncidentWizard.Finding finding = state.interview().finding();
        if (finding.needsReview() || finding.candidateId() == null) {
            message(viewer, "This incident requires individual review. Choose Expert Reasons.");
            return;
        }
        Object token = preparationFence.begin(viewer.getUniqueId());
        boolean submitted = submit(viewer,
                () -> evaluateGuided(viewer, actor, state, finding, token));
        if (!submitted) {
            preparationFence.consume(viewer.getUniqueId(), token);
        }
    }

    private void evaluateGuided(Player viewer, Actor actor, PunishmentGuiState.Guided state,
                                GuidedIncidentWizard.Finding finding, Object token) {
        if (!guidedModeEnabled()) {
            message(viewer, "Guided punishment mode was disabled. No draft was created.");
            return;
        }
        if (!targetAllowed(viewer, actor, state.target().playerId())) {
            return;
        }
        if (catalog.reasons(actor, state.commandName(), state.categoryId()).stream()
                .noneMatch(policy -> policy.id().equals(finding.candidateId()))) {
            message(viewer, "Your rank cannot select that configured reason.");
            return;
        }
        PunishmentDraftWorkflow workflow = workflows.get();
        if (workflow == null) {
            message(viewer, "Punishment storage is unavailable; no action was taken.");
            return;
        }
        String explanation = guidedExplanation(state, finding);
        PreparePunishmentDraftRequest readOnly = new PreparePunishmentDraftRequest(
                state.target().playerId(), actor, finding.candidateId(), explanation,
                CaseVisibility.PUBLIC, state.commandName()
        );
        PunishmentEvaluation preview = workflow.preview(readOnly, mode.get());
        if (preview instanceof PunishmentEvaluation.Rejected rejected) {
            message(viewer, rejected.code() + ": " + rejected.message());
            return;
        }
        PunishmentAssessment assessment = ((PunishmentEvaluation.Allowed) preview).assessment();
        GuidedPolicyResolution.Outcome outcome;
        try {
            outcome = GuidedPolicyResolution.calculate(finding, assessment, state.durationChoice());
        } catch (IllegalArgumentException invalid) {
            message(viewer, "The calculated outcome needs manual review.");
            return;
        }
        boolean defaultSanctions = assessment.escalation().selectedStep().sanctions()
                .equals(outcome.sanctions());
        PreparePunishmentDraftRequest proposal = new PreparePunishmentDraftRequest(
                state.target().playerId(), actor, finding.candidateId(),
                explanation + " | " + outcome.reasoning(), CaseVisibility.PUBLIC,
                state.commandName(), defaultSanctions ? List.of() : outcome.sanctions()
        );
        // Recheck just before persistence; the switch may change during async preview.
        if (!guidedModeEnabled()) {
            message(viewer, "Guided punishment mode was disabled during calculation. "
                    + "No draft was created.");
            return;
        }
        PunishmentDraftEvaluation evaluated = workflow.prepare(proposal, mode.get(), assessment);
        // The questionnaire return state is committed only if this request
        // still owns the viewer's navigation at final entity-thread presentation.
        showPrepared(viewer, state.target(), state.commandName(),
                actor, state.overview(), evaluated, token, state);
    }

    private static String guidedExplanation(PunishmentGuiState.Guided state,
                                            GuidedIncidentWizard.Finding finding) {
        StringBuilder result = new StringBuilder(96).append("Guided incident v1 | category=")
                .append(state.categoryId()).append(" | target=").append(state.target().playerId());
        state.interview().answers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.append(" | ").append(entry.getKey())
                        .append('=').append(entry.getValue()));
        return result.append(" | incidentImpact=")
                .append(finding.preliminaryImpact())
                .append(" | staffPreference=")
                .append(switch (state.durationChoice()) {
                    case -1 -> "CONSERVATIVE";
                    case 1 -> "STRONGER";
                    default -> "BALANCED";
                }).toString();
    }

    private void reasonClick(Player viewer, Actor actor, PunishmentGuiState.Reasons state, int slot) {
        List<ReasonPolicy> reasons = catalog.reasons(actor, state.commandName(), state.categoryId());
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
        if (slot == PunishmentGuiRenderer.SEARCH_SLOT) {
            startSearch(viewer, state);
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
                state.categoryId(),
                page
        ));
    }

    private void searchClick(Player viewer, Actor actor, PunishmentGuiState.Search state, int slot) {
        List<ReasonPolicy> results = catalog.search(actor, state.commandName(), state.query());
        if (openHistoryFromControl(viewer, state, slot)
                || handleSearchNavigation(viewer, state, slot, results.size())) {
            return;
        }
        int index = contentIndex(state.page(), slot);
        if (index >= 0 && index < results.size()) {
            prepare(viewer, actor, state, results.get(index));
        }
    }

    private boolean handleSearchNavigation(Player viewer, PunishmentGuiState.Search state,
                                           int slot, int resultCount) {
        if (slot == PunishmentGuiRenderer.SEARCH_SLOT) {
            startSearch(viewer, state);
            return true;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return true;
        }
        if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            openState(viewer, categoriesState(state));
            return true;
        }
        return navigateSearchPages(viewer, state, slot, resultCount);
    }

    private boolean navigateSearchPages(Player viewer, PunishmentGuiState.Search state,
                                        int slot, int resultCount) {
        if (slot == PunishmentGuiRenderer.PREVIOUS_SLOT) {
            if (state.page() > 0) {
                openSearchPage(viewer, state, state.page() - 1);
            }
            return true;
        }
        if (slot == PunishmentGuiRenderer.NEXT_SLOT) {
            if ((state.page() + 1) * PunishmentGuiRenderer.CONTENT_SIZE < resultCount) {
                openSearchPage(viewer, state, state.page() + 1);
            }
            return true;
        }
        return false;
    }

    private void openSearchPage(Player viewer, PunishmentGuiState.Search state, int page) {
        openState(viewer, new PunishmentGuiState.Search(state.viewerId(), state.target(),
                state.commandName(), state.overview(), state.query(), page));
    }

    private void startSearch(Player viewer, PunishmentGuiState state) {
        SearchCapture capture = new SearchCapture(state, clock.instant());
        noteCaptures.remove(viewer.getUniqueId());
        searchCaptures.put(viewer.getUniqueId(), capture);
        viewer.getScheduler().runDelayed(plugin, task -> {
            if (searchCaptures.remove(viewer.getUniqueId(), capture)) {
                viewer.sendMessage(StaffMessageStyle.style(Component.text(
                        "Punishment search timed out. Open the GUI and click Search to try again."
                )));
            }
        }, null, 20L * 60L);
        suppressedClosures.add(viewer.getUniqueId());
        viewer.closeInventory();
        viewer.sendMessage(StaffMessageStyle.style(Component.text(
                "Search punishment reasons: type 2–64 characters, or cancel. Your message will not be broadcast."
        )));
    }

    private void finishSearch(Player viewer, SearchCapture capture, String text) {
        if (!capture.origin().viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        PunishmentGuiState origin = capture.origin();
        if (clock.instant().isAfter(capture.startedAt().plus(Duration.ofSeconds(60)))) {
            message(viewer, "Reason search expired; please try again.");
            openState(viewer, origin);
            return;
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.equalsIgnoreCase("cancel")) {
            openState(viewer, origin);
            return;
        }
        if (!validSearchText(normalized)) {
            message(viewer, "Enter 2–64 search characters, or click Search to try again.");
            openState(viewer, origin);
            return;
        }
        Actor actor = authorizedActor(viewer);
        if (actor == null || !targetAllowed(viewer, actor, origin.target().playerId())) {
            return;
        }
        openState(viewer, new PunishmentGuiState.Search(origin.viewerId(),
                origin.target(), origin.commandName(), origin.overview(), normalized, 0));
    }

    private static boolean validSearchText(String normalized) {
        int length = normalized.codePointCount(0, normalized.length());
        return length >= 2 && length <= 64
                && normalized.codePoints().noneMatch(Character::isISOControl);
    }

    private void reviewClick(Player viewer, Actor actor, PunishmentGuiState.Review state, int slot) {
        if (openHistoryFromControl(viewer, state, slot)) {
            return;
        }
        if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            PunishmentGuiState.Guided previous = matchingGuidedReturn(viewer, state);
            if (previous != null && guidedModeEnabled()) {
                openState(viewer, previous);
                return;
            }
            String categoryId = policies.find(state.draft().reasonId())
                    .map(ReasonPolicy::family)
                    .map(catalog::categoryId)
                    .orElse(PunishmentGuiCategory.OTHER.id());
            if (catalog.reasons(actor, state.commandName(), categoryId).isEmpty()) {
                openState(viewer, categoriesState(state));
                return;
            }
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(),
                    state.target(),
                    state.commandName(),
                    state.overview(),
                    categoryId,
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
            NoteCapture capture = new NoteCapture(state, clock.instant());
            noteCaptures.put(viewer.getUniqueId(), capture);
            viewer.getScheduler().runDelayed(plugin, task -> {
                if (noteCaptures.remove(viewer.getUniqueId(), capture)) {
                    viewer.sendMessage(StaffMessageStyle.style(Component.text(
                            "Punishment note entry timed out. Reopen the saved review to edit it."
                    )));
                }
            }, null, 20L * 60L);
            suppressedClosures.add(viewer.getUniqueId());
            viewer.closeInventory();
            viewer.sendMessage(StaffMessageStyle.style(Component.text(
                    isGuidedDraft(state.draft().internalExplanation())
                            ? "Add a private staff rationale; incident facts are preserved. Type cancel to return."
                            : "Type the private internal explanation in chat, or type cancel. It will not be broadcast."
            )));
            return;
        }
        if (slot == PunishmentGuiRenderer.CONFIRM_SLOT) {
            if (isGuidedDraft(state.draft().internalExplanation()) && !guidedModeEnabled()) {
                message(viewer, "Guided punishment mode is disabled. "
                        + "This saved draft cannot be submitted.");
                return;
            }
            if (!PunishmentGuiRenderer.reviewPolicyCurrent(
                    state.draft().expectation().configurationVersion(), policies.activeVersion())) {
                message(viewer, "Punishment rules changed since this draft was prepared. "
                        + "Go back and reassess before submitting.");
                return;
            }
            if (guidedNeedsStaffRationale(state.draft().internalExplanation())) {
                message(viewer, "You changed the suggested assessment. Add an internal note "
                        + "explaining why before confirming.");
                return;
            }
            if (catalog.find(state.draft().reasonId()).isEmpty()) {
                message(viewer, "That saved reason is no longer available. Go back and choose another reason.");
                return;
            }
            if (!catalog.maySelectReason(actor, state.commandName(), state.draft().reasonId())) {
                message(viewer, "Your current rank or command can no longer select this reason. "
                        + "Return to categories before submitting.");
                return;
            }
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
        Object token = historyFence.begin(viewer.getUniqueId());
        boolean submitted = submit(viewer, () -> {
            ModerationHistoryStore store = histories.get();
            ModerationFeatureSettings active = settings.get();
            if (store == null || active == null) {
                openUnavailableHistory(viewer, returnState, sensitiveHistory, token);
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
                openHistoryState(viewer, new PunishmentGuiState.History(
                        returnState.viewerId(),
                        returnState.target(),
                        returnState.commandName(),
                        returnState.overview(),
                        result,
                        sensitiveHistory,
                        true,
                        returnState
                ), token);
            } catch (IllegalArgumentException exception) {
                historyFence.consume(viewer.getUniqueId(), token);
                message(viewer, "That history page is no longer available.");
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Punishment GUI history lookup failed", exception);
                openUnavailableHistory(viewer, returnState, sensitiveHistory, token);
            }
        });
        if (!submitted) {
            historyFence.consume(viewer.getUniqueId(), token);
        }
    }

    private void openUnavailableHistory(
            Player viewer,
            PunishmentGuiState returnState,
            boolean sensitiveHistory,
            Object token
    ) {
        ModerationHistoryPage empty = new ModerationHistoryPage(
                returnState.target().playerId(),
                1,
                HISTORY_PAGE_SIZE,
                0,
                0,
                List.of()
        );
        openHistoryState(viewer, new PunishmentGuiState.History(
                returnState.viewerId(),
                returnState.target(),
                returnState.commandName(),
                returnState.overview(),
                empty,
                sensitiveHistory,
                false,
                returnState
        ), token);
    }

    private void prepare(
            Player viewer,
            Actor actor,
            PunishmentGuiState state,
            ReasonPolicy policy
    ) {
        Object token = preparationFence.begin(viewer.getUniqueId());
        boolean submitted = submit(viewer, () -> {
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
                    evaluation, token
            );
        });
        if (!submitted) {
            preparationFence.consume(viewer.getUniqueId(), token);
        }
    }

    private boolean guidedModeEnabled() {
        return plugin.getConfig().getBoolean("guided-punishment.enabled", false);
    }

    private static final String GUIDED_AUDIT_PREFIX = "Guided incident v1";
    private static final String STAFF_NOTE_SEPARATOR = " | Staff note: ";

    private static boolean isGuidedDraft(String explanation) {
        return explanation.startsWith(GUIDED_AUDIT_PREFIX);
    }

    static boolean guidedNeedsStaffRationale(String explanation) {
        return isGuidedDraft(explanation)
                && (explanation.contains("staffPreference=CONSERVATIVE")
                        || explanation.contains("staffPreference=STRONGER"))
                && !explanation.contains(STAFF_NOTE_SEPARATOR);
    }

    static String guidedAuditExplanation(String previous, String replacement) {
        if (!isGuidedDraft(previous) || previous.equals(replacement)) {
            return replacement;
        }
        String originalAudit = previous.split(java.util.regex.Pattern.quote(
                STAFF_NOTE_SEPARATOR), 2)[0];
        return originalAudit + STAFF_NOTE_SEPARATOR + replacement.trim();
    }

    private void reprepare(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Review state,
            String explanation,
            CaseVisibility visibility
    ) {
        Object token = preparationFence.begin(viewer.getUniqueId());
        boolean submitted = submit(viewer, () -> {
            if (!targetAllowed(viewer, actor, state.target().playerId())) {
                return;
            }
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                message(viewer, "Moderation storage is not ready; the existing draft remains saved.");
                return;
            }
            if (isGuidedDraft(state.draft().internalExplanation()) && !guidedModeEnabled()) {
                message(viewer, "Guided punishment mode is disabled. "
                        + "This saved draft cannot be modified.");
                return;
            }
            if (isGuidedDraft(state.draft().internalExplanation())
                    && !state.draft().expectation().configurationVersion()
                            .equals(policies.activeVersion())) {
                message(viewer, "The punishment policy changed. Reassess this incident "
                        + "before editing the saved guided draft.");
                return;
            }
            PreparePunishmentDraftRequest edited = new PreparePunishmentDraftRequest(
                    state.target().playerId(),
                    actor,
                    state.draft().reasonId(),
                    guidedAuditExplanation(state.draft().internalExplanation(), explanation),
                    visibility,
                    state.commandName(),
                    PunishmentDraftReprepareOverrides.choose(
                            state.draft().expectation(), state.assessment(),
                            policies.find(state.draft().reasonId()))
            );
            // A note or visibility edit must not silently replace the guided
            // sanctions reviewed by staff, even after a resumed draft has lost
            // its transient assessment. Expert edits retain the original path.
            PunishmentDraftEvaluation evaluation = isGuidedDraft(state.draft().internalExplanation())
                    ? workflow.prepare(edited, mode.get(),
                            state.assessment().orElse(null), state.draft().expectation())
                    : workflow.prepare(edited, mode.get());
            GuidedReturn previous = guidedReturns.get(viewer.getUniqueId());
            PunishmentGuiState.Guided originalAnswers = previous != null
                    && previous.draftId().equals(state.draft().draftId())
                    ? previous.state() : null;
            showPrepared(
                    viewer,
                    state.target(),
                    state.commandName(),
                    actor,
                    state.overview(),
                    evaluation, token, originalAnswers
            );
        });
        if (!submitted) {
            preparationFence.consume(viewer.getUniqueId(), token);
        }
    }

    private void showPrepared(
            Player viewer,
            PlayerIdentity target,
            String commandName,
            Actor actor,
            PunishmentGuiOverview overview,
            PunishmentDraftEvaluation evaluation
    ) {
        showPrepared(viewer, target, commandName, actor, overview, evaluation, null, null);
    }

    private void showPrepared(
            Player viewer,
            PlayerIdentity target,
            String commandName,
            Actor actor,
            PunishmentGuiOverview overview,
            PunishmentDraftEvaluation evaluation,
            Object token
    ) {
        showPrepared(viewer, target, commandName, actor, overview, evaluation, token, null);
    }

    private void showPrepared(
            Player viewer,
            PlayerIdentity target,
            String commandName,
            Actor actor,
            PunishmentGuiOverview overview,
            PunishmentDraftEvaluation evaluation,
            Object token,
            PunishmentGuiState.Guided guidedOrigin
    ) {
        if (evaluation instanceof PunishmentDraftEvaluation.Rejected rejected) {
            preparedMessage(viewer, token, rejected.code() + ": " + rejected.message());
            return;
        }
        PunishmentDraftEvaluation.Prepared prepared = (PunishmentDraftEvaluation.Prepared) evaluation;
        PunishmentAssessment assessment = prepared.assessment();
        if (!PunishmentCommandFilter.matches(commandName, assessment.sanctions())) {
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow != null) {
                workflow.discard(prepared.draft().draftId(), actor.id());
            }
            preparedMessage(viewer, token,
                    "The current recommendation does not contain the sanction type selected by /"
                            + commandName + ".");
            return;
        }
        PunishmentGuiState.Review review = new PunishmentGuiState.Review(
                viewer.getUniqueId(),
                target,
                commandName,
                overview,
                prepared.draft(),
                Optional.of(assessment)
        );
        if (token == null) {
            openState(viewer, review);
        } else {
            presentState(viewer, review, null, token, guidedOrigin);
        }
    }

    private void preparedMessage(Player viewer, Object token, String body) {
        if (token == null) {
            message(viewer, body);
            return;
        }
        onEntity(viewer, () -> {
            if (preparationFence.consume(viewer.getUniqueId(), token)) {
                viewer.sendMessage(StaffMessageStyle.style(Component.text(body)));
            }
        });
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
        if (isGuidedDraft(state.draft().internalExplanation()) && !guidedModeEnabled()) {
            message(viewer, "Guided punishment mode is disabled. "
                    + "No sanction or approval request was submitted.");
            return;
        }
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
            // A feature disable between the GUI click and the worker run must fail closed.
            if (isGuidedDraft(state.draft().internalExplanation()) && !guidedModeEnabled()) {
                message(viewer, "Guided punishment mode was disabled during confirmation. "
                        + "No sanction or approval request was submitted.");
                return Optional.empty();
            }
            return Optional.of(workflow.confirmRouted(state.draft().draftId(), actor, mode.get()));
        } catch (PunishmentDraftCleanupException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Punishment GUI draft cleanup failed after case commit " + exception.accepted().caseId(),
                    exception
            );
            finish(viewer, "Punishment committed as case " + exception.accepted().caseId()
                    + ", but draft cleanup failed. Reconfirming is idempotent.");
            // The case was committed even though draft deletion failed. This
            // first success still deserves its public announcement; replay does not.
            if (!exception.accepted().replayed()) {
                announceNewlyCommitted(viewer, state);
            }
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
            finish(viewer, "Punishment saved | Case " + applied.accepted().caseId()
                    + (applied.accepted().replayed() ? " (already processed)" : "") + '.');
            if (!applied.accepted().replayed()) {
                announceNewlyCommitted(viewer, state);
            }
            return;
        }
        if (result instanceof PunishmentDraftConfirmation.Requested requested) {
            PunishmentRequestStatus status = requested.submitted().request().status();
            if (status == PunishmentRequestStatus.PENDING) {
                finish(viewer, "Approval request "
                        + (requested.submitted().replayed() ? "already exists" : "submitted")
                        + ". No punishment has been applied yet.");
            } else {
                // Recovery can discover a request that was resolved after the
                // original submission committed but draft cleanup failed.
                // Do not tell staff no punishment was applied if it was approved.
                finish(viewer, "Approval request already exists ("
                        + status.name() + "). Check request and case history for its outcome.");
            }
            return;
        }
        handleRejected(viewer, actor, state, (PunishmentDraftConfirmation.Rejected) result);
    }

    /** Publish the first committed result, including after draft cleanup failure. */
    private void announceNewlyCommitted(Player viewer, PunishmentGuiState.Review state) {
        // Resumed reviews have no transient assessment. Use the saved
        // sanctions and an exact-version policy reason, never a fresh
        // recommendation or an unrelated policy after a reload.
        state.assessment()
                .map(assessment -> assessment.policy().publicReason())
                .or(() -> PunishmentPublicAnnouncement.publicReasonForConfirmedDraft(
                        state.draft(), policies))
                .ifPresent(publicReason -> PunishmentPublicAnnouncement.publish(
                        plugin, state.draft().visibility(), false,
                        state.target().currentUsername()
                                .orElse(state.target().playerId().toString()),
                        state.draft().expectation().sanctions(), publicReason));
    }

    /** Reopen only the questionnaire that created this exact target's draft. */
    private PunishmentGuiState.Guided matchingGuidedReturn(
            Player viewer, PunishmentGuiState.Review state) {
        GuidedReturn origin = guidedReturns.get(viewer.getUniqueId());
        return origin != null
                && origin.draftId().equals(state.draft().draftId())
                && origin.state().target().playerId().equals(state.target().playerId())
                ? origin.state() : null;
    }

    private void handleRejected(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Review state,
            PunishmentDraftConfirmation.Rejected rejected
    ) {
        if ("RECOMMENDATION_CHANGED".equals(rejected.code())) {
            if (isGuidedDraft(state.draft().internalExplanation())) {
                // Never reuse a stale guided duration/step without rerunning its
                // facts against the current policy and actual punishment history.
                message(viewer, "The guided recommendation changed. No punishment or "
                        + "request was created. Recalculate this incident before submitting.");
                PunishmentGuiState.Guided previous = matchingGuidedReturn(viewer, state);
                openState(viewer, previous != null && guidedModeEnabled()
                        ? previous : categoriesState(state));
                return;
            }
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

    private PunishmentGuiTargetResolutionFence.Ticket beginTargetResolution(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        noteCaptures.remove(viewer.getUniqueId());
        searchCaptures.remove(viewer.getUniqueId());
        // A new /punish command supersedes any older pending GUI work.
        historyFence.invalidate(viewerId);
        preparationFence.invalidate(viewerId);
        return targetResolutionFence.begin(viewerId,
                viewer.getOpenInventory().getTopInventory());
    }

    private void resolveTarget(
            Player viewer,
            String targetQuery,
            PunishmentGuiTargetResolutionFence.Ticket ticket,
            java.util.function.Consumer<PlayerIdentity> continuation
    ) {
        boolean submitted = submit(viewer, () -> {
            PlayerDirectory directory = players.get();
            if (directory == null) {
                resolvedMessage(viewer, ticket,
                        "Moderation storage is not ready; no player was resolved.");
                return;
            }
            PlayerIdentity target = directory.find(targetQuery).orElse(null);
            if (target == null) {
                resolvedMessage(viewer, ticket,
                        "Player is not present in the authoritative directory. "
                                + "UUIDs and historical names are accepted.");
                return;
            }
            continuation.accept(target);
        });
        if (!submitted) {
            targetResolutionFence.cancel(viewer.getUniqueId(), ticket);
        }
    }

    private void resolvedMessage(Player viewer, PunishmentGuiTargetResolutionFence.Ticket ticket,
                                 String body) {
        onEntity(viewer, () -> {
            if (targetResolutionFence.consume(viewer.getUniqueId(), ticket,
                    viewer.getOpenInventory().getTopInventory())) {
                viewer.sendMessage(StaffMessageStyle.style(Component.text(body)));
            }
        });
    }

    private boolean targetAllowed(Player viewer, Actor actor, UUID targetId,
                                  PunishmentGuiTargetResolutionFence.Ticket ticket) {
        StaffTargetGuard.Result result = targetGuard.check(actor, targetId, false);
        if (result.allowed()) {
            return true;
        }
        resolvedMessage(viewer, ticket, result.message());
        return false;
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
        noteCaptures.remove(viewer.getUniqueId());
        searchCaptures.remove(viewer.getUniqueId());
        historyFence.invalidate(viewer.getUniqueId());
        preparationFence.invalidate(viewer.getUniqueId());
        targetResolutionFence.invalidate(viewer.getUniqueId());
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
        noteCaptures.remove(viewer.getUniqueId());
        searchCaptures.remove(viewer.getUniqueId());
        historyFence.invalidate(viewer.getUniqueId());
        preparationFence.invalidate(viewer.getUniqueId());
        targetResolutionFence.invalidate(viewer.getUniqueId());
        presentState(viewer, state, null, null);
    }

    private void openHistoryState(Player viewer, PunishmentGuiState.History state, Object token) {
        presentState(viewer, state, token, null);
    }

    private void presentResolvedState(Player viewer, PunishmentGuiState state,
                                      PunishmentGuiTargetResolutionFence.Ticket ticket) {
        presentState(viewer, state, null, null, null, ticket);
    }

    private void presentState(Player viewer, PunishmentGuiState state,
                              Object historyToken, Object preparationToken) {
        presentState(viewer, state, historyToken, preparationToken, null, null);
    }

    private void presentState(Player viewer, PunishmentGuiState state,
                              Object historyToken, Object preparationToken,
                              PunishmentGuiState.Guided guidedOrigin) {
        presentState(viewer, state, historyToken, preparationToken, guidedOrigin, null);
    }

    private void presentState(Player viewer, PunishmentGuiState state,
                              Object historyToken, Object preparationToken,
                              PunishmentGuiState.Guided guidedOrigin,
                              PunishmentGuiTargetResolutionFence.Ticket targetTicket) {
        onEntity(viewer, () -> {
            // Consume at final entity-thread presentation, never at worker completion.
            if (targetTicket != null
                    && !targetResolutionFence.consume(viewer.getUniqueId(), targetTicket,
                            viewer.getOpenInventory().getTopInventory())) {
                return;
            }
            if (historyToken != null
                    && !historyFence.consume(viewer.getUniqueId(), historyToken)) {
                return;
            }
            if (preparationToken != null
                    && !preparationFence.consume(viewer.getUniqueId(), preparationToken)) {
                return;
            }
            searchCaptures.remove(viewer.getUniqueId());
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
            Inventory inventory = renderer.render(state, actor, guidedModeEnabled());
            // Resolved targets, history pages and prepared reviews also supersede
            // abandoned chat-note prompts, even without calling openState().
            noteCaptures.remove(viewer.getUniqueId());
            if (guidedOrigin != null && state instanceof PunishmentGuiState.Review review
                    && guidedModeEnabled()) {
                guidedReturns.put(viewer.getUniqueId(),
                        new GuidedReturn(review.draft().draftId(), guidedOrigin));
            }
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
        noteCaptures.remove(viewer.getUniqueId());
        searchCaptures.remove(viewer.getUniqueId());
        historyFence.invalidate(viewer.getUniqueId());
        preparationFence.invalidate(viewer.getUniqueId());
        targetResolutionFence.invalidate(viewer.getUniqueId());
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof PunishmentGuiHolder) {
            suppressedClosures.add(viewer.getUniqueId());
        }
        viewer.closeInventory();
    }

    private void finish(Player viewer, String result) {
        onEntity(viewer, () -> {
            guidedReturns.remove(viewer.getUniqueId());
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

    private record GuidedReturn(UUID draftId, PunishmentGuiState.Guided state) {
    }

    private record SearchCapture(PunishmentGuiState origin, Instant startedAt) {
        private SearchCapture {
            if (origin == null || startedAt == null) {
                throw new IllegalArgumentException("Search origin and time must be provided");
            }
        }
    }

    static boolean noteCaptureExpired(Instant startedAt, Instant now) {
        return !now.isBefore(startedAt.plus(Duration.ofSeconds(60)));
    }

    private record NoteCapture(PunishmentGuiState.Review review, Instant startedAt) {
        private NoteCapture {
            if (review == null || startedAt == null) {
                throw new IllegalArgumentException("punishment note capture requires a review and start time");
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
