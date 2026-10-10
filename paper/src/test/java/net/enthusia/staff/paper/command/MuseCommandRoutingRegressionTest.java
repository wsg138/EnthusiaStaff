package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression guards for command-routing defects reproduced by the Muse staging run. */
class MuseCommandRoutingRegressionTest {
    private static final Path COMMAND_ROOT = Path.of("src/main/java/net/enthusia/staff/paper/command");
    private static final String MUTE_COMMAND = "mute";
    private static final String TARGET = "Target";

    @Test
    void centralPunishSingleArgumentOpensTheGui() throws IOException {
        String source = source("PunishmentCommand.java");
        String method = method(source, "private boolean openTargetOnlyGui", "private void prepareStoredDraft");

        assertFalse(method.contains("CENTRAL_COMMAND.equals(route)"));
        assertTrue(method.contains("gui.open(player, args[0], route)"));
    }

    @Test
    void centralPunishWithoutArgumentsOpensTheTargetPicker() throws IOException {
        String source = source("PunishmentCommand.java");
        String command = method(source, "public boolean onCommand", "private void prepare");

        assertTrue(command.contains("CENTRAL_COMMAND.equals(route) && sender instanceof Player player"));
        assertTrue(command.contains("gui.openTargetPicker(player, route)"));
    }

    @Test
    void centralRemovePunishmentSingleArgumentOpensExactSelectionBeforeAnyFallback() throws IOException {
        String source = source("SanctionChangeCommand.java");
        String method = method(source, "private boolean openAliasGui", "private static boolean hasMinimumArguments");

        assertTrue(method.contains("arguments.length != SINGLE_ARGUMENT"));
        assertTrue(method.contains("picker.open(player, arguments[0], selection,"));
        assertTrue(method.contains("if (central) {"));
        assertTrue(method.contains("return false;"));
        assertTrue(method.indexOf("picker.open(player, arguments[0], selection,")
                < method.indexOf("gui.open(player, arguments[0], route)"));
    }

    @Test
    void reportsSuggestionsRequireManagePermissionAndEvidenceSuggestionsRequireEvidencePermission()
            throws IOException {
        String source = source("ReportsCommand.java");
        String method = method(source, "public List<String> onTabComplete", "private record EvidenceRequest");

        assertTrue(method.contains("!sender.hasPermission(MANAGE_PERMISSION)"));
        assertTrue(method.contains("!sender.hasPermission(EVIDENCE_PERMISSION)"));
    }

    @Test
    void legacyRoseChatTimedMuteFormsAreRejectedBeforeDraftPreparation() throws IOException {
        String source = source("PunishmentCommand.java");
        String command = method(source, "public boolean onCommand", "private void prepare");

        assertTrue(command.contains("if (isLegacyTimedMute(route, args))"));
        assertTrue(command.contains("legacyTimedMuteUsage(sender, label)"));
        assertTrue(PunishmentCommand.isLegacyTimedMute(
                MUTE_COMMAND,
                new String[]{TARGET, "10", "minutes"}
        ));
        assertTrue(PunishmentCommand.isLegacyTimedMute(
                MUTE_COMMAND,
                new String[]{TARGET, "10", "minutes", "spam"}
        ));
        assertTrue(PunishmentCommand.isLegacyTimedMute(
                MUTE_COMMAND,
                new String[]{TARGET, "1h", "spam"}
        ));
        assertFalse(PunishmentCommand.isLegacyTimedMute(
                MUTE_COMMAND,
                new String[]{TARGET, "spam-noise", "repeated messages"}
        ));
        assertFalse(PunishmentCommand.isLegacyTimedMute(
                "ban",
                new String[]{TARGET, "10", "minutes"}
        ));
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
