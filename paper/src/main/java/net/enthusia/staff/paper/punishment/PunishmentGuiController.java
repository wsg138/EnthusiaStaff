package net.enthusia.staff.paper.punishment;

import io.papermc.paper.event.player.AsyncChatEvent;
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
import net.enthusia.staff.domain.application.PunishmentDraftEvaluation;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.paper.auth.PaperActorResolver;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentGuiController implements Listener {
    private final JavaPlugin plugin;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentDraftWorkflow> workflows;
    private final Supplier<PlayerDirectory> players;
    private final AuthorizationPolicy authorization;
    private final ReasonPolicyRepository policies;
    private final ExecutorService workers;
    private final PunishmentGuiCatalog catalog;
    private final PunishmentGuiRenderer renderer;
    private final Set<UUID> suppressedClosures = ConcurrentHashMap.newKeySet();
    private final Set<UUID> confirmations = ConcurrentHashMap.newKeySet();
    private final Map<UUID, NoteCapture> noteCaptures = new ConcurrentHashMap<>();

    public PunishmentGuiController(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ReasonPolicyRepository policies,
            ExecutorService workers
    ) {
        if (plugin == null || mode == null || workflows == null || players == null
                || authorization == null || policies == null || workers == null) {
            throw new IllegalArgumentException("punishment GUI dependencies must be present");
        }
        this.plugin = plugin;
        this.mode = mode;
        this.workflows = workflows;
        this.players = players;
        this.authorization = authorization;
        this.policies = policies;
        this.workers = workers;
        this.catalog = new PunishmentGuiCatalog(policies, authorization);
        this.renderer = new PunishmentGuiRenderer(catalog);
    }

    public void open(Player viewer, String targetQuery, String commandName) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        String normalizedCommand = normalizeCommand(commandName);
        resolveTarget(viewer, targetQuery, target -> openState(
                viewer,
                new PunishmentGuiState.Categories(viewer.getUniqueId(), target, normalizedCommand, 0)
        ));
    }

    public void resume(Player viewer, String targetQuery, String invokedCommand) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        resolveTarget(viewer, targetQuery, target -> submit(viewer, () -> {
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
            openState(viewer, new PunishmentGuiState.Review(
                    viewer.getUniqueId(), target, draft.commandName(), draft, Optional.empty()
            ));
        }));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof PunishmentGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        PunishmentGuiState state = holder.state();
        if (!state.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            closeWithoutResume(viewer);
            return;
        }
        if (state instanceof PunishmentGuiState.Categories categories) {
            categoryClick(viewer, actor, categories, slot);
        } else if (state instanceof PunishmentGuiState.Reasons reasons) {
            reasonClick(viewer, actor, reasons, slot);
        } else if (state instanceof PunishmentGuiState.Review review) {
            reviewClick(viewer, actor, review, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PunishmentGuiHolder) {
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
                viewer.sendMessage(Component.text(
                        "The internal explanation must contain 1 to 4000 characters; the prior draft remains saved."
                ));
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

    private void categoryClick(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Categories state,
            int slot
    ) {
        List<String> categories = catalog.categories(actor, state.commandName());
        if (slot == PunishmentGuiRenderer.PREVIOUS_SLOT && state.page() > 0) {
            openState(viewer, new PunishmentGuiState.Categories(
                    state.viewerId(), state.target(), state.commandName(), state.page() - 1
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.NEXT_SLOT
                && (state.page() + 1) * PunishmentGuiRenderer.CONTENT_SIZE < categories.size()) {
            openState(viewer, new PunishmentGuiState.Categories(
                    state.viewerId(), state.target(), state.commandName(), state.page() + 1
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        int index = state.page() * PunishmentGuiRenderer.CONTENT_SIZE + slot;
        if (slot < PunishmentGuiRenderer.CONTENT_SIZE && index < categories.size()) {
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(), state.target(), state.commandName(), categories.get(index), 0
            ));
        }
    }

    private void reasonClick(Player viewer, Actor actor, PunishmentGuiState.Reasons state, int slot) {
        List<ReasonPolicy> reasons = catalog.reasons(actor, state.commandName(), state.family());
        if (slot == PunishmentGuiRenderer.PREVIOUS_SLOT && state.page() > 0) {
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(), state.target(), state.commandName(), state.family(), state.page() - 1
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.NEXT_SLOT
                && (state.page() + 1) * PunishmentGuiRenderer.CONTENT_SIZE < reasons.size()) {
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(), state.target(), state.commandName(), state.family(), state.page() + 1
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            openState(viewer, new PunishmentGuiState.Categories(
                    state.viewerId(), state.target(), state.commandName(), 0
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        int index = state.page() * PunishmentGuiRenderer.CONTENT_SIZE + slot;
        if (slot < PunishmentGuiRenderer.CONTENT_SIZE && index < reasons.size()) {
            prepare(viewer, actor, state, reasons.get(index));
        }
    }

    private void reviewClick(Player viewer, Actor actor, PunishmentGuiState.Review state, int slot) {
        if (slot == PunishmentGuiRenderer.BACK_SLOT) {
            String family = policies.find(state.draft().reasonId())
                    .map(ReasonPolicy::family)
                    .orElse(state.draft().reasonId());
            openState(viewer, new PunishmentGuiState.Reasons(
                    state.viewerId(), state.target(), state.commandName(), family, 0
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
            viewer.sendMessage(Component.text(
                    "Type the private internal explanation in chat, or type cancel. It will not be broadcast."
            ));
            return;
        }
        if (slot == PunishmentGuiRenderer.CONFIRM_SLOT) {
            confirm(viewer, actor, state);
        }
    }

    private void prepare(
            Player viewer,
            Actor actor,
            PunishmentGuiState.Reasons state,
            ReasonPolicy policy
    ) {
        submit(viewer, () -> {
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
            showPrepared(viewer, state.target(), state.commandName(), actor, evaluation);
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
            showPrepared(viewer, state.target(), state.commandName(), actor, evaluation);
        });
    }

    private void showPrepared(
            Player viewer,
            PlayerIdentity target,
            String commandName,
            Actor actor,
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
                viewer.getUniqueId(), target, commandName, prepared.draft(), Optional.of(assessment)
        ));
    }

    private void confirm(Player viewer, Actor actor, PunishmentGuiState.Review state) {
        if (!confirmations.add(viewer.getUniqueId())) {
            viewer.sendMessage(Component.text("That punishment confirmation is already in progress."));
            return;
        }
        boolean submitted = submit(viewer, () -> {
            try {
                PunishmentDraftWorkflow workflow = workflows.get();
                if (workflow == null) {
                    message(viewer, "Moderation storage is not ready; no action was taken.");
                    return;
                }
                PunishmentResult result;
                try {
                    result = workflow.confirm(state.draft().draftId(), actor, mode.get());
                } catch (PunishmentDraftCleanupException exception) {
                    plugin.getLogger().log(
                            Level.SEVERE,
                            "Punishment GUI draft cleanup failed after case commit " + exception.accepted().caseId(),
                            exception
                    );
                    finish(viewer, "Punishment committed as case " + exception.accepted().caseId()
                            + ", but draft cleanup failed. Reconfirming is idempotent.");
                    return;
                }
                if (result instanceof PunishmentResult.Accepted accepted) {
                    finish(viewer, "Punishment committed as case " + accepted.caseId()
                            + (accepted.replayed() ? " (idempotent replay)" : "") + '.');
                    return;
                }
                PunishmentResult.Rejected rejected = (PunishmentResult.Rejected) result;
                if ("RECOMMENDATION_CHANGED".equals(rejected.code())) {
                    message(viewer, "The recommendation changed. A fresh review is being opened; no case was created.");
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
            } finally {
                confirmations.remove(viewer.getUniqueId());
            }
        });
        if (!submitted) {
            confirmations.remove(viewer.getUniqueId());
        }
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
                message(viewer, "Player is not present in the authoritative directory. UUIDs and historical names are accepted.");
                return;
            }
            continuation.accept(target);
        });
    }

    private void openState(Player viewer, PunishmentGuiState state) {
        onEntity(viewer, () -> {
            Actor actor = authorizedActor(viewer);
            if (actor == null) {
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
                || !authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                || !viewer.hasPermission("enthusiastaff.punish.configured")) {
            viewer.sendMessage(Component.text("You do not have punishment authority."));
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
            viewer.sendMessage(Component.text(result));
        });
    }

    private boolean submit(Player viewer, Runnable work) {
        try {
            workers.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Punishment GUI workflow failed", exception);
                    message(viewer, "The punishment workflow failed and its outcome was not confirmed. Check case history before retrying.");
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            viewer.sendMessage(Component.text("The moderation work queue is full; no action was taken."));
            return false;
        }
    }

    private void message(Player viewer, String body) {
        onEntity(viewer, () -> viewer.sendMessage(Component.text(body)));
    }

    private void onEntity(Player player, Runnable task) {
        player.getScheduler().execute(plugin, task, null, 1L);
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

    private record NoteCapture(PunishmentGuiState.Review review) {
        private NoteCapture {
            if (review == null) {
                throw new IllegalArgumentException("punishment note capture requires a review");
            }
        }
    }
}
