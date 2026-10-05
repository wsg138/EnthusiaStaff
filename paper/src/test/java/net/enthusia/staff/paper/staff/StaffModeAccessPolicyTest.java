package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class StaffModeAccessPolicyTest {
    @Test
    void helperCannotMoveItemsOrUseAdvancedTools() {
        assertTrue(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.HELPER));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.HELPER, false));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.HELPER, true));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.HELPER));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.HELPER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.initialGameMode(StaffRank.HELPER));
        // Helpers choose between Survival and Spectator while on duty.
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.SURVIVAL));
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.SPECTATOR));
        assertFalse(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.CREATIVE));
        assertFalse(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.ADVENTURE));
    }

    @Test
    void developerKeepsSeparateModerationAuthorityButUnrestrictedTechnicalStaffMode() {
        assertFalse(StaffRank.DEVELOPER.canApprovePunishmentRequests());
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.DEVELOPER, false));
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.DEVELOPER, true));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.DEVELOPER));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.DEVELOPER));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.initialGameMode(StaffRank.DEVELOPER));
        for (GameMode gameMode : GameMode.values()) {
            assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.DEVELOPER, gameMode));
        }
    }

    @Test
    void onlyDeveloperGetsCombatTestingExceptionWithoutModerationAuthority() {
        assertTrue(StaffModeAccessPolicy.allowsCombatTesting(StaffRank.DEVELOPER));
        assertFalse(StaffModeAccessPolicy.allowsCombatTesting(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.allowsCombatTesting(StaffRank.MOD));
        assertFalse(StaffModeAccessPolicy.allowsCombatTesting(StaffRank.ADMIN));
        assertFalse(StaffModeAccessPolicy.allowsCombatTesting(StaffRank.FOUNDER));
        assertFalse(StaffRank.DEVELOPER.canApprovePunishmentRequests());
    }

    @Test
    void modCannotOpenOrMutateEnderChestInStaffMode() {
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.MOD, false));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.MOD, true));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.MOD));
        // Mods start in protected Survival but may switch to real Spectator.
        assertEquals(GameMode.SURVIVAL, StaffModeAccessPolicy.initialGameMode(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.MOD, GameMode.SURVIVAL));
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.MOD, GameMode.SPECTATOR));
        assertFalse(StaffModeAccessPolicy.allowsGameMode(StaffRank.MOD, GameMode.CREATIVE));
    }

    @Test
    void adminEnderChestIsViewOnlyAndFounderRetainsOwnerAccess() {
        assertFalse(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.ADMIN));
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.ADMIN, false));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.ADMIN, true));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.ADMIN));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.initialGameMode(StaffRank.ADMIN));

        assertFalse(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.FOUNDER));
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.FOUNDER, false));
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.FOUNDER, true));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.FOUNDER));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.FOUNDER));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.initialGameMode(StaffRank.FOUNDER));
    }

    @Test
    void adminAndFounderCanSelectAnyVanillaGameMode() {
        for (StaffRank rank : new StaffRank[]{StaffRank.ADMIN, StaffRank.FOUNDER}) {
            for (GameMode gameMode : GameMode.values()) {
                assertTrue(StaffModeAccessPolicy.allowsGameMode(rank, gameMode));
            }
        }
    }

    @Test
    void helperChoosesBetweenSurvivalAndSpectator() {
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.SURVIVAL));
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.SPECTATOR));
        assertFalse(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.CREATIVE));
        assertFalse(StaffModeAccessPolicy.allowsGameMode(StaffRank.HELPER, GameMode.ADVENTURE));
    }

    @Test
    void modChoosesBetweenProtectedSurvivalAndSpectator() {
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.MOD, GameMode.SURVIVAL));
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.MOD, GameMode.SPECTATOR));
        assertFalse(StaffModeAccessPolicy.allowsGameMode(StaffRank.MOD, GameMode.CREATIVE));
        assertFalse(StaffModeAccessPolicy.allowsGameMode(StaffRank.MOD, GameMode.ADVENTURE));
    }

    @Test
    void rankReconciliationPreservesAllowedModesAndOnlyCorrectsDisallowedOnes() {
        assertEquals(
                GameMode.SURVIVAL,
                StaffModeAccessPolicy.reconciledGameMode(StaffRank.ADMIN, GameMode.SURVIVAL)
        );
        assertEquals(
                GameMode.SPECTATOR,
                StaffModeAccessPolicy.reconciledGameMode(StaffRank.FOUNDER, GameMode.SPECTATOR)
        );
        assertEquals(
                GameMode.SURVIVAL,
                StaffModeAccessPolicy.reconciledGameMode(StaffRank.MOD, GameMode.CREATIVE)
        );
        assertEquals(
                GameMode.SPECTATOR,
                StaffModeAccessPolicy.reconciledGameMode(StaffRank.MOD, GameMode.SPECTATOR)
        );
    }

    @Test
    void currentItemAndCursorStaffToolsAlwaysBlockInventoryTransfer() {
        for (ClickType click : ClickType.values()) {
            assertTrue(StaffModeAccessPolicy.blocksStaffToolTransfer(click, true, false, false, false));
            assertTrue(StaffModeAccessPolicy.blocksStaffToolTransfer(click, false, true, false, false));
        }
    }

    @Test
    void numberKeyBlocksOnlyWhenReferencedHotbarItemIsStaffTool() {
        assertTrue(StaffModeAccessPolicy.blocksStaffToolTransfer(
                ClickType.NUMBER_KEY, false, false, true, false));
        assertFalse(StaffModeAccessPolicy.blocksStaffToolTransfer(
                ClickType.NUMBER_KEY, false, false, false, true));
        assertFalse(StaffModeAccessPolicy.blocksStaffToolTransfer(
                ClickType.NUMBER_KEY, false, false, false, false));
    }

    @Test
    void inventoryOffhandSwapBlocksOnlyWhenOffhandItemIsStaffTool() {
        assertTrue(StaffModeAccessPolicy.blocksStaffToolTransfer(
                ClickType.SWAP_OFFHAND, false, false, false, true));
        assertFalse(StaffModeAccessPolicy.blocksStaffToolTransfer(
                ClickType.SWAP_OFFHAND, false, false, true, false));
        assertFalse(StaffModeAccessPolicy.blocksStaffToolTransfer(
                ClickType.SWAP_OFFHAND, false, false, false, false));
    }

    @Test
    void unrelatedClickDoesNotBlockBecauseUnreferencedHotbarOrOffhandContainsStaffTool() {
        assertFalse(StaffModeAccessPolicy.blocksStaffToolTransfer(
                ClickType.LEFT, false, false, true, true));
    }

    @Test
    void systemRankDoesNotReceivePlayerInventoryOrEnderAccess() {
        assertTrue(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.SYSTEM));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.SYSTEM, false));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.SYSTEM));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.SYSTEM));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.SYSTEM, true));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.SYSTEM));
    }

    @Test
    void unresolvedRankFailsClosedForInventoryAndEnderAccess() {
        assertTrue(StaffModeAccessPolicy.blocksAllInventoryMutation(null));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(null, false));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(null));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(null));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(null, true));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(null));
    }
}
