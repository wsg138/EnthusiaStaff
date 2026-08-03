package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class StaffModeAccessPolicyTest {
    @Test
    void helperCannotMoveItemsOrUseAdvancedTools() {
        assertTrue(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.HELPER));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.HELPER));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.HELPER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.HELPER));
    }

    @Test
    void developerIsNotTreatedAsModerationApprovalHierarchyButKeepsTechnicalStaffMode() {
        assertFalse(StaffRank.DEVELOPER.canApprovePunishmentRequests());
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.DEVELOPER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.DEVELOPER));
    }

    @Test
    void modCannotOpenOrMutateEnderChestInStaffMode() {
        assertTrue(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.MOD));
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.MOD));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.MOD));
    }

    @Test
    void adminEnderChestIsViewOnlyAndFounderRetainsOwnerAccess() {
        assertFalse(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.ADMIN));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.ADMIN));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.requiredGameMode(StaffRank.ADMIN));

        assertFalse(StaffModeAccessPolicy.blocksAllInventoryMutation(StaffRank.FOUNDER));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestOpen(StaffRank.FOUNDER));
        assertFalse(StaffModeAccessPolicy.blocksEnderChestMutation(StaffRank.FOUNDER));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.FOUNDER));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.requiredGameMode(StaffRank.FOUNDER));
    }
}
