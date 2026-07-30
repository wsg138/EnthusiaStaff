package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class SpectatorTabPolicyTest {
    @Test
    void seniorTechnicalAndAdministrativeRanksReceiveTheChoice() {
        assertTrue(SpectatorTabPolicy.offersVisibilityChoice(StaffRank.DEVELOPER));
        assertTrue(SpectatorTabPolicy.offersVisibilityChoice(StaffRank.ADMIN));
        assertTrue(SpectatorTabPolicy.offersVisibilityChoice(StaffRank.FOUNDER));
        assertFalse(SpectatorTabPolicy.offersVisibilityChoice(StaffRank.HELPER));
        assertFalse(SpectatorTabPolicy.offersVisibilityChoice(StaffRank.MOD));
    }

    @Test
    void everyExplicitStaffSpectatorEntryIsMasked() {
        assertTrue(SpectatorTabPolicy.masksSpectatorEntry(StaffRank.HELPER));
        assertTrue(SpectatorTabPolicy.masksSpectatorEntry(StaffRank.MOD));
        assertTrue(SpectatorTabPolicy.masksSpectatorEntry(StaffRank.DEVELOPER));
        assertTrue(SpectatorTabPolicy.masksSpectatorEntry(StaffRank.ADMIN));
        assertTrue(SpectatorTabPolicy.masksSpectatorEntry(StaffRank.FOUNDER));
        assertFalse(SpectatorTabPolicy.masksSpectatorEntry(StaffRank.SYSTEM));
        assertFalse(SpectatorTabPolicy.masksSpectatorEntry(null));
    }

    @Test
    void normalAppearanceFailsClosedWithoutPacketMasking() {
        assertTrue(SpectatorTabPolicy.mayAppearNormally(
                StaffRank.ADMIN,
                GameMode.SPECTATOR,
                false,
                true
        ));
        assertFalse(SpectatorTabPolicy.mayAppearNormally(
                StaffRank.ADMIN,
                GameMode.SPECTATOR,
                false,
                false
        ));
        assertFalse(SpectatorTabPolicy.mayAppearNormally(
                StaffRank.ADMIN,
                GameMode.SPECTATOR,
                true,
                true
        ));
        assertFalse(SpectatorTabPolicy.mayAppearNormally(
                StaffRank.MOD,
                GameMode.SPECTATOR,
                false,
                true
        ));
        assertFalse(SpectatorTabPolicy.mayAppearNormally(
                StaffRank.ADMIN,
                GameMode.CREATIVE,
                false,
                true
        ));
    }

    @Test
    void regularPlayersRemainListedWhenVisible() {
        assertTrue(SpectatorTabPolicy.shouldList(
                null,
                GameMode.SURVIVAL,
                true,
                false,
                false
        ));
        assertTrue(SpectatorTabPolicy.shouldList(
                null,
                GameMode.SPECTATOR,
                true,
                false,
                false
        ));
    }

    @Test
    void staffSpectatorsFailClosedWhenPacketMaskingIsUnavailable() {
        assertFalse(SpectatorTabPolicy.shouldList(
                StaffRank.ADMIN,
                GameMode.SPECTATOR,
                true,
                false,
                false
        ));
        assertTrue(SpectatorTabPolicy.shouldList(
                StaffRank.ADMIN,
                GameMode.SPECTATOR,
                true,
                false,
                true
        ));
        assertFalse(SpectatorTabPolicy.shouldList(
                StaffRank.ADMIN,
                GameMode.CREATIVE,
                false,
                false,
                true
        ));
        assertFalse(SpectatorTabPolicy.shouldList(
                StaffRank.ADMIN,
                GameMode.CREATIVE,
                true,
                true,
                true
        ));
    }
}
