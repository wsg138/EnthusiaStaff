package net.enthusia.staff.paper.sanction;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.paper.auth.PaperActorResolver;
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

public final class SanctionChangeGuiController implements Listener {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<OperationalMode> mode;
    private final Supplier<SanctionChangeService> services;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<CaseLookup> cases;
    private final Supplier<CaseReviewStore> reviews;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;
    private final SanctionChangeGuiCatalog catalog;
    private final SanctionChangeGuiRenderer renderer;
    private final Map<UUID, InputCapture> inputCaptures = new ConcurrentHashMap<>();
    private final Set<UUID> confirmations = ConcurrentHashMap.newKeySet();

    public SanctionChangeGuiController(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<SanctionChangeService> services,
            Supplier<PlayerDirectory> players,
            Supplier<CaseLookup> cases,
            Supplier<CaseReviewStore> reviews,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        if (plugin == null || clock == null || mode == null || services == null || players == null
                || cases == null || reviews == null || authorization == null || workers == null) {
            throw new IllegalArgumentException("sanction change GUI dependencies must be present");
        }
        this.plugin = plugin;
        this.clock = clock;
        this.mode = mode;
        this.services = services;
        this.players = players;
        this.cases = cases;
        this.reviews = reviews;
        this.authorization = authorization;
        this.workers = workers;
        this.catalog = new SanctionChangeGuiCatalog(authorization);
        this.renderer = new SanctionChangeGuiRenderer(catalog);
    }

