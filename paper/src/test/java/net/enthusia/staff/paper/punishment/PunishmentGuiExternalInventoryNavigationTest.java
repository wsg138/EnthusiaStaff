package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** An unrelated inventory open must supersede pending punishment navigation. */
class PunishmentGuiExternalInventoryNavigationTest {
    private static String controller() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
    }

    @Test
    void unrelatedInventoryOpenCancelsPendingHistoryPreparationTargetAndPrivateChat() throws Exception {
        String source = controller();
        assertTrue(source.contains("import org.bukkit.event.inventory.InventoryOpenEvent;"));
        int start = source.indexOf("public void onOpen(InventoryOpenEvent event)");
        assertTrue(start >= 0, "InventoryOpenEvent must be observed even when the previous inventory is not ours");
        String handler = source.substring(start, source.indexOf("\n    }", start) + 6);
        assertTrue(handler.contains("event.getPlayer() instanceof Player viewer"));
        assertTrue(handler.contains("noteCaptures.remove(viewerId)"),
                "Opening an unrelated inventory must stop intercepting staff's private note chat");
        assertTrue(handler.contains("searchCaptures.remove(viewerId)"),
                "Opening an unrelated inventory must stop intercepting staff's private search chat");
        assertTrue(handler.contains("historyFence.invalidate(viewerId)"),
                "A queued history result must not reopen over a newly opened chest");
        assertTrue(handler.contains("preparationFence.invalidate(viewerId)"),
                "A queued draft review must not reopen over a newly opened chest");
        assertTrue(handler.contains("targetResolutionFence.invalidate(viewerId)"),
                "A queued target lookup/picker must not reopen over a newly opened chest");
        assertTrue(source.substring(Math.max(0, start - 150), start)
                .contains("ignoreCancelled = true"),
                "A canceled inventory open must not silently abandon the current punishment operation");
    }

    @Test
    void inventorySwitchInvalidatesAllThreeIndependentNavigationTickets() {
        UUID viewer = UUID.fromString("44000000-0000-0000-0000-000000000001");
        PunishmentGuiHistoryFence history = new PunishmentGuiHistoryFence();
        PunishmentGuiHistoryFence preparation = new PunishmentGuiHistoryFence();
        PunishmentGuiTargetResolutionFence target = new PunishmentGuiTargetResolutionFence();
        Object sourceInventory = new Object();
        Object historyToken = history.begin(viewer);
        Object chatOrDraftToken = preparation.begin(viewer);
        var targetTicket = target.begin(viewer, sourceInventory);

        // InventoryOpenEvent must perform these invalidations before the
        // old worker/chat callback can consume a ticket on the entity thread.
        history.invalidate(viewer);
        preparation.invalidate(viewer);
        target.invalidate(viewer);

        assertFalse(history.consume(viewer, historyToken));
        assertFalse(preparation.consume(viewer, chatOrDraftToken));
        assertFalse(target.consume(viewer, targetTicket, sourceInventory));
    }
}
