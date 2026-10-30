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
    void chatCaptureTicketCannotBeResurrectedAfterNewNavigation() {
        PunishmentGuiHistoryFence preparation = new PunishmentGuiHistoryFence();
        UUID viewer = UUID.fromString("32000000-0000-0000-0000-000000000003");
        // Simulate AsyncChatEvent taking the old capture, then newer
        // navigation invalidating it before the queued entity callback runs.
        Object capturedAtPrompt = preparation.begin(viewer);
        preparation.invalidate(viewer);
        Object newNavigation = preparation.begin(viewer);
        assertFalse(preparation.consume(viewer, capturedAtPrompt));
        assertTrue(preparation.consume(viewer, newNavigation));
    }

    @Test
    void delayedPrivateChatRepliesCannotRestoreSupersededSearchOrDraft() throws Exception {
        String controller = controller();
        String chat = controller.substring(controller.indexOf("public void onChat("),
                controller.indexOf("public void onQuit("));
        // The async event may remove a capture, then lose a race to newer
        // entity-thread navigation BEFORE it can enqueue its callback. Minting
        // a token here would resurrect an already invalidated old capture.
        assertFalse(chat.contains("preparationFence.begin(viewer.getUniqueId())"),
                "Async chat must never mint a new token after navigation");
        assertTrue(chat.contains("preparationFence.consume(viewer.getUniqueId(), search.navigationToken())"),
                "Search must use the token minted with its original prompt");
        assertTrue(chat.contains("preparationFence.consume(viewer.getUniqueId(), capture.navigationToken())"),
                "Note must use the token minted with its original prompt");
        assertTrue(chat.indexOf("preparationFence.consume(viewer.getUniqueId(), search.navigationToken())")
                < chat.indexOf("finishSearch(viewer, search, query)"));
        assertTrue(chat.indexOf("preparationFence.consume(viewer.getUniqueId(), capture.navigationToken())")
                < chat.indexOf("reprepare(viewer, actor, capture.review(), note,"));

        String pickerEntry = controller.substring(controller.indexOf("public void openTargetPicker("),
                controller.indexOf("public void open(Player viewer,"));
        assertTrue(pickerEntry.contains("beginTargetResolution(viewer)"),
                "A picker command must invalidate chat through beginTargetResolution");
        assertTrue(pickerEntry.indexOf("beginTargetResolution(viewer)")
                < pickerEntry.indexOf("onEntity(viewer,"),
                "The picker must cancel old chat before its page is queued");
        String targetBegin = controller.substring(
                controller.indexOf("private PunishmentGuiTargetResolutionFence.Ticket beginTargetResolution("),
                controller.indexOf("private void resolveTarget("));
        assertTrue(targetBegin.contains("preparationFence.invalidate(viewerId)"),
                "The shared target navigation entry must synchronously invalidate old chat tickets");

        String searchPrompt = controller.substring(controller.indexOf("private void startSearch("),
                controller.indexOf("private void finishSearch("));
        assertTrue(searchPrompt.indexOf("viewer.closeInventory()")
                < searchPrompt.indexOf("preparationFence.begin(viewer.getUniqueId())"),
                "Close invalidates tickets, so the new search ticket must be minted after close");
        assertTrue(searchPrompt.contains("new SearchCapture(state, clock.instant(), chatToken)"));

        String notePrompt = controller.substring(controller.indexOf("if (slot == PunishmentGuiRenderer.NOTE_SLOT)"),
                controller.indexOf("if (slot == PunishmentGuiRenderer.CONFIRM_SLOT)",
                        controller.indexOf("if (slot == PunishmentGuiRenderer.NOTE_SLOT)")));
        assertTrue(notePrompt.indexOf("viewer.closeInventory()")
                < notePrompt.indexOf("preparationFence.begin(viewer.getUniqueId())"),
                "Close invalidates tickets, so the new note ticket must be minted after close");
        assertTrue(notePrompt.contains("new NoteCapture(state, clock.instant(), chatToken)"));
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
