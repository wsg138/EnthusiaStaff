package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SanctionChangeReplyThreadContractTest {
    @Test
    void asynchronousSanctionResultsUsePlayerAwareResponseDispatcher() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/SanctionChangeCommand.java"
        )).replace("\r\n", "\n");
        int start = source.indexOf("private void send(CommandSender sender, String message)");
        int end = source.indexOf("@Override", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        assertTrue(method.contains("new CommandResponseDispatcher(plugin).send(sender, Component.text(message))"));
        assertFalse(method.contains("getGlobalRegionScheduler()"));
        assertFalse(method.contains("sender.sendMessage("));
    }

    @Test
    void sharedDispatcherSeparatesEntityAndConsoleScheduling() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/CommandResponseDispatcher.java"
        )).replace("\r\n", "\n");
        int player = source.indexOf("if (sender instanceof Player player)");
        int entity = source.indexOf("player.getScheduler().execute(", player);
        int console = source.indexOf("getGlobalRegionScheduler().execute(", entity);
        assertTrue(player >= 0 && entity > player && console > entity);
        assertTrue(source.contains("List.copyOf(messages)"));
    }
}