    public void open(Player viewer, String targetQuery, String commandName) {
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            return;
        }
        String normalizedCommand = commandName.toLowerCase(Locale.ROOT);
        submit(viewer, () -> resolveAndOpen(viewer, targetQuery, normalizedCommand));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof SanctionChangeGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        SanctionChangeGuiState state = holder.state();
        if (!state.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Actor actor = authorizedActor(viewer);
        if (actor == null) {
            close(viewer);
            return;
        }
        if (state instanceof SanctionChangeGuiState.Cases casesState) {
            caseClick(viewer, casesState, slot);
        } else if (state instanceof SanctionChangeGuiState.Actions actions) {
            actionClick(viewer, actor, actions, slot);
        } else if (state instanceof SanctionChangeGuiState.Review review) {
            reviewClick(viewer, actor, review, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof SanctionChangeGuiHolder) {
            event.setCancelled(true);
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
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        onEntity(viewer, () -> handleInput(viewer, capture, input));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        inputCaptures.remove(viewerId);
        confirmations.remove(viewerId);
    }

    private void resolveAndOpen(
            Player viewer,
            String targetQuery,
            String commandName
    ) {
        CaseReviewStore reviewStore = reviews.get();
        CaseLookup caseLookup = cases.get();
        PlayerDirectory directory = players.get();
        if (reviewStore == null || caseLookup == null || directory == null) {
            message(viewer, "Moderation storage is not ready; no case was opened.");
            return;
        }
        CaseId direct = parseCaseId(targetQuery);
        if (direct != null) {
            CaseReview review = reviewStore.find(direct).orElse(null);
            if (review != null && matchesCommandCase(commandName, direct, caseLookup)) {
                openState(viewer, new SanctionChangeGuiState.Actions(
                        viewer.getUniqueId(), commandName, review.targetId().toString(), review, Optional.empty()
                ));
                return;
            }
        }
        PlayerIdentity target = directory.find(targetQuery).orElse(null);
        if (target == null) {
            message(viewer, "No matching player or case was found.");
            return;
        }
        String targetLabel = target.currentUsername().orElse(target.playerId().toString());
        if (!"removepunishment".equals(commandName)) {
            CaseId latest = caseLookup.latestCase(
                    target.playerId(), SanctionChangeAccess.aliasTypes(commandName), true
            ).orElse(null);
            CaseReview review = latest == null ? null : reviewStore.find(latest).orElse(null);
            if (review == null) {
                message(viewer, "No matching active punishment was found for " + targetLabel + '.');
                return;
            }
            openState(viewer, new SanctionChangeGuiState.Actions(
                    viewer.getUniqueId(), commandName, targetLabel, review, Optional.empty()
            ));
            return;
        }
        List<CaseReview> recent = reviewStore.recent(target.playerId(), 100);
        if (recent.isEmpty()) {
            message(viewer, "No punishment history was found for " + targetLabel + '.');
            return;
        }
        openState(viewer, new SanctionChangeGuiState.Cases(
                viewer.getUniqueId(), commandName, targetLabel, recent, 0
        ));
    }

    private void caseClick(
            Player viewer,
            SanctionChangeGuiState.Cases state,
            int slot
    ) {
        if (slot == SanctionChangeGuiRenderer.PREVIOUS_SLOT && state.page() > 0) {
            openState(viewer, new SanctionChangeGuiState.Cases(
                    state.viewerId(), state.commandName(), state.targetLabel(), state.cases(), state.page() - 1
            ));
            return;
        }
        if (slot == SanctionChangeGuiRenderer.NEXT_SLOT
                && (state.page() + 1) * SanctionChangeGuiRenderer.CONTENT_SIZE < state.cases().size()) {
            openState(viewer, new SanctionChangeGuiState.Cases(
                    state.viewerId(), state.commandName(), state.targetLabel(), state.cases(), state.page() + 1
            ));
            return;
        }
        if (slot == SanctionChangeGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        int index = state.page() * SanctionChangeGuiRenderer.CONTENT_SIZE + slot;
        if (slot < SanctionChangeGuiRenderer.CONTENT_SIZE && index < state.cases().size()) {
            openState(viewer, new SanctionChangeGuiState.Actions(
                    state.viewerId(), state.commandName(), state.targetLabel(), state.cases().get(index),
                    Optional.of(state)
            ));
        }
    }

    private void actionClick(
            Player viewer,
            Actor actor,
            SanctionChangeGuiState.Actions state,
            int slot
    ) {
        if (slot == SanctionChangeGuiRenderer.BACK_SLOT && state.origin().isPresent()) {
            openState(viewer, state.origin().orElseThrow());
            return;
        }
        if (slot == SanctionChangeGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        List<SanctionChangeAction> actions = catalog.actions(
                actor, state.review(), viewer::hasPermission, state.commandName()
        );
        int index = slot - SanctionChangeGuiRenderer.ACTION_START;
        if (slot >= SanctionChangeGuiRenderer.ACTION_START
                && slot <= SanctionChangeGuiRenderer.ACTION_END
                && index < actions.size()) {
            beginInput(viewer, state, actions.get(index));
        }
    }

    private void reviewClick(
            Player viewer,
            Actor actor,
            SanctionChangeGuiState.Review state,
            int slot
    ) {
        if (slot == SanctionChangeGuiRenderer.BACK_SLOT) {
            openState(viewer, new SanctionChangeGuiState.Actions(
                    state.viewerId(), state.commandName(), state.targetLabel(), state.caseReview(), state.origin()
            ));
            return;
        }
        if (slot == SanctionChangeGuiRenderer.CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        if (slot == SanctionChangeGuiRenderer.CONFIRM_SLOT) {
            confirm(viewer, actor, state);
        }
    }

    private void beginInput(
            Player viewer,
            SanctionChangeGuiState.Actions state,
            SanctionChangeAction action
    ) {
        boolean expiration = action == SanctionChangeAction.REDUCE_DURATION
                || action == SanctionChangeAction.REPLACE_EXPIRATION;
        inputCaptures.put(viewer.getUniqueId(), new InputCapture(
                state,
                action,
                expiration ? InputStage.EXPIRATION : InputStage.REASON,
                Optional.empty()
        ));
        close(viewer);
        viewer.sendMessage(Component.text(expiration
                ? "Type the new ISO-8601 expiration in chat (for example 2026-08-01T00:00:00Z), or cancel."
                : "Type the private audit reason in chat, or cancel. It will not be broadcast."));
    }

    private void handleInput(Player viewer, InputCapture capture, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            openState(viewer, capture.state());
            return;
        }
        if (capture.stage() == InputStage.EXPIRATION) {
            Instant expiration;
            try {
                expiration = Instant.parse(input);
            } catch (DateTimeParseException exception) {
                viewer.sendMessage(Component.text("That expiration is invalid; the change was cancelled."));
                openState(viewer, capture.state());
                return;
            }
            if (!expiration.isAfter(clock.instant())) {
                viewer.sendMessage(Component.text("The expiration must be in the future; the change was cancelled."));
                openState(viewer, capture.state());
                return;
            }
            inputCaptures.put(viewer.getUniqueId(), new InputCapture(
                    capture.state(), capture.action(), InputStage.REASON, Optional.of(expiration)
            ));
            viewer.sendMessage(Component.text(
                    "Now type the private audit reason in chat, or cancel. It will not be broadcast."
            ));
            return;
        }
        if (input.isBlank() || input.length() > 2_000) {
            viewer.sendMessage(Component.text("The audit reason must contain 1 to 2000 characters."));
            openState(viewer, capture.state());
            return;
        }
        openState(viewer, new SanctionChangeGuiState.Review(
                viewer.getUniqueId(),
                capture.state().commandName(),
                capture.state().targetLabel(),
                capture.state().origin(),
                capture.state().review(),
                capture.action(),
                capture.expiration(),
                input,
                UUID.randomUUID()
        ));
    }

    private void confirm(Player viewer, Actor actor, SanctionChangeGuiState.Review state) {
        if (!confirmations.add(viewer.getUniqueId())) {
            viewer.sendMessage(Component.text("That sanction change is already being confirmed."));
            return;
        }
        boolean submitted = submit(viewer, () -> {
            try {
                SanctionChangeService service = services.get();
                if (service == null) {
                    message(viewer, "Moderation storage is not ready; no change was made.");
                    return;
                }
                SanctionChangeRequest request = new SanctionChangeRequest(
                        new IdempotencyKey("sanction-change-gui:" + state.operationId()),
                        state.caseReview().caseId(),
                        actor,
                        state.action(),
                        state.replacementExpiration(),
                        state.reason(),
                        Optional.of(state.caseReview().changeExpectation())
                );
                SanctionChangeResult result = service.apply(request, mode.get());
                if (result instanceof SanctionChangeResult.Applied applied) {
                    finish(viewer, "Sanction change committed for case " + state.caseReview().caseId()
                            + "; affected sanctions=" + applied.affectedSanctions()
                            + (applied.replayed() ? " (idempotent replay)" : "") + '.');
                    return;
                }
                SanctionChangeResult.Rejected rejected = (SanctionChangeResult.Rejected) result;
                message(viewer, rejected.code() + ": " + rejected.message());
            } finally {
                confirmations.remove(viewer.getUniqueId());
            }
        });
        if (!submitted) {
            confirmations.remove(viewer.getUniqueId());
        }
    }

    private void openState(Player viewer, SanctionChangeGuiState state) {
        onEntity(viewer, () -> {
            Actor actor = authorizedActor(viewer);
            if (actor == null) {
                return;
            }
            viewer.openInventory(renderer.render(state, actor, viewer));
        });
    }

    private Actor authorizedActor(Player viewer) {
        Actor actor = PaperActorResolver.resolve(viewer).orElse(null);
        if (actor == null || !actor.id().equals(viewer.getUniqueId())
                || !SanctionChangeAccess.canChangeAnything(authorization, actor)) {
            viewer.sendMessage(Component.text("You do not have punishment modification authority."));
            return null;
        }
        return actor;
    }

    private boolean matchesCommandCase(String commandName, CaseId caseId, CaseLookup lookup) {
        return "removepunishment".equals(commandName)
                || lookup.containsSanction(caseId, SanctionChangeAccess.aliasTypes(commandName), true);
    }

    private void close(Player viewer) {
        viewer.closeInventory();
    }

    private void finish(Player viewer, String result) {
        onEntity(viewer, () -> {
            close(viewer);
            viewer.sendMessage(Component.text(result));
        });
    }

    private boolean submit(Player viewer, Runnable work) {
        try {
            workers.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Sanction change GUI workflow failed", exception);
                    message(viewer, "The sanction change outcome was not confirmed. Reopen the case before retrying.");
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            viewer.sendMessage(Component.text("The moderation work queue is full; no change was made."));
            return false;
        }
    }

    private void message(Player viewer, String body) {
        onEntity(viewer, () -> viewer.sendMessage(Component.text(body)));
    }

    private void onEntity(Player viewer, Runnable task) {
        viewer.getScheduler().execute(plugin, task, null, 1L);
    }

    private static CaseId parseCaseId(String value) {
        try {
            return new CaseId(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private enum InputStage {
        EXPIRATION,
        REASON
    }

    private record InputCapture(
            SanctionChangeGuiState.Actions state,
            SanctionChangeAction action,
            InputStage stage,
            Optional<Instant> expiration
    ) {
        private InputCapture {
            if (state == null || action == null || stage == null || expiration == null) {
                throw new IllegalArgumentException("sanction change input capture fields must be present");
            }
        }
    }
}
