package net.enthusia.staff.paper.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryCursorJournal;
import net.enthusia.staff.domain.inventory.InventoryCursorTransfer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

final class LiveCursorEscrow {
    private final CursorStackCodec codec = new CursorStackCodec();
    private final LiveCursorEscrowMarker marker;

    LiveCursorEscrow(JavaPlugin plugin) {
        marker = new LiveCursorEscrowMarker(plugin);
    }

    InventoryCursorTransfer durableTransfer(ItemStack expected, ItemStack replacement) {
        return codec.transfer(expected, replacement);
    }

    boolean escrowSource(Player viewer, LiveInventoryTransferExecution transfer) {
        ItemStack current = viewer.getItemOnCursor();
        if (!underlyingMatches(current, transfer.expectedCursor()) || marker.read(current).isPresent()) {
            return false;
        }
        ItemStack expected = transfer.expectedCursor();
        if (expected != null) {
            viewer.setItemOnCursor(marker.mark(expected, transfer.operationId(), LiveCursorEscrowMarker.Role.SOURCE));
            viewer.updateInventory();
            return sourceMatches(viewer.getItemOnCursor(), transfer);
        }
        return empty(current);
    }

    boolean applyResult(Player viewer, LiveInventoryTransferExecution transfer) {
        if (!sourceMatches(viewer.getItemOnCursor(), transfer)) {
            return false;
        }
        ItemStack result = transfer.resultingCursor();
        viewer.setItemOnCursor(result == null
                ? null
                : marker.mark(result, transfer.operationId(), LiveCursorEscrowMarker.Role.RESULT));
        viewer.updateInventory();
        return resultMatches(viewer.getItemOnCursor(), transfer);
    }

    boolean restoreSource(Player viewer, LiveInventoryTransferExecution transfer) {
        if (!sourceMatches(viewer.getItemOnCursor(), transfer)) {
            return false;
        }
        viewer.setItemOnCursor(transfer.expectedCursor());
        viewer.updateInventory();
        return underlyingMatches(viewer.getItemOnCursor(), transfer.expectedCursor())
                && marker.read(viewer.getItemOnCursor()).isEmpty();
    }

    boolean settleResult(Player viewer, LiveInventoryTransferExecution transfer) {
        if (!resultMatches(viewer.getItemOnCursor(), transfer)) {
            return false;
        }
        viewer.setItemOnCursor(transfer.resultingCursor());
        viewer.updateInventory();
        return underlyingMatches(viewer.getItemOnCursor(), transfer.resultingCursor())
                && marker.read(viewer.getItemOnCursor()).isEmpty();
    }

    RecoveryResult recover(Player viewer, InventoryCursorJournal journal, boolean targetApplied) {
        List<LocatedMarker> markers = locate(viewer, journal.patch().operationId());
        if (markers.size() > 1 || hasUnmarkedCursorConflict(viewer, markers)) {
            return RecoveryResult.CONFLICT;
        }
        ItemStack desired = codec.decode(targetApplied
                ? journal.cursorTransfer().replacementSnapshot()
                : journal.cursorTransfer().expectedSnapshot());
        LocatedMarker located = markers.isEmpty() ? null : markers.getFirst();
        ItemStack removed = located == null ? null : located.item();
        try {
            if (located != null) {
                write(viewer, located.location(), null);
            }
            viewer.setItemOnCursor(desired);
            viewer.updateInventory();
            String expectedChecksum = targetApplied
                    ? journal.cursorTransfer().replacementChecksum()
                    : journal.cursorTransfer().expectedChecksum();
            if (!codec.matches(viewer.getItemOnCursor(), expectedChecksum)) {
                rollbackRecovery(viewer, located, removed);
                return RecoveryResult.CONFLICT;
            }
            return RecoveryResult.APPLIED;
        } catch (RuntimeException exception) {
            rollbackRecovery(viewer, located, removed);
            return RecoveryResult.CONFLICT;
        }
    }

