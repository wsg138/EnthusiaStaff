package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void automaticRecoveryRetryIsSingleFlightPerPlayer() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        int start = source.indexOf("private void scheduleRecoveryRetry");
        int end = source.indexOf("private boolean staleFromPriorRuntime", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("if (!scheduledRecoveryRetries.add(playerId))"));
        assertTrue(method.contains("scheduledRecoveryRetries.remove(playerId)"));
        assertTrue(method.contains("20L"));
    }
}
