package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Regression for abandoned private-note prompts, not a live-server simulation. */
class PunishmentGuiNoteCaptureSafetyTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
    }

    @Test
    void noteCaptureExpiresAfterOneMinuteIncludingExactBoundary() {
        Instant started = Instant.parse("2026-10-25T00:00:00Z");
        assertFalse(PunishmentGuiController.noteCaptureExpired(started, started));
        assertFalse(PunishmentGuiController.noteCaptureExpired(started, started.plusSeconds(59)));
        assertTrue(PunishmentGuiController.noteCaptureExpired(started, started.plusSeconds(60)));
        assertTrue(PunishmentGuiController.noteCaptureExpired(started, started.plusSeconds(61)));
    }

    @Test
    void notePromptExpiresByIdentityAndCannotInterceptMessagesAfterOtherNavigation() throws Exception {
        String controller = source();
        String note = controller.substring(controller.indexOf("if (slot == PunishmentGuiRenderer.NOTE_SLOT)"),
                controller.indexOf("if (slot == PunishmentGuiRenderer.CONFIRM_SLOT)", controller.indexOf("if (slot == PunishmentGuiRenderer.NOTE_SLOT)")));
        assertTrue(note.contains("new NoteCapture(state, clock.instant(), chatToken)"));
        assertTrue(note.contains("noteCaptures.remove(viewer.getUniqueId(), capture)"),
                "Timeout must only remove the original prompt, not a later one");
        assertTrue(note.contains("20L * 60L"), "Abandoned note prompts should auto-expire");

        String onChat = controller.substring(controller.indexOf("public void onChat("),
                controller.indexOf("public void onQuit("));
        assertTrue(onChat.contains("noteCaptureExpired(capture.startedAt(), clock.instant())"),
                "Server-lagged timeouts must not let expired notes edit old drafts");
        assertTrue(onChat.indexOf("noteCaptureExpired(capture.startedAt(), clock.instant())")
                < onChat.indexOf("reprepare(viewer, actor, capture.review(), note"));

        for (String method : new String[] {
                "private PunishmentGuiTargetResolutionFence.Ticket beginTargetResolution(",
                "private void openTargetPickerPage(", "private void openState("
        }) {
            int start = controller.indexOf(method);
            assertTrue(start >= 0, method);
            assertTrue(controller.substring(start, Math.min(controller.length(), start + 1750))
                    .contains("noteCaptures.remove(viewer.getUniqueId())"), method);
        }
        String finalPresentation = controller.substring(
                controller.lastIndexOf("private void presentState("),
                controller.indexOf("private Actor authorizedActor("));
        assertTrue(finalPresentation.contains("noteCaptures.remove(viewer.getUniqueId())"),
                "All accepted GUI presentations must clear an abandoned private note prompt");
    }
}
