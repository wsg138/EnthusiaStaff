package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffModeLifecycleAuditTest {
    private static final UUID SESSION = UUID.fromString("41dfab4a-31a0-442b-8ae9-483140b1a960");
    private static final Path MANAGER = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java");

    @Test
    void lifecycleDetailsKeepStableSessionAndNonSensitiveFixedLabels() {
        assertEquals("session=" + SESSION + " path=fresh-entry reason=initial",
                StaffModeLifecycleAudit.detail(SESSION, "fresh-entry", "initial"));
        assertEquals("manual", StaffModeLifecycleAudit.exitReason("Staff mode exit"));
        assertEquals("rank-removed", StaffModeLifecycleAudit.exitReason("Staff rank removal"));
        assertEquals("profile-reconciliation-failed",
                StaffModeLifecycleAudit.exitReason("Staff rank profile reconciliation failure"));
        assertEquals("recovery", StaffModeLifecycleAudit.exitReason("Unknown operation"));
        assertEquals("recovery", StaffModeLifecycleAudit.exitReason(null));
    }

    @Test
    void entryAndRecoveryAuditFollowConfirmedRuntimeActivation() throws IOException {
        String source = Files.readString(MANAGER);
        String entry = between(source, "private void activateFreshSession(", "public void exit(");
        assertOrdered(entry, "if (active.get(playerId) == session)", "\"staff-mode-entered\"");
        String recovered = between(source, "private void finishActiveRecovery(", "private void activateDurableSession(");
        assertOrdered(recovered, "if (active.get(playerId) == session)", "\"staff-mode-resumed\"");
        String rebinding = between(source, "private void rebindDetachedSession(", "private void validateDetachedRebind(");
        assertOrdered(rebinding, "if (active.get(playerId) == rebound)", "\"staff-mode-resumed\"");
    }

    @Test
    void exitsAreAuditedOnlyAfterDurableClosureAndNotOnBackendHandoff() throws IOException {
        String source = Files.readString(MANAGER);
        String exit = between(source, "private void completeRestoration(", "private void retainRecoveryAfterRuntimeExit(");
        assertOrdered(exit, "if (!closed)", "logCommittedExit(");
        assertOrdered(exit, "completeRuntimeExit(playerId)", "logCommittedExit(");
        String handoff = between(source, "private void completeBackendHandoff(", "private void retainCancelledHandoffRecovery(");
        assertTrue(!handoff.contains("logCommittedExit("));
        assertTrue(!handoff.contains("\"staff-mode-exited\""));
    }

    @Test
    void recoveryRankRemovalPreservesItsReasonAndExitCapturesLiveRankFallback() throws IOException {
        String source = Files.readString(MANAGER);
        String recovery = between(source, "public void recover(UUID playerId, StaffRank rankSnapshot)",
                "private void recoverDetachedSession(");
        assertTrue(recovery.contains("restoreAndVerify(playerId, exiting, loaded, \"rank-removed\")"));
        String recoveryWithPlayer = between(source, "private void finishActiveRecovery(",
                "private void activateDurableSession(");
        assertTrue(recoveryWithPlayer.contains("restoreAndVerify(playerId, exiting, loaded, \"rank-removed\")"));
        String exitCapture = between(source, "private ExitAuditContext captureExitAudit(",
                "private void logCommittedExit(");
        assertOrdered(exitCapture, "StaffRank rank = ranks.get(playerId);",
                "rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);");
    }

    private static void assertOrdered(String source, String first, String second) {
        int firstAt = source.indexOf(first);
        int secondAt = source.indexOf(second);
        assertTrue(firstAt >= 0, "Missing marker: " + first);
        assertTrue(secondAt >= 0, "Missing marker: " + second);
        assertTrue(firstAt < secondAt, "Expected " + first + " before " + second);
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        assertTrue(begin >= 0 && finish > begin, start);
        return source.substring(begin, finish);
    }
}
