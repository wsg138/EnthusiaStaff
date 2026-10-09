package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentGuiSecurityRegressionTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"
    );
    private static final Path RENDERER = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiRenderer.java"
    );
    private static final Path OVERVIEW = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiOverview.java"
    );

    @Test
    void historyPageRechecksCurrentSensitiveHistoryPermission() throws IOException {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        String method = method(source, "private void openHistory(", "private void openUnavailableHistory(");

        assertTrue(method.contains(
                "viewer.hasPermission(HistoryCommand.SENSITIVE_PERMISSION)"
        ));
        assertTrue(method.contains("historyOptions(active, sensitiveHistory)"));
        assertTrue(method.contains("result,\n                        sensitiveHistory,"));
        assertFalse(method.contains("returnState.overview().sensitiveHistory()"));
    }

    @Test
    void overviewNeverCachesSensitiveHistoryPermissionOrEntries() throws IOException {
        String source = Files.readString(OVERVIEW);
        String controller = Files.readString(CONTROLLER).replace("\r\n", "\n");

        assertFalse(source.contains("sensitiveHistory"));
        assertFalse(source.contains("ModerationHistoryEntry"));
        assertFalse(source.contains("CaseReview"));
        assertFalse(source.contains("internalExplanation"));
        assertTrue(controller.contains("historyOptions(active, false)"));
        assertTrue(controller.contains("private static final int OVERVIEW_HISTORY_LIMIT = 1"));
    }

    @Test
    void historyResultRechecksSensitivePermissionBeforeRendering() throws IOException {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");
        String method = method(source, "private void openState(", "private Actor authorizedActor(");

        assertTrue(method.contains("history.sensitiveHistory()"));
        assertTrue(method.contains("!viewer.hasPermission(HistoryCommand.SENSITIVE_PERMISSION)"));
        assertTrue(method.contains("openHistory(viewer, history.returnState(), history.history().page())"));
    }

    @Test
    void resumedReviewStillShowsTheFullConfiguredLadder() throws IOException {
        String source = Files.readString(RENDERER);
        String method = method(source, "private void renderReview(", "private void renderHistory(");

        assertTrue(method.contains("catalog.find(draft.reasonId())"));
        assertTrue(method.contains(
                "renderLadder(inventory, policy.steps(), draft.expectation().stepOrdinal(), true)"
        ));
        assertTrue(source.contains("FROZEN DRAFT STEP"));
    }

    @Test
    void unavailableHistoryIsNotRenderedAsAnEmptySuccessfulTimeline() throws IOException {
        String source = Files.readString(RENDERER);
        String method = method(source, "private void renderHistory(", "private void renderHeader(");

        assertTrue(method.contains("if (!state.available())"));
        assertTrue(method.contains("Punishment history unavailable"));
        assertTrue(method.contains("else if (entries.isEmpty())"));
    }

    @Test
    void historyBackButtonDoesNotClaimItAlwaysReturnsToCategories() throws IOException {
        String source = Files.readString(RENDERER);
        String method = method(source, "private void renderHistory(", "private void renderHeader(");

        assertTrue(method.contains(
                "button(Material.ARROW, \"Back\", NamedTextColor.AQUA)"
        ));
        assertFalse(method.contains("Back · Categories"));
    }

    @Test
    void targetLabelsKeepFriendlyNamesWithoutRedundantUuidLore() throws IOException {
        String source = Files.readString(RENDERER);
        String targetItem = method(source, "private static ItemStack targetItem(",
                "private static ItemStack activeSanctionsItem(");
        String targetLabel = method(source, "private static String targetName(",
                "private static String formatInstant(");

        assertTrue(targetItem.contains("targetName(state.target())"));
        assertFalse(targetItem.contains("playerId().toString()"));
        assertTrue(targetLabel.contains("PlayerNames.label(target)"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate source method boundaries");
        }
        return source.substring(start, end);
    }
}
