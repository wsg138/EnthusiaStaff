package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeEntryVanishWiringTest {
    @Test
    void freshEntryStillPublishesSessionBeforeInvokingVisibilityListener() throws IOException {
        String source = read("staff/StaffModeManager.java");
        assertEquals(2, source.split("activateFreshSession", -1).length - 1);
        String fresh = source.substring(source.indexOf("private void activateFreshSession"),
                source.indexOf("public void exit("));
        assertTrue(fresh.indexOf("activateDurableSession(") < fresh.indexOf("entryListener.accept(player)"));
        assertTrue(fresh.contains("if (active.get(playerId) == session)"));
        assertTrue(read("PaperRuntimeComponents.java").contains("staffMode.setEntryListener(vanish::staffModeEntered)"));
    }

    @Test
    void preparedPreferenceIsConsumedByExistingDurableVanishPath() throws IOException {
        String source = read("visibility/VanishManager.java");
        String entry = source.substring(source.indexOf("public UUID prepareStaffModeEntry("),
                source.indexOf("public void configureSpectatorTab("));

        assertTrue(entry.contains("PreparedStaffModeEntry prepared = preparedStaffModeEntries.remove(playerId)"));
        assertTrue(entry.contains("boolean desired = prepared == null || prepared.desired()"));
        assertTrue(entry.contains("VanishStore.PreferenceUpdate.SET"));
        assertTrue(entry.contains("VanishStore.PreferenceUpdate.KEEP"));
        assertTrue(entry.contains("set(player, rank, desired, true, preferenceUpdate)"));
        assertFalse(entry.contains("toggle("));
        assertTrue(entry.contains("sessionId.equals(staffMode.activeSessionId(playerId))"));
        assertTrue(entry.contains("staffMode.exit(current)"));
    }

    @Test
    void pendingEnableRechecksLiveRankBeforeQueuingPersistence() throws IOException {
        String source = read("visibility/VanishManager.java");
        String validation = source.substring(
                source.indexOf("private void validatePendingEnable("),
                source.indexOf("private boolean queuePersistSet(")
        );

        assertTrue(validation.contains("audiences.onOwner("));
        assertTrue(validation.indexOf("resolveAndPublishRank(current)")
                < validation.indexOf("VanishEnableAuthorityFence.eligible(expectedRank, liveRank)"));
        assertTrue(validation.indexOf("VanishEnableAuthorityFence.eligible(expectedRank, liveRank)")
                < validation.indexOf("queuePersistSet("));
        assertTrue(validation.contains("failPendingSet("));
    }

    @Test
    void persistenceBoundaryRechecksPendingEnableAndRollsBackIfRankChanges() throws IOException {
        String source = read("visibility/VanishManager.java");
        String persistence = source.substring(
                source.indexOf("private boolean persistSet("),
                source.indexOf("private void rememberCommittedState(")
        );

        assertTrue(persistence.contains("VanishEnableAuthorityFence.commitIfEligible("));
        assertTrue(persistence.contains("() -> onlineStaffRanks.get(playerId)"));
        assertTrue(persistence.contains("persistState(loaded, playerId, rank, true, selectedGameMode, preferenceUpdate)"));
        assertTrue(persistence.contains("persistState(loaded, playerId, rank, false, selectedGameMode,"));
        assertTrue(persistence.contains("VanishStore.PreferenceUpdate.KEEP"));
        assertTrue(persistence.contains("return false;"));
    }
    @Test
    void manualToggleRemembersChoiceButAutomaticExitCleanupDoesNotOverwriteIt() throws IOException {
        String source = read("visibility/VanishManager.java");
        assertTrue(source.contains(
                "set(player, rank, next, true, VanishStore.PreferenceUpdate.SET)"
        ));
        assertTrue(source.contains(
                "set(player, rank, false, false, VanishStore.PreferenceUpdate.KEEP)"
        ));
    }

    private static String read(String suffix) throws IOException {
        return Files.readString(Path.of("src/main/java/net/enthusia/staff/paper", suffix))
                .replace("\r\n", "\n");
    }
}
