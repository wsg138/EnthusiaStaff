package net.enthusia.staff.paper.automod;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Player;

/**
 * Global-region recipient enumeration to per-player owner-thread delivery.
 * Permission and online status must be checked inside the owner callback, not
 * on the global region or the moderation storage worker.
 */
final class AutomodOwnerAlertDispatch {
    private AutomodOwnerAlertDispatch() {
    }

    @FunctionalInterface
    interface OwnerScheduler {
        boolean submit(Player player, Runnable delivery);
    }

    static void dispatch(
            Iterable<? extends Player> recipients,
            OwnerScheduler ownerScheduler,
            Predicate<Player> currentlyAuthorized,
            Consumer<Player> deliver,
            Consumer<RuntimeException> onFailure
    ) {
        Objects.requireNonNull(recipients);
        Objects.requireNonNull(ownerScheduler);
        Objects.requireNonNull(currentlyAuthorized);
        Objects.requireNonNull(deliver);
        Objects.requireNonNull(onFailure);
        for (Player recipient : recipients) {
            try {
                // Only schedule here. All live Player reads and delivery run
                // inside the entity-owned callback after any permission drift.
                ownerScheduler.submit(recipient, () -> {
                    try {
                        if (currentlyAuthorized.test(recipient)) {
                            deliver.accept(recipient);
                        }
                    } catch (RuntimeException failure) {
                        onFailure.accept(failure);
                    }
                });
            } catch (RuntimeException failure) {
                // A retired region/player must not suppress alerts to others.
                onFailure.accept(failure);
            }
        }
    }
}
