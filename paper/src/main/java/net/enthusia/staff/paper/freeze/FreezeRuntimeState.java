package net.enthusia.staff.paper.freeze;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class FreezeRuntimeState {
    private final Map<UUID, Entry> states = new ConcurrentHashMap<>();
    private final AtomicLong nextVerificationToken = new AtomicLong();

    long beginVerification(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        long token = nextVerificationToken.incrementAndGet();
        states.put(playerId, Entry.pending(token));
        return token;
    }

    boolean resolveVerification(UUID playerId, long token, boolean active) {
        Objects.requireNonNull(playerId, "playerId");
        AtomicBoolean resolved = new AtomicBoolean();
        states.compute(playerId, (ignored, current) -> {
            if (current == null || !current.pending(token)) {
                return current;
            }
            resolved.set(true);
            return active ? Entry.frozen() : null;
        });
        return resolved.get();
    }

    boolean isVerificationCurrent(UUID playerId, long token) {
        Entry current = states.get(Objects.requireNonNull(playerId, "playerId"));
        return current != null && current.pending(token);
    }

    void apply(UUID playerId) {
        states.put(Objects.requireNonNull(playerId, "playerId"), Entry.frozen());
    }

    void release(UUID playerId) {
        states.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    boolean retire(UUID playerId) {
        Entry retired = states.remove(Objects.requireNonNull(playerId, "playerId"));
        return retired != null && retired.status() == Status.FROZEN;
    }

    boolean isRestricted(UUID playerId) {
        return states.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    boolean isFrozen(UUID playerId) {
        Entry current = states.get(Objects.requireNonNull(playerId, "playerId"));
        return current != null && current.status() == Status.FROZEN;
    }

    private enum Status {
        PENDING_VERIFICATION,
        FROZEN
    }

    private record Entry(Status status, long verificationToken) {
        private static Entry pending(long token) {
            return new Entry(Status.PENDING_VERIFICATION, token);
        }

        private static Entry frozen() {
            return new Entry(Status.FROZEN, 0L);
        }

        private boolean pending(long token) {
            return status == Status.PENDING_VERIFICATION && verificationToken == token;
        }
    }
}
