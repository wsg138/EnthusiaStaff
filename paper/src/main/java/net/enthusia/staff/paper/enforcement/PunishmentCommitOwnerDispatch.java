package net.enthusia.staff.paper.enforcement;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.entity.Player;

/**
 * Resolves a punishment target globally, but checks live entity state and
 * applies the committed effect only on the target's owning entity thread.
 */
final class PunishmentCommitOwnerDispatch {
    @FunctionalInterface
    interface OwnerScheduler {
        boolean execute(Player player, Runnable callback);
    }

    private PunishmentCommitOwnerDispatch() {
    }

    static void forward(UUID playerId, Function<UUID, Player> lookup,
            OwnerScheduler ownerScheduler, Predicate<Player> eligible,
            Consumer<Player> effect, Consumer<RuntimeException> onFailure) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(ownerScheduler, "ownerScheduler");
        Objects.requireNonNull(eligible, "eligible");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(onFailure, "onFailure");

        try {
            Player player = lookup.apply(playerId);
            if (player == null) {
                return;
            }
            boolean accepted = ownerScheduler.execute(player, () -> {
                try {
                    if (eligible.test(player)) {
                        effect.accept(player);
                    }
                } catch (RuntimeException failure) {
                    onFailure.accept(failure);
                }
            });
            if (!accepted) {
                onFailure.accept(new IllegalStateException(
                        "Punishment target entity scheduler declined delivery"));
            }
        } catch (RuntimeException failure) {
            onFailure.accept(failure);
        }
    }
}
