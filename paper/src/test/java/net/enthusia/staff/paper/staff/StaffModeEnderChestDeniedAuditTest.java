package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class StaffModeEnderChestDeniedAuditTest {
    @Test
    void onlyRanksDeniedByEnderChestPolicyWithoutExistingContainerDenialAreLogged() {
        assertTrue(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.ADMIN, true, true));
        assertTrue(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.MOD, true, true));
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.HELPER, true, true));
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.DEVELOPER, true, true));
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.FOUNDER, true, true));
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.SYSTEM, true, true));
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                null, true, true));
    }

    @Test
    void ordinaryInventoryOrNoOpCannotProduceDeniedEnderChestEditRecord() {
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.ADMIN, false, true));
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.ADMIN, true, false));
        assertFalse(StaffModeManager.shouldAuditDeniedEnderChestEdit(
                StaffRank.MOD, false, true));
    }

    @Test
    void bothEnforcementPathsRecordSafeDetailsWithoutItemData() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"));
        assertTrue(source.contains("event.getAction(), event.getRawSlot(),"));
        assertTrue(source.contains("StaffModeWorldInteractionListener.touchesProtectedContainerSlot("));
        assertTrue(source.contains("\"click=\" + event.getClick() + \" action=\" + event.getAction()"));
        assertTrue(source.contains("\"drag affected-slots=\" + event.getRawSlots().size()"));
        assertTrue(source.contains("audit(player, rank, \"ender-chest-edit-denied\""));
    }
}
