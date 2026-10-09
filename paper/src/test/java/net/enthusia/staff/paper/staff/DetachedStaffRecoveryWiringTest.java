package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DetachedStaffRecoveryWiringTest {
    private static final Path MANAGER = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"
    );

    @Test
    void detachedStateIsClassifiedBeforeRebind() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        assertTrue(source.contains(
                "DetachedStaffSessionRecoveryPolicy.decide(session.state())"
        ));
        assertTrue(source.contains("case RETIRE_RESTORED_LEASE -> retireRestoredDetachedSession("));
        assertTrue(source.contains("case HOLD_INVALID_TRANSITION ->"));
    }

    @Test
    void terminalDetachedRecoveryUsesExactFenceAndNeverRestoresSourceSnapshot() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        int start = source.indexOf("private void retireRestoredDetachedSession");
        int end = source.indexOf("private void resumeDetachedSession", start);
        String method = source.substring(start, end);
        assertTrue(method.contains("loaded.beginDetachedExit(session, clock.instant())"));
        assertTrue(method.contains("loaded.completeExit(exiting.sessionId(), exiting.checksum()"));
        assertFalse(method.contains("loaded.beginExit("));
        assertFalse(method.contains("restoreSavedState("));
        assertFalse(method.contains("restoreAndVerify("));
    }

    @Test
    void automaticRecoveryRetryIsSingleFlightPerPlayer() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        int start = source.indexOf("private void scheduleRecoveryRetry");
        int end = source.indexOf("private boolean staleFromPriorRuntime", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("recoveryRetries.begin(playerId)"));
        assertTrue(method.contains("recoveryRetries.consume(playerId, ticket)"));
        assertTrue(method.contains("20L"));
        assertTrue(method.contains("onEntity(playerId, this::recover)"));
    }
}
