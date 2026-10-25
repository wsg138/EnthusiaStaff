package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source contract: a pending reason-search prompt must not consume unrelated
 * chat after staff navigate elsewhere, even if target resolution fails.
 */
class PunishmentGuiSearchCaptureNavigationTest {
    @Test
    void newCommandAndPickerInvalidateSearchBeforeAsyncTargetResolution() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
        for (String marker : new String[] {
                "public void openTargetPicker(",
                "private PunishmentGuiTargetResolutionFence.Ticket beginTargetResolution(",
                "private void openTargetPickerPage(",
                "private void openState(",
                "private void closeWithoutResume("
        }) {
            int start = source.indexOf(marker);
            assertTrue(start >= 0, marker);
            String method = source.substring(start, Math.min(source.length(), start + 600));
            assertTrue(method.contains("searchCaptures.remove(viewer.getUniqueId())"),
                    "Abandoned search must be cleared on navigation: " + marker);
        }
    }
}
