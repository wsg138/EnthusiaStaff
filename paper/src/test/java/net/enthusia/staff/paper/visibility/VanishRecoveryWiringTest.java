package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Wiring evidence supplements the executable operation-ticket race regressions. */
class VanishRecoveryWiringTest {
    private static final Path MANAGER = Path.of(
            "src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java");

    @Test
    void resultApplicationAndEveryLoadCompletionUseTheCapturedTicket() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        String method = method(source, "private void refreshDurableVanish(",
                "private void applyDurableVanishRecovery(");
        assertTrue(method.contains("recoveryFence.begin(playerId)"));
        assertTrue(method.contains("if (recoveryFence.current(read)) {\n"
                + "                                    applyDurableVanishRecovery"));
        assertTrue(method.contains("finally {\n                                recoveryFence.finish(read)"));
        assertTrue(method.contains("() -> recoveryFence.finish(read)"));
        assertTrue(method.contains("if (loaded == null) {\n                    recoveryFence.finish(read)"));
        assertTrue(method.contains("stateWrites.contains(playerId) || selectedModeWrites.contains(playerId)"));
    }

    @Test
    void lifecycleAndAcceptedLocalChangesInvalidateOutstandingReads() throws IOException {
        String source = Files.readString(MANAGER).replace("\r\n", "\n");
        for (String[] boundaries : new String[][] {
                {"public void staffModeExited(", "public void beginPluginGameModeApplication("},
                {"public void applyTransferSnapshot(", "public boolean canSee("},
                {"private java.util.concurrent.CompletableFuture<Boolean> set(", "private boolean persistSet("},
                {"private void rememberCommittedState(", "private void persistState("},
                {"private void applyReconciledMemoryState(", "private void reconcileDurableState("},
                {"private void reconcileDurableState(", "private void reconciliationFailed("},
                {"public void onJoin(", "public void onQuit("},
                {"public void onQuit(", "public void onPluginDisable("},
                {"private void persistSelectedGameMode(", "public interface PresenceTransitionSink"}
        }) {
            assertTrue(method(source, boundaries[0], boundaries[1])
                    .contains("recoveryFence.invalidate(playerId)"), boundaries[0]);
        }
        assertTrue(method(source, "public void onPluginDisable(", "public void refreshAll(")
                .contains("recoveryFence.clear()"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate manager method boundaries");
        }
        return source.substring(start, end);
    }
}
