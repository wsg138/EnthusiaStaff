package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression guards for command-routing defects reproduced by the Muse staging run. */
class MuseCommandRoutingRegressionTest {
    private static final Path COMMAND_ROOT = Path.of("src/main/java/net/enthusia/staff/paper/command");

    @Test
    void centralPunishSingleArgumentDoesNotOpenTheGui() throws IOException {
        String source = source("PunishmentCommand.java");
        String method = method(source, "private boolean openTargetOnlyGui", "private void prepareStoredDraft");

        assertTrue(method.contains("CENTRAL_COMMAND.equals(route)"));
        assertTrue(method.contains("return false;"));
    }

    @Test
    void centralRemovePunishmentSingleArgumentDoesNotOpenTheGui() throws IOException {
        String source = source("SanctionChangeCommand.java");
        String method = method(source, "public boolean onCommand", "private void apply");

        assertTrue(method.contains("!central && arguments.length == 1"));
    }

    @Test
    void reportsSuggestionsRequireManagePermissionAndEvidenceSuggestionsRequireEvidencePermission()
            throws IOException {
        String source = source("ReportsCommand.java");
        String method = method(source, "public List<String> onTabComplete", "private record EvidenceRequest");

        assertTrue(method.contains("!sender.hasPermission(MANAGE_PERMISSION)"));
        assertTrue(method.contains("!sender.hasPermission(EVIDENCE_PERMISSION)"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(COMMAND_ROOT.resolve(name));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate source method boundaries");
        }
        return source.substring(start, end);
    }
}
