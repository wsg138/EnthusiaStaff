package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SelfPlayerInfoSafetyWiringTest {
    @Test
    void vanishedPresentationDoesNotUnlistTheActorFromTheirOwnClient() throws IOException {
        String presentation = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffStatePresentation.java"
        ));

        assertFalse(presentation.contains("player.unlistPlayer(player)"));
        assertFalse(presentation.contains("unlistSelf(player)"));
    }

    @Test
    void audienceReconciliationAlwaysKeepsTheViewerOwnEntryListed() throws IOException {
        String vanish = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java"
        ));

        assertTrue(vanish.contains("viewer.getUniqueId().equals(target.getUniqueId())"));
        assertTrue(vanish.contains("viewer.listPlayer(target)"));
    }
}
