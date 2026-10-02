package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MuseCommandSurfaceRegressionTest {
    private static final Path PUNISH = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/PunishmentCommand.java"
    );
    private static final Path REMOVE = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/SanctionChangeCommand.java"
    );
    private static final Path REPORTS = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/ReportsCommand.java"
    );

    @Test
    void centralPunishTargetOnlyRouteDoesNotOpenGui() throws IOException {
        String source = Files.readString(PUNISH);
        assertTrue(source.contains("if (CENTRAL_COMMAND.equals(route)"));
        assertTrue(source.indexOf("CENTRAL_COMMAND.equals(route)") < source.indexOf("gui.open(player, args[0], route)"));
    }

    @Test
    void centralRemovePunishmentTargetOnlyRouteDoesNotOpenGui() throws IOException {
        String source = Files.readString(REMOVE);
        int helper = source.indexOf("private boolean openAliasGui");
        int centralGate = source.indexOf("central || arguments.length != SINGLE_ARGUMENT", helper);
        int guiOpen = source.indexOf("gui.open(player, arguments[0], route)", helper);

        assertTrue(helper >= 0 && centralGate > helper);
        assertTrue(guiOpen > centralGate);
    }

    @Test
    void reportCompletionsRequireMatchingPermissions() throws IOException {
        String source = Files.readString(REPORTS);
        int completion = source.indexOf("public List<String> onTabComplete");
        int manageGate = source.indexOf("if (!sender.hasPermission(MANAGE_PERMISSION))", completion);
        int evidenceGate = source.indexOf("&& sender.hasPermission(EVIDENCE_PERMISSION)", completion);
        assertTrue(completion >= 0 && manageGate > completion);
        assertTrue(evidenceGate > manageGate);
    }
}
