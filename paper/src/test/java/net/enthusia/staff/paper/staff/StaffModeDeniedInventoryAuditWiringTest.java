package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeDeniedInventoryAuditWiringTest {
    private static final Path MANAGER = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java");

    @Test
    void blockedDropIsAuditedButStaffToolDropKeepsItsSeparateAudit() throws IOException {
        String source = Files.readString(MANAGER);
        String drop = between(source, "public void onDrop(", "public void onPickup(");
        assertTrue(drop.contains("\"staff-tool-drop-denied\""));
        assertTrue(drop.contains("\"item-drop-denied\""));
        assertTrue(drop.contains("\"item-drop\""));
        assertTrue(drop.indexOf("\"staff-tool-drop-denied\"") < drop.indexOf("\"item-drop-denied\""));
        assertTrue(drop.contains("event.setCancelled(true);\n            audit(player, rank, \"item-drop-denied\""));
    }

    @Test
    void blockedHandSwapIsAuditedWithoutChangingPermittedSwaps() throws IOException {
        String source = Files.readString(MANAGER);
        String swap = between(source, "public void onSwapHands(", "public void onInventoryClick(");
        assertTrue(swap.contains("\"inventory-swap-hands-denied\""));
        assertTrue(swap.contains("\"inventory-swap-hands\""));
        assertTrue(swap.indexOf("event.setCancelled(true);")
                < swap.indexOf("\"inventory-swap-hands-denied\""));
        assertTrue(swap.indexOf("\"inventory-swap-hands-denied\"")
                < swap.lastIndexOf("\"inventory-swap-hands\""));
        assertFalse(swap.contains("event.setCancelled(true);\n            return;\n        }\n        // Mod"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int until = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && until > from, start);
        return source.substring(from, until).replace("\r\n", "\n");
    }
}
