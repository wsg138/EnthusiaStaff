package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffCombatProtectionDeveloperWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffCombatProtectionListener.java"
    );

    @Test
    void onDutyDeveloperSkipsAutomaticCombatUntagProtection() throws IOException {
        String source = Files.readString(SOURCE).replace("\r\n", "\n");
        int start = source.indexOf("private void scheduleProtection");
        int end = source.indexOf("private void reconcile", start);
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate scheduleProtection");
        }
        String method = source.substring(start, end);

        assertTrue(method.contains("staffMode.active(player.getUniqueId())"));
        assertTrue(method.contains("staffMode.dutyTier(player) == StaffDutyTier.DEVELOPER"));
        assertTrue(method.indexOf("StaffDutyTier.DEVELOPER")
                < method.indexOf("player.getScheduler().execute"));
    }

    @Test
    void developerIsRecheckedBeforeDelayedUntagRuns() throws IOException {
        String source = Files.readString(SOURCE).replace("\r\n", "\n");
        int start = source.indexOf("private void reconcile");
        int end = source.indexOf("private boolean protectedState", start);
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate reconcile");
        }
        String method = source.substring(start, end);

        assertTrue(method.contains("staffMode.active(playerId)"));
        assertTrue(method.contains("staffMode.dutyTier(player) == StaffDutyTier.DEVELOPER"));
        assertTrue(method.indexOf("StaffDutyTier.DEVELOPER")
                < method.indexOf("combat.status(player)"));
    }
}
