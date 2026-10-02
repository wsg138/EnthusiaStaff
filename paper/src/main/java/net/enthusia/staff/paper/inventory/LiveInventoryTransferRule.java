package net.enthusia.staff.paper.inventory;

import java.util.Objects;

/** Bukkit-independent vanilla cursor arithmetic used by the remote live inventory adapter. */
final class LiveInventoryTransferRule {
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

    private LiveInventoryTransferRule() {
    }

    static Decision decide(Stack target, Stack cursor, boolean compatible, Click click) {
        Objects.requireNonNull(click, "click");
        return click == Click.LEFT
                ? left(target, cursor, compatible)
                : right(target, cursor, compatible);
    }

    private static Decision left(Stack target, Stack cursor, boolean compatible) {
        if (cursor == null) {
            return target == null ? unchanged(target, cursor) : new Decision(Action.PICKUP, null, target);
        }
        if (target == null) {
            return new Decision(Action.PLACE, cursor, null);
        }
        if (!compatible) {
            return new Decision(Action.SWAP, cursor, target);
        }
        int capacity = target.maxStackSize() - target.amount();
        if (capacity <= 0) {
            return unchanged(target, cursor);
        }
        int moved = Math.min(capacity, cursor.amount());
        return new Decision(
                Action.MERGE,
                target.withAmount(target.amount() + moved),
                cursor.remaining(moved)
        );
    }

    private static Decision right(Stack target, Stack cursor, boolean compatible) {
        if (cursor == null) {
            return target == null ? unchanged(null, null) : split(target);
        }
        if (target == null) {
            return new Decision(Action.PLACE_ONE, cursor.withAmount(1), cursor.remaining(1));
        }
        if (!compatible || target.amount() >= target.maxStackSize()) {
            return unchanged(target, cursor);
        }
        return new Decision(
                Action.PLACE_ONE,
                target.withAmount(target.amount() + 1),
                cursor.remaining(1)
        );
    }

    private static Decision split(Stack target) {
        int cursorAmount = (target.amount() + 1) / 2;
        int targetAmount = target.amount() - cursorAmount;
        return new Decision(
                Action.SPLIT,
                targetAmount == 0 ? null : target.withAmount(targetAmount),
                target.withAmount(cursorAmount)
        );
    }

    private static Decision unchanged(Stack target, Stack cursor) {
        return new Decision(Action.NO_CHANGE, target, cursor);
    }

    record Stack(int amount, int maxStackSize) {
        Stack {
            if (amount < 1 || maxStackSize < 1 || amount > maxStackSize) {
                throw new IllegalArgumentException("stack amount must fit its maximum");
            }
        }

        Stack withAmount(int replacement) {
            return new Stack(replacement, maxStackSize);
        }

        Stack remaining(int removed) {
            int replacement = amount - removed;
            return replacement == 0 ? null : withAmount(replacement);
        }
    }

    record Decision(Action action, Stack targetAfter, Stack cursorAfter) {
        Decision {
            Objects.requireNonNull(action, "action");
        }

        boolean changed() {
            return action != Action.NO_CHANGE;
        }
    }
}
