package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeRestorationWiringTest {
    private static final String CODEC_RESTORE = "codec.restore";
    private static final String COMPLETE_RESTORATION = "private void completeRestoration";
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"
    );

    @Test
    void savedStateRestoreChecksBackendOwnershipBeforeMutation() throws IOException {
        String method = method("private boolean restoreSavedState", COMPLETE_RESTORATION);

        int ownership = method.indexOf("serverId.equals(session.serverId())");
        int removeTools = method.indexOf("removeStaffTools(player)");
        int restore = method.indexOf(CODEC_RESTORE);

        assertTrue(ownership >= 0);
        assertTrue(removeTools > ownership);
        assertTrue(restore > ownership);
    }

    @Test
    void savedStateRestoreIsAuthorizedAndClearsSpectatorTarget() throws IOException {
        String method = method("private boolean restoreSavedState", COMPLETE_RESTORATION);

        assertTrue(method.indexOf("profileApplications.add(playerId)") < method.indexOf(CODEC_RESTORE));
        assertTrue(method.indexOf("setSpectatorTarget(null)") < method.indexOf(CODEC_RESTORE));
        assertTrue(method.indexOf(CODEC_RESTORE) < method.indexOf("profileApplications.remove(playerId)"));
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
        String method = method(COMPLETE_RESTORATION, "private void retainRecoveryAfterRuntimeExit");
        int verification = method.indexOf("loaded.completeExit");
        int catchRetention = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", verification);
        int mismatch = method.indexOf("if (!closed)", catchRetention);
        int mismatchRetention = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", mismatch);
        int mismatchReturn = method.indexOf("return;", mismatchRetention);
        int successCleanup = method.indexOf("finishVerifiedExit(playerId, session, restorationAttempt)", mismatchReturn);

        assertTrue(verification >= 0);
        assertTrue(catchRetention > verification);
        assertTrue(mismatch > catchRetention);
        assertTrue(mismatchRetention > mismatch);
        assertTrue(mismatchReturn > mismatchRetention);
        assertTrue(successCleanup > mismatchReturn);
    }

    @Test
    void failedClosureRetainsRecoveryAndInvokesRuntimeExitCleanup() throws IOException {
        String retained = method("private void retainRecoveryAfterRuntimeExit", "private void completeRuntimeExit");
        String completed = method("private void completeRuntimeExit", "private void removeRuntimeState");

        assertTrue(retained.contains("recoveryGate.retry(playerId)"));
        assertTrue(retained.contains("removeRuntimeState(playerId)"));
        assertTrue(retained.contains("exitListener.accept(playerId)"));
        assertTrue(completed.contains("recoveryGate.clear(playerId)"));
        assertTrue(completed.contains("exitListener.accept(playerId)"));
    }

    @Test
    void successfulBackendHandoffPreservesVanishWithoutGrantingAuthority() throws IOException {
        String completion = method("private void completeBackendHandoff", "@EventHandler(priority = EventPriority.MONITOR)");
        String activeMethod = method("public boolean active", "public boolean authorityActive");
        String authorityMethod = method("public boolean authorityActive", "public CombatStatusAdapter combat");

        assertTrue(completion.contains("handoffGaps.add(playerId)"));
        assertTrue(!completion.contains("exitListener.accept(playerId)"));
        assertTrue(activeMethod.contains("handoffGaps.contains(playerId)"));
        assertTrue(!authorityMethod.contains("handoffGaps.contains(playerId)"));
    }

    @Test
    void destinationHandoffIntentProtectsVanishUntilFreshSessionActivates() throws IOException {
        String prepare = method("public boolean prepareBackendHandoffResume", "public boolean cancelBackendHandoffResume");
        String activation = method("private void activateDurableSession", "private boolean validHandoffSource");

        assertTrue(prepare.contains("handoffGaps.add(playerId)"));
        assertTrue(activation.contains("handoffGaps.remove(playerId)"));
        assertTrue(activation.indexOf("handoffGaps.remove(playerId)")
                < activation.indexOf("activeSessionListener.accept(session)"));
    }

    @Test
    void backendHandoffRestoresLocalStateThenDetachesNetworkSession() throws IOException {
        String completion = method("private void completeBackendHandoff", "private void retainCancelledHandoffRecovery");

        assertTrue(completion.contains("sourceHandoffs.commitIfActive"));
        assertTrue(completion.indexOf("sourceHandoffs.commitIfActive")
                < completion.indexOf("loaded.detach"));
        assertTrue(completion.contains("session.sessionId()"));
        assertTrue(completion.contains("session.serverId()"));
        assertTrue(completion.contains("handoffGaps.add(playerId)"));
    }

    @Test
    void staffExitRequiresAnActiveSessionOwnedByThisBackend() throws IOException {
        String method = method("public void exit(Player player)", "// Handoff resume must snapshot");

        assertTrue(method.contains("active.get(playerId)"));
        assertTrue(method.contains("localSession.state() != StaffSessionState.ACTIVE"));
        assertTrue(method.contains("!serverId.equalsIgnoreCase(localSession.serverId())"));
        assertTrue(method.indexOf("localSession.state() != StaffSessionState.ACTIVE")
                < method.indexOf("beginDurableExit(playerId"));
    }

    @Test
    void cleanExitSuccessMessageIsOnlyEmittedAfterVerificationPasses() throws IOException {
        String completion = method(COMPLETE_RESTORATION, "private void finishVerifiedExit");
        int mismatch = completion.indexOf("if (!closed)");
        int mismatchReturn = completion.indexOf("return;", mismatch);
        int scheduled = completion.indexOf("finishVerifiedExit(playerId, session, restorationAttempt)", mismatchReturn);
        String cleanup = method("private void finishVerifiedExitOnEntity", "private boolean ownsClosingSession");
        int ownership = cleanup.indexOf("ownsClosingSession(playerId, session.sessionId())");
        int removedTools = cleanup.indexOf("removeStaffTools(player)");
        int removedRuntime = cleanup.indexOf("removeCompletedRuntimeState(playerId)");
        int completed = cleanup.indexOf("if (completed)");
        int success = cleanup.indexOf("Staff mode exited; your exact saved state was restored and verified.");

        assertTrue(mismatch >= 0 && mismatchReturn > mismatch);
        assertTrue(scheduled > mismatchReturn);
        assertTrue(ownership >= 0 && removedTools > ownership);
        assertTrue(removedRuntime > removedTools);
        assertTrue(completed > removedRuntime);
        assertTrue(success > completed);
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
