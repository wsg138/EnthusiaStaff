package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

class StaffModeWorldInteractionPolicyTest {
    @Test
    void helperTierKeepsFullLockdown() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.allowsContainerView(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.HELPER));
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
        assertFalse(StaffModeWorldInteractionPolicy.allowsContainerView(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.MOD));
        assertTrue(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.LEFT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.RIGHT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.PHYSICAL));
    }

    @Test
    void developerRankMapsToDedicatedTechnicalTier() {
        assertEquals(StaffDutyTier.DEVELOPER, StaffDutyTier.of(StaffRank.DEVELOPER));
    }

    @Test
    void developerTierIsUnrestrictedButLoggedForTechnicalTesting() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.DEVELOPER));
        assertTrue(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.allowsContainerView(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.DEVELOPER));
        assertTrue(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.DEVELOPER));
        for (Action action : Action.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(StaffDutyTier.DEVELOPER, action));
        }
    }

    @Test
    void adminTierIsUnrestrictedButLogged() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.ADMIN));
        assertTrue(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.ADMIN));
        assertTrue(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.ADMIN));
        for (Action action : Action.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(StaffDutyTier.ADMIN, action));
        }
    }

    @Test
    void unresolvedTierFailsClosed() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockEdit(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(null));
        assertFalse(StaffModeWorldInteractionPolicy.logsWorldInteraction(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEdit(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(null));
        assertFalse(StaffModeWorldInteractionPolicy.logsContainerEdit(null));
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
