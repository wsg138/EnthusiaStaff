package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiReviewActiveDutyContractTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java").resolve(relative)).replace("\r\n", "\n");
    }

    @Test
    void centralWritesRecheckAuthoritativeDutyImmediatelyBeforeDispatch() throws IOException {
        String subsystem = source("net/enthusia/staff/paper/aireview/AiReviewSubsystem.java");
        int start = subsystem.indexOf("private <T> void submitWrite");
        int end = subsystem.indexOf("<T> void submit(", start);
        assertTrue(start >= 0 && end > start);
        String method = subsystem.substring(start, end);
        assertTrue(method.contains("UUID sessionId = activeSession.apply(reviewerId)"));
        assertTrue(method.contains("StaffRank expectedRank = sessionRank.apply(reviewerId)"));
        assertTrue(method.contains("!allowsAuthority(expectedRank, authority)"));
        assertTrue(method.contains("!sessionId.equals(activeSession.apply(reviewerId))"));
        assertTrue(method.contains("currentRank != expectedRank"));
        assertTrue(method.contains("!allowsAuthority(currentRank, authority)"));
        assertTrue(method.contains("throw new ReviewerDutyEndedException()"));
        assertTrue(method.indexOf("throw new ReviewerDutyEndedException()")
                < method.indexOf("return work.get()"));
    }

    @Test
    void reviewAuthorityIsLimitedToModerationRanks() throws IOException {
        String subsystem = source("net/enthusia/staff/paper/aireview/AiReviewSubsystem.java");
        assertTrue(subsystem.contains("rank == StaffRank.MOD || rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER"));
        assertTrue(subsystem.contains("rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER"));
    }

    @Test
    void guiAndTextPathsFailClosedWhenStaffDutyEnds() throws IOException {
        String gui = source("net/enthusia/staff/paper/aireview/AiReviewGuiController.java");
        String command = source("net/enthusia/staff/paper/aireview/AiReviewCommand.java");
        assertTrue(gui.contains("|| !subsystem.activeDuty(viewer)"));
        assertTrue(gui.contains("if (!subsystem.activeDuty(viewer) || !AiReviewPermissions.correct(viewer))"));
        assertTrue(command.contains("sender instanceof Player player && !subsystem.activeDuty(player)"));
        assertTrue(command.contains("if (!subsystem.activeDuty(player) || !AiReviewPermissions.correct(player))"));
    }

    @Test
    void productionWiringUsesStaffModeAuthorityAsDutySource() throws IOException {
        String plugin = source("net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java");
        String gate = source("net/enthusia/staff/paper/staff/StaffDutyCommandGate.java");
        assertTrue(plugin.contains("runtimeComponents.staffMode()::authorityActive"));
        assertTrue(plugin.contains("runtimeComponents.staffMode()::activeSessionId"));
        assertTrue(plugin.contains("runtimeComponents.staffMode()::sessionRank"));
        assertTrue(gate.contains("\"aireview\""));
    }
}
