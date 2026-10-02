package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class ModerationInventoryHolderMappingTest {
    @Test
    void playerViewMapsStorageArmorAndOffhandButLeavesUnusedCellsUnmapped() {
        ModerationInventoryHolder holder = holder(ModerationInventoryHolder.Kind.PLAYER);

        assertEquals(0, holder.logicalSlot(0));
        assertEquals(35, holder.logicalSlot(35));
        assertEquals(36, holder.logicalSlot(36));
        assertEquals(39, holder.logicalSlot(39));
        assertEquals(InventoryImage.OFFHAND_SLOT, holder.logicalSlot(40));
        for (int rawSlot = 41; rawSlot < 54; rawSlot++) {
            assertEquals(-1, holder.logicalSlot(rawSlot));
        }
    }

    @Test
    void enderViewMapsExactlyTwentySevenSlots() {
        ModerationInventoryHolder holder = holder(ModerationInventoryHolder.Kind.ENDER_CHEST);

        assertEquals(InventoryImage.ENDER_OFFSET, holder.logicalSlot(0));
        assertEquals(InventoryImage.ENDER_OFFSET + 26, holder.logicalSlot(26));
        assertEquals(-1, holder.logicalSlot(27));
    }

    private static ModerationInventoryHolder holder(ModerationInventoryHolder.Kind kind) {
        UUID targetId = UUID.randomUUID();
        InventoryObservation observation = new InventoryObservation(
                UUID.randomUUID(),
                targetId,
                "survival",
                "paper-test",
                0L,
                "0".repeat(64),
                new byte[]{1},
                Instant.EPOCH
        );
        InventoryImage image = new InventoryImage(
                new ItemStack[InventoryImage.STORAGE_SIZE],
                new ItemStack[InventoryImage.ARMOR_SIZE],
                null,
                new ItemStack[InventoryImage.ENDER_SIZE],
                0
        );
        return new ModerationInventoryHolder(
                UUID.randomUUID(), targetId, "Target", kind, false, observation, image
        );
    }
}
