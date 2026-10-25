package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guard against a changed rank/target hierarchy during slow queued work. */
class PunishmentGuidedLateHierarchyRecheckContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/net/enthusia/staff/paper/" + path));
    }

    @Test
    void guidedPreviewMustRecheckHierarchyAfterReadBeforeCreatingDurableDraft() throws Exception {
        String gui = source("punishment/PunishmentGuiController.java");
        String worker = gui.substring(gui.indexOf("private void evaluateGuided("),
                gui.indexOf("private static String guidedExplanation("));
        int preview = worker.indexOf("workflow.preview(readOnly, mode.get())");
        int firstCheck = worker.indexOf("targetAllowed(viewer, actor, state.target().playerId())");
        int secondCheck = worker.lastIndexOf("targetAllowed(viewer, actor, state.target().playerId())");
        int write = worker.indexOf("workflow.prepare(proposal, mode.get(), assessment)");
        assertTrue(firstCheck >= 0 && preview > firstCheck && secondCheck > preview && write > secondCheck,
                "Current actor rank and target hierarchy must be rechecked after lengthy preview, before draft write");
    }

}
