package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentGuiTargetResolutionNavigationTest {
    private static String controller() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
    }

    @Test
    void onlyLatestLookupCanPresentAndTicketIsSingleUse() {
        PunishmentGuiTargetResolutionFence fence = new PunishmentGuiTargetResolutionFence();
        UUID viewer = UUID.fromString("43000000-0000-0000-0000-000000000001");
        Object inventory = new Object();
        var old = fence.begin(viewer, inventory);
        var latest = fence.begin(viewer, inventory);
        assertFalse(fence.consume(viewer, old, inventory));
        assertTrue(fence.consume(viewer, latest, inventory));
        assertFalse(fence.consume(viewer, latest, inventory));
    }

    @Test
    void switchingInventoryAndClosingCancelDelayedLookup() {
        PunishmentGuiTargetResolutionFence fence = new PunishmentGuiTargetResolutionFence();
        UUID viewer = UUID.fromString("43000000-0000-0000-0000-000000000002");
        Object original = new Object();
        var changed = fence.begin(viewer, original);
        assertFalse(fence.consume(viewer, changed, new Object()));
        assertFalse(fence.consume(viewer, changed, original));
        var closed = fence.begin(viewer, original);
        fence.invalidate(viewer);
        assertFalse(fence.consume(viewer, closed, original));
    }

    @Test
    void rejectedLookupCannotCancelNewerLookup() {
        PunishmentGuiTargetResolutionFence fence = new PunishmentGuiTargetResolutionFence();
        UUID viewer = UUID.fromString("43000000-0000-0000-0000-000000000003");
        Object original = new Object();
        var old = fence.begin(viewer, original);
        var latest = fence.begin(viewer, original);
        assertFalse(fence.cancel(viewer, old));
        assertTrue(fence.consume(viewer, latest, original));
    }

    @Test
    void queuedTargetPickerCannotReplaceNewerTypedCommandOrDifferentInventory() throws Exception {
        PunishmentGuiTargetResolutionFence fence = new PunishmentGuiTargetResolutionFence();
        UUID viewer = UUID.fromString("43000000-0000-0000-0000-000000000004");
        Object originalInventory = new Object();

        // The first /punish command schedules a picker; a second typed command
        // arrives before the first entity-scheduler callback executes.
        var oldPicker = fence.begin(viewer, originalInventory);
        var newerTypedCommand = fence.begin(viewer, originalInventory);
        assertFalse(fence.consume(viewer, oldPicker, originalInventory),
                "A queued picker must not supersede a newer typed command");
        assertTrue(fence.consume(viewer, newerTypedCommand, originalInventory));

        var switchedInventory = fence.begin(viewer, originalInventory);
        assertFalse(fence.consume(viewer, switchedInventory, new Object()),
                "Opening another inventory before the picker callback must cancel it");

        String source = controller();
        String picker = source.substring(source.indexOf("public void openTargetPicker("),
                source.indexOf("public void open(Player viewer,"));
        assertTrue(picker.contains("beginTargetResolution(viewer)"),
                "Picker request must capture navigation identity before scheduling its page");
        assertTrue(picker.contains("targetResolutionFence.consume("),
                "The queued picker must consume its original ticket on the entity thread");
        assertTrue(picker.indexOf("targetResolutionFence.consume(")
                        < picker.indexOf("openTargetPickerPage(viewer,"),
                "Stale queued picker must be rejected before it invalidates newer command work");
    }

    @Test
    void openAndResumeAreGuardedAtFinalEntityThreadPresentation() throws Exception {
        String source = controller();
        String open = source.substring(source.indexOf("public void open(Player viewer,"),
                source.indexOf("public void showPreparedDraft("));
        assertTrue(open.contains("beginTargetResolution(viewer)"));
        assertTrue(open.contains("presentResolvedState(viewer,"));
        String resume = open.substring(open.indexOf("public void resume("));
        assertEquals(4, resume.split("resolvedMessage\\(viewer, ticket,", -1).length - 1,
                "Every resume rejection should consume its ticket on the entity thread");
        String presentation = source.substring(source.indexOf("private void presentState("),
                source.indexOf("private Actor authorizedActor("));
        assertTrue(presentation.contains("targetResolutionFence.consume("));
        assertTrue(presentation.indexOf("targetResolutionFence.consume(")
                < presentation.indexOf("renderer.render(state, actor, guidedModeEnabled())"));
    }

    @Test
    void mergedTargetAndGuidedPreparationTokensShareSafeEntityThreadPresentation() throws Exception {
        String source = controller();
        String presentation = source.substring(source.indexOf("private void presentResolvedState("),
                source.indexOf("private Actor authorizedActor("));
        assertTrue(presentation.contains(
                "presentState(viewer, state, null, null, null, ticket)"),
                "Resolved target lookup must carry its inventory-bound ticket");
        assertTrue(presentation.contains(
                "presentState(viewer, state, historyToken, preparationToken, guidedOrigin, null)"),
                "Guided draft preparation must keep its original questionnaire separately");
        assertTrue(presentation.contains("PunishmentGuiTargetResolutionFence.Ticket targetTicket"));
        assertTrue(presentation.contains("PunishmentGuiState.Guided guidedOrigin"));
        int target = presentation.indexOf("targetResolutionFence.consume(viewer.getUniqueId(), targetTicket");
        int prepared = presentation.indexOf("preparationFence.consume(viewer.getUniqueId(), preparationToken)");
        int returnState = presentation.indexOf("guidedReturns.put(viewer.getUniqueId()");
        int open = presentation.indexOf("viewer.openInventory(inventory)");
        assertTrue(target > 0 && prepared > target && returnState > prepared && open > returnState,
                "Both navigation fences must reject stale work before saving the guided return or opening GUI");
    }

    @Test
    void navigationCloseAndQuitCancelPendingTargetLookup() throws Exception {
        String source = controller();
        for (String method : new String[] {
                "public void onClick(", "public void onDrag(", "public void onClose(",
                "public void onQuit(", "private void openState(",
                "private void openTargetPickerPage(", "private void closeWithoutResume("
        }) {
            int start = source.indexOf(method);
            assertTrue(start >= 0, method);
            assertTrue(source.substring(start, Math.min(source.length(), start + 1250))
                    .contains("targetResolutionFence.invalidate("), method);
        }
        assertTrue(source.contains("punishmentHolder(event.getInventory().getHolder(false))"),
                "Closing the target picker must cancel its pending player lookup");
    }
}
