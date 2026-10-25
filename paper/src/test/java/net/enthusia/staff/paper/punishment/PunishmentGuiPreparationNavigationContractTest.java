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
    void delayedGuidedCalculationRequiresTokenAndCannotSaveWrongQuestionnaireReturn() throws Exception {
        String controller = controller();
        String guided = controller.substring(controller.indexOf("private void prepareGuided("),
                controller.indexOf("private static String guidedExplanation("));
        assertTrue(guided.contains("preparationFence.begin(viewer.getUniqueId())"),
                "Guided requests must have the same single-use navigation fence as expert drafts");
        assertTrue(guided.contains("evaluateGuided(viewer, actor, state, finding, token)"));
        assertTrue(guided.contains("preparationFence.consume(viewer.getUniqueId(), token)"),
                "Queue admission failures must clear the outstanding guided request");
        assertTrue(guided.contains("evaluated, token, state"),
                "Guided result presentation must carry the exact questionnaire origin");
        String edit = controller.substring(controller.indexOf("private void reprepare("),
                controller.indexOf("private void showPrepared("));
        assertTrue(edit.contains("evaluation, token, originalAnswers"),
                "Edited guided drafts must also restore answers only after accepted presentation");
        assertFalse(edit.contains("guidedReturns.put("),
                "Async draft edits must not overwrite a newer questionnaire return state");
        assertFalse(guided.contains("guidedReturns.put("),
                "Workers must not publish a return page for stale or abandoned requests");

        String presentation = controller.substring(controller.indexOf("private void presentState("),
                controller.indexOf("private Actor authorizedActor("));
        int accept = presentation.indexOf("preparationFence.consume(viewer.getUniqueId(), preparationToken)");
        int remember = presentation.indexOf("guidedReturns.put(");
        int open = presentation.indexOf("viewer.openInventory(inventory)");
        assertTrue(accept >= 0 && remember > accept && open > remember,
                "Guided origin must be updated only after the navigation token was accepted");
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
