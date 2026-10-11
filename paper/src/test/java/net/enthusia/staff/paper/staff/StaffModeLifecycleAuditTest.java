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
        assertTrue(entry.indexOf("if (active.get(playerId) == session)")
                < entry.indexOf("\"staff-mode-entered\""));
        String recovered = between(source, "private void finishActiveRecovery(", "private void activateDurableSession(");
        assertTrue(recovered.indexOf("if (active.get(playerId) == session)")
                < recovered.indexOf("\"staff-mode-resumed\""));
        String rebinding = between(source, "private void rebindDetachedSession(", "private void validateDetachedRebind(");
        assertTrue(rebinding.indexOf("if (active.get(playerId) == rebound)")
                < rebinding.indexOf("\"staff-mode-resumed\""));
    }

    @Test
    void exitsAreAuditedOnlyAfterDurableClosureAndNotOnBackendHandoff() throws IOException {
        String source = Files.readString(MANAGER);
        String exit = between(source, "private void completeRestoration(", "private void retainRecoveryAfterRuntimeExit(");
        assertTrue(exit.indexOf("if (!closed)") < exit.indexOf("logCommittedExit("));
        assertTrue(exit.indexOf("completeRuntimeExit(playerId)") < exit.indexOf("logCommittedExit("));
        String handoff = between(source, "private void completeBackendHandoff(", "private void retainCancelledHandoffRecovery(");
        assertTrue(!handoff.contains("logCommittedExit("));
        assertTrue(!handoff.contains("\"staff-mode-exited\""));
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        assertTrue(begin >= 0 && finish > begin, start);
        return source.substring(begin, finish);
    }
}
