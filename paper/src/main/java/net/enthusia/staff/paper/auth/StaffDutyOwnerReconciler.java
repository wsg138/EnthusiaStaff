package net.enthusia.staff.paper.auth;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Player;

/**
 * Global-region player enumeration with live duty/permission checks and LuckPerms
 * invalidation performed exclusively on each player's owning entity thread.
 */
final class StaffDutyOwnerReconciler implements AutoCloseable {
    @FunctionalInterface
    interface OwnerScheduler {
        boolean submit(Player player, Runnable operation, Runnable retired);
    }

    private static final class Entry {
        final Player player;
        final AtomicBoolean pending = new AtomicBoolean();
        // Only the owning player thread reads or writes this value.
        boolean active;

        Entry(Player player) {
            this.player = player;
        }
    }

    private final ConcurrentMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    void reconcile(
            Iterable<? extends Player> onlinePlayers,
            OwnerScheduler scheduler,
            Predicate<Player> liveActive,
            Consumer<Player> signalContextUpdate,
            Consumer<RuntimeException> onFailure
    ) {
        Objects.requireNonNull(onlinePlayers);
        Objects.requireNonNull(scheduler);
        Objects.requireNonNull(liveActive);
        Objects.requireNonNull(signalContextUpdate);
        Objects.requireNonNull(onFailure);
        if (closed.get()) {
            return;
        }

        Set<UUID> online = new HashSet<>();
        for (Player player : onlinePlayers) {
            if (closed.get()) {
                return;
            }
            // UUID is immutable identity; do not read live permission or state here.
            UUID playerId = player.getUniqueId();
            online.add(playerId);
            Entry entry = entries.compute(playerId,
                    (ignored, prior) -> prior != null && prior.player == player ? prior : new Entry(player));
            if (!entry.pending.compareAndSet(false, true)) {
                continue;
            }
            try {
                boolean accepted = scheduler.submit(player, () -> {
                    try {
                        if (closed.get() || entries.get(playerId) != entry || !player.isOnline()) {
                            return;
                        }
                        boolean active = liveActive.test(player);
                        if (entry.active != active && !closed.get()) {
                            // A failed LuckPerms invalidation must be retried on the next tick.
                            signalContextUpdate.accept(player);
                            entry.active = active;
                        }
                    } catch (RuntimeException failure) {
                        onFailure.accept(failure);
                    } finally {
                        entry.pending.set(false);
                    }
                }, () -> entry.pending.set(false));
                if (!accepted) {
                    entry.pending.set(false);
                }
            } catch (RuntimeException failure) {
                entry.pending.set(false);
                onFailure.accept(failure);
            }
        }
        // Disconnects invalidate old callbacks. A new Player object gets a fresh entry.
        entries.keySet().removeIf(id -> !online.contains(id));
    }

    void forget(Player player) {
        UUID id = player.getUniqueId();
        Entry current = entries.get(id);
        if (current != null && current.player == player) {
            entries.remove(id, current);
        }
    }

    @Override
    public void close() {
        closed.set(true);
        entries.clear();
    }
}