    List<LocatedMarker> locate(Player viewer, UUID operationId) {
        List<LocatedMarker> found = new ArrayList<>();
        addIfMatch(found, SlotLocation.cursor(), viewer.getItemOnCursor(), operationId);
        PlayerInventory inventory = viewer.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            addIfMatch(found, SlotLocation.storage(slot), storage[slot], operationId);
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            addIfMatch(found, SlotLocation.armor(slot), armor[slot], operationId);
        }
        addIfMatch(found, SlotLocation.offHand(), inventory.getItemInOffHand(), operationId);
        return List.copyOf(found);
    }

    private boolean sourceMatches(ItemStack current, LiveInventoryTransferExecution transfer) {
        ItemStack expected = transfer.expectedCursor();
        if (expected == null) {
            return empty(current);
        }
        return marker.matches(current, transfer.operationId(), LiveCursorEscrowMarker.Role.SOURCE)
                && underlyingMatches(current, expected);
    }

    private boolean resultMatches(ItemStack current, LiveInventoryTransferExecution transfer) {
        ItemStack result = transfer.resultingCursor();
        if (result == null) {
            return empty(current);
        }
        return marker.matches(current, transfer.operationId(), LiveCursorEscrowMarker.Role.RESULT)
                && underlyingMatches(current, result);
    }

    private boolean underlyingMatches(ItemStack marked, ItemStack expected) {
        ItemStack cleared = marker.clearAny(marked);
        return LiveInventoryTransferDecision.same(cleared, expected);
    }

    private boolean hasUnmarkedCursorConflict(Player viewer, List<LocatedMarker> markers) {
        ItemStack cursor = viewer.getItemOnCursor();
        if (empty(cursor)) {
            return false;
        }
        return markers.stream().noneMatch(value -> value.location().kind() == SlotKind.CURSOR);
    }

    private void addIfMatch(
            List<LocatedMarker> found,
            SlotLocation location,
            ItemStack item,
            UUID operationId
    ) {
        marker.read(item)
                .filter(value -> value.operationId().equals(operationId))
                .ifPresent(value -> found.add(new LocatedMarker(location, item.clone(), value.role())));
    }

    private static void write(Player viewer, SlotLocation location, ItemStack item) {
        PlayerInventory inventory = viewer.getInventory();
        switch (location.kind()) {
            case CURSOR -> viewer.setItemOnCursor(item);
            case STORAGE -> inventory.setItem(location.index(), item);
            case ARMOR -> {
                ItemStack[] armor = inventory.getArmorContents();
                armor[location.index()] = item;
                inventory.setArmorContents(armor);
            }
            case OFF_HAND -> inventory.setItemInOffHand(item);
        }
    }

    private static void rollbackRecovery(Player viewer, LocatedMarker located, ItemStack removed) {
        if (located != null) {
            write(viewer, located.location(), removed);
        }
        viewer.updateInventory();
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    enum RecoveryResult {
        APPLIED,
        CONFLICT
    }

    enum SlotKind {
        CURSOR,
        STORAGE,
        ARMOR,
        OFF_HAND
    }

    record SlotLocation(SlotKind kind, int index) {
        SlotLocation {
            Objects.requireNonNull(kind, "kind");
            if ((kind == SlotKind.STORAGE || kind == SlotKind.ARMOR) && index < 0) {
                throw new IllegalArgumentException("indexed escrow slot cannot be negative");
            }
        }

        static SlotLocation cursor() {
            return new SlotLocation(SlotKind.CURSOR, -1);
        }

        static SlotLocation storage(int slot) {
            return new SlotLocation(SlotKind.STORAGE, slot);
        }

        static SlotLocation armor(int slot) {
            return new SlotLocation(SlotKind.ARMOR, slot);
        }

        static SlotLocation offHand() {
            return new SlotLocation(SlotKind.OFF_HAND, -1);
        }
    }

    record LocatedMarker(SlotLocation location, ItemStack item, LiveCursorEscrowMarker.Role role) {
        LocatedMarker {
            Objects.requireNonNull(location, "location");
            item = Objects.requireNonNull(item, "item").clone();
            Objects.requireNonNull(role, "role");
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }
    }
}
