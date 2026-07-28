package net.enthusia.staff.paper.sanction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.SanctionReview;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SanctionChangeGuiRenderer {
    static final int CONTENT_SIZE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int BACK_SLOT = 48;
    static final int CONFIRM_SLOT = 49;
    static final int CLOSE_SLOT = 50;
    static final int NEXT_SLOT = 53;
    static final int ACTION_START = 27;
    static final int ACTION_END = 44;

    private final SanctionChangeGuiCatalog catalog;

    SanctionChangeGuiRenderer(SanctionChangeGuiCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("sanction change GUI catalog must be present");
        }
        this.catalog = catalog;
    }

    Inventory render(SanctionChangeGuiState state, Actor actor, Player viewer) {
        SanctionChangeGuiHolder holder = new SanctionChangeGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(state));
        holder.attach(inventory);
        fillFooter(inventory);
        switch (state) {
            case SanctionChangeGuiState.Cases cases -> renderCases(inventory, cases);
            case SanctionChangeGuiState.Actions actions -> renderActions(inventory, actions, actor, viewer);
            case SanctionChangeGuiState.Review review -> renderReview(inventory, review);
        }
        return inventory;
    }

    private static void renderCases(Inventory inventory, SanctionChangeGuiState.Cases state) {
        int offset = state.page() * CONTENT_SIZE;
        for (int slot = 0; slot < CONTENT_SIZE && offset + slot < state.cases().size(); slot++) {
            CaseReview review = state.cases().get(offset + slot);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(review.publicReason(), NamedTextColor.WHITE));
            lore.add(Component.text(review.exactReasonId(), NamedTextColor.DARK_GRAY));
            lore.add(Component.text("State: " + review.state(), NamedTextColor.GRAY));
            lore.add(Component.text("Issued: " + review.issuedAt(), NamedTextColor.GRAY));
            lore.add(Component.text("Actor: " + review.actorName() + " (" + review.actorRank() + ')',
                    NamedTextColor.GRAY));
            lore.add(Component.text("Sanctions: " + review.sanctions().size(), NamedTextColor.GRAY));
            lore.add(Component.text("Click to inspect available actions", NamedTextColor.YELLOW));
            inventory.setItem(slot, item(caseMaterial(review), review.caseId().value(), lore));
        }
        if (state.page() > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "Previous page", List.of()));
        }
        if ((state.page() + 1) * CONTENT_SIZE < state.cases().size()) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next page", List.of()));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
    }

    private void renderActions(
            Inventory inventory,
            SanctionChangeGuiState.Actions state,
            Actor actor,
            Player viewer
    ) {
        CaseReview review = state.review();
        inventory.setItem(4, item(
                Material.WRITTEN_BOOK,
                "Case " + review.caseId(),
                List.of(
                        Component.text(review.publicReason(), NamedTextColor.WHITE),
                        Component.text(review.exactReasonId(), NamedTextColor.DARK_GRAY),
                        Component.text("State: " + review.state() + "; visibility: " + review.visibility(),
                                NamedTextColor.GRAY),
                        Component.text("Issued by " + review.actorName() + " (" + review.actorRank() + ')',
                                NamedTextColor.GRAY),
                        Component.text("Configuration: " + review.configurationVersion(), NamedTextColor.DARK_GRAY)
                )
        ));
        for (int index = 0; index < review.sanctions().size() && index < 9; index++) {
            SanctionReview sanction = review.sanctions().get(index);
            inventory.setItem(9 + index, item(
                    sanctionMaterial(sanction),
                    sanction.type().name(),
                    List.of(
                            Component.text("Status: " + sanction.status(), NamedTextColor.GRAY),
                            Component.text("Issued: " + sanction.issuedAt(), NamedTextColor.DARK_GRAY),
                            Component.text("Expiration: " + sanction.expirationAt()
                                    .map(Instant::toString).orElse("none"), NamedTextColor.GRAY),
                            Component.text(sanction.sanctionId().toString(), NamedTextColor.DARK_GRAY)
                    )
            ));
        }
        review.punishmentStep().ifPresent(step -> inventory.setItem(22, item(
                step.escalationContributes() ? Material.LIME_DYE : Material.GRAY_DYE,
                "Escalation contribution",
                List.of(
                        Component.text("Step " + step.effectiveOrdinal() + ": " + step.label(),
                                NamedTextColor.GRAY),
                        Component.text(step.escalationContributes() ? "Currently contributes" : "Currently removed",
                                step.escalationContributes() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                )
        )));
        review.openOverturnRequest().ifPresent(request -> inventory.setItem(24, item(
                Material.ENCHANTED_BOOK,
                "Open overturn request",
                List.of(
                        Component.text("Requested: " + request.requestedAt(), NamedTextColor.GRAY),
                        Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY),
                        Component.text(request.explanation(), NamedTextColor.DARK_GRAY)
                )
        )));
        List<SanctionChangeAction> actions = catalog.actions(
                actor, review, viewer::hasPermission, state.commandName()
        );
        for (int index = 0; index < actions.size() && ACTION_START + index <= ACTION_END; index++) {
            SanctionChangeAction action = actions.get(index);
            inventory.setItem(ACTION_START + index, item(
                    actionMaterial(action),
                    actionName(action),
                    List.of(Component.text(actionDescription(action), NamedTextColor.GRAY))
            ));
        }
        if (state.origin().isPresent()) {
            inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to cases", List.of()));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
    }

    private static void renderReview(Inventory inventory, SanctionChangeGuiState.Review state) {
        inventory.setItem(11, item(
                Material.WRITTEN_BOOK,
                "Case " + state.caseReview().caseId(),
                List.of(
                        Component.text(state.caseReview().publicReason(), NamedTextColor.WHITE),
                        Component.text("Current state: " + state.caseReview().state(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(13, item(
                actionMaterial(state.action()),
                actionName(state.action()),
                List.of(
                        Component.text(actionDescription(state.action()), NamedTextColor.GRAY),
                        Component.text("Current authority is checked again on confirm", NamedTextColor.YELLOW)
                )
        ));
        inventory.setItem(15, item(
                Material.CLOCK,
                "Replacement expiration",
                List.of(Component.text(
                        state.replacementExpiration().map(Instant::toString).orElse("Not applicable"),
                        NamedTextColor.GRAY
                ))
        ));
        inventory.setItem(31, item(
                Material.NAME_TAG,
                "Audit reason",
                wrap(state.reason())
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to actions", List.of()));
        inventory.setItem(CONFIRM_SLOT, item(
                Material.LIME_CONCRETE,
                "Confirm change",
                List.of(
                        Component.text("This click changes sanction state", NamedTextColor.YELLOW),
                        Component.text("History and the original actor remain immutable", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close without changing", List.of()));
    }

    private static void fillFooter(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = CONTENT_SIZE; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static Component title(SanctionChangeGuiState state) {
        return switch (state) {
            case SanctionChangeGuiState.Cases ignored -> Component.text("Cases: " + state.targetLabel());
            case SanctionChangeGuiState.Actions actions -> Component.text("Change " + actions.review().caseId());
            case SanctionChangeGuiState.Review review -> Component.text("Confirm " + actionName(review.action()));
        };
    }

    private static Material caseMaterial(CaseReview review) {
        if (review.state() == net.enthusia.staff.domain.casefile.CaseState.FULLY_OVERTURNED) {
            return Material.GRAY_DYE;
        }
        return review.hasActiveSanctions() ? Material.RED_DYE : Material.PAPER;
    }

    private static Material sanctionMaterial(SanctionReview sanction) {
        return sanction.active() ? Material.REDSTONE : Material.GUNPOWDER;
    }

    private static Material actionMaterial(SanctionChangeAction action) {
        return switch (action) {
            case END_EARLY -> Material.SHEARS;
            case REDUCE_DURATION, REPLACE_EXPIRATION -> Material.CLOCK;
            case REVOKE -> Material.MILK_BUCKET;
            case FULL_OVERTURN, APPROVE_FULL_OVERTURN -> Material.TOTEM_OF_UNDYING;
            case REMOVE_ESCALATION_CONTRIBUTION -> Material.GRAY_DYE;
            case RESTORE_ESCALATION_CONTRIBUTION -> Material.LIME_DYE;
            case REQUEST_FULL_OVERTURN -> Material.WRITABLE_BOOK;
            case DENY_FULL_OVERTURN -> Material.BARRIER;
        };
    }

    static String actionName(SanctionChangeAction action) {
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

    private static String actionDescription(SanctionChangeAction action) {
        return switch (action) {
            case END_EARLY -> "End pending or active sanctions now while retaining the case.";
            case REDUCE_DURATION -> "Set an earlier expiration for active sanctions.";
            case REPLACE_EXPIRATION -> "Replace active sanction expiration with an authorized custom instant.";
            case REVOKE -> "Revoke active or already-applied sanctions while retaining history.";
            case FULL_OVERTURN -> "Overturn the complete case and remove its escalation contribution.";
            case REMOVE_ESCALATION_CONTRIBUTION -> "Keep the case but stop counting it in later ladders.";
            case RESTORE_ESCALATION_CONTRIBUTION -> "Restore this case's ladder contribution.";
            case REQUEST_FULL_OVERTURN -> "Submit the case for Admin or Founder overturn review.";
            case APPROVE_FULL_OVERTURN -> "Approve the open request and fully overturn the case.";
            case DENY_FULL_OVERTURN -> "Deny the open request without changing sanctions.";
        };
    }

    private static List<Component> wrap(String value) {
        String compact = value.replace('\r', ' ').replace('\n', ' ');
        List<Component> lines = new ArrayList<>();
        for (int start = 0; start < compact.length() && lines.size() < 8; start += 48) {
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
