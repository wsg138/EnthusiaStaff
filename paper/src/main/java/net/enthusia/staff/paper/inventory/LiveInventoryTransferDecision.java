package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Pure vanilla-style cursor decision for one remote logical inventory slot. */
final class LiveInventoryTransferDecision {
    enum Click {
        LEFT,
        RIGHT
    }

    enum Action {
        PICKUP,
        PLACE,
        MERGE,
        SWAP,
        SPLIT,
        PLACE_ONE,
        NO_CHANGE
    }

    private LiveInventoryTransferDecision() {
    }

    static Decision decide(ItemStack target, ItemStack cursor, Click click) {
        Objects.requireNonNull(click, "click");
        ItemStack targetCopy = copy(target);
        ItemStack cursorCopy = copy(cursor);
        return click == Click.LEFT
                ? left(targetCopy, cursorCopy)
                : right(targetCopy, cursorCopy);
    }

    static boolean same(ItemStack first, ItemStack second) {
        return Objects.equals(normalized(first), normalized(second));
    }

    private static Decision left(ItemStack target, ItemStack cursor) {
        if (!usable(cursor)) {
            return usable(target)
                    ? new Decision(Action.PICKUP, null, target)
                    : unchanged(target, cursor);
        }
        if (!usable(target)) {
            return new Decision(Action.PLACE, cursor, null);
        }
        if (!target.isSimilar(cursor)) {
            return new Decision(Action.SWAP, cursor, target);
        }
        int capacity = target.getMaxStackSize() - target.getAmount();
        if (capacity <= 0) {
            return unchanged(target, cursor);
        }
        int moved = Math.min(capacity, cursor.getAmount());
        ItemStack targetAfter = amount(target, target.getAmount() + moved);
        ItemStack cursorAfter = remaining(cursor, moved);
        return new Decision(Action.MERGE, targetAfter, cursorAfter);
    }

    private static Decision right(ItemStack target, ItemStack cursor) {
        if (!usable(cursor)) {
            return usable(target) ? split(target) : unchanged(target, cursor);
        }
        if (!usable(target)) {
            return new Decision(Action.PLACE_ONE, amount(cursor, 1), remaining(cursor, 1));
        }
        if (!target.isSimilar(cursor) || target.getAmount() >= target.getMaxStackSize()) {
            return unchanged(target, cursor);
        }
        return new Decision(
                Action.PLACE_ONE,
                amount(target, target.getAmount() + 1),
                remaining(cursor, 1)
        );
    }

    private static Decision split(ItemStack target) {
        int cursorAmount = (target.getAmount() + 1) / 2;
        int targetAmount = target.getAmount() - cursorAmount;
        ItemStack targetAfter = targetAmount == 0 ? null : amount(target, targetAmount);
        return new Decision(Action.SPLIT, targetAfter, amount(target, cursorAmount));
    }

    private static Decision unchanged(ItemStack target, ItemStack cursor) {
        return new Decision(Action.NO_CHANGE, target, cursor);
    }

    private static ItemStack remaining(ItemStack item, int removed) {
        int amount = item.getAmount() - removed;
        return amount <= 0 ? null : amount(item, amount);
    }

    private static ItemStack amount(ItemStack item, int amount) {
        ItemStack result = item.clone();
        result.setAmount(amount);
        return result;
    }

    private static ItemStack normalized(ItemStack item) {
        return usable(item) ? item : null;
    }

    private static ItemStack copy(ItemStack item) {
        return usable(item) ? item.clone() : null;
    }

    private static boolean usable(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType() != Material.AIR;
    }

    record Decision(Action action, ItemStack targetAfter, ItemStack cursorAfter) {
        Decision {
            Objects.requireNonNull(action, "action");
            targetAfter = copy(targetAfter);
            cursorAfter = copy(cursorAfter);
        }

        @Override
        public ItemStack targetAfter() {
            return copy(targetAfter);
        }

        @Override
        public ItemStack cursorAfter() {
            return copy(cursorAfter);
        }

        boolean changed() {
            return action != Action.NO_CHANGE;
        }
    }
}
