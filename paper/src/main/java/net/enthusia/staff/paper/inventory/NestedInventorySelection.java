package net.enthusia.staff.paper.inventory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class NestedInventorySelection {
    private static final int MAX_VISITED_STACKS = 16_384;

    private NestedInventorySelection() {
    }

    public static ItemStack item(InventoryImage image, ItemPath path) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(path, "path");
        ItemStack current = image.item(path.rootSlot());
        for (int childSlot : path.nestedSlots()) {
            ContainerView view = container(current).orElseThrow(
                    () -> new IllegalArgumentException("item path traverses a non-container")
            );
            if (childSlot >= view.items().size()) {
                throw new IllegalArgumentException("item path traverses a missing container slot");
            }
            current = copy(view.items().get(childSlot));
        }
        return copy(current);
    }

    public static boolean isContainer(ItemStack item) {
        return container(item).isPresent();
    }

    public static List<ItemStack> children(ItemStack item) {
        return container(item)
                .map(view -> view.items().stream().map(NestedInventorySelection::copy).toList())
                .orElse(List.of());
    }

    public static SelectionResult remove(InventoryImage image, Set<ItemPath> selectedPaths) {
        Objects.requireNonNull(image, "image");
        Set<ItemPath> paths = Set.copyOf(Objects.requireNonNull(selectedPaths, "selectedPaths"));
        if (paths.isEmpty() || paths.size() > MAX_VISITED_STACKS) {
            throw new IllegalArgumentException("selected item path count is invalid");
        }
        Map<Integer, SelectionNode> roots = buildTree(paths);
        InventoryImage replacement = image;
        List<ConfiscatedAssetEntry> entries = new ArrayList<>();
        List<Integer> changedRoots = new ArrayList<>();
        VisitCounter counter = new VisitCounter();
        for (Map.Entry<Integer, SelectionNode> root : roots.entrySet()) {
            int slot = root.getKey();
            ItemStack original = image.item(slot);
            ItemStack changed = mutate(
                    original,
                    root.getValue(),
                    ItemPath.root(slot),
                    entries,
                    counter
            );
            if (Objects.equals(original, changed)) {
                throw new IllegalArgumentException("selected path did not change its root item");
            }
            replacement = replacement.withItem(slot, changed);
            changedRoots.add(slot);
        }
        return new SelectionResult(replacement, entries, changedRoots);
    }

    public static RestorationResult restore(
            InventoryImage image,
            List<ConfiscatedAssetEntry> confiscatedAssets
    ) {
        Objects.requireNonNull(image, "image");
        List<ConfiscatedAssetEntry> entries = new ArrayList<>(
                Objects.requireNonNull(confiscatedAssets, "confiscatedAssets")
        );
        if (entries.isEmpty() || entries.size() > MAX_VISITED_STACKS) {
            throw new IllegalArgumentException("confiscated asset entry count is invalid");
        }
        entries.sort(Comparator.comparing(ConfiscatedAssetEntry::path));
        InventoryImage replacement = image;
        Set<Integer> changedRoots = new java.util.LinkedHashSet<>();
        for (ConfiscatedAssetEntry entry : entries) {
            if (!fingerprint(entry.item()).equals(entry.fingerprint())) {
                throw new IllegalArgumentException("confiscated asset fingerprint is invalid");
            }
            Placement placement = placeAtOriginalPath(replacement, entry);
            if (placement == null) {
                placement = placeAtSafeRoot(replacement, entry.item());
            }
            replacement = placement.image();
            changedRoots.add(placement.rootSlot());
        }
        return new RestorationResult(replacement, List.copyOf(changedRoots));
    }

    public static String fingerprint(ItemStack item) {
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("cannot fingerprint an empty item");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(item.serializeAsBytes())
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ItemStack mutate(
            ItemStack current,
            SelectionNode selection,
            ItemPath path,
            List<ConfiscatedAssetEntry> entries,
            VisitCounter counter
    ) {
        counter.visit();
        if (current == null || current.isEmpty()) {
            throw new IllegalArgumentException("selected item path became empty");
        }
        if (selection.whole()) {
            entries.add(new ConfiscatedAssetEntry(path, fingerprint(current), current));
            return null;
        }
        ContainerView view = container(current).orElseThrow(
                () -> new IllegalArgumentException("selected path traverses a non-container")
        );
        List<ItemStack> children = new ArrayList<>(view.items());
        for (Map.Entry<Integer, SelectionNode> child : selection.children().entrySet()) {
            int index = child.getKey();
            if (index < 0 || index >= children.size()) {
                throw new IllegalArgumentException("selected nested slot no longer exists");
            }
            children.set(
                    index,
                    mutate(children.get(index), child.getValue(), path.child(index), entries, counter)
            );
        }
        return writeContainer(current, view.kind(), children);
    }

    private static Map<Integer, SelectionNode> buildTree(Set<ItemPath> paths) {
        Map<Integer, SelectionNode> roots = new TreeMap<>();
        paths.stream().sorted().forEach(path -> {
            SelectionNode current = roots.computeIfAbsent(path.rootSlot(), ignored -> new SelectionNode());
            if (current.whole() && !path.nestedSlots().isEmpty()) {
                throw new IllegalArgumentException("selection contains an item and its descendant");
            }
            for (int slot : path.nestedSlots()) {
                if (current.whole()) {
                    throw new IllegalArgumentException("selection contains an item and its descendant");
                }
                current = current.children().computeIfAbsent(slot, ignored -> new SelectionNode());
            }
            if (!current.children().isEmpty()) {
                throw new IllegalArgumentException("selection contains an item and its descendant");
            }
            current.selectWhole();
        });
        return roots;
    }

    private static Optional<ContainerView> container(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundle) {
            return Optional.of(new ContainerView(
                    ContainerKind.BUNDLE,
                    bundle.getItems().stream().map(NestedInventorySelection::copy).toList()
            ));
        }
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()) {
            BlockState state = blockMeta.getBlockState();
            if (state instanceof ShulkerBox shulker) {
                return Optional.of(new ContainerView(
                        ContainerKind.SHULKER,
                        java.util.Arrays.stream(shulker.getInventory().getContents())
                                .map(NestedInventorySelection::copy)
                                .toList()
                ));
            }
        }
        return Optional.empty();
    }

    private static ItemStack writeContainer(
            ItemStack original,
            ContainerKind kind,
            List<ItemStack> children
    ) {
        ItemStack result = original.clone();
        switch (kind) {
            case BUNDLE -> {
                if (!(result.getItemMeta() instanceof BundleMeta bundle)) {
                    throw new IllegalArgumentException("bundle metadata changed during selection");
                }
                bundle.setItems(children.stream()
                        .filter(item -> item != null && !item.isEmpty())
                        .map(NestedInventorySelection::copy)
                        .toList());
                result.setItemMeta(bundle);
            }
            case SHULKER -> {
                if (!(result.getItemMeta() instanceof BlockStateMeta blockMeta)
                        || !(blockMeta.getBlockState() instanceof ShulkerBox shulker)) {
                    throw new IllegalArgumentException("shulker metadata changed during selection");
                }
                if (children.size() != shulker.getInventory().getSize()) {
                    throw new IllegalArgumentException("shulker slot count changed during selection");
                }
                shulker.getInventory().setContents(children.toArray(ItemStack[]::new));
                blockMeta.setBlockState(shulker);
                result.setItemMeta(blockMeta);
            }
            default -> throw new IllegalStateException("Unsupported nested container kind: " + kind);
        }
        return result;
    }

    private static Placement placeAtOriginalPath(
            InventoryImage image,
            ConfiscatedAssetEntry entry
    ) {
        ItemPath path = entry.path();
        if (path.nestedSlots().isEmpty()) {
            ItemStack existing = image.item(path.rootSlot());
            return existing == null || existing.isEmpty()
                    ? new Placement(image.withItem(path.rootSlot(), entry.item()), path.rootSlot())
                    : null;
        }
        ItemStack root = image.item(path.rootSlot());
        if (root == null || root.isEmpty()) {
            return null;
        }
        try {
            ItemStack restored = insertNested(root, path.nestedSlots(), 0, entry.item());
            return new Placement(image.withItem(path.rootSlot(), restored), path.rootSlot());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ItemStack insertNested(
            ItemStack containerItem,
            List<Integer> path,
            int depth,
            ItemStack restoredItem
    ) {
        if (depth >= path.size()) {
            throw new IllegalArgumentException("nested restoration path ended unexpectedly");
        }
        ContainerView view = container(containerItem).orElseThrow(
                () -> new IllegalArgumentException("restoration path traverses a non-container")
        );
        int slot = path.get(depth);
        List<ItemStack> children = new ArrayList<>(view.items());
        if (depth == path.size() - 1) {
            switch (view.kind()) {
                case SHULKER -> {
                    if (slot < 0 || slot >= children.size()
                            || children.get(slot) != null && !children.get(slot).isEmpty()) {
                        throw new IllegalArgumentException("original shulker slot is unavailable");
                    }
                    children.set(slot, restoredItem.clone());
                }
                case BUNDLE -> {
                    if (slot < 0 || slot > children.size()) {
                        throw new IllegalArgumentException("original bundle position is unavailable");
                    }
                    children.add(slot, restoredItem.clone());
                }
                default -> throw new IllegalStateException(
                        "Unsupported nested container kind: " + view.kind()
                );
            }
            return writeContainer(containerItem, view.kind(), children);
        }
        if (slot < 0 || slot >= children.size()) {
            throw new IllegalArgumentException("restoration parent slot is unavailable");
        }
        ItemStack child = children.get(slot);
        if (child == null || child.isEmpty()) {
            throw new IllegalArgumentException("restoration parent container is unavailable");
        }
        children.set(slot, insertNested(child, path, depth + 1, restoredItem));
        return writeContainer(containerItem, view.kind(), children);
    }

    private static Placement placeAtSafeRoot(InventoryImage image, ItemStack restoredItem) {
        for (int slot = 0; slot < InventoryImage.STORAGE_SIZE; slot++) {
            ItemStack existing = image.item(slot);
            if (existing == null || existing.isEmpty()) {
                return new Placement(image.withItem(slot, restoredItem), slot);
            }
        }
        for (int slot = InventoryImage.ENDER_OFFSET;
             slot < InventoryImage.TOTAL_SLOTS;
             slot++) {
            ItemStack existing = image.item(slot);
            if (existing == null || existing.isEmpty()) {
                return new Placement(image.withItem(slot, restoredItem), slot);
            }
        }
        throw new IllegalArgumentException(
                "insufficient empty storage or Ender chest slots for exact restoration"
        );
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    public record SelectionResult(
            InventoryImage replacement,
            List<ConfiscatedAssetEntry> entries,
            List<Integer> changedRootSlots
    ) {
        public SelectionResult {
            Objects.requireNonNull(replacement, "replacement");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            changedRootSlots = List.copyOf(
                    Objects.requireNonNull(changedRootSlots, "changedRootSlots")
            );
            if (entries.isEmpty() || changedRootSlots.isEmpty()) {
                throw new IllegalArgumentException("selection result cannot be empty");
            }
        }
    }

    public record RestorationResult(
            InventoryImage replacement,
            List<Integer> changedRootSlots
    ) {
        public RestorationResult {
            Objects.requireNonNull(replacement, "replacement");
            changedRootSlots = List.copyOf(
                    Objects.requireNonNull(changedRootSlots, "changedRootSlots")
            );
            if (changedRootSlots.isEmpty()) {
                throw new IllegalArgumentException("restoration result cannot be empty");
            }
        }
    }

    private record Placement(InventoryImage image, int rootSlot) {
        private Placement {
            Objects.requireNonNull(image, "image");
            if (rootSlot < 0 || rootSlot >= InventoryImage.TOTAL_SLOTS) {
                throw new IllegalArgumentException("restoration root slot is invalid");
            }
        }
    }

    private enum ContainerKind {
        BUNDLE,
        SHULKER
    }

    private record ContainerView(ContainerKind kind, List<ItemStack> items) {
        private ContainerView {
            Objects.requireNonNull(kind, "kind");
            List<ItemStack> copied = new ArrayList<>(items.size());
            items.forEach(item -> copied.add(copy(item)));
            items = java.util.Collections.unmodifiableList(copied);
        }
    }

    private static final class SelectionNode {
        // Nodes belong to one method-local traversal tree and are never shared.
        @SuppressWarnings("PMD.DocumentMutableMapFieldConcurrency")
        private final Map<Integer, SelectionNode> children = new LinkedHashMap<>();
        private boolean whole;

        Map<Integer, SelectionNode> children() {
            return children;
        }

        boolean whole() {
            return whole;
        }

        void selectWhole() {
            whole = true;
        }
    }

    private static final class VisitCounter {
        private int visited;

        void visit() {
            visited++;
            if (visited > MAX_VISITED_STACKS) {
                throw new IllegalArgumentException("nested inventory traversal exceeds the safety limit");
            }
        }
    }

}
