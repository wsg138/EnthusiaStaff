package net.enthusia.staff.paper.tester;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Resolves a player globally, then performs live reads only on that player's owning scheduler. */
final class FoliaPlayerHandoff {
    private final JavaPlugin plugin;

    FoliaPlayerHandoff(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    void execute(UUID playerId, Consumer<Player> operation, Runnable unavailable) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(operation, "operation");
        Completion completion = new Completion(unavailable);
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(
                    plugin,
                    () -> resolveAndDispatch(playerId, operation, completion)
            );
        } catch (RuntimeException exception) {
            completion.unavailable();
        }
    }

    private void resolveAndDispatch(
            UUID playerId,
            Consumer<Player> operation,
            Completion completion
    ) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            completion.unavailable();
            return;
        }
        dispatchResolved(
                player::isOnline,
                (owned, retired) -> player.getScheduler().execute(plugin, owned, retired, 1L),
                () -> operation.accept(player),
                completion
        );
    }

    static void dispatchResolved(
            BooleanSupplier online,
            EntityDispatch dispatch,
            Runnable operation,
            Runnable unavailable
    ) {
        dispatchResolved(online, dispatch, operation, new Completion(unavailable));
    }

    private static void dispatchResolved(
            BooleanSupplier online,
            EntityDispatch dispatch,
            Runnable operation,
            Completion completion
    ) {
        boolean scheduled;
        try {
            scheduled = dispatch.execute(
                    () -> completion.runOwned(online, operation),
                    completion::unavailable
            );
        } catch (RuntimeException exception) {
            completion.unavailable();
            return;
        }
        if (!scheduled) {
            completion.unavailable();
        }
    }

    @FunctionalInterface
    interface EntityDispatch {
        boolean execute(Runnable operation, Runnable retired);
    }

    private static final class Completion {
        private final AtomicBoolean settled = new AtomicBoolean();
        private final Runnable unavailable;

        private Completion(Runnable unavailable) {
            this.unavailable = java.util.Objects.requireNonNull(unavailable, "unavailable");
        }

        private void runOwned(BooleanSupplier online, Runnable operation) {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            boolean available;
            try {
                available = online.getAsBoolean();
            } catch (RuntimeException exception) {
                unavailable.run();
                return;
            }
            if (!available) {
                unavailable.run();
                return;
            }
            operation.run();
        }

        private void unavailable() {
            if (settled.compareAndSet(false, true)) {
                unavailable.run();
            }
        }
    }
}
