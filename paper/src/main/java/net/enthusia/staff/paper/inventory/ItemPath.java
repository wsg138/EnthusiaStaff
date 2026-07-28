package net.enthusia.staff.paper.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ItemPath(int rootSlot, List<Integer> nestedSlots)
        implements Comparable<ItemPath> {
    private static final int MAX_DEPTH = 16;

    public ItemPath {
        if (rootSlot < 0 || rootSlot >= InventoryImage.TOTAL_SLOTS) {
            throw new IllegalArgumentException("rootSlot is outside the inventory image");
        }
        nestedSlots = List.copyOf(Objects.requireNonNull(nestedSlots, "nestedSlots"));
        if (nestedSlots.size() > MAX_DEPTH
                || nestedSlots.stream().anyMatch(slot -> slot == null || slot < 0 || slot > 16_383)) {
            throw new IllegalArgumentException("nested item path is invalid");
        }
    }

    public static ItemPath root(int rootSlot) {
        return new ItemPath(rootSlot, List.of());
    }

    public ItemPath child(int childSlot) {
        if (nestedSlots.size() >= MAX_DEPTH) {
            throw new IllegalArgumentException("nested item path exceeds the maximum depth");
        }
        List<Integer> next = new ArrayList<>(nestedSlots);
        next.add(childSlot);
        return new ItemPath(rootSlot, next);
    }

    public ItemPath parent() {
        if (nestedSlots.isEmpty()) {
            return this;
        }
        return new ItemPath(rootSlot, nestedSlots.subList(0, nestedSlots.size() - 1));
    }

    public boolean ancestorOf(ItemPath other) {
        if (other == null || rootSlot != other.rootSlot()
                || nestedSlots.size() > other.nestedSlots().size()) {
            return false;
        }
        for (int index = 0; index < nestedSlots.size(); index++) {
            if (!nestedSlots.get(index).equals(other.nestedSlots().get(index))) {
                return false;
            }
        }
        return true;
    }

    public String encoded() {
        StringBuilder value = new StringBuilder(Integer.toString(rootSlot));
        nestedSlots.forEach(slot -> value.append('/').append(slot));
        return value.toString();
    }

    public static ItemPath parse(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(?:/[0-9]{1,4}){0,16}")) {
            throw new IllegalArgumentException("item path is invalid");
        }
        String[] parts = value.split("/");
        int root = Integer.parseInt(parts[0]);
        List<Integer> nested = new ArrayList<>();
        for (int index = 1; index < parts.length; index++) {
            nested.add(Integer.parseInt(parts[index]));
        }
        return new ItemPath(root, nested);
    }

    @Override
    public int compareTo(ItemPath other) {
        int rootComparison = Integer.compare(rootSlot, other.rootSlot);
        if (rootComparison != 0) {
            return rootComparison;
        }
        int common = Math.min(nestedSlots.size(), other.nestedSlots.size());
        for (int index = 0; index < common; index++) {
            int comparison = Integer.compare(nestedSlots.get(index), other.nestedSlots.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(nestedSlots.size(), other.nestedSlots.size());
    }
}
