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

        java.util.regex.Matcher count = java.util.regex.Pattern.compile(
                "arguments\\.length\\s*==\\s*(1|[A-Z][A-Z0-9_]*)"
        ).matcher(recoveryMethod);
        assertTrue(count.find());
        String countExpression = count.group(1);
        boolean literalOne = "1".equals(countExpression);
        boolean namedOne = java.util.regex.Pattern.compile(
                "private static final int\\s+" + java.util.regex.Pattern.quote(countExpression)
                        + "\\s*=\\s*1\\s*;"
        ).matcher(source).find();
        assertTrue(literalOne || namedOne);

        java.util.regex.Matcher command = java.util.regex.Pattern.compile(
                "(?:\"recover\"|([A-Z][A-Z0-9_]*))"
                        + "\\.equalsIgnoreCase\\(arguments\\[0\\]\\)"
        ).matcher(recoveryMethod);
        assertTrue(command.find());
        String commandExpression = command.group(1);
        boolean literalRecover = commandExpression == null;
        boolean namedRecover = commandExpression != null && java.util.regex.Pattern.compile(
                "private static final String\\s+" + java.util.regex.Pattern.quote(commandExpression)
                        + "\\s*=\\s*\"recover\"\\s*;"
        ).matcher(source).find();
        assertTrue(literalRecover || namedRecover);
    }
}
