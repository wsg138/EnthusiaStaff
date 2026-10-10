package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiReviewWriteFeedbackTest {
    @Test
    void lostAcknowledgementsAreTreatedAsPotentiallyCommitted() {
        for (String issue : new String[] {
                "central review timeout; retry backoff 5s",
                "central review network; retry backoff 5s",
                "central review unavailable; retry backoff 5s",
                "central review malformed; retry backoff 5s",
                "central review oversized; retry backoff 5s",
                "central review http; retry backoff 5s",
                "central review internal error; retry backoff 5s"
        }) {
            assertTrue(AiReviewWriteFeedback.outcomeUncertain(issue), issue);
            String message = AiReviewWriteFeedback.message(issue);
            assertTrue(message.contains("status UNKNOWN"), issue);
            assertTrue(message.contains("Check the central event before submitting another vote."), issue);
            assertFalse(message.contains("No correction was committed"), issue);
        }
        assertTrue(AiReviewWriteFeedback.outcomeUncertain(null));
    }

    @Test
    void KnownRejectionsAreNotPresentedAsSuccessfulWrites() {
        for (String issue : new String[] {
                "central review conflict",
                "active staff mode session or rank changed; no review write was sent",
                "central review backing off for 5s",
                "AI review work queue is full",
                "central review auth; retry backoff 5s"
        }) {
            assertFalse(AiReviewWriteFeedback.outcomeUncertain(issue), issue);
            assertTrue(AiReviewWriteFeedback.message(issue).startsWith("Correction was not confirmed:"), issue);
        }
    }

    @Test
    void GuiAndTextWriteCallbacksUseUncertainOutcomeFeedback() throws IOException {
        Path source = Path.of("src/main/java/net/enthusia/staff/paper/aireview");
        String command = Files.readString(source.resolve("AiReviewCommand.java"));
        String gui = Files.readString(source.resolve("AiReviewGuiController.java"));

        assertTrue(command.contains("send(player, AiReviewWriteFeedback.message(issue)"));
        assertTrue(gui.contains("message(viewer, AiReviewWriteFeedback.message(issue)"));
        assertTrue(gui.contains("if (AiReviewWriteFeedback.outcomeUncertain(issue))"));
        assertFalse(command.contains("No correction was committed"));
        assertFalse(gui.contains("No correction was committed"));
    }
}
