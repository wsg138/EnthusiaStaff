package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/** Vanilla-style ItemStack adapter for one remote logical inventory slot. */
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
        LiveInventoryTransferRule.Decision rule = LiveInventoryTransferRule.decide(
                state(targetCopy),
                state(cursorCopy),
                similar(targetCopy, cursorCopy),
                LiveInventoryTransferRule.Click.valueOf(click.name())
        );
        Action action = Action.valueOf(rule.action().name());
        return new Decision(
                action,
                item(rule.targetAfter(), targetTemplate(action, targetCopy, cursorCopy)),
                item(rule.cursorAfter(), cursorTemplate(action, targetCopy, cursorCopy))
        );
    }

    static boolean same(ItemStack first, ItemStack second) {
        return Objects.equals(normalized(first), normalized(second));
    }

    private static LiveInventoryTransferRule.Stack state(ItemStack item) {
        return usable(item)
                ? new LiveInventoryTransferRule.Stack(item.getAmount(), item.getMaxStackSize())
                : null;
    }

    private static boolean similar(ItemStack target, ItemStack cursor) {
        return usable(target) && usable(cursor) && target.isSimilar(cursor);
    }

    private static ItemStack targetTemplate(Action action, ItemStack target, ItemStack cursor) {
        return switch (action) {
            case PLACE, SWAP -> cursor;
            case PLACE_ONE -> usable(target) ? target : cursor;
            default -> target;
        };
    }

    private static ItemStack cursorTemplate(Action action, ItemStack target, ItemStack cursor) {
        return switch (action) {
            case PICKUP, SWAP, SPLIT -> target;
            default -> cursor;
        };
    }

    private static ItemStack item(LiveInventoryTransferRule.Stack state, ItemStack template) {
        if (state == null) {
            return null;
        }
        if (!usable(template)) {
            throw new IllegalStateException("transfer rule produced a stack without an item template");
        }
        ItemStack result = template.clone();
        result.setAmount(state.amount());
        return result;
    }

    private static ItemStack normalized(ItemStack item) {
        return usable(item) ? item : null;
    }

    private static ItemStack copy(ItemStack item) {
        return usable(item) ? item.clone() : null;
    }

    private static boolean usable(ItemStack item) {
        return item != null && !item.isEmpty();
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
