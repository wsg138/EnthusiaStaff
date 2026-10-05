package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeDeveloperProfileWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"
    );

    @Test
    void developerKeepsNormalCollisionPickupAndDamageFlagsForTesting() throws IOException {
        String source = Files.readString(SOURCE).replace("\r\n", "\n");
        int start = source.indexOf("private void applyStaffState");
        int end = source.indexOf("private boolean protectedMode", start);
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate applyStaffState");
        }
        String method = source.substring(start, end);

        assertTrue(method.contains("boolean technicalTesting = rank == StaffRank.DEVELOPER"));
        assertTrue(method.contains("player.setInvulnerable(!technicalTesting)"));
        assertTrue(method.contains("player.setCollidable(technicalTesting)"));
        assertTrue(method.contains("player.setCanPickupItems(technicalTesting)"));
    }
}
