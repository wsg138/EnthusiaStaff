package net.enthusia.staff.paper.punishment.policyv2;

import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class PolicyV2GuiRenderer {
    static final int CONTENT_START = 9;
    static final int PAGE_SIZE = 36;
    static final int BACK_SLOT = 47;
    static final int PRIMARY_SLOT = 49;
    static final int CLOSE_SLOT = 51;
    static final int NEXT_SLOT = 53;
    static final int PREVIOUS_SLOT = 45;
    static final int POLICY_GAP_QUESTION_SLOT = 22;

    Inventory render(PolicyV2GuiState state) {
        PolicyV2GuiHolder holder = new PolicyV2GuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(state));
        holder.attach(inventory);
        header(inventory, state);
        renderBody(inventory, state);
        return inventory;
    }

    private static void renderBody(Inventory inventory, PolicyV2GuiState state) {
        if (state instanceof PolicyV2GuiState.Categories categories) {
            categories(inventory, categories);
        } else if (state instanceof PolicyV2GuiState.Offenses offenses) {
            offenses(inventory, offenses);
        } else if (state instanceof PolicyV2GuiState.Questions questions) {
            questions(inventory, questions);
        } else if (state instanceof PolicyV2GuiState.Review review) {
            review(inventory, review);
        } else {
            result(inventory, (PolicyV2GuiState.Result) state);
        }
    }

    private static void categories(Inventory inventory, PolicyV2GuiState.Categories state) {
        for (int index = 0; index < state.categories().size(); index++) {
            PolicyV2Category category = state.categories().get(index);
            inventory.setItem(CONTENT_START + index, item(
                    category.material(),
                    category.title(),
                    category.color(),
                    List.of(
                            Component.text(category.description(), NamedTextColor.GRAY),
                            Component.text(
                                    category.reviewOnly() ? "Review-only safe fallback" : "Click to view exact conduct",
                                    NamedTextColor.YELLOW
                            )
                    )
            ));
        }
        close(inventory);
    }

    private static void offenses(Inventory inventory, PolicyV2GuiState.Offenses state) {
        int offset = state.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < state.offenses().size(); index++) {
            OffensePolicy offense = state.offenses().get(offset + index);
            inventory.setItem(CONTENT_START + index, item(
                    Material.PAPER,
                    offense.displayName(),
                    NamedTextColor.WHITE,
                    List.of(
                            Component.text(questionCount(offense), NamedTextColor.GRAY),
                            Component.text("Click to select this conduct", NamedTextColor.YELLOW)
                    )
            ));
        }
        if (state.offenses().isEmpty()) {
            inventory.setItem(22, item(
                    Material.GRAY_DYE,
                    "No configured conduct",
                    NamedTextColor.GRAY,
                    List.of(Component.text("Choose another category or use Policy Gap.", NamedTextColor.YELLOW))
            ));
        }
        pagination(inventory, state.page(), state.offenses().size());
        back(inventory, "Back · Categories");
        close(inventory);
    }

    private static String questionCount(OffensePolicy offense) {
        int count = offense.attributes().size();
        return count == 0 ? "No extra questions required"
                : count + (count == 1 ? " relevant question" : " relevant questions");
    }

    private static void questions(Inventory inventory, PolicyV2GuiState.Questions state) {
        if (state.draft().isPolicyGap()) {
            policyGapQuestion(inventory, state.draft());
        } else {
            configuredQuestions(inventory, state);
        }
        inventory.setItem(PRIMARY_SLOT, item(
                Material.LIME_CONCRETE,
                "Review Incident",
                NamedTextColor.GREEN,
                List.of(Component.text("Continue after all required answers are confirmed.", NamedTextColor.GRAY))
        ));
        back(inventory, state.draft().isPolicyGap() ? "Back · Categories" : "Back · Exact Conduct");
        close(inventory);
    }

    private static void configuredQuestions(Inventory inventory, PolicyV2GuiState.Questions state) {
        for (int index = 0; index < state.questions().size() && index < PAGE_SIZE; index++) {
            IncidentAttributeDefinition definition = state.questions().get(index);
            IncidentAttributeValue answer = state.draft().attributes().get(definition.id());
            inventory.setItem(CONTENT_START + index, questionItem(definition, answer));
        }
        if (state.questions().isEmpty()) {
            inventory.setItem(22, item(
                    Material.LIME_DYE,
                    "No Additional Questions",
                    NamedTextColor.GREEN,
                    List.of(Component.text("The selected conduct has no extra configured attributes.", NamedTextColor.GRAY))
            ));
        }
    }

    private static ItemStack questionItem(
            IncidentAttributeDefinition definition,
            IncidentAttributeValue answer
    ) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(definition.required() ? "Required" : "Optional", NamedTextColor.GRAY));
        lore.add(Component.text(questionHint(definition), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(
                answer == null ? "Not answered" : "Confirmed: " + displayValue(answer),
                answer == null ? NamedTextColor.YELLOW : NamedTextColor.GREEN
        ));
        lore.add(Component.text("Click to answer or edit", NamedTextColor.YELLOW));
        return item(
                answer == null ? Material.WRITABLE_BOOK : Material.KNOWLEDGE_BOOK,
                PolicyV2ReviewPresentation.humanize(definition.id()),
                NamedTextColor.AQUA,
                lore
        );
    }

    private static String questionHint(IncidentAttributeDefinition definition) {
        return switch (definition.kind()) {
            case BOOLEAN -> "Choose Yes or No";
            case INTEGER -> "Enter a number from " + definition.minimum() + " to " + definition.maximum();
            case ENUM -> "Choose: " + definition.allowedValues().stream()
                    .map(PolicyV2ReviewPresentation::humanize)
                    .collect(java.util.stream.Collectors.joining(", "));
            case TEXT -> "Enter a short factual answer";
        };
    }

    private static void policyGapQuestion(Inventory inventory, PolicyV2ManualDraft draft) {
        String status = draft.policyGapSummary().isPresent()
                ? "Confirmed: " + draft.policyGapSummary().orElseThrow()
                : "A factual description is required before review.";
        inventory.setItem(POLICY_GAP_QUESTION_SLOT, item(
                Material.MAP,
                "What happened?",
                NamedTextColor.YELLOW,
                List.of(
                        Component.text(status, NamedTextColor.GRAY),
                        Component.text("Ordinary staff cannot invent a punishment here.", NamedTextColor.RED),
                        Component.text("This path always requires Admin/Founder review.", NamedTextColor.GOLD),
                        Component.text("Click to enter or edit the description.", NamedTextColor.YELLOW)
                )
        ));
    }

    private static void review(Inventory inventory, PolicyV2GuiState.Review state) {
        String conduct = state.draft().isPolicyGap()
                ? state.draft().policyGapSummary().orElse("Description required")
                : state.conductLabel();
        inventory.setItem(20, item(
                Material.BOOK,
                "What Happened",
                NamedTextColor.AQUA,
                List.of(Component.text(conduct, NamedTextColor.GRAY))
        ));
        inventory.setItem(22, item(
                Material.WRITABLE_BOOK,
                "Confirmed Attributes",
                NamedTextColor.AQUA,
                draftAttributes(state.draft())
        ));
        inventory.setItem(24, item(
                Material.CLOCK,
                "Evaluate Current Policy",
                NamedTextColor.GOLD,
                List.of(
                        Component.text("History and policy are recalculated now.", NamedTextColor.GRAY),
                        Component.text("They are checked again before confirmation.", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(PRIMARY_SLOT, item(
                Material.LIME_CONCRETE,
                "Calculate Policy Result",
                NamedTextColor.GREEN,
                List.of(Component.text("No live punishment is applied.", NamedTextColor.GRAY))
        ));
        back(inventory, state.draft().isPolicyGap() ? "Back · Categories" : "Back · Questions");
        close(inventory);
    }

    private static List<Component> draftAttributes(PolicyV2ManualDraft draft) {
        if (draft.attributes().isEmpty()) {
            return List.of(Component.text("No additional facts selected.", NamedTextColor.GRAY));
        }
        return draft.attributes().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> (Component) Component.text(
                        PolicyV2ReviewPresentation.humanize(entry.getKey()) + ": " + displayValue(entry.getValue()),
                        NamedTextColor.GRAY
                ))
                .toList();
    }

    private static void result(Inventory inventory, PolicyV2GuiState.Result state) {
        PolicyV2ReviewPresentation view = state.presentation();
        inventory.setItem(10, detail(Material.BOOK, "What Happened", view.whatHappened()));
        inventory.setItem(12, lines(Material.WRITABLE_BOOK, "Confirmed Attributes", view.confirmedAttributes()));
        inventory.setItem(14, detail(Material.CLOCK, "Relevant History", view.historyExplanation()));
        inventory.setItem(16, lines(Material.CHEST, "Required Remedies", view.remedies()));
        inventory.setItem(28, lines(Material.ANVIL, "Sanction Recommendation", view.sanctionRecommendation()));
        inventory.setItem(30, detail(Material.COMPASS, "Why", view.why()));
        inventory.setItem(32, detail(Material.NAME_TAG, "Policy Version", view.policyVersion()));
        inventory.setItem(34, detail(Material.SHIELD, "Approval Route", view.approvalRoute()));
        inventory.setItem(40, detail(Material.SPYGLASS, "Authority Boundary", view.authorityNotice()));
        inventory.setItem(PRIMARY_SLOT, item(
                Material.LIME_CONCRETE,
                confirmationLabel(state.review()),
                NamedTextColor.GREEN,
                List.of(
                        Component.text(view.approvalRoute(), NamedTextColor.YELLOW),
                        Component.text("Records a shadow evaluation only.", NamedTextColor.GRAY)
                )
        ));
        back(inventory, "Back · Edit Incident");
        close(inventory);
    }

    private static String confirmationLabel(PolicyV2ManualReview review) {
        return switch (review.route()) {
            case DIRECT_CONFIRM -> "Record Shadow · Would Confirm";
            case APPROVAL_REQUIRED -> "Record Shadow · Would Request Approval";
            case ADMIN_FOUNDER_REVIEW -> "Record Shadow · Admin/Founder Review";
        };
    }

    private static ItemStack detail(Material material, String title, String detail) {
        return item(material, title, NamedTextColor.AQUA, List.of(Component.text(detail, NamedTextColor.GRAY)));
    }

    private static ItemStack lines(Material material, String title, List<String> values) {
        return item(material, title, NamedTextColor.AQUA,
                values.stream().map(value -> (Component) Component.text(value, NamedTextColor.GRAY)).toList());
    }

    private static void header(Inventory inventory, PolicyV2GuiState state) {
        inventory.setItem(4, item(
                Material.PLAYER_HEAD,
                state.targetName(),
                NamedTextColor.WHITE,
                List.of(
                        Component.text("Policy v2 manual workflow", NamedTextColor.AQUA),
                        Component.text("SHADOW — Policy v1 remains authoritative", NamedTextColor.RED)
                )
        ));
    }

    private static Component title(PolicyV2GuiState state) {
        String screen = state instanceof PolicyV2GuiState.Categories ? "Category"
                : state instanceof PolicyV2GuiState.Offenses ? "Exact Conduct"
                : state instanceof PolicyV2GuiState.Questions ? "Relevant Questions"
                : state instanceof PolicyV2GuiState.Review ? "Review"
                : "Policy Result";
        return Component.text("Punish v2 · " + screen, NamedTextColor.DARK_AQUA);
    }

    private static void pagination(Inventory inventory, int page, int total) {
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(
                    Material.ARROW, "Previous", NamedTextColor.AQUA, List.of()
            ));
        }
        if ((page + 1) * PAGE_SIZE < total) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next", NamedTextColor.AQUA, List.of()));
        }
    }

    private static void back(Inventory inventory, String label) {
        inventory.setItem(BACK_SLOT, item(Material.ARROW, label, NamedTextColor.AQUA, List.of()));
    }

    private static void close(Inventory inventory) {
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of()));
    }

    private static ItemStack item(
            Material material,
            String name,
            NamedTextColor color,
            List<Component> lore
    ) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(List.copyOf(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private static String displayValue(IncidentAttributeValue value) {
        if (value instanceof IncidentAttributeValue.BooleanValue booleanValue) {
            return booleanValue.value() ? "Yes" : "No";
        }
        if (value instanceof IncidentAttributeValue.IntegerValue integerValue) {
            return Long.toString(integerValue.value());
        }
        if (value instanceof IncidentAttributeValue.EnumValue enumValue) {
            return PolicyV2ReviewPresentation.humanize(enumValue.value());
        }
        return ((IncidentAttributeValue.TextValue) value).value();
    }
}
