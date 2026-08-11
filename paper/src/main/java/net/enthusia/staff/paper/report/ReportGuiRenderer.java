package net.enthusia.staff.paper.report;

import java.util.ArrayList;
import java.util.List;
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
    private static final String REFRESH_KEY = "refresh";
    private static final String CLOSE_KEY = "close";
    private static final String BACK_KEY = "back";
    private static final String QUEUE_KEY_PREFIX = "queue-";

    Inventory render(ReportGuiState state, ReportGuiConfiguration configuration) {
        ReportGuiHolder holder = new ReportGuiHolder(state, configuration);
        Inventory inventory = Bukkit.createInventory(
                holder,
                configuration.inventorySize(),
                title(state, configuration)
        );
        holder.attach(inventory);
        fillControls(inventory, configuration);
        if (state instanceof ReportGuiState.Queue queue) {
            renderQueue(inventory, queue, configuration);
        } else if (state instanceof ReportGuiState.Detail detail) {
            renderDetail(inventory, detail, configuration);
        } else if (state instanceof ReportGuiState.Review review) {
            renderReview(inventory, review, configuration);
        }
        return inventory;
    }

    private static void renderQueue(
            Inventory inventory,
            ReportGuiState.Queue state,
            ReportGuiConfiguration configuration
    ) {
        int pageSize = configuration.pageSize();
        int offset = state.queuePage() * pageSize;
        renderQueueEntries(inventory, state, configuration, pageSize, offset);
        renderQueueControls(inventory, state.queue(), configuration);
        renderQueueNavigation(inventory, state, configuration, pageSize);
    }

    private static void renderQueueEntries(
            Inventory inventory,
            ReportGuiState.Queue state,
            ReportGuiConfiguration configuration,
            int pageSize,
            int offset
    ) {
        for (int index = 0; index < pageSize && offset + index < state.reports().size(); index++) {
            ReportSummary summary = state.reports().get(offset + index);
            inventory.setItem(configuration.contentSlots().get(index), item(
                    stateMaterial(summary.state(), configuration),
                    summary.reasonId(),
                    List.of(
                            Component.text("Target: " + summary.targetId(), NamedTextColor.WHITE),
                            Component.text("State: " + summary.state(), NamedTextColor.GRAY),
                            Component.text("Assigned: " + summary.assignedTo().map(UUID::toString).orElse("none"),
                                    NamedTextColor.GRAY),
                            Component.text("Server: " + summary.serverId(), NamedTextColor.DARK_GRAY),
                            Component.text("Updated: " + summary.updatedAt(), NamedTextColor.DARK_GRAY),
                            Component.text("Revision: " + summary.revision(), NamedTextColor.DARK_GRAY),
                            Component.text(configuration.message("click-inspect"), NamedTextColor.YELLOW)
                    )
            ));
        }
    }

    private static void renderQueueNavigation(
            Inventory inventory,
            ReportGuiState.Queue state,
            ReportGuiConfiguration configuration,
            int pageSize
    ) {
        inventory.setItem(configuration.slot(REFRESH_KEY), item(
                configuration.material(REFRESH_KEY),
                configuration.message("refresh-queue"),
                List.of()
        ));
        inventory.setItem(configuration.slot(CLOSE_KEY), item(
                configuration.material(CLOSE_KEY),
                configuration.message(CLOSE_KEY),
                List.of()
        ));
        if (state.queuePage() > 0) {
            inventory.setItem(configuration.slot("previous"), item(
                    configuration.material("previous"),
                    configuration.message("previous-page"),
                    List.of()
            ));
        }
        if ((state.queuePage() + 1) * pageSize < state.reports().size()) {
            inventory.setItem(configuration.slot("next"), item(
                    configuration.material("next"),
                    configuration.message("next-page"),
                    List.of()
            ));
        }
        if (state.reports().isEmpty()) {
            inventory.setItem(configuration.slot("empty"), item(
                    configuration.material("empty"),
                    configuration.message("empty-title"),
                    List.of(Component.text(configuration.message("empty-lore"), NamedTextColor.GRAY))
            ));
        }
    }

    private static void renderDetail(
            Inventory inventory,
            ReportGuiState.Detail state,
            ReportGuiConfiguration configuration
    ) {
        ReportDetails details = state.details();
        ReportSummary summary = details.summary();
        renderDetailHeader(inventory, summary, configuration);
        renderDetailIdentity(inventory, details, configuration);
        renderDetailEvidence(inventory, details, configuration);
        renderQueueControls(inventory, state.queue(), configuration);
        renderDetailNavigation(inventory, configuration);
        renderDetailActions(inventory, state, summary, configuration);
    }

    private static void renderDetailHeader(
            Inventory inventory,
            ReportSummary summary,
            ReportGuiConfiguration configuration
    ) {
        inventory.setItem(configuration.slot("detail-header"), item(
                stateMaterial(summary.state(), configuration),
                "Report " + summary.reportId(),
                List.of(
                        Component.text(summary.reasonId(), NamedTextColor.WHITE),
                        Component.text("State: " + summary.state(), NamedTextColor.GRAY),
                        Component.text("Revision: " + summary.revision(), NamedTextColor.DARK_GRAY),
                        Component.text("Created: " + summary.createdAt(), NamedTextColor.DARK_GRAY),
                        Component.text("Updated: " + summary.updatedAt(), NamedTextColor.DARK_GRAY)
                )
        ));
    }

    private static void renderDetailIdentity(
            Inventory inventory,
            ReportDetails details,
            ReportGuiConfiguration configuration
    ) {
        ReportSummary summary = details.summary();
        inventory.setItem(configuration.slot("detail-reporter"), item(
                configuration.material("reporter"),
                configuration.message("reporter"),
                List.of(Component.text(summary.reporterId().toString(), NamedTextColor.GRAY))
        ));
        inventory.setItem(configuration.slot("detail-target"), item(
                configuration.material("target"),
                configuration.message("target"),
                List.of(Component.text(summary.targetId().toString(), NamedTextColor.GRAY))
        ));
        inventory.setItem(configuration.slot("detail-location"), item(
                configuration.material("location"),
                configuration.message("location-context"),
                List.of(
                        Component.text("Server: " + summary.serverId(), NamedTextColor.GRAY),
                        Component.text("World: " + details.worldId().orElse("unavailable"), NamedTextColor.GRAY),
                        Component.text("Exact coordinates require the sensitive evidence permission and text view.",
                                NamedTextColor.YELLOW)
                )
        ));
    }

    private static void renderDetailEvidence(
            Inventory inventory,
            ReportDetails details,
            ReportGuiConfiguration configuration
    ) {
        inventory.setItem(configuration.slot("detail-evidence"), item(
                configuration.material("evidence"),
                configuration.message("captured-evidence"),
                List.of(
                        Component.text("Public chat: " + details.publicChatSnapshots().size(), NamedTextColor.GRAY),
                        Component.text("Private messages: " + details.privateMessageSnapshots().size(),
                                NamedTextColor.GRAY),
                        Component.text("Client snapshots: " + details.clientEvidenceSnapshots().size(),
                                NamedTextColor.GRAY),
                        Component.text(configuration.message("sensitive-evidence"), NamedTextColor.YELLOW)
                )
        ));
        inventory.setItem(configuration.slot("detail-description"), item(
                configuration.material("description"),
                configuration.message("description"),
                wrap(details.description())
        ));
        inventory.setItem(configuration.slot("detail-public-chat"), snapshotItem(
                configuration.material("public-chat"),
                configuration.message("public-chat-snapshots"),
                details.publicChatSnapshots(),
                configuration
        ));
        inventory.setItem(configuration.slot("detail-private-message"), snapshotItem(
                configuration.material("private-message"),
                configuration.message("private-message-snapshots"),
                details.privateMessageSnapshots(),
                configuration
        ));
        inventory.setItem(configuration.slot("detail-client-evidence"), snapshotItem(
                configuration.material("client-evidence"),
                configuration.message("client-evidence-snapshots"),
                details.clientEvidenceSnapshots(),
                configuration
        ));
    }

    private static void renderDetailNavigation(
            Inventory inventory,
            ReportGuiConfiguration configuration
    ) {
        inventory.setItem(configuration.slot(REFRESH_KEY), item(
                configuration.material(REFRESH_KEY),
                configuration.message("reload-report"),
                List.of()
        ));
        inventory.setItem(configuration.slot(BACK_KEY), item(
                configuration.material(BACK_KEY),
                configuration.message("back-queue"),
                List.of()
        ));
        inventory.setItem(configuration.slot(CLOSE_KEY), item(
                configuration.material(CLOSE_KEY),
                configuration.message(CLOSE_KEY),
                List.of()
        ));
    }

    private static void renderDetailActions(
            Inventory inventory,
            ReportGuiState.Detail state,
            ReportSummary summary,
            ReportGuiConfiguration configuration
    ) {
        List<ReportAction> actions = ReportGuiAccess.actions(summary, state.viewerId());
        for (int index = 0; index < actions.size() && index < configuration.actionSlots().size(); index++) {
            ReportAction action = actions.get(index);
            inventory.setItem(configuration.actionSlots().get(index), item(
                    actionMaterial(action, configuration),
                    actionName(action, configuration),
                    List.of(
                            Component.text(actionDescription(action, configuration), NamedTextColor.GRAY),
                            Component.text(configuration.message("private-note-required"), NamedTextColor.YELLOW)
                    )
            ));
        }
    }

    private static void renderReview(
            Inventory inventory,
            ReportGuiState.Review state,
            ReportGuiConfiguration configuration
    ) {
        ReportSummary summary = state.details().summary();
        inventory.setItem(configuration.slot("review-report"), item(
                stateMaterial(summary.state(), configuration),
                "Report " + summary.reportId(),
                List.of(
                        Component.text(summary.reasonId(), NamedTextColor.WHITE),
                        Component.text("Expected revision: " + summary.revision(), NamedTextColor.YELLOW),
                        Component.text("Current state: " + summary.state(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(configuration.slot("review-action"), item(
                actionMaterial(state.action(), configuration),
                actionName(state.action(), configuration),
                List.of(Component.text(actionDescription(state.action(), configuration), NamedTextColor.GRAY))
        ));
        inventory.setItem(configuration.slot("review-note"), item(
                configuration.material("private-note"),
                configuration.message("private-note"),
                wrap(state.note())
        ));
        inventory.setItem(configuration.slot(BACK_KEY), item(
                configuration.material(BACK_KEY),
                configuration.message("back-no-change"),
                List.of()
        ));
        inventory.setItem(configuration.slot("confirm"), item(
                configuration.material("confirm"),
                configuration.message("confirm-action"),
                List.of(
                        Component.text(configuration.message("confirm-revision"), NamedTextColor.YELLOW),
                        Component.text(configuration.message("stale-rejected"), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(configuration.slot(CLOSE_KEY), item(
                configuration.material(CLOSE_KEY),
                configuration.message("close-no-change"),
                List.of()
        ));
    }

    private static ItemStack snapshotItem(
            Material material,
            String name,
            List<String> snapshots,
            ReportGuiConfiguration configuration
    ) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(
                configuration.message("snapshot-count").replace("{count}", Integer.toString(snapshots.size())),
                NamedTextColor.GRAY
        ));
        if (snapshots.isEmpty()) {
            lore.add(Component.text(configuration.message("snapshot-none"), NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text(configuration.message("snapshot-protected"), NamedTextColor.YELLOW));
        }
        return item(material, name, lore);
    }

    private static void renderQueueControls(
            Inventory inventory,
            ReportQueue active,
            ReportGuiConfiguration configuration
    ) {
        for (ReportQueue queue : ReportQueue.values()) {
            String key = queueKey(queue);
            inventory.setItem(configuration.slot(QUEUE_KEY_PREFIX + key), queueItem(
                    configuration.material(QUEUE_KEY_PREFIX + key),
                    configuration.message(QUEUE_KEY_PREFIX + key),
                    queue,
                    active,
                    configuration
            ));
        }
    }

    private static ItemStack queueItem(
            Material material,
            String name,
            ReportQueue queue,
            ReportQueue active,
            ReportGuiConfiguration configuration
    ) {
        return item(
                queue == active ? configuration.material("active-queue") : material,
                name,
                List.of(Component.text(
                        configuration.message(queue == active ? "current-queue" : "click-open"),
                        NamedTextColor.GRAY
                ))
        );
    }

    private static void fillControls(Inventory inventory, ReportGuiConfiguration configuration) {
        ItemStack filler = item(configuration.material("filler"), " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!configuration.contentSlots().contains(slot)) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private static Component title(ReportGuiState state, ReportGuiConfiguration configuration) {
        if (state instanceof ReportGuiState.Detail detail) {
            return Component.text(configuration.title("detail")
                    .replace("{id}", shortId(detail.details().summary().reportId())));
        }
        if (state instanceof ReportGuiState.Review review) {
            return Component.text(configuration.title("review")
                    .replace("{action}", actionName(review.action(), configuration)));
        }
        return Component.text(configuration.title("queue")
                .replace("{queue}", configuration.message(QUEUE_KEY_PREFIX + queueKey(state.queue()))));
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String queueKey(ReportQueue queue) {
        return switch (queue) {
            case OPEN -> "open";
            case CLAIMED_BY_ME -> "mine";
            case ALL_CLAIMED -> "claimed";
            case AWAITING_REVIEW -> "review";
            case RECENTLY_CLOSED -> "closed";
        };
    }

    private static Material stateMaterial(ReportState state, ReportGuiConfiguration configuration) {
        return configuration.material(switch (state) {
            case OPEN -> "state-open";
            case CLAIMED -> "state-claimed";
            case AWAITING_REVIEW -> "state-awaiting-review";
            case CLOSED -> "state-closed";
            case NO_VIOLATION -> "state-no-violation";
        });
    }

    private static Material actionMaterial(ReportAction action, ReportGuiConfiguration configuration) {
        return configuration.material("action-" + actionKey(action));
    }

    private static String actionName(ReportAction action, ReportGuiConfiguration configuration) {
        return configuration.message("action-" + actionKey(action));
    }

    private static String actionDescription(ReportAction action, ReportGuiConfiguration configuration) {
        return configuration.message("action-" + actionKey(action) + "-description");
    }

    private static String actionKey(ReportAction action) {
        return switch (action) {
            case CLAIM -> "claim";
            case AWAIT_REVIEW -> "await-review";
            case CLOSE -> CLOSE_KEY;
            case NO_VIOLATION -> "no-violation";
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
