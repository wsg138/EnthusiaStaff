package net.enthusia.staff.paper.punishment;

import java.util.List;
import java.util.Locale;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class PunishmentRequestGuiRenderer {
    static final int QUEUE_CONTENT_SIZE = 45;
    static final int REFRESH_SLOT = 45;
    static final int PREVIOUS_SLOT = 46;
    static final int CLOSE_SLOT = 49;
    static final int NEXT_SLOT = 52;
    static final int APPROVE_SLOT = 21;
    static final int DENY_SLOT = 23;
    static final int BACK_SLOT = 18;
    static final int REVIEW_CLOSE_SLOT = 26;
    static final int CUSTOM_DENIAL_SLOT = 22;

    private static final int QUEUE_SIZE = 54;
    private static final int STANDARD_SIZE = 27;
    private static final int SUMMARY_SLOT = 4;
    private static final int STATUS_SLOT = 16;
    private static final String CLOSE_LABEL = "Close";

    private PunishmentRequestGuiRenderer() {
    }

    static Inventory renderQueue(PunishmentRequestGuiState.Queue state) {
        String title = "Punishment requests " + (state.page() + 1) + '/' + state.totalPages();
        Inventory inventory = create(state, QUEUE_SIZE, title);
        fillFooter(inventory);
        for (int slot = 0; slot < state.requests().size(); slot++) {
            inventory.setItem(slot, requestItem(state.requests().get(slot), "pending", NamedTextColor.AQUA));
        }
        addEmptyQueueMessage(inventory, state);
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
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, CLOSE_LABEL, List.of()));
        return inventory;
    }

    static Inventory renderReview(PunishmentRequestGuiState.Review state) {
        PunishmentApprovalRequest request = state.lease().request();
        Inventory inventory = create(state, STANDARD_SIZE, "Claimed punishment request");
        inventory.setItem(SUMMARY_SLOT, requestItem(
                new PunishmentRequestGuiState.RequestView(request, state.targetName()),
                "claimed by you",
                NamedTextColor.AQUA
        ));
        addRequestDetails(inventory, request, state.targetName());
        inventory.setItem(STATUS_SLOT, item(
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
        inventory.setItem(REVIEW_CLOSE_SLOT, item(Material.BARRIER, CLOSE_LABEL, List.of()));
        return inventory;
    }

    static Inventory renderDetails(PunishmentRequestGuiState.Details state) {
        PunishmentApprovalRequest request = state.view().request();
        Inventory inventory = create(state, STANDARD_SIZE, "Punishment request details");
        inventory.setItem(SUMMARY_SLOT, requestItem(
                state.view(),
                PunishmentRequestPresentation.status(request.status()),
                PunishmentRequestPresentation.statusColor(request.status())
        ));
        addRequestDetails(inventory, request, state.view().targetName());
        inventory.setItem(STATUS_SLOT, item(
                statusMaterial(request.status()),
                "Resolution",
                List.of(
                        Component.text(
                                PunishmentRequestPresentation.resolution(request),
                                PunishmentRequestPresentation.statusColor(request.status())
                        ),
                        Component.text("Resolved: " + displayTime(request.resolvedAt()), NamedTextColor.GRAY),
                        Component.text("Current revision: " + request.revision(), NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to queue", List.of()));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK, "Refresh details", List.of()));
        inventory.setItem(REVIEW_CLOSE_SLOT, item(Material.BARRIER, CLOSE_LABEL, List.of()));
        return inventory;
    }

    static Inventory renderDenial(PunishmentRequestGuiState.Denial state) {
        Inventory inventory = create(state, STANDARD_SIZE, "Deny punishment request");
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
        inventory.setItem(REVIEW_CLOSE_SLOT, item(Material.BARRIER, CLOSE_LABEL, List.of()));
        return inventory;
    }

    private static Inventory create(PunishmentRequestGuiState state, int size, String title) {
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title));
        holder.attach(inventory);
        return inventory;
    }

    private static void addEmptyQueueMessage(Inventory inventory, PunishmentRequestGuiState.Queue state) {
        if (!state.requests().isEmpty()) {
            return;
        }
        inventory.setItem(22, item(
                Material.PAPER,
                "No reviewable requests",
                List.of(Component.text("No pending requests match your approval rank.", NamedTextColor.GRAY))
        ));
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
                proposalLore(request)
        ));
        inventory.setItem(14, item(
                Material.NAME_TAG,
                "Requester and authority",
                authorityLore(request)
        ));
    }

    private static List<Component> proposalLore(PunishmentApprovalRequest request) {
        return List.of(
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
        );
    }

    private static List<Component> authorityLore(PunishmentApprovalRequest request) {
        return List.of(
                Component.text(request.proposal().requester().displayName(), NamedTextColor.WHITE),
                Component.text("Requester rank: " + request.proposal().requester().rank(), NamedTextColor.GRAY),
                Component.text("Minimum approval: " + request.proposal().requiredRank(), NamedTextColor.GRAY),
                Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY),
                Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY),
                Component.text("Revision: " + request.revision(), NamedTextColor.DARK_GRAY)
        );
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

    private static void denialItem(Inventory inventory, int slot, Material material, String reason) {
        inventory.setItem(slot, item(
                material,
                reason,
                List.of(Component.text("Click to deny with this audit note", NamedTextColor.YELLOW))
        ));
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
            appendWord(result, word);
        }
        return result.isEmpty() ? "Punishment request" : result.toString();
    }

    private static void appendWord(StringBuilder result, String word) {
        if (word.isBlank()) {
            return;
        }
        if (!result.isEmpty()) {
            result.append(' ');
        }
        result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
        result.append(word.substring(1));
    }
}
