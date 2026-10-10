package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verify Folia player-state checks occur only inside the player scheduler task. */
class AiReviewNotificationThreadContractTest {
    @Test
    void notificationPermissionsAreCheckedOnPlayerScheduler() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/aireview/AiReviewSubsystem.java"
        )).replace("\r\n", "\n");
        int start = source.indexOf("private void notifyNewItems(");
        int end = source.indexOf("@EventHandler(priority = EventPriority.MONITOR)", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        int each = method.indexOf("for (Player player : plugin.getServer().getOnlinePlayers())");
        int schedule = method.indexOf("player.getScheduler().execute(plugin,", each);
        assertTrue(each >= 0 && schedule > each);
        String beforeSchedule = method.substring(each, schedule);
        assertFalse(beforeSchedule.contains("activeDuty(player)"));
        assertFalse(beforeSchedule.contains("AiReviewPermissions.queue(player)"));
        assertFalse(beforeSchedule.contains("player.hasPermission("));
        String onScheduler = method.substring(schedule);
        assertTrue(onScheduler.contains("activeDuty(player)"));
        assertTrue(onScheduler.contains("AiReviewPermissions.queue(player)"));
        assertTrue(onScheduler.contains("player.hasPermission(configuration.notificationPermission())"));
        assertTrue(onScheduler.contains("player.sendMessage("));
    }
}
