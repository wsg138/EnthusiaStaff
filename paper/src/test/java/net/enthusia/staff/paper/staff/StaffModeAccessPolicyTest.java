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
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.HELPER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.HELPER));
    }

    @Test
    void developerIsNotTreatedAsModerationApprovalHierarchyButKeepsTechnicalStaffMode() {
        assertFalse(StaffRank.DEVELOPER.canApprovePunishmentRequests());
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.DEVELOPER, false));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.DEVELOPER, true));
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.DEVELOPER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.DEVELOPER));
    }

    @Test
    void modCannotOpenOrMutateEnderChestInStaffMode() {
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.MOD, false));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.MOD, true));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.MOD));
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.MOD));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.MOD));
    }

    @Test
    void adminEnderChestIsViewOnlyAndFounderRetainsOwnerAccess() {
        assertFalse(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.ADMIN));
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.ADMIN, false));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.ADMIN, true));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.ADMIN));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.requiredGameMode(StaffRank.ADMIN));

        assertFalse(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.FOUNDER));
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.FOUNDER, false));
        assertFalse(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.FOUNDER, true));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.FOUNDER));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.FOUNDER));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.FOUNDER));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.requiredGameMode(StaffRank.FOUNDER));
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
    void systemRankDoesNotReceivePlayerEnderAccess() {
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.SYSTEM));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.SYSTEM));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(StaffRank.SYSTEM, true));
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.SYSTEM));
    }

    @Test
    void unresolvedRankFailsClosedForEnderChestAccess() {
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(null));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(null));
        assertTrue(StaffModeAccessPolicy.blocksInventoryMutation(null, true));
    }
}
