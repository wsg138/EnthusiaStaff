package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class LiveInventorySessionLifecycleTest {
    @Test
    void lastViewerClosingDuringReconcileBecomesRemovableAfterWorkFinishes() {
        UUID targetId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        InventoryImage image = emptyImage();
        InventoryObservation observation = observation(targetId);
        InventoryCoordinator.LiveSession session = new InventoryCoordinator.LiveSession(targetId);
        session.observed(observation, image);
        session.addViewer(new ModerationInventoryHolder(
                viewerId,
                targetId,
                "Target",
                ModerationInventoryHolder.Kind.PLAYER,
                false,
                observation,
                image
        ));

        assertTrue(session.beginReconcile());
        session.removeViewer(viewerId);
        assertFalse(session.removable());

        session.finishWork();

        assertTrue(session.removable());
    }

    private static InventoryObservation observation(UUID targetId) {
        return new InventoryObservation(
                UUID.randomUUID(),
                targetId,
                "survival",
                "paper-1",
                0L,
                "0".repeat(64),
                new byte[] {1},
                Instant.parse("2026-10-01T12:00:00Z")
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
}
