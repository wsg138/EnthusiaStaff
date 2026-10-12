package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

class StaffModeDeniedPlayerInventoryAuditTest {
    @Test
    void helperLowerSlotActionHasOneOwnerWhenContainerOpen() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, InventoryAction.PICKUP_ALL, 33, 27, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.PICKUP_ALL, 33, 27));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, InventoryAction.MOVE_TO_OTHER_INVENTORY, 33, 27, true));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.MOVE_TO_OTHER_INVENTORY, 33, 27));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, InventoryAction.COLLECT_TO_CURSOR, 33, 27, true));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.COLLECT_TO_CURSOR, 33, 27));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, InventoryAction.PICKUP_ALL, 2, 27, true));
    }

    @Test
    void helperOrdinaryInventoryIsAuditedButNoOpAndUnrelatedTiersAreNot() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, InventoryAction.PICKUP_ALL, 2, 5, false));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, InventoryAction.NOTHING, 2, 5, false));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, null, 2, 5, false));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.HELPER, InventoryAction.PICKUP_ALL, -999, 27, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                StaffDutyTier.MOD, InventoryAction.PICKUP_ALL, 33, 27, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick(
                null, InventoryAction.PICKUP_ALL, 33, 27, true));
    }

    @Test
    void deniedDragUsesOnlyTopContainerOrLowerPlayerInventory() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryDrag(
                StaffDutyTier.HELPER, Set.of(31, 35), 27, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryDrag(
                StaffDutyTier.HELPER, Set.of(3, 31), 27, true));
        assertTrue(StaffModeWorldInteractionListener.touchesProtectedContainerSlot(
                Set.of(3, 31), 27));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryDrag(
                StaffDutyTier.HELPER, Set.of(1), 5, false));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryDrag(
                StaffDutyTier.HELPER, Set.of(), 27, true));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryDrag(
                StaffDutyTier.ADMIN, Set.of(31, 35), 27, true));
    }

    @Test
    void enderLowerInventoryIsNotConfusedWithTransferToEnderChest() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedEnderLowerClick(
                InventoryAction.PICKUP_ALL, 32, 27));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedEnderLowerClick(
                InventoryAction.MOVE_TO_OTHER_INVENTORY, 32, 27));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedEnderLowerClick(
                InventoryAction.COLLECT_TO_CURSOR, 32, 27));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedEnderLowerClick(
                InventoryAction.PICKUP_ALL, 4, 27));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedEnderLowerClick(
                InventoryAction.NOTHING, 32, 27));
    }

    @Test
    void bothCancelledOwnerPathsLogOnlyActionAndSlotDetails() throws IOException {
        String world = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeWorldInteractionListener.java"));
        String manager = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"));
        assertTrue(world.contains("else if (shouldAuditDeniedPlayerInventoryClick("));
        assertTrue(world.contains("else if (shouldAuditDeniedPlayerInventoryDrag("));
        assertTrue(world.contains("staffMode.logStaffAction(player, \"inventory-edit-denied\""));
        assertTrue(manager.contains("StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryClick("));
        assertTrue(manager.contains("StaffModeWorldInteractionListener.shouldAuditDeniedPlayerInventoryDrag("));
        assertTrue(manager.contains("audit(player, rank, \"inventory-edit-denied\""));
    }
}
