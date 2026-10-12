package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

class StaffModeInventoryAuditOwnershipTest {
    @Test
    void protectedContainerClickUsesOnlyTheSpecializedSuccessfulAudit() {
        int topSize = 27;
        boolean specialized = StaffModeWorldInteractionListener.shouldAuditCompletedContainerEdit(
                false, StaffDutyTier.MOD)
                && StaffModeWorldInteractionListener.shouldAuditCompletedContainerClick(
                        InventoryAction.PICKUP_ALL, 3, topSize);
        assertTrue(specialized);
        assertFalse(StaffModeManager.shouldAuditGenericInventoryClick(
                InventoryAction.PICKUP_ALL, specialized));
    }

    @Test
    void shiftTransferFromBottomStillHasOneContainerOwner() {
        int topSize = 27;
        boolean specialized = StaffModeWorldInteractionListener.shouldAuditCompletedContainerEdit(
                false, StaffDutyTier.MOD)
                && StaffModeWorldInteractionListener.shouldAuditCompletedContainerClick(
                        InventoryAction.MOVE_TO_OTHER_INVENTORY, 33, topSize);
        assertTrue(specialized);
        assertFalse(StaffModeManager.shouldAuditGenericInventoryClick(
                InventoryAction.MOVE_TO_OTHER_INVENTORY, specialized));
    }

    @Test
    void ordinaryLowerInventoryClickKeepsGenericAuditAndNoOpStaysSilent() {
        assertFalse(StaffModeWorldInteractionListener.shouldAuditCompletedContainerClick(
                InventoryAction.PICKUP_ALL, 32, 27));
        assertTrue(StaffModeManager.shouldAuditGenericInventoryClick(
                InventoryAction.PICKUP_ALL, false));
        assertFalse(StaffModeManager.shouldAuditGenericInventoryClick(
                InventoryAction.NOTHING, false));
        assertFalse(StaffModeManager.shouldAuditGenericInventoryClick(null, false));
    }

    @Test
    void dragOwnershipUsesTopSlotContactNotUnrelatedBottomSlots() {
        assertTrue(StaffModeWorldInteractionListener.touchesProtectedContainerSlot(
                Set.of(2, 33), 27));
        assertFalse(StaffModeWorldInteractionListener.touchesProtectedContainerSlot(
                Set.of(27, 33), 27));
    }

    @Test
    void specializedAuditRetainsMaterialAndAmountWithoutSensitiveItemMetadata()
            throws IOException {
        // Native ItemStack construction relies on a live Bukkit registry. The
        // simple descriptor contract is source-checked in this offline unit test.
        Path manager = Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java");
        String managerSource = Files.readString(manager);
        assertTrue(managerSource.contains("return item.getType() + \"x\" + item.getAmount();"));
        assertTrue(managerSource.contains("return \"empty\";"));
        Path world = Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeWorldInteractionListener.java");
        String source = Files.readString(world);
        assertTrue(source.contains("StaffModeManager.describe(event.getCurrentItem())"));
        assertTrue(source.contains("StaffModeManager.describe(event.getOldCursor())"));
    }

    @Test
    void genericListenerDelegatesToSpecializedOwnerOnlyForActiveStaffSessions()
            throws IOException {
        Path manager = Path.of("src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java");
        String source = Files.readString(manager).replace("\r\n", "\n");
        assertTrue(source.contains("active.containsKey(player.getUniqueId())"));
        assertTrue(source.contains("StaffModeWorldInteractionListener.isProtectedContainerInventory(top)"));
        assertTrue(source.contains("StaffModeWorldInteractionListener.shouldAuditCompletedContainerClick("));
        assertTrue(source.contains("StaffModeWorldInteractionListener.touchesProtectedContainerSlot("));
        assertTrue(source.contains("dedicatedContainerClickAuditOwner(player, true, event)"));
        assertTrue(source.contains("dedicatedContainerClickAuditOwner(player, false, event)"));
        assertTrue(source.contains("dedicatedContainerDragAuditOwner(player, true, event)"));
        assertTrue(source.contains("dedicatedContainerDragAuditOwner(player, false, event)"));
    }
}
