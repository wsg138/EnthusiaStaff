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
    void verificationFailureStillReleasesRuntimeTransitionAndCleanup() throws IOException {
        String method = method("private void completeRestoration", "private void completeRuntimeExit");

        int verification = method.indexOf("loaded.completeExit");
        int catchCleanup = method.indexOf("completeRuntimeExit(playerId)", verification);
        int normalCleanup = method.indexOf("completeRuntimeExit(playerId)", catchCleanup + 1);
        int mismatch = method.indexOf("if (!closed)", normalCleanup);

        assertTrue(verification >= 0);
        assertTrue(catchCleanup > verification);
        assertTrue(normalCleanup > catchCleanup);
        assertTrue(mismatch > normalCleanup);
    }

    @Test
    void cleanExitSuccessMessageIsOnlyEmittedAfterVerificationPasses() throws IOException {
        String method = method("private void completeRestoration", "private void completeRuntimeExit");

        int mismatch = method.indexOf("if (!closed)");
        int mismatchReturn = method.indexOf("return;", mismatch);
        int success = method.indexOf("Staff mode exited; your exact saved state was restored and verified.");

        assertTrue(mismatch >= 0 && mismatchReturn > mismatch);
        assertTrue(success > mismatchReturn);
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
