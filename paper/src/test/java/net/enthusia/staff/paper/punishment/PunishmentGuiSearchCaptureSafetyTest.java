package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Prevent a server-lagged search timeout from restoring a GUI after its deadline. */
class PunishmentGuiSearchCaptureSafetyTest {
    private static String controller() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
    }

    @Test
    void searchCaptureExpiresAtExactOneMinuteBoundary() {
        Instant started = Instant.parse("2026-10-25T00:00:00Z");
        assertFalse(PunishmentGuiController.searchCaptureExpired(started, started));
        assertFalse(PunishmentGuiController.searchCaptureExpired(started, started.plusSeconds(59)));
        assertTrue(PunishmentGuiController.searchCaptureExpired(started, started.plusSeconds(60)));
        assertTrue(PunishmentGuiController.searchCaptureExpired(started, started.plusSeconds(61)));
    }

    @Test
    void lateSearchChatCannotRestoreExpiredOriginPage() throws Exception {
        String source = controller();
        String chat = source.substring(source.indexOf("public void onChat("),
                source.indexOf("public void onQuit("));
        String search = chat.substring(chat.indexOf("if (search != null)"),
                chat.indexOf("        event.setCancelled(true);", chat.indexOf("finishSearch(viewer, search, query)")));
        assertTrue(search.contains("event.setCancelled(true)"),
                "The expired private query must not leak into public chat");
        assertTrue(search.contains("searchCaptureExpired(search.startedAt(), clock.instant())"),
                "Do not wait for the 60-second scheduler callback when the server is lagged");
        assertTrue(search.contains("preparationFence.consume(viewer.getUniqueId(), search.navigationToken())"),
                "A late search expiry notice must still obey the original prompt navigation ticket");
        assertTrue(search.indexOf("searchCaptureExpired(search.startedAt(), clock.instant())")
                < search.indexOf("finishSearch(viewer, search, query)"),
                "An expired search must not enter the normal search presentation path");

        String finish = source.substring(source.indexOf("private void finishSearch("),
                source.indexOf("private static boolean validSearchText("));
        String expired = finish.substring(finish.indexOf("if (searchCaptureExpired("),
                finish.indexOf("String normalized ="));
        assertFalse(expired.contains("openState("),
                "Late entity callbacks must not restore a GUI after the search expired");
    }
}
