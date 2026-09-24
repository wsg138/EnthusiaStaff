package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FoliaTesterHandoffWiringTest {
    private static final Path TESTER_SOURCE = Path.of("src/main/java/net/enthusia/staff/paper/tester");

    @Test
    void cheatTesterRecoveryAndRestoreUseOwnedHandoff() throws IOException {
        String manager = source("CheatTesterManager.java");
        String runtime = source("CheatTesterRuntimeSupport.java");

        assertTrue(manager.contains("scheduleRecovery(record);"));
        assertTrue(manager.contains("handoff.execute(\n                session.targetId,"));
        assertTrue(manager.contains("target -> restoreTarget(target, session, terminalState, reason, captured)"));
        assertFalse(manager.contains("Player current = plugin.getServer().getPlayer(record.targetId())"));
        assertFalse(manager.contains("Target probe could not be scheduled after journal commit"));

        assertTrue(runtime.contains("handoff.execute(targetId, operation, retired);"));
        assertFalse(runtime.contains("target == null || !target.isOnline()"));
    }

    @Test
    void fakeBaseContinuationUsesOwnedHandoff() throws IOException {
        String fakeBase = source("FakeBaseManager.java");

        assertTrue(fakeBase.contains("handoff.execute(playerId, operation, () -> { });"));
        assertFalse(fakeBase.contains("player != null && player.isOnline()"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(TESTER_SOURCE.resolve(name)).replace("\r\n", "\n");
    }
}
