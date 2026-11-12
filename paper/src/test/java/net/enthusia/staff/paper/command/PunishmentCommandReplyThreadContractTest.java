package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentCommandReplyThreadContractTest {
    private static final Path COMMAND_ROOT =
            Path.of("src/main/java/net/enthusia/staff/paper/command");

    @Test
    void punishmentDraftRepliesNeverUseLegacyBukkitScheduler() throws Exception {
        String source = Files.readString(COMMAND_ROOT.resolve("PunishmentCommand.java"));
        String send = source.substring(source.indexOf("private void send(CommandSender sender, Component message)"),
                source.indexOf("private boolean permitsPunishmentDraft("));
        assertTrue(send.contains("new CommandResponseDispatcher(plugin).execute(sender,"));
        assertTrue(send.contains("sender.sendMessage(message)"));
        assertFalse(send.contains("getScheduler().runTask("));
    }

    @Test
    void punishmentRequestWorkerRepliesAreAlwaysDispatchedBySender() throws Exception {
        String source = Files.readString(COMMAND_ROOT.resolve("PunishmentRequestCommandHandler.java"));
        String helper = source.substring(source.indexOf("private void onSender(CommandSender sender, Runnable action)"),
                source.indexOf("private static void sendQueue("));
        assertEquals(5, source.split("onSender\\(sender, \\(", -1).length - 1);
        assertTrue(helper.contains("new CommandResponseDispatcher(plugin).execute(sender, action)"));
        assertFalse(source.contains("onMain("));
        assertFalse(source.contains("getScheduler().runTask("));
    }

    @Test
    void dispatcherRoutesPlayersToEntityOwnersAndConsoleToGlobalRegion() throws Exception {
        String source = Files.readString(COMMAND_ROOT.resolve("CommandResponseDispatcher.java"));
        assertTrue(source.contains("sender instanceof Player player"));
        assertTrue(source.contains("player.getScheduler().execute("));
        assertTrue(source.contains("getGlobalRegionScheduler().execute(plugin, delivery)"));
        assertTrue(source.contains("if (!scheduled)"));
    }
}
