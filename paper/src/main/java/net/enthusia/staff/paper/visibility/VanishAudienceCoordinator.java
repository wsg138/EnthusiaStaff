package net.enthusia.staff.paper.visibility;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.GameMode;

final class VanishAudienceCoordinator<T> {
    private final Map<UUID, OnlineEntity<T>> online = new ConcurrentHashMap<>();
    private final AtomicLong nextSession = new AtomicLong();
    private final OwningScheduler<T> scheduler;
    private final PairRefresher<T> refresher;

    VanishAudienceCoordinator(OwningScheduler<T> scheduler, PairRefresher<T> refresher) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.refresher = Objects.requireNonNull(refresher, "refresher");
    }

    void register(UUID playerId, T owner, GameMode gameMode) {
        online.put(
                Objects.requireNonNull(playerId, "playerId"),
                new OnlineEntity<>(playerId, Objects.requireNonNull(owner, "owner"),
                        Objects.requireNonNull(gameMode, "gameMode"), nextSession.incrementAndGet())
        );
    }

    void updateGameMode(UUID playerId, GameMode gameMode) {
        Objects.requireNonNull(gameMode, "gameMode");
        online.computeIfPresent(playerId, (ignored, current) -> current.withGameMode(gameMode));
    }

    void remove(UUID playerId) {
        online.remove(playerId);
    }

    List<UUID> playerIds() {
        return List.copyOf(online.keySet());
    }

    boolean onOwner(UUID playerId, Consumer<T> operation) {
        return onOwner(playerId, operation, () -> {
        });
    }

    boolean onOwner(UUID playerId, Consumer<T> operation, Runnable retired) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(retired, "retired");
        OnlineEntity<T> scheduled = online.get(playerId);
        if (scheduled == null) {
            retired.run();
            return false;
        }
        boolean accepted = scheduler.execute(scheduled.owner(), () -> {
            OnlineEntity<T> current = online.get(playerId);
            if (sameSession(current, scheduled)) {
                operation.accept(current.owner());
            } else {
                retired.run();
            }
        });
        if (!accepted) {
            retired.run();
        }
        return accepted;
    }

    void forEachOwner(Consumer<T> operation) {
        playerIds().forEach(playerId -> onOwner(playerId, operation));
    }

    void refreshAll() {
        playerIds().forEach(this::refreshViewer);
    }

    void refreshViewer(UUID viewerId) {
        scheduleRefresh(viewerId, playerIds());
    }

    void refreshTarget(UUID targetId) {
        if (!online.containsKey(targetId)) {
            return;
        }
        for (UUID viewerId : playerIds()) {
            scheduleRefresh(viewerId, List.of(targetId));
        }
    }

    private void scheduleRefresh(UUID viewerId, List<UUID> targetIds) {
        OnlineEntity<T> scheduled = online.get(viewerId);
        if (scheduled == null) {
            return;
        }
        scheduler.execute(scheduled.owner(), () -> refreshScheduled(scheduled, targetIds));
    }

    private void refreshScheduled(OnlineEntity<T> scheduled, List<UUID> targetIds) {
        OnlineEntity<T> viewer = online.get(scheduled.playerId());
        if (!sameSession(viewer, scheduled)) {
            return;
        }
        for (UUID targetId : targetIds) {
            OnlineEntity<T> target = online.get(targetId);
            if (target != null) {
                refresher.refresh(viewer, target);
            }
        }
    }

    private boolean sameSession(OnlineEntity<T> current, OnlineEntity<T> scheduled) {
        return current != null && current.session() == scheduled.session();
    }

    record OnlineEntity<T>(UUID playerId, T owner, GameMode gameMode, long session) {
        OnlineEntity<T> withGameMode(GameMode nextGameMode) {
            return new OnlineEntity<>(playerId, owner, nextGameMode, session);
        }
    }

    @FunctionalInterface
    interface OwningScheduler<T> {
        boolean execute(T owner, Runnable operation);
    }

    @FunctionalInterface
    interface PairRefresher<T> {
        void refresh(OnlineEntity<T> viewer, OnlineEntity<T> target);
    }
}
