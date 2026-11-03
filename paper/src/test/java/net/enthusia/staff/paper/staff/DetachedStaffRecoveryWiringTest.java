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

        assertTrue(method.contains("scheduledRecoveryRetries.putIfAbsent(playerId, ticket) != null"),
                "One pending recovery token per player must reject duplicate retries");
        assertTrue(method.contains("StaffRecoveryRetryOwnerDispatcher.forward("),
                "Global timer must hand permission-sensitive recovery to the entity owner");
        assertTrue(method.contains("scheduledRecoveryRetries.remove(playerId, ticket)"),
                "Scheduling errors must release only the matching retry token");
        assertTrue(method.contains("20L"));
    }
}
