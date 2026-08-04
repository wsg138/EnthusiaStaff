package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

class StaffModeWorldInteractionPolicyTest {
    @Test
    void ordinaryPlayersKeepAllWorldInteractions() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksMutation(false));
        for (Action action : Action.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(false, action));
        }
    }

    @Test
    void activeStaffModeBlocksWorldMutations() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksMutation(true));
    }

    @Test
    void activeStaffModeAllowsAirClicksButBlocksBlockAndPhysicalInteractions() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(true, Action.LEFT_CLICK_AIR));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(true, Action.RIGHT_CLICK_AIR));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(true, Action.LEFT_CLICK_BLOCK));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(true, Action.RIGHT_CLICK_BLOCK));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(true, Action.PHYSICAL));
    }
}
