package net.enthusia.staff.paper.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportSummary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class ReportGuiRenderer {
    static final int CONTENT_SIZE = 36;
    static final int OPEN_QUEUE_SLOT = 36;
    static final int MINE_QUEUE_SLOT = 37;
    static final int CLAIMED_QUEUE_SLOT = 38;
    static final int REVIEW_QUEUE_SLOT = 39;
    static final int CLOSED_QUEUE_SLOT = 40;
    static final int REFRESH_SLOT = 41;
    static final int BACK_SLOT = 42;
    static final int CLOSE_SLOT = 44;
    static final int PREVIOUS_SLOT = 45;
    static final int CONFIRM_SLOT = 49;
    static final int NEXT_SLOT = 53;
    static final int ACTION_START = 45;
    static final int ACTION_END = 47;

    Inventory render(ReportGuiState state) {
        ReportGuiHolder holder = new ReportGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(state));
        holder.attach(inventory);
        fillControls(inventory);
        if (state instanceof ReportGuiState.Queue queue) {
            renderQueue(inventory, queue);
        } else if (state instanceof ReportGuiState.Detail detail) {
            renderDetail(inventory, detail);
        } else if (state instanceof ReportGuiState.Review review) {
            renderReview(inventory, review);
        }
        return inventory;
    }

    private static void renderQueue(Inventory inventory, ReportGuiState.Queue state) {
        int offset = state.queuePage() * CONTENT_SIZE;
        for (int slot = 0; slot < CONTENT_SIZE && offset + slot < state.reports().size(); slot++) {
            ReportSummary summary = state.reports().get(offset + slot);
            inventory.setItem(slot, item(
                    stateMaterial(summary.state()),
                    summary.reasonId(),
                    List.of(
                            Component.text("Target: " + summary.targetId(), NamedTextColor.WHITE),
                            Component.text("State: " + summary.state(), NamedTextColor.GRAY),
                            Component.text("Assigned: " + summary.assignedTo().map(UUID::toString).orElse("none"),
                                    NamedTextColor.GRAY),
                            Component.text("Server: " + summary.serverId(), NamedTextColor.DARK_GRAY),
                            Component.text("Updated: " + summary.updatedAt(), NamedTextColor.DARK_GRAY),
                            Component.text("Revision: " + summary.revision(), NamedTextColor.DARK_GRAY),
                            Component.text("Click to inspect", NamedTextColor.YELLOW)
                    )
            ));
        }
        renderQueueControls(inventory, state.queue());
        inventory.setItem(REFRESH_SLOT, item(Material.COMPASS, "Refresh queue", List.of()));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        if (state.queuePage() > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "Previous page", List.of()));
        }
        if ((state.queuePage() + 1) * CONTENT_SIZE < state.reports().size()) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next page", List.of()));
        }
        if (state.reports().isEmpty()) {
            inventory.setItem(13, item(
                    Material.LIGHT_GRAY_DYE,
                    "No reports in this queue",
                    List.of(Component.text("Use the queue buttons below to change filters", NamedTextColor.GRAY))
            ));
        }
    }

    private static void renderDetail(Inventory inventory, ReportGuiState.Detail state) {
        ReportDetails details = state.details();
        ReportSummary summary = details.summary();
        inventory.setItem(4, item(
                stateMaterial(summary.state()),
                "Report " + summary.reportId(),
                List.of(
                        Component.text(summary.reasonId(), NamedTextColor.WHITE),
                        Component.text("State: " + summary.state(), NamedTextColor.GRAY),
                        Component.text("Revision: " + summary.revision(), NamedTextColor.DARK_GRAY),
                        Component.text("Created: " + summary.createdAt(), NamedTextColor.DARK_GRAY),
                        Component.text("Updated: " + summary.updatedAt(), NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(10, item(
                Material.WRITABLE_BOOK,
                "Reporter",
                List.of(Component.text(summary.reporterId().toString(), NamedTextColor.GRAY))
        ));
        inventory.setItem(12, item(
                Material.PLAYER_HEAD,
                "Target",
                List.of(Component.text(summary.targetId().toString(), NamedTextColor.GRAY))
        ));
        inventory.setItem(14, item(
                Material.COMPASS,
                "Location context",
                List.of(
                        Component.text("Server: " + summary.serverId(), NamedTextColor.GRAY),
                        Component.text("World: " + details.worldId().orElse("unavailable"), NamedTextColor.GRAY),
                        Component.text("Reporter: " + details.reporterCoordinates().orElse("unavailable"),
                                NamedTextColor.DARK_GRAY),
                        Component.text("Target: " + details.targetCoordinates().orElse("unavailable"),
                                NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(16, item(
                Material.CHEST,
                "Captured evidence",
                List.of(
                        Component.text("Public chat: " + details.publicChatSnapshots().size(), NamedTextColor.GRAY),
                        Component.text("Private messages: " + details.privateMessageSnapshots().size(),
                                NamedTextColor.GRAY),
                        Component.text("Client snapshots: " + details.clientEvidenceSnapshots().size(),
                                NamedTextColor.GRAY),
                        Component.text("Sensitive contents remain inside staff storage", NamedTextColor.YELLOW)
                )
        ));
        inventory.setItem(22, item(Material.PAPER, "Description", wrap(details.description())));
        inventory.setItem(28, snapshotItem(
                Material.BOOK,
                "Public chat snapshots",
                details.publicChatSnapshots()
        ));
        inventory.setItem(30, snapshotItem(
                Material.ENDER_EYE,
                "Private-message snapshots",
                details.privateMessageSnapshots()
        ));
        inventory.setItem(32, snapshotItem(
                Material.REDSTONE_TORCH,
                "Client evidence snapshots",
                details.clientEvidenceSnapshots()
        ));
        renderQueueControls(inventory, state.queue());
        inventory.setItem(REFRESH_SLOT, item(Material.COMPASS, "Reload report", List.of()));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to queue", List.of()));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        List<ReportAction> actions = ReportGuiAccess.actions(summary, state.viewerId());
        for (int index = 0; index < actions.size() && ACTION_START + index <= ACTION_END; index++) {
            ReportAction action = actions.get(index);
            inventory.setItem(ACTION_START + index, item(
                    actionMaterial(action),
                    actionName(action),
                    List.of(
                            Component.text(actionDescription(action), NamedTextColor.GRAY),
                            Component.text("A private action note is required", NamedTextColor.YELLOW)
                    )
            ));
        }
    }

    private static void renderReview(Inventory inventory, ReportGuiState.Review state) {
        ReportSummary summary = state.details().summary();
        inventory.setItem(11, item(
                stateMaterial(summary.state()),
                "Report " + summary.reportId(),
                List.of(
                        Component.text(summary.reasonId(), NamedTextColor.WHITE),
                        Component.text("Expected revision: " + summary.revision(), NamedTextColor.YELLOW),
                        Component.text("Current state: " + summary.state(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(13, item(
                actionMaterial(state.action()),
                actionName(state.action()),
                List.of(Component.text(actionDescription(state.action()), NamedTextColor.GRAY))
        ));
        inventory.setItem(15, item(Material.NAME_TAG, "Private action note", wrap(state.note())));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back without changing", List.of()));
        inventory.setItem(CONFIRM_SLOT, item(
                Material.LIME_CONCRETE,
                "Confirm report action",
                List.of(
                        Component.text("The exact displayed revision will be checked", NamedTextColor.YELLOW),
                        Component.text("Stale state is rejected and reloaded", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close without changing", List.of()));
    }

    private static ItemStack snapshotItem(Material material, String name, List<String> snapshots) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Available snapshots: " + snapshots.size(), NamedTextColor.GRAY));
        if (snapshots.isEmpty()) {
            lore.add(Component.text("None retained or currently readable", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text("Contents are intentionally not copied into item lore", NamedTextColor.YELLOW));
        }
        return item(material, name, lore);
    }

    private static void renderQueueControls(Inventory inventory, ReportQueue active) {
        inventory.setItem(OPEN_QUEUE_SLOT, queueItem(Material.PAPER, "Open", ReportQueue.OPEN, active));
        inventory.setItem(MINE_QUEUE_SLOT, queueItem(Material.NAME_TAG, "Mine", ReportQueue.CLAIMED_BY_ME, active));
        inventory.setItem(CLAIMED_QUEUE_SLOT,
                queueItem(Material.CHEST, "Claimed", ReportQueue.ALL_CLAIMED, active));
        inventory.setItem(REVIEW_QUEUE_SLOT,
                queueItem(Material.ENCHANTED_BOOK, "Awaiting review", ReportQueue.AWAITING_REVIEW, active));
        inventory.setItem(CLOSED_QUEUE_SLOT,
                queueItem(Material.GRAY_DYE, "Recently closed", ReportQueue.RECENTLY_CLOSED, active));
    }

    private static ItemStack queueItem(Material material, String name, ReportQueue queue, ReportQueue active) {
        return item(
                queue == active ? Material.LIME_STAINED_GLASS_PANE : material,
                name,
                List.of(Component.text(queue == active ? "Current queue" : "Click to open", NamedTextColor.GRAY))
        );
    }

    private static void fillControls(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = CONTENT_SIZE; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static Component title(ReportGuiState state) {
        if (state instanceof ReportGuiState.Detail detail) {
            return Component.text("Report " + shortId(detail.details().summary().reportId()));
        }
        if (state instanceof ReportGuiState.Review review) {
            return Component.text("Confirm " + actionName(review.action()));
        }
        return Component.text("Reports: " + queueName(state.queue()));
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String queueName(ReportQueue queue) {
        return switch (queue) {
            case OPEN -> "Open";
            case CLAIMED_BY_ME -> "Mine";
            case ALL_CLAIMED -> "Claimed";
            case AWAITING_REVIEW -> "Awaiting Review";
            case RECENTLY_CLOSED -> "Recently Closed";
        };
    }

    private static Material stateMaterial(ReportState state) {
        return switch (state) {
            case OPEN -> Material.PAPER;
            case CLAIMED -> Material.NAME_TAG;
            case AWAITING_REVIEW -> Material.ENCHANTED_BOOK;
            case CLOSED -> Material.LIME_DYE;
            case NO_VIOLATION -> Material.GRAY_DYE;
        };
    }

    private static Material actionMaterial(ReportAction action) {
        return switch (action) {
            case CLAIM -> Material.NAME_TAG;
            case AWAIT_REVIEW -> Material.ENCHANTED_BOOK;
            case CLOSE -> Material.LIME_CONCRETE;
            case NO_VIOLATION -> Material.LIGHT_GRAY_CONCRETE;
        };
    }

    static String actionName(ReportAction action) {
        String[] words = action.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String actionDescription(ReportAction action) {
        return switch (action) {
            case CLAIM -> "Assign this open report to yourself.";
            case AWAIT_REVIEW -> "Send your claimed report for another staff review.";
            case CLOSE -> "Resolve the report after a confirmed violation workflow.";
            case NO_VIOLATION -> "Resolve the report without finding a rule violation.";
        };
    }

    private static List<Component> wrap(String value) {
        String compact = value.replace('\r', ' ').replace('\n', ' ');
        List<Component> lines = new ArrayList<>();
        for (int start = 0; start < compact.length() && lines.size() < 10; start += 48) {
            lines.add(Component.text(
                    compact.substring(start, Math.min(compact.length(), start + 48)),
                    NamedTextColor.GRAY
            ));
        }
        return List.copyOf(lines);
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
