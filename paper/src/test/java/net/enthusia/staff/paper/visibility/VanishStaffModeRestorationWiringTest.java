package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VanishStaffModeRestorationWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java"
    );

    @Test
    void savedStateRestorationBypassesVanishGameModeCancellationWithoutDisablingVanish() throws IOException {
        String method = method(
                "public void onGameModeChange",
                "@EventHandler(priority = EventPriority.HIGHEST)\n    public void onJoin"
        );

        assertTrue(method.contains("boolean restoringStaffState = staffMode.restoringSavedState(playerId)"));
        assertTrue(method.contains("&& !restoringStaffState"));
        assertFalse(method.contains("visibility.setVanished(playerId"));
        assertFalse(method.contains("selectedGameModes.remove(playerId)"));
    }

    @Test
    void vanishGameModeWaitsForStaffModeCaptureOrRebindToFinish() throws IOException {
        String method = method(
                "private void reconcileVanishGameMode",
                "private GameMode selectedGameModeForEnable"
        );

        assertTrue(method.contains("staffMode.transitioning(playerId)"));
        assertTrue(method.indexOf("staffMode.transitioning(playerId)")
                < method.indexOf("reconcileVanishedGameMode(player)"));
    }

    @Test
    void manualVanishDoesNotForceSpectatorDuringStaffRebind() throws IOException {
        String method = method("private void finishSet(", "private Set<UUID> presenceViewers");

        assertTrue(method.contains("!staffMode.transitioning(playerId)"));
        assertTrue(method.indexOf("!staffMode.transitioning(playerId)")
                < method.indexOf("reconcileVanishedGameMode(player)"));
    }

    @Test
    void vanishPresenceTargetsEveryOtherOnlinePlayerRegardlessOfVisibility() throws IOException {
        String method = method(
                "private Set<UUID> presenceViewers",
                "private void publishPresenceTransition"
        );

        assertTrue(method.contains("!viewerId.equals(subjectId)"));
        assertFalse(method.contains("visibility.canSee"));
    }

    @Test
    void vanishUsesRankAllowedRealModesInsteadOfForcingSpectator() throws IOException {
        String change = method(
                "public void onGameModeChange",
                "@EventHandler(priority = EventPriority.HIGHEST)\n    public void onJoin"
        );
        assertTrue(change.contains("!isSelectableGameMode(rank, event.getNewGameMode())"));

        String reconcile = method(
                "private void reconcileVanishedGameMode",
                "private void restoreSelectedGameMode"
        );
        assertTrue(reconcile.contains("selectedGameModeForEnable(player, rank)"));
        assertTrue(reconcile.contains("player.setGameMode(selected)"));
        assertFalse(reconcile.contains("player.setGameMode(GameMode.SPECTATOR)"));
    }

    @Test
    void selectedRankAllowedModeIsAppliedEvenWhileVanished() throws IOException {
        String method = method(
                "public boolean selectGameplayMode",
                "/**\n     * Cross-server transfer hook"
        );

        assertTrue(method.contains("!isSelectableGameMode(rank, selected)"));
        assertTrue(method.contains("persistSelectedGameMode(playerId, rank, selected)"));
        assertTrue(method.contains("player.setGameMode(selected)"));
        assertFalse(method.contains("GameMode.SPECTATOR;"));
    }

    @Test
    void everyPlayerRankDisablesLiveVanishAfterStaffModeExitWithoutOverwritingPreference() throws IOException {
        String method = method(
                "private void disableAfterStaffModeExit",
                "private static boolean requiresStaffMode"
        );

        assertFalse(method.contains("if (!requiresStaffMode(rank))"));
        assertTrue(method.contains("pendingStaffModeExitDisables.add(playerId)"));
        assertTrue(method.contains(
                "set(player, rank, false, false, VanishStore.PreferenceUpdate.KEEP)"
        ));
        assertFalse(method.contains("reconcileVanishGameMode(player)"));
    }

    private static String method(String startMarker, String endMarker) throws IOException {
        String source = Files.readString(SOURCE).replace("\r\n", "\n");
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not locate VanishManager method boundaries");
        }
        return source.substring(start, end);
    }
}
