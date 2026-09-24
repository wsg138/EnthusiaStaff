package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentHierarchyWiringTest {
    private static final Path COMMAND_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/PunishmentCommand.java"
    );
    private static final Path GUI_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"
    );

    @Test
    void commandChecksTargetBeforePrepareAndAgainBeforeConfirm() throws IOException {
        String source = Files.readString(COMMAND_SOURCE);

        int prepareGuard = source.indexOf("!targetAllowed(sender, actor, target.playerId())");
        int prepareMutation = source.indexOf("workflow.prepare(");
        int confirmLookup = source.indexOf("workflow.find(draftId, actor.id())");
        int confirmGuard = source.indexOf("!targetAllowed(sender, actor, draft.targetId())");
        int confirmMutation = source.indexOf("workflow.confirmRouted(draftId, actor, mode.get())");

        assertTrue(prepareGuard >= 0 && prepareGuard < prepareMutation);
        assertTrue(confirmLookup >= 0 && confirmLookup < confirmGuard);
        assertTrue(confirmGuard < confirmMutation);
    }

    @Test
    void guiRechecksHierarchyAtEveryMutationBoundary() throws IOException {
        String source = Files.readString(GUI_SOURCE);

        assertTrue(occurrences(source, "!targetAllowed(viewer, actor, state.target().playerId())") >= 3);
        assertTrue(source.contains("LuckPermsStaffTargetGuard.discover(plugin)"));
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int offset = source.indexOf(value);
        while (offset >= 0) {
            count++;
            offset = source.indexOf(value, offset + value.length());
        }
        return count;
    }
}
