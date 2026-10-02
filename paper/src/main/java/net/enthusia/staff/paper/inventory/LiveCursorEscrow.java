package net.enthusia.staff.paper.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import net.enthusia.staff.domain.inventory.InventoryCursorJournal;
import net.enthusia.staff.domain.inventory.InventoryCursorTransfer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

final class LiveCursorEscrow {
    private static final int SINGLE_MARKER = 1;

    private final JavaPlugin plugin;
    private final CursorStackCodec codec = new CursorStackCodec();
    private final LiveCursorEscrowMarker marker;

    LiveCursorEscrow(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
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
        if (expected == null) {
            return empty(current);
        }
        viewer.setItemOnCursor(marker.mark(
                expected,
                transfer.operationId(),
                LiveCursorEscrowMarker.Role.SOURCE
        ));
        viewer.updateInventory();
        return sourceMatches(viewer.getItemOnCursor(), transfer);
    }

    boolean applyResult(Player viewer, LiveInventoryTransferExecution transfer) {
        if (!sourceMatches(viewer.getItemOnCursor(), transfer)) {
            return false;
        }
        ItemStack result = transfer.resultingCursor();
        viewer.setItemOnCursor(markResult(result, transfer.operationId()));
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

    RecoveryResult recoverSource(Player viewer, InventoryCursorJournal journal) {
        List<LocatedMarker> markers = locate(viewer, journal.patch().operationId());
        if (markers.size() > SINGLE_MARKER) {
            return RecoveryResult.CONFLICT;
        }
        if (markers.isEmpty()) {
            return codec.matches(
                    viewer.getItemOnCursor(),
                    journal.cursorTransfer().expectedChecksum()
            ) ? RecoveryResult.SOURCE_RESTORED : RecoveryResult.CONFLICT;
        }
        return replaceLocatedMarker(
                viewer,
                journal,
                markers.getFirst(),
                codec.decode(journal.cursorTransfer().expectedSnapshot()),
                journal.cursorTransfer().expectedChecksum(),
                false
        ) ? RecoveryResult.SOURCE_RESTORED : RecoveryResult.CONFLICT;
    }

    RecoveryResult recoverResult(Player viewer, InventoryCursorJournal journal) {
        List<LocatedMarker> markers = locate(viewer, journal.patch().operationId());
        if (markers.size() > SINGLE_MARKER) {
            return RecoveryResult.CONFLICT;
        }
        if (markers.isEmpty()) {
            return recoverUnmarkedResult(viewer, journal);
        }
        ItemStack desired = codec.decode(journal.cursorTransfer().replacementSnapshot());
        boolean replaced = replaceLocatedMarker(
                viewer,
                journal,
                markers.getFirst(),
                markResult(desired, journal.patch().operationId()),
                journal.cursorTransfer().replacementChecksum(),
                true
        );
        return replaced ? RecoveryResult.RESULT_MARKED : RecoveryResult.CONFLICT;
    }

    boolean settleRecoveredResult(Player viewer, InventoryCursorJournal journal) {
        UUID operationId = journal.patch().operationId();
        List<LocatedMarker> markers = locate(viewer, operationId);
        if (markers.isEmpty()) {
            return codec.matches(
                    viewer.getItemOnCursor(),
                    journal.cursorTransfer().replacementChecksum()
            );
        }
        if (markers.size() != SINGLE_MARKER) {
            return false;
        }
        LocatedMarker located = markers.getFirst();
        if (located.location().kind() != SlotKind.CURSOR
                || located.role() != LiveCursorEscrowMarker.Role.RESULT
                || !underlyingChecksumMatches(
                        located.item(),
                        journal.cursorTransfer().replacementChecksum()
                )) {
            return false;
        }
        viewer.setItemOnCursor(marker.clear(located.item(), operationId));
        viewer.updateInventory();
        return codec.matches(
                viewer.getItemOnCursor(),
                journal.cursorTransfer().replacementChecksum()
        );
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

    private RecoveryResult recoverUnmarkedResult(Player viewer, InventoryCursorJournal journal) {
        InventoryCursorTransfer transfer = journal.cursorTransfer();
        ItemStack cursor = viewer.getItemOnCursor();
        if (codec.matches(cursor, transfer.replacementChecksum())) {
            return RecoveryResult.RESULT_ALREADY_SETTLED;
        }
        if (!codec.matches(cursor, transfer.expectedChecksum())) {
            return RecoveryResult.CONFLICT;
        }
        ItemStack desired = codec.decode(transfer.replacementSnapshot());
        viewer.setItemOnCursor(markResult(desired, journal.patch().operationId()));
        viewer.updateInventory();
        return underlyingChecksumMatches(
                viewer.getItemOnCursor(),
                transfer.replacementChecksum()
        ) && marker.matches(
                viewer.getItemOnCursor(),
                journal.patch().operationId(),
                LiveCursorEscrowMarker.Role.RESULT
        ) ? RecoveryResult.RESULT_MARKED : RecoveryResult.CONFLICT;
    }

    private boolean replaceLocatedMarker(
            Player viewer,
            InventoryCursorJournal journal,
            LocatedMarker located,
            ItemStack desired,
            String desiredChecksum,
            boolean preserveResultMarker
    ) {
        ItemStack originalCursor = copy(viewer.getItemOnCursor());
        if (located.location().kind() != SlotKind.CURSOR && !empty(originalCursor)) {
            return false;
        }
        try {
            write(viewer, located.location(), null);
            viewer.setItemOnCursor(desired);
            viewer.updateInventory();
            boolean checksumMatches = preserveResultMarker
                    ? underlyingChecksumMatches(viewer.getItemOnCursor(), desiredChecksum)
                    : codec.matches(viewer.getItemOnCursor(), desiredChecksum);
            boolean markerStateMatches = preserveResultMarker
                    ? marker.matches(
                            viewer.getItemOnCursor(),
                            journal.patch().operationId(),
                            LiveCursorEscrowMarker.Role.RESULT
                    )
                    : locate(viewer, journal.patch().operationId()).isEmpty();
            if (checksumMatches && markerStateMatches) {
                return true;
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.FINE,
                    "Live cursor escrow mutation failed; restoring the exact before-state",
                    exception
            );
        }
        restoreRecoveryBeforeState(viewer, located, originalCursor);
        return false;
    }

    private void restoreRecoveryBeforeState(
            Player viewer,
            LocatedMarker located,
            ItemStack originalCursor
    ) {
        viewer.setItemOnCursor(originalCursor);
        if (located.location().kind() != SlotKind.CURSOR) {
            write(viewer, located.location(), located.item());
        }
        viewer.updateInventory();
    }

    private boolean sourceMatches(ItemStack current, LiveInventoryTransferExecution transfer) {
        ItemStack expected = transfer.expectedCursor();
        if (expected == null) {
            return empty(current);
        }
        return marker.matches(
                current,
                transfer.operationId(),
                LiveCursorEscrowMarker.Role.SOURCE
        ) && underlyingMatches(current, expected);
    }

    private boolean resultMatches(ItemStack current, LiveInventoryTransferExecution transfer) {
        ItemStack result = transfer.resultingCursor();
        if (result == null) {
            return empty(current);
        }
        return marker.matches(
                current,
                transfer.operationId(),
                LiveCursorEscrowMarker.Role.RESULT
        ) && underlyingMatches(current, result);
    }

    private ItemStack markResult(ItemStack item, UUID operationId) {
        return item == null ? null : marker.mark(item, operationId, LiveCursorEscrowMarker.Role.RESULT);
    }

    private boolean underlyingMatches(ItemStack marked, ItemStack expected) {
        return LiveInventoryTransferDecision.same(marker.clearAny(marked), expected);
    }

    private boolean underlyingChecksumMatches(ItemStack marked, String checksum) {
        return codec.matches(marker.clearAny(marked), checksum);
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
            default -> throw new IllegalStateException("Unsupported cursor escrow slot kind");
        }
    }

    private static ItemStack copy(ItemStack item) {
        return empty(item) ? null : item.clone();
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    enum RecoveryResult {
        SOURCE_RESTORED,
        RESULT_MARKED,
        RESULT_ALREADY_SETTLED,
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
