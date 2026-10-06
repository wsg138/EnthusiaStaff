package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffInventoryPreferenceWiringTest {
    private static final Path MANAGER = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"
    );

    @Test
    void toggleIsDurableAndFencedFromStaffModeTransitions() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        int start = source.indexOf("public void toggleToolInventory");
        int end = source.indexOf("private void applyToolInventoryPreference", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("transitioning(playerId)"));
        assertTrue(method.contains("toolPreferenceWrites.add(playerId)"));
        assertTrue(method.contains("loaded.toolInventoryEnabled(playerId)"));
        assertTrue(method.contains("loaded.setToolInventoryEnabled(playerId, next, clock.instant())"));
        assertTrue(method.contains("toolPreferenceWrites.remove(playerId)"));
    }

    @Test
    void emptyInventoryEntryKeepsSnapshotSealedAndOffersOneClickToggle() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        int applyStart = source.indexOf("private void applyStaffState");
        int applyEnd = source.indexOf("private boolean protectedMode", applyStart);
        String apply = source.substring(applyStart, applyEnd);

        assertTrue(apply.contains("player.getInventory().clear()"));
        assertTrue(apply.contains("if (toolInventoryEnabled(playerId))"));
        assertTrue(source.contains("You have entered Staff Mode with an empty inventory."));
        assertTrue(source.contains("ClickEvent.runCommand(\"/staffinv\")"));
    }
}
