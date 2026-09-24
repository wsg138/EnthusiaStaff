package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeRestorationWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"
    );

    @Test
    void savedStateRestoreIsAuthorizedAndClearsSpectatorTarget() throws IOException {
        String method = method("private boolean restoreSavedState", "private void completeRestoration");

        assertTrue(method.indexOf("profileApplications.add(playerId)") < method.indexOf("codec.restore"));
        assertTrue(method.indexOf("setSpectatorTarget(null)") < method.indexOf("codec.restore"));
        assertTrue(method.indexOf("codec.restore") < method.indexOf("profileApplications.remove(playerId)"));
    }

    @Test
    void rejectedVerificationSubmissionKeepsRecoveryFence() throws IOException {
        String method = method("private void restoreAndVerify", "private boolean restoreSavedState");
        int rejected = method.indexOf("if (!submit(() -> completeRestoration");
        int retained = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", rejected);

        assertTrue(rejected >= 0);
        assertTrue(retained > rejected);
    }

    @Test
    void verificationFailureKeepsRecoveryFenceUntilDurableClosure() throws IOException {
        String method = method("private void completeRestoration", "private void retainRecoveryAfterRuntimeExit");
        int verification = method.indexOf("loaded.completeExit");
        int catchRetention = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", verification);
        int mismatch = method.indexOf("if (!closed)", catchRetention);
        int mismatchRetention = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", mismatch);
        int successCleanup = method.indexOf("completeRuntimeExit(playerId)", mismatchRetention);

        assertTrue(verification >= 0);
        assertTrue(catchRetention > verification);
        assertTrue(mismatch > catchRetention);
        assertTrue(mismatchRetention > mismatch);
        assertTrue(successCleanup > mismatchRetention);
    }

    @Test
    void failedClosureDoesNotInvokeVerifiedExitListener() throws IOException {
        String retained = method("private void retainRecoveryAfterRuntimeExit", "private void completeRuntimeExit");
        String completed = method("private void completeRuntimeExit", "private void removeRuntimeState");

        assertTrue(retained.contains("recoveryGate.retry(playerId)"));
        assertTrue(!retained.contains("exitListener.accept"));
        assertTrue(completed.contains("recoveryGate.clear(playerId)"));
        assertTrue(completed.contains("exitListener.accept(playerId)"));
    }

    @Test
    void cleanExitSuccessMessageIsOnlyEmittedAfterVerificationPasses() throws IOException {
        String method = method("private void completeRestoration", "private void retainRecoveryAfterRuntimeExit");
        int mismatch = method.indexOf("if (!closed)");
        int mismatchReturn = method.indexOf("return;", mismatch);
        int cleanup = method.indexOf("completeRuntimeExit(playerId)", mismatchReturn);
        int success = method.indexOf("Staff mode exited; your exact saved state was restored and verified.");

        assertTrue(mismatch >= 0 && mismatchReturn > mismatch);
        assertTrue(cleanup > mismatchReturn);
        assertTrue(success > cleanup);
    }

    private static String method(String startMarker, String endMarker) throws IOException {
        String source = Files.readString(SOURCE);
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate staff-mode restoration method boundaries");
        }
        return source.substring(start, end);
    }
}
