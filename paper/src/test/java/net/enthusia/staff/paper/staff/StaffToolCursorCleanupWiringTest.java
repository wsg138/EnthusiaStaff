package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffToolCursorCleanupWiringTest {
    private static final Path MANAGER = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java");

    @Test
    void modeRestoreClearsTaggedCursorWithoutDiscardingOrdinaryCursorItems() throws Exception {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        assertTrue(source.contains("if (isStaffTool(player.getItemOnCursor())) {\n"
                + "            player.setItemOnCursor(null);"));
        assertTrue(source.contains("private void removeStaffTools(Player player) {\n"
                + "        clearStaffToolCursor(player);"));
        assertTrue(source.contains("removeStaffTools(player);\n"
                + "            if (player.getGameMode() == GameMode.SPECTATOR)"));
    }

    @Test
    void layoutRefreshAndStaffProfileApplicationClearCursorBeforeClosingInventory() throws Exception {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        int profile = source.indexOf("private void applyStaffState(");
        int preference = source.indexOf("private void applyToolInventoryPreference(");
        assertTrue(profile >= 0 && preference >= 0);
        assertTrue(source.indexOf("clearStaffToolCursor(player);\n"
                + "            player.closeInventory();", profile) > profile);
        assertTrue(source.indexOf("clearStaffToolCursor(player);\n"
                + "            player.closeInventory();", preference) > preference);
    }
}
