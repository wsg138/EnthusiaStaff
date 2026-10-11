package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.Test;

class StaffToolTransferAuditTest {
    @Test
    void auditRunsAfterCancellationAndIncludesAlreadyCancelledAttempts() throws Exception {
        Method audit = StaffToolTransferListener.class.getMethod(
                "onDeniedToolTransferAudit", InventoryClickEvent.class);
        EventHandler registration = audit.getAnnotation(EventHandler.class);
        assertNotNull(registration);
        assertEquals(EventPriority.MONITOR, registration.priority());
        assertFalse(registration.ignoreCancelled());
    }

    @Test
    void auditDetailContainsOnlyClickAndContainerTypeNotItemData() {
        assertEquals("click=NUMBER_KEY container=CHEST",
                StaffToolTransferListener.transferAuditDetail(ClickType.NUMBER_KEY, "CHEST"));
        assertEquals("click=SWAP_OFFHAND container=PLAYER",
                StaffToolTransferListener.transferAuditDetail(ClickType.SWAP_OFFHAND, "PLAYER"));
    }
}
