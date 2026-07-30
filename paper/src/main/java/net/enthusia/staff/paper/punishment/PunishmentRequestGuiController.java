package net.enthusia.staff.paper.punishment;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
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
    private static final int QUEUE_SIZE = 54;
    private static final int QUEUE_CONTENT_SIZE = 45;
    private static final int REFRESH_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int APPROVE_SLOT = 21;
    private static final int DENY_SLOT = 23;
    private static final int BACK_SLOT = 18;
    private static final int REVIEW_CLOSE_SLOT = 26;
    private static final int CUSTOM_DENIAL_SLOT = 22;
    private static final String REVIEW_PERMISSION = "enthusiastaff.punishment.requests.review";

    private final JavaPlugin plugin;
    private final Supplier<PunishmentRequestService> services;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;

    public PunishmentRequestGuiController(
            JavaPlugin plugin,
            Supplier<PunishmentRequestService> services,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        if (plugin == null || services == null || authorization == null || workers == null) {
            throw new IllegalArgumentException("punishment request GUI dependencies must be present");
        }
        this.plugin = plugin;
        this.services = services;
        this.authorization = authorization;
        this.workers = workers;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean openQueue(Player player) {
        Actor actor = authorizedActor(player);
        if (actor == null) {
            return false;
        }
        submit(player, () -> {
            List<PunishmentApprovalRequest> requests = reviewable(services.get().pending(500), actor)
                    .stream()
                    .limit(QUEUE_CONTENT_SIZE)
                    .toList();
            onMain(() -> player.openInventory(renderQueue(requests)));
        });
        return true;
    }

    public boolean openReview(Player player, UUID requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("punishment request identifier must be present");
        }
        Actor actor = authorizedActor(player);
        if (actor == null) {
            return false;
        }
        acquireAndOpen(player, actor, requestId);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PunishmentRequestGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        PunishmentRequestGuiState state = holder.state();
        if (state instanceof PunishmentRequestGuiState.Queue queue) {
            handleQueueClick(player, queue, slot);
        } else if (state instanceof PunishmentRequestGuiState.Review review) {
            handleReviewClick(player, review, slot);
        } else if (state instanceof PunishmentRequestGuiState.Denial denial) {
            handleDenialClick(player, denial, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PunishmentRequestGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleQueueClick(Player player, PunishmentRequestGuiState.Queue queue, int slot) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == REFRESH_SLOT) {
            openQueue(player);
            return;
        }
        if (slot >= queue.requests().size() || slot >= QUEUE_CONTENT_SIZE) {
            return;
        }
        PunishmentApprovalRequest request = queue.requests().get(slot);
        Actor actor = authorizedActor(player);
        if (actor != null) {
            acquireAndOpen(player, actor, request.requestId());
        }
    }

    private void handleReviewClick(Player player, PunishmentRequestGuiState.Review review, int slot) {
        if (slot == REVIEW_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            openQueue(player);
            return;
        }
        if (slot == DENY_SLOT) {
            player.openInventory(renderDenial(review.lease()));
            return;
        }
        if (slot != APPROVE_SLOT) {
            return;
        }
        Actor actor = authorizedActor(player);
        if (actor != null) {
            decide(player, () -> services.get().approve(review.lease(), actor));
        }
    }

    private void handleDenialClick(Player player, PunishmentRequestGuiState.Denial denial, int slot) {
        if (slot == REVIEW_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            player.openInventory(renderReview(denial.lease()));
            return;
        }
        if (slot == CUSTOM_DENIAL_SLOT) {
            player.closeInventory();
            String command = "/punish deny " + denial.lease().request().requestId() + " ";
            player.sendMessage(Component.text("Custom denial reason required. ", NamedTextColor.YELLOW)
                    .append(Component.text("Click to prepare the command", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand(command))));
            return;
        }
        String note = denialReason(slot);
        if (note == null) {
            return;
        }
        Actor actor = authorizedActor(player);
        if (actor != null) {
            decide(player, () -> services.get().deny(denial.lease(), actor, note));
        }
    }

    private void acquireAndOpen(Player player, Actor actor, UUID requestId) {
        submit(player, () -> {
            PunishmentRequestResult result = services.get().acquire(requestId, actor);
            if (result instanceof PunishmentRequestResult.Leased leased) {
                onMain(() -> player.openInventory(renderReview(leased.lease())));
            } else {
                PunishmentRequestResult.Rejected rejected = (PunishmentRequestResult.Rejected) result;
                onMain(() -> rejection(player, rejected));
            }
        });
    }

    private void decide(Player player, Supplier<PunishmentRequestResult> decision) {
        player.closeInventory();
        submit(player, () -> {
            PunishmentRequestResult result = decision.get();
            onMain(() -> decisionMessage(player, result));
        });
    }

    private Inventory renderQueue(List<PunishmentApprovalRequest> requests) {
        PunishmentRequestGuiState.Queue state = new PunishmentRequestGuiState.Queue(requests);
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, QUEUE_SIZE, Component.text("Punishment requests"));
        holder.attach(inventory);
        fillFooter(inventory);
        for (int slot = 0; slot < requests.size() && slot < QUEUE_CONTENT_SIZE; slot++) {
            inventory.setItem(slot, requestItem(requests.get(slot)));
        }
        if (requests.isEmpty()) {
            inventory.setItem(22, item(
                    Material.PAPER,
                    "No reviewable requests",
                    List.of(Component.text("No pending requests match your approval rank.", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(REFRESH_SLOT, item(
                Material.CLOCK,
                "Refresh queue",
                List.of(Component.text(requests.size() + " reviewable request(s)", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        return inventory;
    }

    private Inventory renderReview(PunishmentApprovalLease lease) {
        PunishmentApprovalRequest request = lease.request();
        PunishmentRequestGuiState.Review state = new PunishmentRequestGuiState.Review(lease);
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Review punishment request"));
        holder.attach(inventory);
        inventory.setItem(4, requestItem(request));
        inventory.setItem(10, item(
                Material.PLAYER_HEAD,
                "Target",
                List.of(Component.text("Authoritative player record", NamedTextColor.GRAY))
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
                                PunishmentGuiRenderer.describe(request.proposal().sanctions()),
                                NamedTextColor.GOLD
                        ),
                        Component.text("Visibility: " + request.proposal().visibility(), NamedTextColor.GRAY),
                        Component.text("Policy: " + request.proposal().configurationVersion(), NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(14, item(
                Material.NAME_TAG,
                "Requester",
                List.of(
                        Component.text(request.proposal().requester().displayName(), NamedTextColor.WHITE),
                        Component.text("Rank: " + request.proposal().requester().rank(), NamedTextColor.GRAY),
                        Component.text("Minimum approval: " + request.proposal().requiredRank(), NamedTextColor.GRAY),
                        Component.text("Revision: " + request.revision(), NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(16, item(
                Material.CLOCK,
                "Lease and expiration",
                List.of(
                        Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY),
                        Component.text("Request expires: " + request.expiresAt(), NamedTextColor.GRAY),
                        Component.text("Review lease expires: " + lease.expiresAt(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to queue", List.of()));
        inventory.setItem(APPROVE_SLOT, item(
                Material.LIME_CONCRETE,
                "Approve request",
                List.of(
                        Component.text("Creates the frozen punishment atomically", NamedTextColor.GREEN),
                        Component.text("Current lease and revision are rechecked", NamedTextColor.GRAY)
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

    private Inventory renderDenial(PunishmentApprovalLease lease) {
        PunishmentRequestGuiState.Denial state = new PunishmentRequestGuiState.Denial(lease);
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
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to review", List.of()));
        inventory.setItem(REVIEW_CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        return inventory;
    }

    private static ItemStack requestItem(PunishmentApprovalRequest request) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: pending", NamedTextColor.AQUA));
        lore.add(Component.text("Requester: " + request.proposal().requester().displayName()
                + " (" + request.proposal().requester().rank() + ')', NamedTextColor.GRAY));
        lore.add(Component.text("Reason: " + request.proposal().reasonId(), NamedTextColor.WHITE));
        lore.add(Component.text(
                PunishmentGuiRenderer.describe(request.proposal().sanctions()),
                NamedTextColor.GOLD
        ));
        lore.add(Component.text("Visibility: " + request.proposal().visibility(), NamedTextColor.GRAY));
        lore.add(Component.text("Required: " + request.proposal().requiredRank(), NamedTextColor.GRAY));
        lore.add(Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY));
        lore.add(Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY));
        lore.add(Component.text("Revision: " + request.revision(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Click to claim this request for review", NamedTextColor.YELLOW));
        Material material = request.proposal().requester().rank() == StaffRank.HELPER
                ? Material.GOLDEN_SWORD
                : Material.REDSTONE;
        return item(material, humanize(request.proposal().publicReason()), lore);
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
                    onMain(() -> player.sendMessage(Component.text(
                            "Punishment request storage is unavailable; no action was taken.",
                            NamedTextColor.RED
                    )));
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

    private void onMain(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private static List<PunishmentApprovalRequest> reviewable(
            List<PunishmentApprovalRequest> requests,
            Actor actor
    ) {
        return requests.stream()
                .filter(request -> !request.proposal().requester().id().equals(actor.id()))
                .filter(request -> meetsRequiredApprovalRank(actor.rank(), request.proposal().requiredRank()))
                .toList();
    }

    private static boolean meetsRequiredApprovalRank(StaffRank approver, StaffRank required) {
        return switch (required) {
            case HELPER, MOD -> approver.canApprovePunishmentRequests();
            case ADMIN -> approver == StaffRank.ADMIN || approver == StaffRank.FOUNDER;
            case FOUNDER -> approver == StaffRank.FOUNDER;
            case DEVELOPER, SYSTEM -> false;
        };
    }

    private static void decisionMessage(Player player, PunishmentRequestResult result) {
        if (result instanceof PunishmentRequestResult.Approved approved) {
            player.sendMessage(Component.text(
                    "Punishment request approved as case " + approved.caseId().value() + '.',
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentRequestResult.Denied) {
            player.sendMessage(Component.text("Punishment request was denied.", NamedTextColor.YELLOW));
        } else if (result instanceof PunishmentRequestResult.Rejected rejected) {
            rejection(player, rejected);
        }
    }

    private static void rejection(Player player, PunishmentRequestResult.Rejected rejected) {
        player.sendMessage(Component.text(rejected.code() + ": " + rejected.message(), NamedTextColor.RED));
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

    private static String humanize(String value) {
        String normalized = value == null || value.isBlank() ? "Punishment request" : value;
        String[] words = normalized.replace('.', ' ').replace('-', ' ').split(" +");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            result.append(word.substring(1));
        }
        return result.toString();
    }

    static String remaining(Instant expiresAt, Instant now) {
        Duration duration = Duration.between(now, expiresAt);
        if (duration.isNegative() || duration.isZero()) {
            return "expired";
        }
        if (duration.toDays() > 0) {
            return duration.toDays() + "d " + duration.toHoursPart() + "h";
        }
        if (duration.toHours() > 0) {
            return duration.toHours() + "h " + duration.toMinutesPart() + "m";
        }
        return Math.max(1, duration.toMinutes()) + "m";
    }
}
