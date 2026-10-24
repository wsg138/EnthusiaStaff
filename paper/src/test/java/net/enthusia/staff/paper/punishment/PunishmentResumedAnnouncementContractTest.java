package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentResumedAnnouncementContractTest {
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
