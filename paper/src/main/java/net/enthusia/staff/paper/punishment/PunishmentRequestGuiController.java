package net.enthusia.staff.paper.punishment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
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
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentRequestGuiController implements Listener {
    private static final int MAXIMUM_REQUESTS = 500;
    private static final int QUEUE_SIZE = 54;
    private static final int QUEUE_CONTENT_SIZE = 45;
    private static final int REFRESH_SLOT = 45;
    private static final int PREVIOUS_SLOT = 46;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 52;
    private static final int APPROVE_SLOT = 21;
    private static final int DENY_SLOT = 23;
    private static final int BACK_SLOT = 18;
    private static final int REVIEW_CLOSE_SLOT = 26;
    private static final int CUSTOM_DENIAL_SLOT = 22;
    private static final String REVIEW_PERMISSION = "enthusiastaff.punishment.requests.review";

    private final JavaPlugin plugin;
    private final Supplier<PunishmentRequestService> services;
    private final Supplier<PlayerDirectory> players;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;

    public PunishmentRequestGuiController(
            JavaPlugin plugin,
            Supplier<PunishmentRequestService> services,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        if (plugin == null || services == null || players == null || authorization == null || workers == null) {
            throw new IllegalArgumentException("punishment request GUI dependencies must be present");
        }
        this.plugin = plugin;
        this.services = services;
        this.players = players;
        this.authorization = authorization;
        this.workers = workers;
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
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder(false) instanceof PunishmentRequestGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        PunishmentRequestGuiState state = holder.state();
        if (state instanceof PunishmentRequestGuiState.Queue queue) {
            handleQueueClick(player, queue, slot);
        } else if (state instanceof PunishmentRequestGuiState.Review review) {
            handleReviewClick(player, review, slot);
        } else if (state instanceof PunishmentRequestGuiState.Denial denial) {
            handleDenialClick(player, denial, slot);
        } else if (state instanceof PunishmentRequestGuiState.Details details) {
            handleDetailsClick(player, details, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PunishmentRequestGuiHolder) {
            event.setCancelled(true);
        }
    }

    private boolean openQueue(Player player, int page) {
        Actor actor = authorizedActor(player);
        if (actor == null) {
            return false;
        }
        submit(player, () -> {
            PunishmentRequestService service = services.get();
            if (service == null) {
                message(player, "Punishment request storage is not ready.");
                return;
            }
            PlayerDirectory directory = players.get();
            List<PunishmentRequestGuiState.RequestView> views = service.reviewable(actor, MAXIMUM_REQUESTS).stream()
                    .map(request -> view(request, directory))
                    .toList();
            PunishmentRequestGuiState.Queue state = PunishmentRequestGuiState.Queue.page(
                    views,
                    page,
                    QUEUE_CONTENT_SIZE
            );
            onMain(() -> player.openInventory(renderQueue(state)));
        });
        return true;
    }

    private void openRequest(Player player, Actor actor, UUID requestId, int returnPage) {
        submit(player, () -> {
            PunishmentRequestService service = services.get();
            if (service == null) {
                message(player, "Punishment request storage is not ready.");
                return;
            }
            PunishmentApprovalRequest request = service.find(requestId).orElse(null);
            if (request == null) {
                onMain(() -> rejection(player, rejected("REQUEST_NOT_FOUND", "The punishment request does not exist")));
                return;
            }
            if (!service.mayReview(actor, request)) {
                onMain(() -> rejection(player, rejected(
                        "FORBIDDEN",
                        "You are not authorized to review this punishment request"
                )));
                return;
            }
            String targetName = PunishmentRequestPresentation.targetName(players.get(), request.proposal().targetId());
            if (request.status() != PunishmentRequestStatus.PENDING) {
                PunishmentRequestGuiState.Details state = new PunishmentRequestGuiState.Details(
                        new PunishmentRequestGuiState.RequestView(request, targetName),
                        returnPage
                );
                onMain(() -> player.openInventory(renderDetails(state)));
                return;
            }
            acquireAndOpen(player, actor, requestId, targetName, returnPage, service);
        });
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
            onMain(() -> player.openInventory(renderReview(state)));
            return;
        }
        PunishmentApprovalRequest current = service.find(requestId).orElse(null);
        if (current != null && current.status() != PunishmentRequestStatus.PENDING && service.mayReview(actor, current)) {
            PunishmentRequestGuiState.Details state = new PunishmentRequestGuiState.Details(
                    view(current, players.get()),
                    returnPage
            );
            onMain(() -> player.openInventory(renderDetails(state)));
            return;
        }
        PunishmentRequestResult.Rejected rejected = (PunishmentRequestResult.Rejected) result;
        onMain(() -> rejection(player, rejected));
    }

    private void handleQueueClick(Player player, PunishmentRequestGuiState.Queue queue, int slot) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == REFRESH_SLOT) {
            openQueue(player, queue.page());
        } else if (slot == PREVIOUS_SLOT && queue.hasPrevious()) {
            openQueue(player, queue.page() - 1);
        } else if (slot == NEXT_SLOT && queue.hasNext()) {
            openQueue(player, queue.page() + 1);
        } else if (slot < queue.requests().size()) {
            Actor actor = authorizedActor(player);
            if (actor != null) {
                openRequest(player, actor, queue.requests().get(slot).request().requestId(), queue.page());
            }
        }
    }

    private void handleReviewClick(Player player, PunishmentRequestGuiState.Review review, int slot) {
        if (slot == REVIEW_CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == BACK_SLOT) {
            openQueue(player, review.returnPage());
        } else if (slot == DENY_SLOT) {
            player.openInventory(renderDenial(new PunishmentRequestGuiState.Denial(
                    review.lease(),
                    review.targetName(),
                    review.returnPage()
            )));
        } else if (slot == APPROVE_SLOT) {
            Actor actor = authorizedActor(player);
            if (actor != null) {
                decide(
                        player,
                        actor,
                        review.lease(),
                        review.returnPage(),
                        () -> services.get().approve(review.lease(), actor)
                );
            }
        }
    }

    private void handleDenialClick(Player player, PunishmentRequestGuiState.Denial denial, int slot) {
        if (slot == REVIEW_CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == BACK_SLOT) {
            player.openInventory(renderReview(new PunishmentRequestGuiState.Review(
                    denial.lease(),
                    denial.targetName(),
                    denial.returnPage()
            )));
        } else if (slot == CUSTOM_DENIAL_SLOT) {
            player.closeInventory();
            String command = "/punish deny " + denial.lease().request().requestId() + " ";
            player.sendMessage(Component.text("Custom denial reason required. ", NamedTextColor.YELLOW)
                    .append(Component.text("Click to prepare the command", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand(command))));
        } else {
            String note = denialReason(slot);
            Actor actor = authorizedActor(player);
            if (note != null && actor != null) {
                decide(
                        player,
                        actor,
                        denial.lease(),
                        denial.returnPage(),
                        () -> services.get().deny(denial.lease(), actor, note)
                );
            }
        }
    }

    private void handleDetailsClick(Player player, PunishmentRequestGuiState.Details details, int slot) {
        if (slot == REVIEW_CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == BACK_SLOT) {
            openQueue(player, details.returnPage());
        } else if (slot == REFRESH_SLOT) {
            Actor actor = authorizedActor(player);
            if (actor != null) {
                openRequest(player, actor, details.view().request().requestId(), details.returnPage());
            }
        }
    }

    private void decide(
            Player player,
            Actor actor,
            PunishmentApprovalLease lease,
            int returnPage,
            Supplier<PunishmentRequestResult> decision
    ) {
        player.closeInventory();
        submit(player, () -> {
            PunishmentRequestResult result = decision.get();
            PunishmentApprovalRequest latest = result instanceof PunishmentRequestResult.Rejected
                    ? services.get().find(lease.request().requestId()).orElse(null)
                    : null;
            onMain(() -> {
                decisionMessage(player, result);
                if (result instanceof PunishmentRequestResult.Rejected) {
                    if (latest != null && latest.status() != PunishmentRequestStatus.PENDING
                            && services.get().mayReview(actor, latest)) {
                        player.openInventory(renderDetails(new PunishmentRequestGuiState.Details(
                                view(latest, players.get()),
                                returnPage
                        )));
                    } else {
                        openQueue(player, returnPage);
                    }
                }
            });
        });
    }

    private Inventory renderQueue(PunishmentRequestGuiState.Queue state) {
        String title = "Punishment requests " + (state.page() + 1) + '/' + state.totalPages();
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, QUEUE_SIZE, Component.text(title));
        holder.attach(inventory);
        fillFooter(inventory);
        for (int slot = 0; slot < state.requests().size(); slot++) {
            inventory.setItem(slot, requestItem(state.requests().get(slot), "pending", NamedTextColor.AQUA));
        }
        if (state.requests().isEmpty()) {
            inventory.setItem(22, item(
                    Material.PAPER,
                    "No reviewable requests",
                    List.of(Component.text("No pending requests match your approval rank.", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(REFRESH_SLOT, item(
                Material.CLOCK,
                "Refresh queue",
                List.of(Component.text(state.totalEntries() + " reviewable request(s)", NamedTextColor.GRAY))
        ));
        if (state.hasPrevious()) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "Previous page", List.of()));
        }
        if (state.hasNext()) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next page", List.of()));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        return inventory;
    }

    private Inventory renderReview(PunishmentRequestGuiState.Review state) {
        PunishmentApprovalRequest request = state.lease().request();
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Claimed punishment request"));
        holder.attach(inventory);
        inventory.setItem(4, requestItem(
                new PunishmentRequestGuiState.RequestView(request, state.targetName()),
                "claimed by you",
                NamedTextColor.AQUA
        ));
        addRequestDetails(inventory, request, state.targetName());
        inventory.setItem(16, item(
                Material.CLOCK,
                "Claim and expiration",
                List.of(
                        Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY),
                        Component.text("Request expires: " + request.expiresAt(), NamedTextColor.GRAY),
                        Component.text("Your review claim expires: " + state.lease().expiresAt(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to queue", List.of()));
        inventory.setItem(APPROVE_SLOT, item(
                Material.LIME_CONCRETE,
                "Approve request",
                List.of(
                        Component.text("Creates the frozen punishment atomically", NamedTextColor.GREEN),
                        Component.text("The current claim and revision are rechecked", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(DENY_SLOT, item(
                Material.RED_CONCRETE,
                "Deny request",
                List.of(Component.text("Select an audited denial reason", NamedTextColor.YELLOW))
        ));
        inventory.setItem(REVIEW_CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        return inventory;
    }

    private Inventory renderDetails(PunishmentRequestGuiState.Details state) {
        PunishmentApprovalRequest request = state.view().request();
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Punishment request details"));
        holder.attach(inventory);
        inventory.setItem(4, requestItem(
                state.view(),
                PunishmentRequestPresentation.status(request.status()),
                PunishmentRequestPresentation.statusColor(request.status())
        ));
        addRequestDetails(inventory, request, state.view().targetName());
        inventory.setItem(16, item(
                statusMaterial(request.status()),
                "Resolution",
                List.of(
                        Component.text(PunishmentRequestPresentation.resolution(request),
                                PunishmentRequestPresentation.statusColor(request.status())),
                        Component.text("Resolved: " + displayTime(request.resolvedAt()), NamedTextColor.GRAY),
                        Component.text("Current revision: " + request.revision(), NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to queue", List.of()));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK, "Refresh details", List.of()));
        inventory.setItem(REVIEW_CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        return inventory;
    }

    private Inventory renderDenial(PunishmentRequestGuiState.Denial state) {
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Deny punishment request"));
        holder.attach(inventory);
        denialItem(inventory, 10, Material.PAPER, "Insufficient evidence");
        denialItem(inventory, 12, Material.WRITABLE_BOOK, "Incorrect reason or classification");
        denialItem(inventory, 14, Material.ANVIL, "Requested sanction is not appropriate");
        denialItem(inventory, 16, Material.BARRIER, "Duplicate or already handled");
        inventory.setItem(CUSTOM_DENIAL_SLOT, item(
                Material.NAME_TAG,
                "Custom reason",
                List.of(Component.text("Prepare a /punish deny command", NamedTextColor.YELLOW))
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to claimed review", List.of()));
        inventory.setItem(REVIEW_CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        return inventory;
    }

    private static void addRequestDetails(Inventory inventory, PunishmentApprovalRequest request, String targetName) {
        inventory.setItem(10, item(
                Material.PLAYER_HEAD,
                "Target",
                List.of(Component.text(targetName, NamedTextColor.WHITE))
        ));
        inventory.setItem(12, item(
                Material.WRITABLE_BOOK,
                "Frozen proposal",
                List.of(
                        Component.text("Reason: " + request.proposal().reasonId(), NamedTextColor.WHITE),
                        Component.text(
                                "Step " + request.proposal().escalation().selectedStep().ordinal()
                                        + ": " + request.proposal().escalation().selectedStep().label(),
                                NamedTextColor.GRAY
                        ),
                        Component.text(
                                PunishmentRequestPresentation.sanctions(request.proposal().sanctions()),
                                NamedTextColor.GOLD
                        ),
                        Component.text("Visibility: " + request.proposal().visibility(), NamedTextColor.GRAY),
                        Component.text("Policy: " + request.proposal().configurationVersion(), NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(14, item(
                Material.NAME_TAG,
                "Requester and authority",
                List.of(
                        Component.text(request.proposal().requester().displayName(), NamedTextColor.WHITE),
                        Component.text("Requester rank: " + request.proposal().requester().rank(), NamedTextColor.GRAY),
                        Component.text("Minimum approval: " + request.proposal().requiredRank(), NamedTextColor.GRAY),
                        Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY),
                        Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY),
                        Component.text("Revision: " + request.revision(), NamedTextColor.DARK_GRAY)
                )
        ));
    }

    private static ItemStack requestItem(
            PunishmentRequestGuiState.RequestView view,
            String status,
            NamedTextColor statusColor
    ) {
        PunishmentApprovalRequest request = view.request();
        List<Component> lore = List.of(
                Component.text("Status: " + status, statusColor),
                Component.text("Target: " + view.targetName(), NamedTextColor.WHITE),
                Component.text("Requester: " + request.proposal().requester().displayName()
                        + " (" + request.proposal().requester().rank() + ')', NamedTextColor.GRAY),
                Component.text("Reason: " + request.proposal().reasonId(), NamedTextColor.WHITE),
                Component.text(PunishmentRequestPresentation.sanctions(request.proposal().sanctions()), NamedTextColor.GOLD),
                Component.text("Visibility: " + request.proposal().visibility(), NamedTextColor.GRAY),
                Component.text("Required: " + request.proposal().requiredRank(), NamedTextColor.GRAY),
                Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY),
                Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY),
                Component.text("Revision: " + request.revision(), NamedTextColor.DARK_GRAY)
        );
        return item(statusMaterial(request.status()), humanize(request.proposal().publicReason()), lore);
    }

    private Actor authorizedActor(Player player) {
        if (!player.hasPermission(REVIEW_PERMISSION)) {
            player.sendMessage(Component.text("You do not have permission to review requests.", NamedTextColor.RED));
            return null;
        }
        Actor actor = PaperActorResolver.resolve(player).orElse(null);
        if (actor == null
                || !authorization.permits(actor, ModerationAction.APPROVE_POLICY_SANCTION)
                || !actor.rank().canApprovePunishmentRequests()) {
            player.sendMessage(Component.text(
                    "Only Mod, Admin, or Founder may review punishment requests.",
                    NamedTextColor.RED
            ));
            return null;
        }
        return actor;
    }

    private void submit(Player player, Runnable operation) {
        try {
            workers.submit(() -> {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Punishment request GUI operation failed", exception);
                    message(player, "Punishment request storage is unavailable; no action was taken.");
                }
            });
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().log(Level.WARNING, "Punishment request GUI worker rejected operation", exception);
            player.sendMessage(Component.text(
                    "Punishment request storage is unavailable; try again shortly.",
                    NamedTextColor.RED
            ));
        }
    }

    private void message(Player player, String text) {
        onMain(() -> player.sendMessage(Component.text(text, NamedTextColor.RED)));
    }

    private void onMain(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private PunishmentRequestGuiState.RequestView view(
            PunishmentApprovalRequest request,
            PlayerDirectory directory
    ) {
        return new PunishmentRequestGuiState.RequestView(
                request,
                PunishmentRequestPresentation.targetName(directory, request.proposal().targetId())
        );
    }

    private static void decisionMessage(Player player, PunishmentRequestResult result) {
        if (result instanceof PunishmentRequestResult.Approved approved) {
            player.sendMessage(Component.text(
                    "Punishment request approved as case " + approved.caseId().value()
                            + (approved.replayed() ? " (idempotent replay)." : "."),
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentRequestResult.Denied denied) {
            player.sendMessage(Component.text(
                    denied.replayed()
                            ? "Punishment request denial replayed safely."
                            : "Punishment request was denied.",
                    NamedTextColor.YELLOW
            ));
        } else if (result instanceof PunishmentRequestResult.Rejected rejected) {
            rejection(player, rejected);
        }
    }

    private static void rejection(Player player, PunishmentRequestResult.Rejected rejected) {
        player.sendMessage(Component.text(rejected.code() + ": " + rejected.message(), NamedTextColor.RED));
    }

    private static PunishmentRequestResult.Rejected rejected(String code, String message) {
        return new PunishmentRequestResult.Rejected(code, message);
    }

    private static void denialItem(Inventory inventory, int slot, Material material, String reason) {
        inventory.setItem(slot, item(
                material,
                reason,
                List.of(Component.text("Click to deny with this audit note", NamedTextColor.YELLOW))
        ));
    }

    private static String denialReason(int slot) {
        return switch (slot) {
            case 10 -> "Denied: insufficient evidence supports the requested punishment";
            case 12 -> "Denied: the selected reason or classification is incorrect";
            case 14 -> "Denied: the requested sanction is not appropriate for the evidence";
            case 16 -> "Denied: duplicate request or the incident was already handled";
            default -> null;
        };
    }

    private static void fillFooter(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = QUEUE_CONTENT_SIZE; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material statusMaterial(PunishmentRequestStatus status) {
        return switch (status) {
            case PENDING -> Material.WRITABLE_BOOK;
            case APPROVED -> Material.LIME_CONCRETE;
            case DENIED -> Material.YELLOW_CONCRETE;
            case EXPIRED -> Material.CLOCK;
            case FULFILLED_EXTERNALLY -> Material.EMERALD;
        };
    }

    private static String displayTime(java.time.Instant time) {
        return time == null ? "not resolved" : time.toString();
    }

    private static String humanize(String value) {
        String normalized = value == null || value.isBlank() ? "Punishment request" : value;
        String[] words = normalized.replace('.', ' ').replace('-', ' ').split(" +");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            result.append(word.substring(1));
        }
        return result.isEmpty() ? "Punishment request" : result.toString();
    }
}
