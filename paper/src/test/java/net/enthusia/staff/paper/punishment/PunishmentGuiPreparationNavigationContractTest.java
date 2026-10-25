package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentGuiPreparationNavigationContractTest {
    private static String controller() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
    }

    @Test
    void newerPreparationWinsAndSingleUseTokenCannotReopenTwice() {
        PunishmentGuiHistoryFence preparation = new PunishmentGuiHistoryFence();
        UUID viewer = UUID.fromString("32000000-0000-0000-0000-000000000001");
        Object first = preparation.begin(viewer);
        Object second = preparation.begin(viewer);
        assertFalse(preparation.consume(viewer, first));
        assertTrue(preparation.consume(viewer, second));
        assertFalse(preparation.consume(viewer, second));
        Object afterClose = preparation.begin(viewer);
        preparation.invalidate(viewer);
        assertFalse(preparation.consume(viewer, afterClose));
    }

    @Test
    void historyAndPreparationTokensAreIndependentForTheSameViewer() {
        UUID viewer = UUID.fromString("32000000-0000-0000-0000-000000000002");
        PunishmentGuiHistoryFence history = new PunishmentGuiHistoryFence();
        PunishmentGuiHistoryFence preparation = new PunishmentGuiHistoryFence();
        Object pendingHistory = history.begin(viewer);
        Object pendingPreparation = preparation.begin(viewer);
        preparation.invalidate(viewer);
        assertFalse(preparation.consume(viewer, pendingPreparation));
        assertTrue(history.consume(viewer, pendingHistory));
    }

    @Test
    void asynchronousExpertDraftAndReviewEditsHaveSingleUseNavigationTokens() throws Exception {
        String controller = controller();
        String prepare = controller.substring(controller.indexOf("private void prepare("),
                controller.indexOf("private boolean guidedModeEnabled()"));
        String reprepare = controller.substring(controller.indexOf("private void reprepare("),
                controller.indexOf("private void showPrepared("));
        assertTrue(prepare.contains("preparationFence.begin(viewer.getUniqueId())"));
        assertTrue(reprepare.contains("preparationFence.begin(viewer.getUniqueId())"));
        assertTrue(prepare.contains("evaluation, token"));
        assertTrue(reprepare.contains("evaluation, token"));
    }

    @Test
    void delayedPreparedInventoryOnlyOpensIfTokenIsConsumedOnEntityThread() throws Exception {
        String controller = controller();
        String presentation = controller.substring(controller.indexOf("private void presentState("),
                controller.indexOf("private Actor authorizedActor("));
        assertTrue(presentation.contains("preparationFence.consume(viewer.getUniqueId(), preparationToken)"));
        assertTrue(presentation.indexOf("preparationFence.consume(viewer.getUniqueId(), preparationToken)")
                < presentation.indexOf("renderer.render(state, actor, guidedModeEnabled())"));
    }

    @Test
    void closeClickDragQuitAndNewNavigationInvalidatePendingDraftPresentation() throws Exception {
        String controller = controller();
        for (String method : new String[] {
                "public void onClick(", "public void onDrag(", "public void onClose(",
                "public void onQuit(", "private void openState(",
                "private void openTargetPickerPage(", "private void closeWithoutResume("
        }) {
            int start = controller.indexOf(method);
            assertTrue(start >= 0, method);
            assertTrue(controller.substring(start, Math.min(controller.length(), start + 1100))
                    .contains("preparationFence.invalidate("), method);
        }
    }
}
