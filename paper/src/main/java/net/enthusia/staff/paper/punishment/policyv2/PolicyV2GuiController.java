package net.enthusia.staff.paper.punishment.policyv2;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Complete Policy v2 manual GUI interaction controller. It is deliberately not
 * registered by PaperCommandRegistrar while Policy v1 remains authoritative.
 */
public final class PolicyV2GuiController implements Listener {
    private static final String CANCEL = "cancel";
    private static final String SKIP = "skip";
    private static final String OPERATION_PREFIX = "policy-v2-gui:";

    private final JavaPlugin plugin;
    private final Clock clock;
    private final PolicyV2ManualWorkflow workflow;
    private final ExecutorService workers;
    private final BooleanSupplier enabled;
    private final PolicyV2GuiNavigator navigator;
    private final PolicyV2GuiRenderer renderer = new PolicyV2GuiRenderer();
    private final Map<UUID, InputCapture> inputCaptures = new ConcurrentHashMap<>();
    private final Set<UUID> confirmations = ConcurrentHashMap.newKeySet();

    public PolicyV2GuiController(
            JavaPlugin plugin,
            Clock clock,
            PolicyV2ManualWorkflow workflow,
            ExecutorService workers
    ) {
        this(plugin, clock, workflow, workers, () -> true);
    }

    public PolicyV2GuiController(
            JavaPlugin plugin,
            Clock clock,
            PolicyV2ManualWorkflow workflow,
            ExecutorService workers,
            BooleanSupplier enabled
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.workflow = java.util.Objects.requireNonNull(workflow, "workflow");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.enabled = java.util.Objects.requireNonNull(enabled, "enabled");
        this.navigator = new PolicyV2GuiNavigator(workflow);
    }

