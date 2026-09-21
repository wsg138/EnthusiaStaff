package net.enthusia.staff.paper.freeze;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.FreezeStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Reconciles a broadcast freeze change against authoritative storage on the target backend. */
public final class FreezeNetworkReconciler {
    private static final Duration RECONCILIATION_TIMEOUT = Duration.ofSeconds(5);

    private final Clock clock;
    private final Supplier<FreezeStore> store;
    private final Executor workers;
    private final RestrictionController restrictions;
    private final LocalTargetRouter targets;
    private final Logger logger;

    public FreezeNetworkReconciler(
            JavaPlugin plugin,
            Clock clock,
            Supplier<FreezeStore> store,
            ExecutorService workers,
            FreezeManager manager
    ) {
        this(
                clock,
                store,
                workers,
                RestrictionController.forManager(manager),
                localTargetRouter(plugin),
                plugin.getLogger()
        );
    }

    FreezeNetworkReconciler(
            Clock clock,
            Supplier<FreezeStore> store,
            Executor workers,
            RestrictionController restrictions,
            LocalTargetRouter targets,
            Logger logger
    ) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.restrictions = java.util.Objects.requireNonNull(restrictions, "restrictions");
        this.targets = java.util.Objects.requireNonNull(targets, "targets");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    public boolean reconcile(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        targets.dispatch(
                playerId,
                () -> submitLookup(playerId, completion),
                () -> completion.complete(true),
                () -> completion.complete(false)
        );
        return await(completion);
    }

    private void submitLookup(UUID playerId, CompletableFuture<Boolean> completion) {
        try {
            workers.execute(() -> loadAndRoute(playerId, completion));
        } catch (RejectedExecutionException exception) {
            logger.warning("Freeze reconciliation deferred because the bounded worker queue is full");
            completion.complete(false);
        }
    }

    private void loadAndRoute(UUID playerId, CompletableFuture<Boolean> completion) {
        try {
            FreezeStore loaded = store.get();
            if (loaded == null) {
                completion.complete(false);
                return;
            }
            boolean active = loaded.active(playerId, clock.instant()).isPresent();
            targets.dispatch(
                    playerId,
                    () -> applyAuthoritativeState(playerId, active, completion),
                    () -> completion.complete(true),
                    () -> completion.complete(false)
            );
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Freeze network reconciliation lookup failed", exception);
            completion.complete(false);
        }
    }

    private void applyAuthoritativeState(
            UUID playerId,
            boolean active,
            CompletableFuture<Boolean> completion
    ) {
        if (active != restrictions.restricted(playerId)) {
            if (active) {
                restrictions.apply(playerId);
            } else {
                restrictions.release(playerId);
            }
        }
        completion.complete(true);
    }

    private boolean await(CompletableFuture<Boolean> completion) {
        try {
            return completion.get(RECONCILIATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            logger.log(Level.WARNING, "Freeze network reconciliation did not complete", exception);
            return false;
        }
    }

    private static LocalTargetRouter localTargetRouter(JavaPlugin plugin) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        return (playerId, present, absent, failed) -> {
            try {
                plugin.getServer().getGlobalRegionScheduler().execute(
                        plugin,
                        () -> routeFromGlobal(plugin, playerId, present, absent, failed)
                );
            } catch (RuntimeException exception) {
                failed.run();
            }
        };
    }

    private static void routeFromGlobal(
            JavaPlugin plugin,
            UUID playerId,
            Runnable present,
            Runnable absent,
            Runnable failed
    ) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            absent.run();
            return;
        }
        scheduleOnOwner(plugin, player, present, failed);
    }

    private static void scheduleOnOwner(
            JavaPlugin plugin,
            Player player,
            Runnable present,
            Runnable failed
    ) {
        AtomicBoolean completed = new AtomicBoolean();
        Runnable owned = once(completed, present);
        Runnable rejected = once(completed, failed);
        try {
            boolean scheduled = player.getScheduler().execute(plugin, owned, rejected, 1L);
            if (!scheduled) {
                rejected.run();
            }
        } catch (RuntimeException exception) {
            rejected.run();
        }
    }

    private static Runnable once(AtomicBoolean completed, Runnable action) {
        return () -> {
            if (completed.compareAndSet(false, true)) {
                action.run();
            }
        };
    }

    interface RestrictionController {
        boolean restricted(UUID playerId);

        void apply(UUID playerId);

        void release(UUID playerId);

        static RestrictionController forManager(FreezeManager manager) {
            java.util.Objects.requireNonNull(manager, "manager");
            return new RestrictionController() {
                @Override
                public boolean restricted(UUID playerId) {
                    return manager.isRestricted(playerId);
                }

                @Override
                public void apply(UUID playerId) {
                    manager.applyOnline(playerId);
                }

                @Override
                public void release(UUID playerId) {
                    manager.releaseOnline(playerId);
                }
            };
        }
    }

    @FunctionalInterface
    interface LocalTargetRouter {
        void dispatch(UUID playerId, Runnable present, Runnable absent, Runnable failed);
    }
}
