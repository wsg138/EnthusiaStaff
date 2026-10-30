package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentGuiHistoryNavigationContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
    }

    @Test
    void allHistoryResultsUseSingleUseTokenOnEntityThread() throws Exception {
        String controller = source();
        String query = controller.substring(controller.indexOf("private void openHistory("),
                controller.indexOf("private void prepare("));
        assertTrue(query.contains("Object token = historyFence.begin(viewer.getUniqueId())"));
        assertTrue(query.contains("openHistoryState(viewer, new PunishmentGuiState.History("));
        assertTrue(query.contains("), token);"));
        assertTrue(query.contains("openUnavailableHistory(viewer, actor, returnState, sensitiveHistory, token)"));
        assertTrue(query.contains("if (!submitted)"));
        String denial = controller.substring(controller.indexOf("private boolean historyTargetAllowed("),
                controller.indexOf("private void openUnavailableHistory("));
        assertTrue(denial.contains("onEntity(viewer, () -> {"));
        assertTrue(denial.contains("historyFence.consume(viewer.getUniqueId(), token)"),
                "Hierarchy denial messages must not outlive their navigation token");
        String render = controller.substring(controller.indexOf("private void presentState("),
                controller.indexOf("private Actor authorizedActor("));
        assertTrue(render.contains("historyFence.consume(viewer.getUniqueId(), historyToken)"));
        assertTrue(render.indexOf("historyFence.consume(viewer.getUniqueId(), historyToken)")
                < render.indexOf("renderer.render(state, actor, guidedModeEnabled())"));
    }

    @Test
    void leavingOrInteractingWithSourceGuiInvalidatesPendingHistory() throws Exception {
        String controller = source();
        String click = controller.substring(controller.indexOf("public void onClick("),
                controller.indexOf("private static boolean punishmentHolder("));
        String close = controller.substring(controller.indexOf("public void onClose("),
                controller.indexOf("public void onChat("));
        String quit = controller.substring(controller.indexOf("public void onQuit("),
                controller.indexOf("private void targetPickerClick("));
        String normalOpen = controller.substring(controller.indexOf("private void openState("),
                controller.indexOf("private void openHistoryState("));
        assertTrue(click.contains("historyFence.invalidate(viewer.getUniqueId())"));
        assertTrue(close.contains("historyFence.invalidate(viewer.getUniqueId())"));
        assertTrue(quit.contains("historyFence.invalidate(viewerId)"));
        assertTrue(normalOpen.contains("historyFence.invalidate(viewer.getUniqueId())"));
        assertTrue(controller.contains("historyFence.invalidate(event.getWhoClicked().getUniqueId())"));
        String closing = controller.substring(controller.indexOf("private void closeWithoutResume("),
                controller.indexOf("private void finish("));
        assertTrue(closing.contains("historyFence.invalidate(viewer.getUniqueId())"));
    }
}
