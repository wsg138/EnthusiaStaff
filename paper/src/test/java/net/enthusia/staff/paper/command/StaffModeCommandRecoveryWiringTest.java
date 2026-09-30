package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeCommandRecoveryWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/StaffModeCommand.java"
    );

    @Test
    void explicitRecoveryRunsBeforeLocalStateAndOperationalModeChecks() throws IOException {
        String source = Files.readString(SOURCE);
        int recovery = source.indexOf("if (recoveryRequested(arguments))");
        int recoverCall = source.indexOf("manager.recover(player)", recovery);
        int localState = source.indexOf("manager.active(player.getUniqueId())");
        int modeGate = source.indexOf("staffModeTransitionAllowed");

        assertTrue(recovery >= 0);
        assertTrue(recoverCall > recovery);
        assertTrue(localState > recoverCall);
        assertTrue(modeGate > localState);
    }

    @Test
    void recoveryRequiresExactlyOneRecoverArgument() throws IOException {
        String source = Files.readString(SOURCE);
        int method = source.indexOf("private static boolean recoveryRequested");
        String recoveryMethod = source.substring(method);

        assertTrue(recoveryMethod.contains("arguments.length == 1"));
        assertTrue(recoveryMethod.contains("\"recover\".equalsIgnoreCase(arguments[0])"));
    }
}
