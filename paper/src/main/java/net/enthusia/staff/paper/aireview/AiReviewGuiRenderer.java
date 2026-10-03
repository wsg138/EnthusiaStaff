package net.enthusia.staff.paper.aireview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionStatus;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class AiReviewGuiRenderer {
    static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    static final int PREVIOUS = 45;
    static final int BACK = 45;
    static final int REFRESH = 49;
    static final int NEXT = 53;
    static final int CLOSE = 50;
    static final int ALLOW = 20;
    static final int BLOCK = 22;
    static final int REVIEW = 24;
    static final int LABEL = 30;
    static final int APPROVE = 32;
    static final int REJECT = 34;
    static final int ADMIN_APPROVE = 41;
    static final int CONFIRM = 22;

    private final AiReviewConfiguration configuration;

    AiReviewGuiRenderer(AiReviewConfiguration configuration) {
        this.configuration = configuration;
    }

    Inventory render(AiReviewGuiState state, Instant now, boolean adminAvailable) {
        AiReviewGuiHolder holder = new AiReviewGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(state));
        holder.attach(inventory);
        fill(inventory);
        if (state instanceof AiReviewGuiState.Queue queue) {
            renderQueue(inventory, queue, now);
        } else if (state instanceof AiReviewGuiState.Detail detail) {
            renderDetail(inventory, detail, adminAvailable);
        } else if (state instanceof AiReviewGuiState.LabelPicker picker) {
            renderLabels(inventory, picker);
        } else if (state instanceof AiReviewGuiState.Confirm confirm) {
            renderConfirm(inventory, confirm);
        }
        return inventory;
    }

    private void renderQueue(Inventory inventory, AiReviewGuiState.Queue state, Instant now) {
        int pageSize = Math.min(configuration.pageSize(), CONTENT_SLOTS.size());
        int offset = state.page() * pageSize;
        for (int index = 0; index < pageSize && offset + index < state.items().size(); index++) {
            ReviewItem review = state.items().get(offset + index);
            inventory.setItem(
                    CONTENT_SLOTS.get(index),
                    item(
                            priorityMaterial(review.reviewPriority()),
                            review.reviewPriority() + " · " + review.semanticLabel(),
                            AiReviewPresentation.queueLore(review, now, configuration)
                    )
            );
        }
        String status = state.authoritative()
                ? "Central queue · " + state.items().size() + " pending"
                : "Central queue unavailable · cached display only";
        inventory.setItem(REFRESH, item(Material.CLOCK, status, state.issue() == null
                ? List.of("Refresh from the central service")
                : List.of(AiReviewPresentation.bounded(state.issue(), 120))));
        if (state.page() > 0) {
            inventory.setItem(PREVIOUS, item(Material.ARROW, "Previous page", List.of()));
        }
        if ((state.page() + 1) * pageSize < state.items().size()) {
            inventory.setItem(NEXT, item(Material.ARROW, "Next page", List.of()));
        }
        inventory.setItem(CLOSE, item(Material.BARRIER, "Close", List.of()));
    }

    private void renderDetail(
            Inventory inventory,
            AiReviewGuiState.Detail state,
            boolean adminAvailable
    ) {
        EventDetails details = state.details();
        List<String> bounded = AiReviewPresentation.detailLines(details, configuration);
        inventory.setItem(13, item(
                Material.PAPER,
                "Event " + AiReviewPresentation.bounded(details.eventId(), 24),
                bounded.stream().limit(18).toList()
        ));
        inventory.setItem(11, item(
                Material.WRITABLE_BOOK,
                "Message & context",
                contextLore(details)
        ));
        inventory.setItem(15, item(
                Material.COMPARATOR,
                "Decision & evidence",
                bounded.stream().skip(Math.min(5, bounded.size())).limit(18).toList()
        ));
        inventory.setItem(ALLOW, item(Material.LIME_DYE, "Should allow", List.of(
                "Preserves every other decision dimension.",
                "Requires confirmation."
        )));
        inventory.setItem(BLOCK, item(Material.RED_DYE, "Should block", List.of(
                "Preserves every other decision dimension.",
                "Does not punish the player.",
                "Requires confirmation."
        )));
        inventory.setItem(REVIEW, item(Material.YELLOW_DYE, "Needs review", List.of(
                "Raises NONE review priority to NORMAL.",
                "Preserves action/strike/containment/support.",
                "Requires confirmation."
        )));
        inventory.setItem(LABEL, item(Material.NAME_TAG, "Correct semantic label", List.of(
                "Open the bounded Policy-v1 label picker."
        )));
        Correction pending = details.latestPendingCorrection();
        if (pending != null) {
            inventory.setItem(APPROVE, item(Material.EMERALD, "Approve pending correction", List.of(
                    "Proposal: " + AiReviewPresentation.bounded(pending.proposalId(), 28),
                    "Central quorum remains authoritative.",
                    "Requires confirmation."
            )));
            inventory.setItem(REJECT, item(Material.FIRE_CHARGE, "Reject pending correction", List.of(
                    "Proposal: " + AiReviewPresentation.bounded(pending.proposalId(), 28),
                    "Central quorum remains authoritative.",
                    "Requires confirmation."
            )));
            if (adminAvailable) {
                inventory.setItem(ADMIN_APPROVE, item(Material.NETHER_STAR, "Admin accept override", List.of(
                        "Immediate ADMIN path is locally permission-gated",
                        "and must be enabled in AI review config.",
                        "Requires confirmation."
                )));
            }
        }
        inventory.setItem(BACK, item(Material.ARROW, "Back to queue", List.of()));
        inventory.setItem(REFRESH, item(Material.CLOCK, "Refresh event", List.of()));
        inventory.setItem(CLOSE, item(Material.BARRIER, "Close", List.of()));
    }

    private void renderLabels(Inventory inventory, AiReviewGuiState.LabelPicker state) {
        int pageSize = CONTENT_SLOTS.size();
        int offset = state.page() * pageSize;
        for (int index = 0; index < pageSize && offset + index < state.labels().size(); index++) {
            String label = state.labels().get(offset + index);
            inventory.setItem(
                    CONTENT_SLOTS.get(index),
                    item(Material.NAME_TAG, label, List.of(
                            "Current: " + state.details().decision().semanticLabel(),
                            "Click to review this correction."
                    ))
            );
        }
        if (state.page() > 0) {
            inventory.setItem(PREVIOUS, item(Material.ARROW, "Previous labels", List.of()));
        }
        if ((state.page() + 1) * pageSize < state.labels().size()) {
            inventory.setItem(NEXT, item(Material.ARROW, "Next labels", List.of()));
        }
        inventory.setItem(BACK, item(Material.ARROW, "Back to event", List.of()));
        inventory.setItem(CLOSE, item(Material.BARRIER, "Close", List.of()));
    }

    private void renderConfirm(Inventory inventory, AiReviewGuiState.Confirm state) {
        inventory.setItem(13, item(Material.PAPER, "Review change", List.of(
                "Event: " + AiReviewPresentation.bounded(state.details().eventId(), 40),
                AiReviewPresentation.bounded(state.description(), 180),
                "No punishment is executed from this screen."
        )));
        if (state.decision() != null) {
            inventory.setItem(31, item(Material.COMPARATOR, "Complete corrected decision", List.of(
                    "Label: " + state.decision().semanticLabel(),
                    "Action: " + state.decision().messageAction(),
                    "Review: " + state.decision().reviewPriority(),
                    "Strike: " + state.decision().strikeRecommendation(),
                    "Containment: " + state.decision().containment(),
                    "Support: " + state.decision().supportFlow()
            )));
        }
        inventory.setItem(CONFIRM, item(
                state.adminRequested() ? Material.NETHER_STAR : Material.EMERALD_BLOCK,
                state.adminRequested() ? "Confirm ADMIN action" : "Confirm staff vote",
                List.of(
                        "The event is re-fetched before this write.",
                        "Permissions are rechecked before this write.",
                        "Central quorum/correction state is authoritative."
                )
        ));
        inventory.setItem(BACK, item(Material.ARROW, "Back — no change", List.of()));
        inventory.setItem(CLOSE, item(Material.BARRIER, "Close — no change", List.of()));
    }

    private List<String> contextLore(EventDetails details) {
        List<String> lines = new ArrayList<>();
        lines.add("Message: " + AiReviewPresentation.bounded(
                details.text(),
                Math.min(configuration.maximumMessageCharacters(), 320)
        ));
        int limit = Math.min(configuration.maximumContextItems(), details.contextEvidence().size());
        for (int index = 0; index < limit; index++) {
            lines.add((index + 1) + ": " + AiReviewPresentation.bounded(
                    details.contextEvidence().get(index).text(),
                    180
            ));
        }
        if (details.contextEvidence().size() > limit) {
            lines.add("+" + (details.contextEvidence().size() - limit) + " more bounded evidence item(s)");
        }
        return List.copyOf(lines);
    }

    private static Component title(AiReviewGuiState state) {
        if (state instanceof AiReviewGuiState.Queue queue) {
            return Component.text("AI Review Queue · Page " + (queue.page() + 1));
        }
        if (state instanceof AiReviewGuiState.LabelPicker) {
            return Component.text("AI Review · Semantic Label");
        }
        if (state instanceof AiReviewGuiState.Confirm confirm) {
            return Component.text(confirm.adminRequested() ? "AI Review · ADMIN Confirm" : "AI Review · Confirm");
        }
        return Component.text("AI Review · Event");
    }

    private static Material priorityMaterial(ReviewPriority priority) {
        return switch (priority) {
            case URGENT -> Material.REDSTONE;
            case NORMAL -> Material.GOLD_INGOT;
            case NONE -> Material.PAPER;
        };
    }

    private static void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(lore.stream()
                .limit(24)
                .map(line -> Component.text(AiReviewPresentation.bounded(line, 220), NamedTextColor.GRAY))
                .toList());
        item.setItemMeta(meta);
        return item;
    }
}
