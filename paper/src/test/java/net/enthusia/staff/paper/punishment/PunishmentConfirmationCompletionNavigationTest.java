package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

/** Offline contract: confirmation may not close a later, unrelated inventory. */
class PunishmentConfirmationCompletionNavigationTest {
    @Test
    void onlyExactConfirmationSourceInventoryCanBeClosedAfterWorkerFinishes() {
        Inventory original = inventory();
        Inventory replacement = inventory();
        assertTrue(PunishmentGuiController.confirmedInventoryStillOpen(original, original));
        assertFalse(PunishmentGuiController.confirmedInventoryStillOpen(original, replacement));
        assertFalse(PunishmentGuiController.confirmedInventoryStillOpen(original, null));
        assertFalse(PunishmentGuiController.confirmedInventoryStillOpen(null, original));
    }

    @Test
    void allCommitAndRecoveryOutcomesPassCapturedInventoryToFinalCallback() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
        String confirmation = controller.substring(controller.indexOf("private void confirm(Player viewer,"),
                controller.indexOf("private void announceNewlyCommitted("));
        assertTrue(confirmation.contains("Inventory confirmationOrigin = viewer.getOpenInventory().getTopInventory()"),
                "Capture source inventory on click before database work enters queue");
        assertTrue(confirmation.contains("runConfirmation(viewer, actor, state, viewerId, confirmationOrigin)"));
        assertTrue(confirmation.contains("confirmOnce(viewer, actor, state, confirmationOrigin)"));
        assertTrue(confirmation.contains("confirmDraft(viewer, actor, state, workflow, confirmationOrigin)"));
        assertTrue(confirmation.contains("handleConfirmation(viewer, actor, state, value, confirmationOrigin)"));
        assertTrue(confirmation.contains("finish(viewer,") && confirmation.contains("confirmationOrigin"),
                "Normal and cleanup-recovery responses must preserve original inventory identity");

        String finish = controller.substring(controller.indexOf("private void finish(Player viewer,"),
                controller.indexOf("private boolean submit(Player viewer,"));
        assertTrue(finish.contains("confirmedInventoryStillOpen(confirmationOrigin,"),
                "The deferred callback must recheck the visible inventory");
        int gate = finish.indexOf("if (confirmedInventoryStillOpen(");
        int clear = finish.indexOf("guidedReturns.remove(");
        int close = finish.indexOf("closeWithoutResume(");
        assertTrue(gate >= 0 && clear > gate && close > gate,
                "Never clear another guided review or close an unrelated inventory");
        assertTrue(finish.contains("viewer.sendMessage("),
                "Still deliver committed outcome even when player navigated elsewhere");
    }

    private static Inventory inventory() {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (self, method, args) -> {
                    if ("toString".equals(method.getName())) return "stub-inventory";
                    throw new AssertionError("Unexpected inventory API call: " + method.getName());
                }
        );
    }
}
