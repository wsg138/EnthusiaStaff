package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Any player-entered slash command supersedes delayed punishment UI and chat capture. */
class PunishmentGuiExternalCommandNavigationTest {
    private static String controller() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
    }

    @Test
    void typedSlashCommandCancelsAllPendingPunishmentNavigationAndChatCaptures() throws Exception {
        String source = controller();
        assertTrue(source.contains("import org.bukkit.event.player.PlayerCommandPreprocessEvent;"));
        int start = source.indexOf("public void onCommand(PlayerCommandPreprocessEvent event)");
        assertTrue(start >= 0, "All typed slash commands must supersede pending punishment navigation");
        String handler = source.substring(start, source.indexOf("\n    }", start) + 6);
        assertTrue(handler.contains("event.getPlayer().getUniqueId()"));
        assertTrue(handler.contains("noteCaptures.remove(viewerId)"),
                "A staff member running another command must not leave a private note prompt active");
        assertTrue(handler.contains("searchCaptures.remove(viewerId)"),
                "A staff member running another command must not leave a private search prompt active");
        assertTrue(handler.contains("historyFence.invalidate(viewerId)"),
                "A delayed history lookup must not replace the next command's inventory");
        assertTrue(handler.contains("preparationFence.invalidate(viewerId)"),
                "A queued note/search callback or draft preparation must not survive a newer command");
        assertTrue(handler.contains("targetResolutionFence.invalidate(viewerId)"),
                "A queued target picker/lookup must not reopen after a newer command");
        assertTrue(source.substring(Math.max(0, start - 120), start)
                .contains("EventPriority.MONITOR"),
                "Observe the command without modifying or cancelling another plugin's command");
    }

    @Test
    void commandAfterChatEventAlreadyConsumedPromptStillCancelsItsQueuedCallback() {
        UUID viewer = UUID.fromString("45000000-0000-0000-0000-000000000001");
        PunishmentGuiHistoryFence preparation = new PunishmentGuiHistoryFence();
        Object originalPrompt = preparation.begin(viewer);
        // AsyncChatEvent may have removed the capture but queued its entity-thread callback.
        // Command handling must invalidate the ticket even if no capture is still in the map.
        preparation.invalidate(viewer);
        assertFalse(preparation.consume(viewer, originalPrompt));
    }

    @Test
    void nextPunishmentCommandCanMintFreshTicketsAfterCommandInvalidation() {
        UUID viewer = UUID.fromString("45000000-0000-0000-0000-000000000002");
        PunishmentGuiTargetResolutionFence targets = new PunishmentGuiTargetResolutionFence();
        Object sourceInventory = new Object();
        var stale = targets.begin(viewer, sourceInventory);
        targets.invalidate(viewer);
        var fresh = targets.begin(viewer, sourceInventory);
        assertFalse(targets.consume(viewer, stale, sourceInventory));
        assertTrue(targets.consume(viewer, fresh, sourceInventory));
    }
}
