package net.enthusia.staff.paper.punishment;

import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentRequestGuiController implements Listener {
    private static final int MAXIMUM_REQUESTS = 500;
    private static final String NOT_READY_MESSAGE = "Punishment request storage is not ready.";

    private final Plugin plugin;
    private final Supplier<PunishmentRequestService> services;
    private final Supplier<PlayerDirectory> players;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;
    private final Function<PunishmentRequestGuiState.Details, Inventory> detailsRenderer;

    public PunishmentRequestGuiController(
            JavaPlugin plugin,
            Supplier<PunishmentRequestService> services,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        this((Plugin) plugin, services, players, authorization, workers);
    }

    PunishmentRequestGuiController(
            Plugin plugin,
            Supplier<PunishmentRequestService> services,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        this(plugin, services, players, authorization, workers,
                PunishmentRequestGuiRenderer::renderDetails);
    }

    /** Allows scheduler tests to render details without a static Bukkit server. */
    PunishmentRequestGuiController(
            Plugin plugin,
            Supplier<PunishmentRequestService> services,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ExecutorService workers,
            Function<PunishmentRequestGuiState.Details, Inventory> detailsRenderer
    ) {
        if (plugin == null || services == null || players == null || authorization == null || workers == null) {
            throw new IllegalArgumentException("punishment request GUI dependencies must be present");
        }
        this.plugin = plugin;
        this.services = services;
        this.players = players;
        this.authorization = authorization;
        this.workers = workers;
        this.detailsRenderer = Objects.requireNonNull(detailsRenderer);
    }

    public String targetName(PunishmentApprovalRequest request) {
        Objects.requireNonNull(request, "punishment request");
        return PunishmentRequestPresentation.targetName(players.get(), request.proposal().targetId());
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean openQueue(Player player) {
        return openQueue(player, 0);
    }

    public boolean openReview(Player player, UUID requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("punishment request identifier must be present");
        }
        Actor actor = authorizedActor(player);
        if (actor == null) {
            return false;
        }
        openRequest(player, actor, requestId, 0);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ClickContext context = clickContext(event);
        if (context == null) {
            return;
        }
        event.setCancelled(true);
        // A reviewer can retain generic permission after demotion while losing
        // the rank needed to see a specific request already open in the GUI.
        Actor current = authorizedActor(context.player());
        if (current == null || !mayUseCurrentInventory(context.state(), current)) {
            context.player().closeInventory();
            return;
        }
        dispatchClick(context);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PunishmentRequestGuiHolder) {
            event.setCancelled(true);
        }
    }

    private static ClickContext clickContext(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return null;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PunishmentRequestGuiHolder holder)) {
            return null;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return null;
        }
        return new ClickContext(player, holder.state(), slot);
    }

    private boolean mayUseCurrentInventory(PunishmentRequestGuiState state, Actor current) {
        PunishmentRequestService service = services.get();
        if (service == null) {
            return false;
        }
        return switch (state) {
            case PunishmentRequestGuiState.Queue queue ->
                    queue.requests().stream().allMatch(view -> service.mayReview(current, view.request()));
            case PunishmentRequestGuiState.Review review ->
                    service.mayReview(current, review.lease().request());
            case PunishmentRequestGuiState.Denial denial ->
                    service.mayReview(current, denial.lease().request());
            case PunishmentRequestGuiState.Details details ->
                    service.mayReview(current, details.view().request());
        };
    }

    private void dispatchClick(ClickContext context) {
        switch (context.state()) {
            case PunishmentRequestGuiState.Queue queue -> handleQueueClick(context.player(), queue, context.slot());
            case PunishmentRequestGuiState.Review review -> handleReviewClick(context.player(), review, context.slot());
            case PunishmentRequestGuiState.Denial denial -> handleDenialClick(context.player(), denial, context.slot());
            case PunishmentRequestGuiState.Details details -> handleDetailsClick(context.player(), details, context.slot());
        }
    }

    private boolean openQueue(Player player, int page) {
        Actor actor = authorizedActor(player);
        if (actor == null) {
            return false;
        }
        submit(player, () -> loadQueue(player, actor, page));
        return true;
    }

    private void loadQueue(Player player, Actor actor, int page) {
        PunishmentRequestService service = readyService(player);
        if (service == null) {
            return;
        }
        List<PunishmentRequestGuiState.RequestView> views = service.reviewable(actor, MAXIMUM_REQUESTS).stream()
                .map(this::view)
                .toList();
        // Rank can change while the async query is loading; only render entries
        // the reviewer may still inspect at the moment of presentation.
        onAuthorizedEntity(player, current -> true, current -> {
            PunishmentRequestGuiState.Queue state = PunishmentRequestGuiState.Queue.page(
                    visibleRequestsForActor(views, current, service),
                    page,
                    PunishmentRequestGuiRenderer.QUEUE_CONTENT_SIZE
            );
            player.openInventory(PunishmentRequestGuiRenderer.renderQueue(state));
        }, () -> { });
    }

    private void openRequest(Player player, Actor actor, UUID requestId, int returnPage) {
        submit(player, () -> loadRequest(player, actor, requestId, returnPage));
    }

    private void loadRequest(Player player, Actor actor, UUID requestId, int returnPage) {
        PunishmentRequestService service = readyService(player);
        if (service == null) {
            return;
        }
        PunishmentApprovalRequest request = service.find(requestId).orElse(null);
        if (request == null) {
            onEntity(player, () -> rejection(player, rejected("REQUEST_NOT_FOUND", "The punishment request does not exist")));
            return;
        }
        if (!service.mayReview(actor, request)) {
            onEntity(player, () -> rejection(player, rejected(
                    "FORBIDDEN",
                    "You are not authorized to review this punishment request"
            )));
            return;
        }
        openLoadedRequest(player, actor, request, returnPage, service);
    }

    private void openLoadedRequest(
            Player player,
            Actor actor,
            PunishmentApprovalRequest request,
            int returnPage,
            PunishmentRequestService service
    ) {
        String resolvedTargetName = targetName(request);
        if (request.status() != PunishmentRequestStatus.PENDING) {
            PunishmentRequestGuiState.Details state = new PunishmentRequestGuiState.Details(
                    new PunishmentRequestGuiState.RequestView(request, resolvedTargetName),
                    returnPage
            );
            onAuthorizedEntity(player,
                    current -> service.mayReview(current, request),
                    current -> player.openInventory(detailsRenderer.apply(state)),
                    () -> { });
            return;
        }
        acquireAndOpen(player, actor, request.requestId(), resolvedTargetName, returnPage, service);
    }

    private void acquireAndOpen(
            Player player,
            Actor actor,
            UUID requestId,
            String targetName,
            int returnPage,
            PunishmentRequestService service
    ) {
        PunishmentRequestResult result = service.acquire(requestId, actor);
        if (result instanceof PunishmentRequestResult.Leased leased) {
            PunishmentRequestGuiState.Review state = new PunishmentRequestGuiState.Review(
                    leased.lease(),
                    targetName,
                    returnPage
            );
            onAuthorizedEntity(player,
                    current -> service.mayReview(current, leased.lease().request()),
                    current -> player.openInventory(PunishmentRequestGuiRenderer.renderReview(state)),
                    () -> abandonLeaseAsync(service, leased.lease()));
            return;
        }
        PunishmentApprovalRequest current = service.find(requestId).orElse(null);
        if (current != null && current.status() != PunishmentRequestStatus.PENDING && service.mayReview(actor, current)) {
            PunishmentRequestGuiState.Details state = new PunishmentRequestGuiState.Details(view(current), returnPage);
            onAuthorizedEntity(player,
                    fresh -> service.mayReview(fresh, current),
                    fresh -> player.openInventory(detailsRenderer.apply(state)),
                    () -> { });
            return;
        }
        PunishmentRequestResult.Rejected rejected = (PunishmentRequestResult.Rejected) result;
        onEntity(player, () -> rejection(player, rejected));
    }

    private void handleQueueClick(Player player, PunishmentRequestGuiState.Queue queue, int slot) {
        if (slot < queue.requests().size()) {
            openQueueEntry(player, queue, slot);
            return;
        }
        switch (slot) {
            case PunishmentRequestGuiRenderer.CLOSE_SLOT -> player.closeInventory();
            case PunishmentRequestGuiRenderer.REFRESH_SLOT -> openQueue(player, queue.page());
            case PunishmentRequestGuiRenderer.PREVIOUS_SLOT -> openAdjacentPage(
                    player,
                    queue.page() - 1,
                    queue.hasPrevious()
            );
            case PunishmentRequestGuiRenderer.NEXT_SLOT -> openAdjacentPage(
                    player,
                    queue.page() + 1,
                    queue.hasNext()
            );
            default -> {
            }
        }
    }

    private void openQueueEntry(Player player, PunishmentRequestGuiState.Queue queue, int slot) {
        Actor actor = authorizedActor(player);
        if (actor != null) {
            openRequest(player, actor, queue.requests().get(slot).request().requestId(), queue.page());
        }
    }

    private void openAdjacentPage(Player player, int page, boolean available) {
        if (available) {
            openQueue(player, page);
        }
    }

    private void handleReviewClick(Player player, PunishmentRequestGuiState.Review review, int slot) {
        if (slot == PunishmentRequestGuiRenderer.REVIEW_CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == PunishmentRequestGuiRenderer.BACK_SLOT) {
            openQueue(player, review.returnPage());
        } else if (slot == PunishmentRequestGuiRenderer.DENY_SLOT) {
            player.openInventory(PunishmentRequestGuiRenderer.renderDenial(new PunishmentRequestGuiState.Denial(
                    review.lease(),
                    review.targetName(),
                    review.returnPage()
            )));
        } else if (slot == PunishmentRequestGuiRenderer.APPROVE_SLOT) {
            approve(player, review);
        }
    }

    private void approve(Player player, PunishmentRequestGuiState.Review review) {
        Actor actor = authorizedActor(player);
        if (actor == null) {
            return;
        }
        decide(
                player,
                actor,
                review.lease(),
                review.returnPage(),
                service -> service.approve(review.lease(), actor)
        );
    }

    private void handleDenialClick(Player player, PunishmentRequestGuiState.Denial denial, int slot) {
        if (slot == PunishmentRequestGuiRenderer.REVIEW_CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == PunishmentRequestGuiRenderer.BACK_SLOT) {
            player.openInventory(PunishmentRequestGuiRenderer.renderReview(new PunishmentRequestGuiState.Review(
                    denial.lease(),
                    denial.targetName(),
                    denial.returnPage()
            )));
        } else if (slot == PunishmentRequestGuiRenderer.CUSTOM_DENIAL_SLOT) {
            prepareCustomDenial(player, denial);
        } else {
            denyWithPreset(player, denial, slot);
        }
    }

    private static void prepareCustomDenial(Player player, PunishmentRequestGuiState.Denial denial) {
        player.closeInventory();
        String command = "/punish deny " + denial.lease().request().requestId() + " ";
        player.sendMessage(StaffMessageStyle.style(Component.text("Custom denial reason required. ", NamedTextColor.YELLOW)
                .append(Component.text("Click to prepare the command", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand(command)))));
    }

    private void denyWithPreset(Player player, PunishmentRequestGuiState.Denial denial, int slot) {
        PunishmentRequestDenialPreset preset = PunishmentRequestDenialPreset.fromSlot(slot);
        Actor actor = authorizedActor(player);
        if (preset == null || actor == null) {
            return;
        }
        decide(
                player,
                actor,
                denial.lease(),
                denial.returnPage(),
                service -> service.deny(denial.lease(), actor, preset.auditNote())
        );
    }

    private void handleDetailsClick(Player player, PunishmentRequestGuiState.Details details, int slot) {
        if (slot == PunishmentRequestGuiRenderer.REVIEW_CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == PunishmentRequestGuiRenderer.BACK_SLOT) {
            openQueue(player, details.returnPage());
        } else if (slot == PunishmentRequestGuiRenderer.DETAILS_REFRESH_SLOT) {
            refreshDetails(player, details);
        }
    }

    private void refreshDetails(Player player, PunishmentRequestGuiState.Details details) {
        Actor actor = authorizedActor(player);
        if (actor != null) {
            openRequest(player, actor, details.view().request().requestId(), details.returnPage());
        }
    }

    private void decide(
            Player player,
            Actor actor,
            PunishmentApprovalLease lease,
            int returnPage,
            Function<PunishmentRequestService, PunishmentRequestResult> decision
    ) {
        player.closeInventory();
        submit(player, () -> completeDecision(player, actor, lease, returnPage, decision));
    }

    private void completeDecision(
            Player player,
            Actor actor,
            PunishmentApprovalLease lease,
            int returnPage,
            Function<PunishmentRequestService, PunishmentRequestResult> decision
    ) {
        PunishmentRequestService service = readyService(player);
        if (service == null) {
            return;
        }
        PunishmentRequestResult result = decision.apply(service);
        PunishmentRequestGuiState.RequestView resolvedView = result instanceof PunishmentRequestResult.Approved approved
                ? view(approved.request())
                : result instanceof PunishmentRequestResult.Denied denied
                ? view(denied.request())
                : resolvedView(service, actor, lease, result);
        onEntity(player, () -> presentDecision(player, service, result, resolvedView, returnPage));
    }

    private PunishmentRequestGuiState.RequestView resolvedView(
            PunishmentRequestService service,
            Actor actor,
            PunishmentApprovalLease lease,
            PunishmentRequestResult result
    ) {
        if (!(result instanceof PunishmentRequestResult.Rejected)) {
            return null;
        }
        PunishmentApprovalRequest latest = service.find(lease.request().requestId()).orElse(null);
        if (latest == null || latest.status() == PunishmentRequestStatus.PENDING || !service.mayReview(actor, latest)) {
            return null;
        }
        return view(latest);
    }

    private void presentDecision(
            Player player,
            PunishmentRequestService service,
            PunishmentRequestResult result,
            PunishmentRequestGuiState.RequestView resolvedView,
            int returnPage
    ) {
        // The decision may already have committed, so acknowledge it even if
        // the reviewer has lost access. Do not show private details afterward.
        decisionMessage(player, result, resolvedView);
        Actor current = authorizedActor(player);
        if (current == null || (resolvedView != null && !service.mayReview(current, resolvedView.request()))) {
            player.closeInventory();
            return;
        }
        if (result instanceof PunishmentRequestResult.Approved || result instanceof PunishmentRequestResult.Denied) {
            player.openInventory(detailsRenderer.apply(new PunishmentRequestGuiState.Details(
                    resolvedView, returnPage
            )));
            return;
        }
        if (resolvedView != null) {
            player.openInventory(detailsRenderer.apply(new PunishmentRequestGuiState.Details(
                    resolvedView,
                    returnPage
            )));
        } else {
            openQueue(player, returnPage);
        }
    }

    private Actor authorizedActor(Player player) {
        Actor actor = PaperActorResolver.resolve(player).orElse(null);
        if (actor == null
                || !authorization.permits(actor, ModerationAction.APPROVE_POLICY_SANCTION)
                || !actor.rank().canApprovePunishmentRequests()) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Only Mod, Admin, or Founder may review punishment requests.",
                    NamedTextColor.RED
            )));
            return null;
        }
        return actor;
    }

    private PunishmentRequestService readyService(Player player) {
        PunishmentRequestService service = services.get();
        if (service == null) {
            message(player, NOT_READY_MESSAGE);
        }
        return service;
    }

    private void submit(Player player, Runnable operation) {
        try {
            workers.submit(() -> runOperation(player, operation));
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().log(Level.WARNING, "Punishment request GUI worker rejected operation", exception);
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Punishment request storage is unavailable; try again shortly.",
                    NamedTextColor.RED
            )));
        }
    }

    private void runOperation(Player player, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Punishment request GUI operation failed", exception);
            // An exception can occur after a durable approve/deny commit but
            // before the worker receives or presents its acknowledgement.
            // Never tell staff that no action occurred on this ambiguous path.
            message(player, "Punishment request operation failed; a decision may have been committed. "
                    + "Check case history and request status before retrying.");
        }
    }

    private void message(Player player, String text) {
        onEntity(player, () -> player.sendMessage(StaffMessageStyle.style(Component.text(text, NamedTextColor.RED))));
    }

    private void onEntity(Player player, Runnable action) {
        scheduleOnOwner(
                plugin,
                player,
                action,
                () -> plugin.getLogger().fine("Skipped punishment request GUI callback for a retired player session")
        );
    }

    /** Recheck current permission and request-specific rank on the player's entity thread. */
    private void onAuthorizedEntity(Player player, Runnable action) {
        onAuthorizedEntity(player, current -> true, current -> action.run(), () -> { });
    }

    private void onAuthorizedEntity(Player player, Runnable action, Runnable onUnavailable) {
        onAuthorizedEntity(player, current -> true, current -> action.run(), onUnavailable);
    }

    private void onAuthorizedEntity(
            Player player,
            java.util.function.Predicate<Actor> mayView,
            java.util.function.Consumer<Actor> action,
            Runnable onUnavailable
    ) {
        java.util.concurrent.atomic.AtomicBoolean cleanupOnce = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> {
            if (cleanupOnce.compareAndSet(false, true)) {
                onUnavailable.run();
            }
        };
        scheduleOnOwner(
                plugin,
                player,
                () -> {
                    Actor current = authorizedActor(player);
                    if (current == null || !mayView.test(current)) {
                        player.closeInventory();
                        cleanup.run();
                        return;
                    }
                    action.accept(current);
                },
                () -> {
                    plugin.getLogger().fine("Skipped punishment request GUI callback for a retired player session");
                    cleanup.run();
                }
        );
    }

    static List<PunishmentRequestGuiState.RequestView> visibleRequestsForActor(
            List<PunishmentRequestGuiState.RequestView> loaded,
            Actor current,
            PunishmentRequestService service
    ) {
        return loaded.stream()
                .filter(view -> service.mayReview(current, view.request()))
                .toList();
    }

    /** Best effort: a revoked or retired reviewer should not retain a pending lease. */
    private void abandonLeaseAsync(PunishmentRequestService service, PunishmentApprovalLease lease) {
        try {
            workers.execute(() -> {
                try {
                    service.abandon(lease);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not release punishment approval lease after reviewer session ended",
                            exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not queue punishment approval lease release after reviewer session ended",
                    exception);
        }
    }

    static boolean scheduleOnOwner(
            Plugin plugin,
            Player player,
            Runnable action,
            Runnable retired
    ) {
        AtomicBoolean retiredOnce = new AtomicBoolean();
        Runnable retireOnce = () -> {
            if (retiredOnce.compareAndSet(false, true)) {
                retired.run();
            }
        };
        boolean scheduled = player.getScheduler().execute(plugin, action, retireOnce, 1L);
        if (!scheduled) {
            retireOnce.run();
        }
        return scheduled;
    }

    private PunishmentRequestGuiState.RequestView view(PunishmentApprovalRequest request) {
        return new PunishmentRequestGuiState.RequestView(request, targetName(request));
    }

    private void decisionMessage(Player player, PunishmentRequestResult result,
            PunishmentRequestGuiState.RequestView resolvedView) {
        if (result instanceof PunishmentRequestResult.Approved approved) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Approved and saved | Case " + approved.caseId().value()
                            + (approved.replayed() ? " (already processed)." : "."),
                    NamedTextColor.GREEN
            )));
        } else if (result instanceof PunishmentRequestResult.Denied denied) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    denied.replayed()
                            ? "Punishment request denial replayed safely."
                            : "Punishment request was denied.",
                    NamedTextColor.YELLOW
            )));
        } else if (result instanceof PunishmentRequestResult.Rejected rejected) {
            rejection(player, rejected);
        }
    }

    private static void rejection(Player player, PunishmentRequestResult.Rejected rejected) {
        player.sendMessage(StaffMessageStyle.style(Component.text("Request not completed: " + rejected.message(), NamedTextColor.RED)));
    }

    private static PunishmentRequestResult.Rejected rejected(String code, String message) {
        return new PunishmentRequestResult.Rejected(code, message);
    }

    private record ClickContext(Player player, PunishmentRequestGuiState state, int slot) {
    }
}
