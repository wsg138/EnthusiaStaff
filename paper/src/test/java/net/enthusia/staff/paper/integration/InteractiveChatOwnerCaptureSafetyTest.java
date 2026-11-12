package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InteractiveChatOwnerCaptureSafetyTest {
    @Test
    void globalLookupDefersAllPlayerStateAccessToOwningCallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/integration/InteractiveChatStagingArtifactProvider.java"
        )).replace("\r\n", "\n");
        int renderStart = source.indexOf("public CompletionStage<List<RichChatArtifact>> render(");
        int ownerStart = source.indexOf("private void snapshotAndRender(", renderStart);
        int ownerEnd = source.indexOf("static void enqueueSnapshot(", ownerStart);
        assertTrue(renderStart >= 0 && ownerStart > renderStart && ownerEnd > ownerStart);

        String global = source.substring(renderStart, ownerStart);
        String owner = source.substring(ownerStart, ownerEnd);
        assertTrue(global.contains("player.getScheduler().execute("));
        assertFalse(global.contains("player.isOnline()"),
                "Global region must not inspect a player's live state");
        assertTrue(owner.contains("!player.isOnline()"));
        assertTrue(owner.indexOf("!player.isOnline()") < owner.indexOf("access.snapshot(player, request)"));
        assertTrue(owner.indexOf("shouldDrop(closed, clock.millis(), request)") <
                owner.indexOf("access.snapshot(player, request)"));
    }
}