    /**
     * Called only by a future explicitly authorized shadow/cutover integration.
     * W3A intentionally does not invoke this from the live command registrar.
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player viewer, PlayerIdentity target) {
        if (!enabled.getAsBoolean() || target == null || authorizedActor(viewer) == null) {
            return;
        }
        String targetName = target.currentUsername().orElse(target.playerId().toString());
        openState(viewer, navigator.start(
                viewer.getUniqueId(), target.playerId(), targetName, clock.instant()
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof PolicyV2GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!enabled.getAsBoolean()) {
            viewer.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        PolicyV2GuiState state = holder.state();
        if (!state.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            viewer.closeInventory();
            return;
        }
        dispatch(viewer, actor, state, slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PolicyV2GuiHolder) {
            event.setCancelled(true);
            if (!enabled.getAsBoolean()) {
                event.getWhoClicked().closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player viewer = event.getPlayer();
        InputCapture capture = inputCaptures.remove(viewer.getUniqueId());
        if (capture == null) {
            return;
        }
        event.setCancelled(true);
        if (!enabled.getAsBoolean()) {
            message(viewer, "Policy v2 shadow mode is disabled; no evaluation was recorded.");
            return;
        }
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        onEntity(viewer, () -> handleInput(viewer, capture, input));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        inputCaptures.remove(viewerId);
        confirmations.remove(viewerId);
    }

    private void dispatch(Player viewer, Actor actor, PolicyV2GuiState state, int slot) {
        if (slot == PolicyV2GuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        if (state instanceof PolicyV2GuiState.Categories categories) {
            categoryClick(viewer, categories, slot);
        } else if (state instanceof PolicyV2GuiState.Offenses offenses) {
            offenseClick(viewer, offenses, slot);
        } else if (state instanceof PolicyV2GuiState.Questions questions) {
            questionClick(viewer, questions, slot);
        } else if (state instanceof PolicyV2GuiState.Review review) {
            reviewClick(viewer, actor, review, slot);
        } else {
            resultClick(viewer, actor, (PolicyV2GuiState.Result) state, slot);
        }
    }

    private void categoryClick(Player viewer, PolicyV2GuiState.Categories state, int slot) {
        int index = slot - PolicyV2GuiRenderer.CONTENT_START;
        if (index < 0 || index >= state.categories().size()) {
            return;
        }
        try {
            openState(viewer, navigator.selectCategory(state, index));
        } catch (RuntimeException exception) {
            message(viewer, exception.getMessage());
        }
    }

    private void offenseClick(Player viewer, PolicyV2GuiState.Offenses state, int slot) {
        if (slot == PolicyV2GuiRenderer.BACK_SLOT) {
            openBack(viewer, state);
            return;
        }
        if (pageControl(viewer, state, slot)) {
            return;
        }
        int index = state.page() * PolicyV2GuiRenderer.PAGE_SIZE
                + slot - PolicyV2GuiRenderer.CONTENT_START;
        if (slot < PolicyV2GuiRenderer.CONTENT_START || index >= state.offenses().size()) {
            return;
        }
        openState(viewer, navigator.selectOffense(state, index));
    }

    private boolean pageControl(Player viewer, PolicyV2GuiState.Offenses state, int slot) {
        if (slot == PolicyV2GuiRenderer.PREVIOUS_SLOT && state.page() > 0) {
            openOffensePage(viewer, state, state.page() - 1);
            return true;
        }
        if (slot == PolicyV2GuiRenderer.NEXT_SLOT
                && (state.page() + 1) * PolicyV2GuiRenderer.PAGE_SIZE < state.offenses().size()) {
            openOffensePage(viewer, state, state.page() + 1);
            return true;
        }
        return false;
    }

    private void openOffensePage(
            Player viewer,
            PolicyV2GuiState.Offenses state,
            int page
    ) {
        openState(viewer, new PolicyV2GuiState.Offenses(
                state.viewerId(), state.targetId(), state.targetName(),
                state.draft(), state.offenses(), page
        ));
    }

    private void questionClick(Player viewer, PolicyV2GuiState.Questions state, int slot) {
        if (slot == PolicyV2GuiRenderer.BACK_SLOT) {
            openBack(viewer, state);
            return;
        }
        if (slot == PolicyV2GuiRenderer.PRIMARY_SLOT) {
            openReview(viewer, state);
            return;
        }
        if (state.draft().isPolicyGap()) {
            if (slot == PolicyV2GuiRenderer.POLICY_GAP_QUESTION_SLOT) {
                beginGapInput(viewer, state);
            }
            return;
        }
        int index = slot - PolicyV2GuiRenderer.CONTENT_START;
        if (index >= 0 && index < state.questions().size()) {
            beginQuestionInput(viewer, state, state.questions().get(index));
        }
    }

    private void openReview(Player viewer, PolicyV2GuiState.Questions state) {
        try {
            openState(viewer, navigator.review(state));
        } catch (IllegalArgumentException exception) {
            message(viewer, exception.getMessage());
        }
    }

    private void reviewClick(
            Player viewer,
            Actor actor,
            PolicyV2GuiState.Review state,
            int slot
    ) {
        if (slot == PolicyV2GuiRenderer.BACK_SLOT) {
            openBack(viewer, state);
        } else if (slot == PolicyV2GuiRenderer.PRIMARY_SLOT) {
            calculateResult(viewer, actor, state);
        }
    }

    private void resultClick(
            Player viewer,
            Actor actor,
            PolicyV2GuiState.Result state,
            int slot
    ) {
        if (slot == PolicyV2GuiRenderer.BACK_SLOT) {
            openBack(viewer, state);
        } else if (slot == PolicyV2GuiRenderer.PRIMARY_SLOT) {
            confirmShadow(viewer, actor, state);
        }
    }

    private void beginQuestionInput(
            Player viewer,
            PolicyV2GuiState.Questions state,
            IncidentAttributeDefinition definition
    ) {
        inputCaptures.put(viewer.getUniqueId(), InputCapture.question(state, definition));
        viewer.closeInventory();
        String optional = definition.required() ? "" : ", " + SKIP + " to leave it unanswered";
        message(viewer, PolicyV2QuestionInput.prompt(definition)
                + optional + ", or " + CANCEL + " to return.");
    }

    private void beginGapInput(Player viewer, PolicyV2GuiState.Questions state) {
        inputCaptures.put(viewer.getUniqueId(), InputCapture.policyGap(state));
        viewer.closeInventory();
        message(viewer, "Describe what happened in 1-500 characters, or " + CANCEL + " to return.");
    }

    private void handleInput(Player viewer, InputCapture capture, String input) {
        if (CANCEL.equalsIgnoreCase(input)) {
            openState(viewer, capture.state());
            return;
        }
        try {
            PolicyV2ManualDraft changed = capture.policyGap()
                    ? capture.state().draft().describePolicyGap(input)
                    : answer(capture, input);
            openState(viewer, navigator.withDraft(capture.state(), changed));
        } catch (IllegalArgumentException exception) {
            message(viewer, exception.getMessage());
            openState(viewer, capture.state());
        }
    }

    private PolicyV2ManualDraft answer(InputCapture capture, String input) {
        IncidentAttributeDefinition definition = capture.definition().orElseThrow();
        if (SKIP.equalsIgnoreCase(input) && !definition.required()) {
            return capture.state().draft().clearAnswer(definition.id());
        }
        IncidentAttributeValue value = PolicyV2QuestionInput.parse(definition, input);
        return workflow.answer(capture.state().draft(), definition.id(), value);
    }

    private void calculateResult(
            Player viewer,
            Actor actor,
            PolicyV2GuiState.Review state
    ) {
        submit(viewer, () -> {
            PolicyV2ManualReview review = workflow.review(actor, state.draft());
            openState(viewer, navigator.result(state, review, UUID.randomUUID()));
        });
    }

    private void confirmShadow(
            Player viewer,
            Actor actor,
            PolicyV2GuiState.Result state
    ) {
        if (!confirmations.add(viewer.getUniqueId())) {
            message(viewer, "That Policy v2 shadow result is already being recorded.");
            return;
        }
        boolean queued = submit(viewer, () -> confirmQueued(viewer, actor, state));
        if (!queued) {
            confirmations.remove(viewer.getUniqueId());
        }
    }

    private void confirmQueued(
            Player viewer,
            Actor actor,
            PolicyV2GuiState.Result state
    ) {
        try {
            PolicyV2ManualWorkflow.SubmissionResult result = workflow.submitShadow(
                    actor, state.review(), OPERATION_PREFIX + state.operationId()
            );
            presentSubmission(viewer, state, result);
        } finally {
            confirmations.remove(viewer.getUniqueId());
        }
    }

    private void presentSubmission(
            Player viewer,
            PolicyV2GuiState.Result state,
            PolicyV2ManualWorkflow.SubmissionResult result
    ) {
        if (result instanceof PolicyV2ManualWorkflow.SubmissionResult.Recorded recorded) {
            finish(viewer, "Policy v2 shadow evaluation recorded. No live punishment was applied. "
                    + "Eventual route: " + route(recorded.route()) + '.');
        } else if (result instanceof PolicyV2ManualWorkflow.SubmissionResult.Stale stale) {
            message(viewer, "Policy or history changed. The result was recalculated; review it again.");
            openState(viewer, navigator.refresh(state, stale.refreshed()));
        } else if (result instanceof PolicyV2ManualWorkflow.SubmissionResult.Rejected rejected) {
            message(viewer, rejected.message());
            openState(viewer, state);
        } else {
            PolicyV2ManualWorkflow.SubmissionResult.Conflict conflict =
                    (PolicyV2ManualWorkflow.SubmissionResult.Conflict) result;
            message(viewer, conflict.message());
            openState(viewer, state);
        }
    }

    private void openBack(Player viewer, PolicyV2GuiState state) {
        navigator.back(state).ifPresentOrElse(
                previous -> openState(viewer, previous),
                viewer::closeInventory
        );
    }

    private void openState(Player viewer, PolicyV2GuiState state) {
        onEntity(viewer, () -> {
            if (enabled.getAsBoolean() && authorizedActor(viewer) != null) {
                viewer.openInventory(renderer.render(state));
            }
        });
    }

    private Actor authorizedActor(Player viewer) {
        if (viewer == null || !enabled.getAsBoolean()) {
            return null;
        }
        Actor actor = PaperActorResolver.resolve(viewer).orElse(null);
        if (actor == null || !actor.id().equals(viewer.getUniqueId()) || !workflow.mayStart(actor)) {
            viewer.sendMessage(StaffMessageStyle.style(Component.text(
                    "You do not have Policy v2 punishment workflow authority."
            )));
            return null;
        }
        return actor;
    }

    private boolean submit(Player viewer, Runnable work) {
        try {
            workers.execute(() -> runWork(viewer, work));
            return true;
        } catch (RejectedExecutionException exception) {
            message(viewer, "The moderation work queue is full; no Policy v2 action was taken.");
            return false;
        }
    }

    private void runWork(Player viewer, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Policy v2 GUI workflow failed", exception);
            message(viewer, "The Policy v2 workflow failed; no live punishment was applied.");
        }
    }

    private void finish(Player viewer, String body) {
        onEntity(viewer, () -> {
            viewer.closeInventory();
            viewer.sendMessage(StaffMessageStyle.style(Component.text(body)));
        });
    }

    private void message(Player viewer, String body) {
        onEntity(viewer, () -> viewer.sendMessage(StaffMessageStyle.style(Component.text(body))));
    }

    private void onEntity(Player viewer, Runnable task) {
        viewer.getScheduler().execute(plugin, task, null, 1L);
    }

    private static String route(PolicyV2ManualReview.ApprovalRoute route) {
        return switch (route) {
            case DIRECT_CONFIRM -> "direct confirmation";
            case APPROVAL_REQUIRED -> "approval request";
            case ADMIN_FOUNDER_REVIEW -> "Admin/Founder review";
        };
    }

    private record InputCapture(
            PolicyV2GuiState.Questions state,
            Optional<IncidentAttributeDefinition> definition,
            boolean policyGap
    ) {
        private InputCapture {
            if (state == null || definition == null || policyGap == definition.isPresent()) {
                throw new IllegalArgumentException("Policy v2 input capture is invalid");
            }
        }

        static InputCapture question(
                PolicyV2GuiState.Questions state,
                IncidentAttributeDefinition definition
        ) {
            return new InputCapture(state, Optional.of(definition), false);
        }

        static InputCapture policyGap(PolicyV2GuiState.Questions state) {
            return new InputCapture(state, Optional.empty(), true);
        }
    }
}
