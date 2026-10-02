package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class LiveInventorySurfaceSyncTest {
    @Test
    void targetChangesRouteOnlyToTheRelevantInventorySurface() {
        assertEquals(
                EnumSet.of(ModerationInventoryHolder.Kind.PLAYER),
                InventoryCoordinator.kindsForChangedSlots(List.of(0, 36, InventoryImage.OFFHAND_SLOT))
        );
        assertEquals(
                EnumSet.of(ModerationInventoryHolder.Kind.ENDER_CHEST),
                InventoryCoordinator.kindsForChangedSlots(List.of(
                        InventoryImage.ENDER_OFFSET,
                        InventoryImage.ENDER_OFFSET + InventoryImage.ENDER_SIZE - 1
                ))
        );
        assertEquals(
                EnumSet.allOf(ModerationInventoryHolder.Kind.class),
                InventoryCoordinator.kindsForChangedSlots(List.of(0, InventoryImage.ENDER_OFFSET))
        );
    }

    @Test
    void staffEditFanoutIncludesEveryViewerOnTheSameSurfaceOnly() {
        UUID targetId = UUID.randomUUID();
        UUID firstPlayerViewer = UUID.randomUUID();
        UUID secondPlayerViewer = UUID.randomUUID();
        UUID enderViewer = UUID.randomUUID();
        InventoryObservation observation = observation(targetId);
        InventoryImage image = emptyImage();
        InventoryCoordinator.LiveSession session = new InventoryCoordinator.LiveSession(targetId);
        session.observed(observation, image);
        session.addViewer(holder(firstPlayerViewer, targetId, ModerationInventoryHolder.Kind.PLAYER, observation, image));
        session.addViewer(holder(secondPlayerViewer, targetId, ModerationInventoryHolder.Kind.PLAYER, observation, image));
        session.addViewer(holder(enderViewer, targetId, ModerationInventoryHolder.Kind.ENDER_CHEST, observation, image));

        Set<UUID> playerViewers = viewerIds(session.viewers(
                EnumSet.of(ModerationInventoryHolder.Kind.PLAYER)
        ));
        Set<UUID> enderViewers = viewerIds(session.viewers(
                EnumSet.of(ModerationInventoryHolder.Kind.ENDER_CHEST)
        ));

        assertEquals(Set.of(firstPlayerViewer, secondPlayerViewer), playerViewers);
        assertEquals(Set.of(enderViewer), enderViewers);
    }

    private static Set<UUID> viewerIds(List<ModerationInventoryHolder> holders) {
        return holders.stream()
                .map(ModerationInventoryHolder::viewerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ModerationInventoryHolder holder(
            UUID viewerId,
            UUID targetId,
            ModerationInventoryHolder.Kind kind,
            InventoryObservation observation,
            InventoryImage image
    ) {
        return new ModerationInventoryHolder(
                viewerId, targetId, "Target", kind, false, observation, image
        );
    }

    private static InventoryObservation observation(UUID targetId) {
        return new InventoryObservation(
                UUID.randomUUID(), targetId, "survival", "paper-test",
                0L, "0".repeat(64), new byte[] {1}, Instant.EPOCH
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
