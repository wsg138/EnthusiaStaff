package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class LiveInventoryTransferExecutionTest {
    @Test
    void disconnectBeforeTargetMutationPreventsBothPhysicalMutations() {
        LiveInventoryTransferExecution execution = execution();

        assertEquals(
                LiveInventoryTransferExecution.AbortResult.NO_TARGET_CHANGE,
                execution.abort()
        );
        assertFalse(execution.applyTarget(() -> true));
        assertFalse(execution.applyCursor(() -> true));
    }

    @Test
    void disconnectAfterTargetMutationRequiresRollbackAndBlocksCursorDelivery() {
        LiveInventoryTransferExecution execution = execution();

        assertTrue(execution.applyTarget(() -> true));
        assertEquals(
                LiveInventoryTransferExecution.AbortResult.ROLLBACK_TARGET,
                execution.abort()
        );
        assertFalse(execution.applyCursor(() -> true));
        execution.rolledBack();
        assertFalse(execution.physicalTransferComplete());
    }

    @Test
    void cursorMismatchLeavesTargetInRollbackRequiredPhase() {
        LiveInventoryTransferExecution execution = execution();

        assertTrue(execution.applyTarget(() -> true));
        assertFalse(execution.applyCursor(() -> false));
        assertTrue(execution.targetAppliedWithoutCursor());
        assertEquals(
                LiveInventoryTransferExecution.AbortResult.ROLLBACK_TARGET,
                execution.abort()
        );
    }

    @Test
    void completedPhysicalTransferIsNeverRolledBackByLateDisconnect() {
        LiveInventoryTransferExecution execution = execution();

        assertTrue(execution.applyTarget(() -> true));
        assertTrue(execution.applyCursor(() -> true));
        assertTrue(execution.physicalTransferComplete());
        assertEquals(
                LiveInventoryTransferExecution.AbortResult.PHYSICAL_TRANSFER_COMPLETE,
                execution.abort()
        );
        execution.committed();
        assertTrue(execution.physicalTransferComplete());
    }

    private static LiveInventoryTransferExecution execution() {
        InventoryImage before = emptyImage().withItem(0, stack(Material.DIAMOND, 4));
        InventoryImage replacement = before.withItem(0, null);
        return new LiveInventoryTransferExecution(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationInventoryHolder.Kind.PLAYER,
                0,
                before,
                replacement,
                null,
                stack(Material.DIAMOND, 4),
                LiveInventoryTransferDecision.Action.PICKUP
        );
    }

    private static InventoryImage emptyImage() {
        return new InventoryImage(
                new ItemStack[InventoryImage.STORAGE_SIZE],
                new ItemStack[InventoryImage.ARMOR_SIZE],
                null,
                new ItemStack[InventoryImage.ENDER_SIZE],
                0
        );
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack item = ItemStack.of(material);
        item.setAmount(amount);
        return item;
    }
}
