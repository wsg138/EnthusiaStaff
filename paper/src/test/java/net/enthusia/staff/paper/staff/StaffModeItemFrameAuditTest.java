package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

class StaffModeItemFrameAuditTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffModeWorldInteractionListener.java");

    @Test
    void mainHandGenericFrameInteractionIsOnlyDenialAuditCandidate() {
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedItemFrame(EquipmentSlot.HAND));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedItemFrame(EquipmentSlot.OFF_HAND));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedItemFrame(null));
    }

    @Test
    void preciseEntityVariantNeverLogsAnotherDeniedItemFrameEdit() throws IOException {
        String code = Files.readString(SOURCE);
        String generic = between(code, "public void onInteractEntity(", "public void onInteractAtEntity(");
        String precise = between(code, "public void onInteractAtEntity(", "public void onArmorStandManipulate(");
        assertTrue(generic.contains("shouldAuditDeniedItemFrame(event.getHand())"));
        assertFalse(precise.contains("shouldAuditDeniedItemFrame("));
        assertTrue(precise.contains("handleContainerEntity(player, event, event.getRightClicked().getType().toString(), false)"));
    }

    @Test
    void blockedFrameEditUsesExistingTierPolicyAndDoesNotEmitArmorStandDuplicate() throws IOException {
        String code = Files.readString(SOURCE);
        String handler = between(code, "private void handleContainerEntity(", "private boolean onDuty(");
        assertTrue(handler.contains("StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier)"));
        assertTrue(handler.contains("event.setCancelled(true);"));
        assertTrue(handler.contains("if (auditDeniedItemFrame)"));
        assertTrue(handler.contains("staffMode.logStaffAction(player, \"item-frame-edit-denied\", detail)"));
        assertFalse(handler.contains("armor-stand-edit-denied"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int until = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && until > from);
        return source.substring(from, until);
    }
}
