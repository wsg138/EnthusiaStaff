package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ReportReasonMenuTest {
    @Test void reasonIdsAreGroupedByTheirPolicyFamilyWithoutChangingStoredIds() {
        assertEquals("cheating", ReportReasonMenu.categoryFor("cheating.combat.killaura"));
        assertEquals("hate", ReportReasonMenu.categoryFor("hate.targeted-harassment"));
        assertEquals("reports", ReportReasonMenu.categoryFor("reports.false"));
        assertEquals("other", ReportReasonMenu.categoryFor("unspecified"));
        assertEquals("other", ReportReasonMenu.categoryFor(null));
    }

    @Test void currentPolicyFamiliesHaveDistinctRecognizableIcons() {
        List<String> families = List.of("cheating", "hate", "exploit", "safety", "spam",
                "account", "evasion", "complicity", "reputation", "reports", "content",
                "advertising", "politics", "mechanics", "market", "privacy", "identity",
                "dishonesty", "staff", "harassment", "language");
        var icons = families.stream().map(ReportReasonMenu::categoryIcon).toList();
        assertEquals(families.size(), new HashSet<>(icons).size());
        assertEquals(Material.DIAMOND_SWORD, ReportReasonMenu.categoryIcon("cheating"));
        assertEquals(Material.REDSTONE, ReportReasonMenu.categoryIcon("exploit"));
        assertEquals(Material.BOOKSHELF, ReportReasonMenu.categoryIcon("other"));
        assertNotEquals(ReportReasonMenu.categoryIcon("hate"), ReportReasonMenu.categoryIcon("harassment"));
    }
}
