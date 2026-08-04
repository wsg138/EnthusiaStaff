package net.enthusia.staff.paper.freeze;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class FreezeRuntimeState {
    private static final String PLAYER_ID_ARGUMENT = "playerId";

    private final Map<UUID, Entry> states = new ConcurrentHashMap<>();
    private final AtomicLong nextGeneration = new AtomicLong();

    long beginVerification(UUID playerId) {
        Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT);
        long token = nextGeneration.incrementAndGet();
        states.put(playerId, Entry.pending(token));
        return token;
    }

    boolean resolveVerification(UUID playerId, long token, boolean active) {
        Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT);
        AtomicBoolean resolved = new AtomicBoolean();
        states.compute(playerId, (ignored, current) -> {
            if (current == null || !current.pending(token)) {
                return current;
            }
            resolved.set(true);
            return active ? Entry.frozen(token) : null;
        });
        return resolved.get();
    }

    boolean isVerificationCurrent(UUID playerId, long token) {
        Entry current = states.get(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
        return current != null && current.pending(token);
    }

    long apply(UUID playerId) {
        Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT);
        long generation = nextGeneration.incrementAndGet();
        states.put(playerId, Entry.frozen(generation));
        return generation;
    }

    void release(UUID playerId) {
        states.remove(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
    }

    boolean retire(UUID playerId) {
        Entry retired = states.remove(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
        return retired != null && retired.status() == Status.FROZEN;
    }

    boolean isRestricted(UUID playerId) {
        return states.containsKey(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
    }

    boolean isFrozen(UUID playerId) {
        Entry current = states.get(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
        return current != null && current.status() == Status.FROZEN;
    }

    boolean isCurrentFrozen(UUID playerId, long generation) {
        Entry current = states.get(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
        return current != null && current.frozen(generation);
    }

    private enum Status {
        PENDING_VERIFICATION,
        FROZEN
    }

    private record Entry(Status status, long generation) {
        private static Entry pending(long token) {
            return new Entry(Status.PENDING_VERIFICATION, token);
        }

        private static Entry frozen(long generation) {
            return new Entry(Status.FROZEN, generation);
        }

        private boolean pending(long token) {
            return status == Status.PENDING_VERIFICATION && generation == token;
        }

        private boolean frozen(long expectedGeneration) {
            return status == Status.FROZEN && generation == expectedGeneration;
        }
    }
}
