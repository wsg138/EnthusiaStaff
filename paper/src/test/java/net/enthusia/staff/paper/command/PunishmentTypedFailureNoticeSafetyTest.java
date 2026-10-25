package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A storage-worker exception can happen after a durable commit but before
 * the response. The handler must never assert that no case/request was saved.
 */
class PunishmentTypedFailureNoticeSafetyTest {
    @Test
    void directPunishmentWorkerExceptionsDoNotAssertUncommittedResults() throws Exception {
        String command = source("PunishmentCommand.java");
        String handler = command.substring(command.indexOf("private void runOperation("),
                command.indexOf("private void sendPrepared("));
        assertFalse(handler.contains("no punishment or request was created"),
                "A missing acknowledgement does not prove that the database rolled back");
        assertTrue(handler.contains("PunishmentOutcomeUnknownNotice.directPunishment()"));
        assertTrue(PunishmentOutcomeUnknownNotice.directPunishment()
                .contains("may have been saved"));
        assertTrue(PunishmentOutcomeUnknownNotice.directPunishment()
                .contains("before retrying"));
    }

    @Test
    void requestApprovalAndDenialWorkerExceptionsDoNotClaimNoDecision() throws Exception {
        String requests = source("PunishmentRequestCommandHandler.java");
        String handler = requests.substring(requests.indexOf("private void runOperation("),
                requests.indexOf("private void onSenderThread("));
        assertFalse(handler.contains("no decision was made"),
                "Acquired request may already be approved/denied when storage acknowledgement fails");
        assertTrue(handler.contains("PunishmentOutcomeUnknownNotice.requestDecision()"));
        assertTrue(PunishmentOutcomeUnknownNotice.requestDecision()
                .contains("may have been committed"));
        assertTrue(PunishmentOutcomeUnknownNotice.requestDecision()
                .contains("before retrying"));
    }

    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/net/enthusia/staff/paper/command/" + name));
    }
}
