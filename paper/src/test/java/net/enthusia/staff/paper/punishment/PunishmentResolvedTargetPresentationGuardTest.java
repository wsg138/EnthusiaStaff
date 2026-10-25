package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression for hierarchy drift while async target overview or draft resume loads. */
class PunishmentResolvedTargetPresentationGuardTest {
    private static final String SOURCE = "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java";

    @Test
    void openingTargetMustRecheckAfterOverviewBeforeSchedulingPresentation() throws Exception {
        String source = Files.readString(Path.of(SOURCE));
        String route = source.substring(source.indexOf("public void open(Player viewer, String targetQuery,"),
                source.indexOf("public void resume(Player viewer, String targetQuery,"));
        int load = route.indexOf("loadOverview(target.playerId())");
        int lateCheck = route.lastIndexOf("targetAllowed(viewer, actor, target.playerId(), ticket)");
        int present = route.indexOf("presentResolvedState(viewer,");
        assertTrue(load >= 0 && lateCheck > load && present > lateCheck,
                "Revalidate protected target after slow overview lookup and before scheduling the GUI");
    }

    @Test
    void resumingDraftMustRecheckAfterOverviewBeforeSchedulingPresentation() throws Exception {
        String source = Files.readString(Path.of(SOURCE));
        String route = source.substring(source.indexOf("public void resume(Player viewer, String targetQuery,"),
                source.indexOf("/** Opaque, viewer-bound ticket"));
        int load = route.indexOf("loadOverview(target.playerId())");
        int lateCheck = route.lastIndexOf("targetAllowed(viewer, actor, target.playerId(), ticket)");
        int present = route.indexOf("presentResolvedState(viewer,");
        assertTrue(load >= 0 && lateCheck > load && present > lateCheck,
                "A saved review must not be presented using a hierarchy decision from before draft/overview reads");
    }
    @Test
    void historyMustRecheckAfterSlowPageReadBeforePresentation() throws Exception {
        String source = Files.readString(Path.of(SOURCE));
        String route = source.substring(source.indexOf("private void openHistory(Player viewer,"),
                source.indexOf("private void openUnavailableHistory("));
        int load = route.indexOf("store.page(");
        int lateCheck = route.lastIndexOf("historyTargetAllowed(viewer, actor, returnState, token)");
        int present = route.indexOf("openHistoryState(viewer, new PunishmentGuiState.History(");
        assertTrue(load >= 0 && lateCheck > load && present > lateCheck,
                "History read must not be displayed after a target becomes protected while storage is loading");
    }

    @Test
    void unavailableHistoryMustRecheckBeforeReusingCachedOverview() throws Exception {
        String source = Files.readString(Path.of(SOURCE));
        String fallback = source.substring(source.indexOf("private void openUnavailableHistory("),
                source.indexOf("private void prepare("));
        int check = fallback.indexOf("historyTargetAllowed(viewer, actor, returnState, token)");
        int present = fallback.indexOf("openHistoryState(viewer, new PunishmentGuiState.History(");
        assertTrue(check >= 0 && present > check,
                "Even an unavailable history page must not reuse a protected target's cached overview");
    }

}
