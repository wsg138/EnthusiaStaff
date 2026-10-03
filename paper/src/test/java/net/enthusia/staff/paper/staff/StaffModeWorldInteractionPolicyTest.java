package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

class StaffModeWorldInteractionPolicyTest {
    @Test
    void helperTierKeepsFullLockdown() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.HELPER, Action.LEFT_CLICK_BLOCK));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.HELPER, Action.RIGHT_CLICK_BLOCK));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.HELPER, Action.PHYSICAL));
    }

    @Test
    void modTierIsLoggedNotBlockedForBlockEditsAndInteractions() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.MOD));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.MOD));
        assertTrue(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.LEFT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.RIGHT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.PHYSICAL));
    }

    @Test
    void adminTierIsUnrestrictedButLogged() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.ADMIN));
        assertTrue(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.ADMIN));
        for (Action action : Action.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(StaffDutyTier.ADMIN, action));
        }
    }

    @Test
    void unresolvedTierFailsClosed() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockEdit(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(null));
        assertFalse(StaffModeWorldInteractionPolicy.logsWorldInteraction(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(null, Action.RIGHT_CLICK_BLOCK));
    }

    @Test
    void everyTierKeepsAirClicksForStaffTools() {
        for (StaffDutyTier tier : StaffDutyTier.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                    tier, Action.LEFT_CLICK_AIR));
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                    tier, Action.RIGHT_CLICK_AIR));
        }
    }
}
