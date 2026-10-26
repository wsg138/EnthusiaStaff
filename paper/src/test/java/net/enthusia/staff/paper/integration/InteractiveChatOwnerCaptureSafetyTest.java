package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InteractiveChatOwnerCaptureSafetyTest {
    @Test
    void globalLookupNeverReadsPlayerOnlineStateBeforeOwnerCallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/integration/InteractiveChatStagingArtifactProvider.java"));
        String global = source.substring(source.indexOf("public CompletionStage<List<RichChatArtifact>> render("),
                source.indexOf("private void snapshotAndRender("));
        String owner = source.substring(source.indexOf("private void snapshotAndRender("),
                source.indexOf("private void ", source.indexOf("private void snapshotAndRender(") + 1));
        assertTrue(global.contains("player.getScheduler().execute("));
        assertFalse(global.contains("player == null || !player.isOnline()"),
                "Global region must not inspect player online state");
        assertFalse(global.contains("player.isOnline()"),
                "No player state checks before scheduling on owner");
        assertTrue(owner.contains("!player.isOnline()"));
        assertTrue(owner.indexOf("!player.isOnline()") < owner.indexOf("access.snapshot(player, request)"));
    }
}
