package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentRequestReplayUiContractTest {
    @Test
    void resolvedRequestReplayNeverClaimsThatNoPunishmentWasApplied() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
        String handling = source.substring(
                source.indexOf("if (result instanceof PunishmentDraftConfirmation.Requested requested)"),
                source.indexOf("handleRejected(viewer, actor, state"));
        assertTrue(handling.contains("status == PunishmentRequestStatus.PENDING"));
        assertTrue(handling.contains("No punishment has been applied yet."));
        assertTrue(handling.contains("Check request and case history for its outcome."));
    }
}
