package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

class StaffToolContainerInteractionTest {
    @Test
    void rightClickingContainersDoesNotActivateTheHeldStaffTool() {
        assertTrue(StaffToolDispatcher.prefersContainerOpen(Action.RIGHT_CLICK_BLOCK, true));
        assertFalse(StaffToolDispatcher.prefersContainerOpen(Action.RIGHT_CLICK_BLOCK, false));
        assertFalse(StaffToolDispatcher.prefersContainerOpen(Action.RIGHT_CLICK_AIR, true));
        assertFalse(StaffToolDispatcher.prefersContainerOpen(Action.LEFT_CLICK_BLOCK, true));
    }

    @Test
    void helperContainerViewPolicyStaysAvailableButEditingRemainsRestricted() {
        assertTrue(StaffModeWorldInteractionPolicy.allowsContainerView(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.DEVELOPER));
    }

    @Test
    void protectedStaffItemsCannotBeTransferredViaOffhandSwap() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"));
        assertTrue(source.contains("isStaffTool(event.getMainHandItem())"));
        assertTrue(source.contains("isStaffTool(event.getOffHandItem())"));
    }
}
