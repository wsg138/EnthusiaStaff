package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * Fences an asynchronous rank-check callback to the exact single-flight ticket
 * issued by its originating online session. A queued callback may resolve a
 * rejoined player with the same UUID, but must not reconcile that new session.
 */
final class StaffRankReconciliationOwnerDispatcher {
    private StaffRankReconciliationOwnerDispatcher() {
    }

    @FunctionalInterface
    interface OwnerDispatch {
        void execute(UUID id, Consumer<Player> operation, Runnable retired);
    }

    static void forward(
            UUID playerId,
            Object ticket,
            ConcurrentMap<UUID, Object> pending,
            OwnerDispatch ownerDispatch,
            Consumer<Player> reconcile
    ) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(ticket);
        Objects.requireNonNull(pending);
        Objects.requireNonNull(ownerDispatch);
        Objects.requireNonNull(reconcile);

        try {
            ownerDispatch.execute(playerId, player -> {
                try {
                    // Quit invalidates the ticket; later joins receive a new identity.
                    if (pending.get(playerId) == ticket) {
                        reconcile.accept(player);
                    }
                } finally {
                    pending.remove(playerId, ticket);
                }
            }, () -> pending.remove(playerId, ticket));
        } catch (RuntimeException schedulingFailure) {
            // A rejected global scheduler submission must not strand single-flight.
            // Compare-and-remove also preserves any replacement from a rejoin.
            pending.remove(playerId, ticket);
            throw schedulingFailure;
        }
    }
}
