package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * Runs only inside the global-region lookup callback. Player operations remain
 * deferred to the owning entity scheduler; rejected or throwing submissions
 * must always execute the caller's retirement cleanup.
 */
final class StaffEntityOwnerDispatch {
    private StaffEntityOwnerDispatch() {
    }

    @FunctionalInterface
    interface OwnerSchedule {
        boolean submit(Player player, Runnable operation, Runnable retired);
    }

    static void forward(
            UUID playerId,
            Function<UUID, Player> lookup,
            OwnerSchedule schedule,
            Consumer<Player> operation,
            Runnable retired,
            Consumer<RuntimeException> onFailure
    ) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(schedule);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(retired);
        Objects.requireNonNull(onFailure);

        Player player;
        try {
            player = lookup.apply(playerId);
        } catch (RuntimeException failure) {
            try {
                retired.run();
            } finally {
                onFailure.accept(failure);
            }
            return;
        }
        if (player == null) {
            retired.run();
            return;
        }

        boolean accepted;
        try {
            accepted = schedule.submit(player, () -> operation.accept(player), retired);
        } catch (RuntimeException failure) {
            try {
                retired.run();
            } finally {
                onFailure.accept(failure);
            }
            return;
        }
        if (!accepted) {
            retired.run();
        }
    }
}
