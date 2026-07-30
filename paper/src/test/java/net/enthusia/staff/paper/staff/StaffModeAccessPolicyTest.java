package net.enthusia.staff.paper.staff;

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
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.HELPER) == GameMode.SPECTATOR);
    }

    @Test
    void developerIsNotTreatedAsModerationApprovalHierarchyButKeepsTechnicalStaffMode() {
        assertFalse(StaffRank.DEVELOPER.canApprovePunishmentRequests());
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.DEVELOPER) == GameMode.SPECTATOR);
    }

    @Test
    void onlyAdministrativeRanksUseCreativeStaffMode() {
        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.FOUNDER));
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.MOD) == GameMode.SPECTATOR);
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.ADMIN) == GameMode.CREATIVE);
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.FOUNDER) == GameMode.CREATIVE);
    }
}
