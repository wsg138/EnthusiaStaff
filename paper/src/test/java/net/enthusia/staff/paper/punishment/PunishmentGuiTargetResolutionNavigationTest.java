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
