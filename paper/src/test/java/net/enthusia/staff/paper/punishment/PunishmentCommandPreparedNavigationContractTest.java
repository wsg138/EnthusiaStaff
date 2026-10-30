package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Cross-class guard against delayed typed /punish ... <reason> results reopening older GUI pages. */
class PunishmentCommandPreparedNavigationContractTest {
    private static String read(String filename) throws Exception {
        return Files.readString(Path.of("src/main/java/net/enthusia/staff/paper/" + filename));
    }

    @Test
    void typedPreparationCapturesNavigationBeforeWorkerAndPresentsOnlyThroughItsTicket() throws Exception {
        String command = read("command/PunishmentCommand.java");
        String prepare = command.substring(command.indexOf("private void prepare(CommandSender sender,"),
                command.indexOf("private static boolean isBanRoute("));
        assertTrue(prepare.contains("gui.beginCommandPreparation(player)"),
                "Typed command must capture the source inventory before dispatching async storage work");
        String stored = command.substring(command.indexOf("private void prepareStoredDraft("),
                command.indexOf("private static PreparePunishmentDraftRequest prepareRequest("));
        assertTrue(stored.contains("PunishmentGuiController.CommandPreparationTicket ticket"),
                "Worker must carry the original navigation ticket");
        assertTrue(stored.contains("gui.showPreparedDraft(player, target, route, actor, prepared, ticket)"),
                "Worker result must use a ticket-aware display path");

        String controller = read("punishment/PunishmentGuiController.java");
        String opening = controller.substring(controller.indexOf("public void showPreparedDraft("),
                controller.indexOf("@EventHandler(priority = EventPriority.HIGHEST)"));
        assertTrue(opening.contains("CommandPreparationTicket ticket"));
        assertTrue(opening.contains("showPrepared(viewer, target, commandName, actor, overview, prepared,"));
        String finalPresentation = controller.substring(controller.lastIndexOf("private void presentState("),
                controller.indexOf("private Actor authorizedActor("));
        assertTrue(finalPresentation.contains("targetResolutionFence.consume(viewer.getUniqueId(), targetTicket"),
                "A delayed typed-command result must not reopen after the source inventory changed");
    }

    @Test
    void typedCommandSupersedesPromptOriginBeforeAnyWorkerCanPresent() throws Exception {
        String command = read("command/PunishmentCommand.java");
        String prepare = command.substring(command.indexOf("private void prepare(CommandSender sender,"),
                command.indexOf("private static boolean isBanRoute("));
        assertTrue(prepare.indexOf("gui.beginCommandPreparation(player)")
                < prepare.indexOf("submit(sender,"),
                "The typed command must invalidate prompts BEFORE scheduling storage work");

        String controller = read("punishment/PunishmentGuiController.java");
        String ticket = controller.substring(controller.indexOf("private PunishmentGuiTargetResolutionFence.Ticket beginTargetResolution("),
                controller.indexOf("private void resolveTarget("));
        assertTrue(ticket.contains("noteCaptures.remove(viewer.getUniqueId())"),
                "A new typed punishment command cancels the previous private note prompt");
        assertTrue(ticket.contains("searchCaptures.remove(viewer.getUniqueId())"),
                "A new typed punishment command cancels the previous private search prompt");
        assertTrue(ticket.indexOf("preparationFence.invalidate(viewerId)")
                < ticket.indexOf("targetResolutionFence.begin(viewerId"),
                "The old prompt ticket must be invalidated before the new typed command ticket is issued");

        String chat = controller.substring(controller.indexOf("public void onChat("),
                controller.indexOf("public void onQuit("));
        assertTrue(chat.contains("preparationFence.consume(viewer.getUniqueId(), search.navigationToken())"),
                "A delayed search response must never mint a fresh token after command navigation");
        assertTrue(chat.contains("preparationFence.consume(viewer.getUniqueId(), capture.navigationToken())"),
                "A delayed note response must never mint a fresh token after command navigation");
        assertTrue(!chat.contains("preparationFence.begin(viewer.getUniqueId())"),
                "Capture identity must be fixed at prompt creation, not revived by late chat");
    }
}
