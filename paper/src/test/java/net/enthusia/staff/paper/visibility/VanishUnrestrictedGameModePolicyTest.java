package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class VanishUnrestrictedGameModePolicyTest {
    @Test
    void unrestrictedIdentityMayKeepAnyRealGameModeRegardlessOfRank() {
        for (StaffRank rank : new StaffRank[] {StaffRank.HELPER, StaffRank.MOD}) {
            assertTrue(VanishManager.isSelectableGameMode(rank, GameMode.SURVIVAL, true));
            assertTrue(VanishManager.isSelectableGameMode(rank, GameMode.CREATIVE, true));
            assertTrue(VanishManager.isSelectableGameMode(rank, GameMode.ADVENTURE, true));
            assertTrue(VanishManager.isSelectableGameMode(rank, GameMode.SPECTATOR, true));
        }
    }

    @Test
    void ordinaryHelperAndModRemainRestrictedToSurvivalOrSpectator() {
        for (StaffRank rank : new StaffRank[] {StaffRank.HELPER, StaffRank.MOD}) {
            assertTrue(VanishManager.isSelectableGameMode(rank, GameMode.SURVIVAL, false));
            assertTrue(VanishManager.isSelectableGameMode(rank, GameMode.SPECTATOR, false));
            assertFalse(VanishManager.isSelectableGameMode(rank, GameMode.CREATIVE, false));
            assertFalse(VanishManager.isSelectableGameMode(rank, GameMode.ADVENTURE, false));
        }
    }
}
