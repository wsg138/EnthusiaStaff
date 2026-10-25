package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Identity-bound global-timer to player-owner recovery handoff. */
final class StaffRecoveryRetryOwnerDispatcher {
    private StaffRecoveryRetryOwnerDispatcher() {
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
            Consumer<Player> recover
    ) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(ticket);
        Objects.requireNonNull(pending);
        Objects.requireNonNull(ownerDispatch);
        Objects.requireNonNull(recover);
        ownerDispatch.execute(
                playerId,
                player -> {
                    // Callbacks queued before disconnect may not consume new retries.
                    if (pending.remove(playerId, ticket)) {
                        recover.accept(player);
                    }
                },
                () -> pending.remove(playerId, ticket)
        );
    }
}
