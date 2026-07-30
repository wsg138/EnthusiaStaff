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
        assertTrue(StaffModeAccessPolicy.blocksEnderChest(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.HELPER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.HELPER));
    }

    @Test
    void developerIsNotTreatedAsModerationApprovalHierarchyButKeepsTechnicalStaffMode() {
        assertFalse(StaffRank.DEVELOPER.canApprovePunishmentRequests());
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.DEVELOPER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.DEVELOPER));
    }

    @Test
    void onlyAdministrativeRanksUseCreativeStaffMode() {
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.FOUNDER));
        assertEquals(GameMode.SPECTATOR, StaffModeAccessPolicy.requiredGameMode(StaffRank.MOD));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.requiredGameMode(StaffRank.ADMIN));
        assertEquals(GameMode.CREATIVE, StaffModeAccessPolicy.requiredGameMode(StaffRank.FOUNDER));
    }
}
