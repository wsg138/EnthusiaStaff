package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentResumedAnnouncementContractTest {
    @Test
    void committedCaseWithFailedDraftCleanupStillAnnouncesExactlyOnce() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
        String cleanup = source.substring(source.indexOf(
                "} catch (PunishmentDraftCleanupException exception) {"), source.indexOf(
                "} catch (PunishmentRequestDraftCleanupException exception) {"));
        assertTrue(cleanup.contains("!exception.accepted().replayed()"),
                "A newly committed public case must be announced even if draft cleanup throws, "
                        + "but an already-committed replay must not broadcast again");
        assertTrue(cleanup.contains("announceNewlyCommitted(viewer, state)"),
                "The durable-commit exception must share the normal announcement path");

        String handler = source.substring(source.indexOf("private void handleConfirmation("),
                source.indexOf("private PunishmentGuiState.Guided matchingGuidedReturn("));
        assertTrue(handler.contains("announceNewlyCommitted(viewer, state)"),
                "Normal commits and cleanup-failed commits must use the same announcement logic");
        assertTrue(handler.contains("publicReasonForConfirmedDraft("),
                "The common path must retain the exact policy-version public reason");
        assertTrue(handler.contains("state.draft().expectation().sanctions()"),
                "The common path must announce the persisted reviewed sanction");
    }

    @Test
    void resumedPublicReviewPublishesItsCommittedSanctionWithoutDuplicatingReplay() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
        String handler = source.substring(source.indexOf("private void handleConfirmation("),
                source.indexOf("private PunishmentGuiState.Guided matchingGuidedReturn("));
        assertTrue(handler.contains("if (!applied.accepted().replayed())"),
                "Only a newly committed punishment may announce");
        assertTrue(handler.contains("publicReasonForConfirmedDraft("),
                "A resumed draft has no transient PunishmentAssessment and needs its persisted reason");
        assertTrue(handler.contains("state.draft().expectation().sanctions()"),
                "A resumed draft must announce its exact saved sanction rather than recalculate it");
    }
}
