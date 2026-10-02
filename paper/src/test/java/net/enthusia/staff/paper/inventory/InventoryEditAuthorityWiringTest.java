package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class InventoryEditAuthorityWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/inventory/InventoryCoordinator.java"
    );

    @Test
    void livePreparationRevalidatesBeforeDurablePrepare() throws IOException {
        String method = methodSource("private void prepareAndApplyLive(", "private InventoryPatch prepareAndClaimLivePatch(");

        assertOrdered(method, "editAuthority.current(viewer)", "prepareAndClaimLivePatch(");
    }

    @Test
    void offlinePreparationRevalidatesBeforeDurablePrepare() throws IOException {
        String method = methodSource("private void queueOfflineEdit(", "private void applyPendingOnLogin(");

        assertOrdered(method, "editAuthority.current(viewer)", "loaded.prepare(request");
    }

    private static String methodSource(String startMarker, String endMarker) throws IOException {
        String source = Files.readString(SOURCE).replace("\r\n", "\n");
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start, "expected production method boundaries");
        return source.substring(start, end);
    }

    private static void assertOrdered(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, "missing first operation: " + first);
        assertTrue(secondIndex > firstIndex, "expected " + first + " before " + second);
    }
}
