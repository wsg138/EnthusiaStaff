package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ReportReasonMenuTest {
    @Test void iconChoicesUseDistinctReportCategories() {
        assertEquals(Material.DIAMOND_SWORD, ReportReasonMenu.icon("combat.cheating"));
        assertEquals(Material.WRITABLE_BOOK, ReportReasonMenu.icon("chat.spam"));
        assertEquals(Material.TNT, ReportReasonMenu.icon("griefing.stealing"));
        assertEquals(Material.REDSTONE, ReportReasonMenu.icon("exploit.duplication"));
        assertEquals(Material.PAPER, ReportReasonMenu.icon("other"));
    }
}
