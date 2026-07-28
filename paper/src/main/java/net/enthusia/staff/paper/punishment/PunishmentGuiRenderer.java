package net.enthusia.staff.paper.punishment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class PunishmentGuiRenderer {
    static final int CONTENT_SIZE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int BACK_SLOT = 48;
    static final int CONFIRM_SLOT = 49;
    static final int CLOSE_SLOT = 50;
    static final int NEXT_SLOT = 53;
    static final int VISIBILITY_SLOT = 28;
    static final int NOTE_SLOT = 30;

    private final PunishmentGuiCatalog catalog;

    PunishmentGuiRenderer(PunishmentGuiCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("punishment GUI catalog must be present");
        }
        this.catalog = catalog;
    }

    Inventory render(PunishmentGuiState state, Actor actor) {
        PunishmentGuiHolder holder = new PunishmentGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(state));
        holder.attach(inventory);
        fillFooter(inventory);
        switch (state) {
            case PunishmentGuiState.Categories categories -> renderCategories(inventory, categories, actor);
            case PunishmentGuiState.Reasons reasons -> renderReasons(inventory, reasons, actor);
            case PunishmentGuiState.Review review -> renderReview(inventory, review);
        }
        return inventory;
    }

    private void renderCategories(Inventory inventory, PunishmentGuiState.Categories state, Actor actor) {
        List<String> categories = catalog.categories(actor, state.commandName());
        int offset = state.page() * CONTENT_SIZE;
        for (int slot = 0; slot < CONTENT_SIZE && offset + slot < categories.size(); slot++) {
            String family = categories.get(offset + slot);
            int reasonCount = catalog.reasons(actor, state.commandName(), family).size();
            inventory.setItem(slot, item(
                    familyMaterial(family),
                    humanize(family),
                    List.of(
                            Component.text(reasonCount + " configured reason" + (reasonCount == 1 ? "" : "s"),
                                    NamedTextColor.GRAY),
                            Component.text("Click to review reasons", NamedTextColor.YELLOW)
                    )
            ));
        }
        pageControls(inventory, state.page(), categories.size());
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
    }

    private void renderReasons(Inventory inventory, PunishmentGuiState.Reasons state, Actor actor) {
        List<ReasonPolicy> reasons = catalog.reasons(actor, state.commandName(), state.family());
        int offset = state.page() * CONTENT_SIZE;
        for (int slot = 0; slot < CONTENT_SIZE && offset + slot < reasons.size(); slot++) {
            ReasonPolicy policy = reasons.get(offset + slot);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(policy.id(), NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Severity: " + policy.severity(), NamedTextColor.GRAY));
            lore.add(Component.text("Required rank: " + policy.requiredRank(), NamedTextColor.GRAY));
            if (!policy.examples().isEmpty()) {
                lore.add(Component.text("Examples:", NamedTextColor.GRAY));
                policy.examples().stream().limit(3)
                        .forEach(example -> lore.add(Component.text("• " + example, NamedTextColor.DARK_GRAY)));
            }
            lore.add(Component.text("Click to calculate the authoritative step", NamedTextColor.YELLOW));
            inventory.setItem(slot, item(reasonMaterial(policy), policy.publicReason(), lore));
        }
        pageControls(inventory, state.page(), reasons.size());
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Categories", List.of()));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
    }

    private void renderReview(Inventory inventory, PunishmentGuiState.Review state) {
        PunishmentDraft draft = state.draft();
        Optional<PunishmentAssessment> assessment = state.assessment();
        inventory.setItem(10, item(
                Material.PLAYER_HEAD,
                targetName(state.target()),
                List.of(
                        Component.text(state.target().playerId().toString(), NamedTextColor.DARK_GRAY),
                        Component.text("Platform: " + state.target().platform(), NamedTextColor.GRAY),
                        Component.text("Last seen: " + state.target().lastSeenAt(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(12, item(
                Material.WRITABLE_BOOK,
                "Reason",
                List.of(
                        Component.text(draft.reasonId(), NamedTextColor.WHITE),
                        Component.text("This exact reason ID will be audited", NamedTextColor.GRAY)
                )
        ));
        List<Component> recommendation = new ArrayList<>();
        recommendation.add(Component.text(
                "Step " + draft.expectation().stepOrdinal() + ": " + draft.expectation().stepLabel(),
                NamedTextColor.WHITE
        ));
        recommendation.add(Component.text(describe(draft.expectation().sanctions()), NamedTextColor.GOLD));
        recommendation.add(Component.text(
                "Policy version: " + draft.expectation().configurationVersion(), NamedTextColor.DARK_GRAY
        ));
        assessment.ifPresent(value -> {
            recommendation.add(Component.text(
                    "Raw " + value.escalation().rawOrdinal()
                            + " → effective " + value.escalation().effectiveOrdinal(),
                    NamedTextColor.GRAY
            ));
            recommendation.add(Component.text(
                    "Recency bonus: " + value.escalation().recencyBonus()
                            + "; related contributions: " + value.escalation().contributions().size(),
                    NamedTextColor.GRAY
            ));
        });
        inventory.setItem(14, item(Material.ANVIL, "Authoritative recommendation", recommendation));
        inventory.setItem(16, item(
                Material.CLOCK,
                "Draft safety",
                List.of(
                        Component.text("Expires: " + draft.expiresAt(), NamedTextColor.GRAY),
                        Component.text("Survives logout, restart, and server switch", NamedTextColor.GREEN),
                        Component.text("A changed ladder requires another review", NamedTextColor.YELLOW)
                )
        ));
        boolean publicCase = draft.visibility() == net.enthusia.staff.domain.casefile.CaseVisibility.PUBLIC;
        inventory.setItem(VISIBILITY_SLOT, item(
                publicCase ? Material.LIME_DYE : Material.GRAY_DYE,
                publicCase ? "Public punishment" : "Private punishment",
                List.of(
                        Component.text(publicCase
                                ? "Public is the default; click to explicitly make private"
                                : "Private was explicitly selected; click to make public", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(NOTE_SLOT, item(
                Material.NAME_TAG,
                "Internal explanation",
                noteLore(draft.internalExplanation())
        ));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back to reasons", List.of()));
        inventory.setItem(CONFIRM_SLOT, item(
                Material.LIME_CONCRETE,
                "Confirm punishment",
                List.of(
                        Component.text("This is the only click that may create the case", NamedTextColor.YELLOW),
                        Component.text("Current rank and policy are checked again server-side", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(CLOSE_SLOT, item(
                Material.BARRIER,
                "Save and close",
                List.of(Component.text("Resume within 24 hours with /punish resume <player>", NamedTextColor.GRAY))
        ));
    }

    private static void fillFooter(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = CONTENT_SIZE; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static void pageControls(Inventory inventory, int page, int entries) {
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "Previous page", List.of()));
        }
        if ((page + 1) * CONTENT_SIZE < entries) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next page", List.of()));
        }
    }

    private static Component title(PunishmentGuiState state) {
        String target = targetName(state.target());
        return switch (state) {
            case PunishmentGuiState.Categories ignored -> Component.text("Punish " + target + ": categories");
            case PunishmentGuiState.Reasons reasons -> Component.text(
                    "Punish " + target + ": " + humanize(reasons.family())
            );
            case PunishmentGuiState.Review ignored -> Component.text("Review punishment: " + target);
        };
    }

    private static Material familyMaterial(String family) {
        String root = family.split("\\.", 2)[0];
        return switch (root) {
            case "hate", "harassment" -> Material.REDSTONE;
            case "safety", "privacy" -> Material.SHIELD;
            case "spam", "language" -> Material.PAPER;
            case "content", "identity" -> Material.PAINTING;
            case "advertising", "market", "reputation" -> Material.EMERALD;
            case "account", "evasion" -> Material.ENDER_EYE;
            case "exploit", "mechanics", "cheating" -> Material.DIAMOND_PICKAXE;
            case "reports", "staff", "dishonesty", "complicity" -> Material.BOOK;
            default -> Material.MAP;
        };
    }

    private static Material reasonMaterial(ReasonPolicy policy) {
        if (policy.requiredRank() == net.enthusia.staff.domain.auth.StaffRank.ADMIN) {
            return Material.RED_CONCRETE;
        }
        if (policy.severity() >= 75) {
            return Material.ORANGE_CONCRETE;
        }
        if (policy.severity() >= 40) {
            return Material.YELLOW_CONCRETE;
        }
        return Material.LIGHT_BLUE_CONCRETE;
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static List<Component> noteLore(String note) {
        List<Component> lore = new ArrayList<>();
        if (note.isBlank()) {
            lore.add(Component.text("No internal explanation entered", NamedTextColor.GRAY));
        } else {
            String compact = note.replace('\n', ' ').replace('\r', ' ');
            for (int start = 0; start < compact.length() && lore.size() < 4; start += 48) {
                lore.add(Component.text(
                        compact.substring(start, Math.min(compact.length(), start + 48)),
                        NamedTextColor.GRAY
                ));
            }
        }
        lore.add(Component.text("Click, then type the explanation in chat", NamedTextColor.YELLOW));
        return List.copyOf(lore);
    }

    static String describe(List<SanctionSpec> sanctions) {
        return sanctions.stream().map(PunishmentGuiRenderer::describe)
                .reduce((left, right) -> left + " + " + right)
                .orElse("No sanction");
    }

    private static String describe(SanctionSpec sanction) {
        SanctionLength length = sanction.length();
        if (length.isInstant()) {
            return sanction.type().name();
        }
        if (length.isPermanent()) {
            return "permanent " + sanction.type().name();
        }
        return duration(length.temporary().orElseThrow()) + ' ' + sanction.type().name();
    }

    private static String duration(Duration duration) {
        if (duration.toDaysPart() > 0 && duration.toHoursPart() == 0
                && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toDays() + "d";
        }
        if (duration.toHours() > 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toHours() + "h";
        }
        return duration.toString();
    }

    private static String targetName(PlayerIdentity target) {
        return target.currentUsername().orElse(target.playerId().toString());
    }

    private static String humanize(String identifier) {
        String[] words = identifier.replace('.', ' ').replace('-', ' ').split(" +");
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
}
