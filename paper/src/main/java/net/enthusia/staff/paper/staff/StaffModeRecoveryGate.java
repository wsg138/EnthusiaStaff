package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

final class StaffModeRecoveryGate {
    private final Object attemptLock = new Object();
    private final Set<UUID> transitions;
    private final Set<UUID> retryable = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Object> attempts = new ConcurrentHashMap<>();

    StaffModeRecoveryGate(Set<UUID> transitions) {
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    boolean begin(UUID playerId) {
        return beginAttempt(playerId) != null;
    }

    Object beginAttempt(UUID playerId) {
        synchronized (attemptLock) {
            Objects.requireNonNull(playerId, "playerId");
            if (transitions.add(playerId) || retryable.remove(playerId)) {
                Object attempt = new Object();
                attempts.put(playerId, attempt);
                return attempt;
            }
            return null;
        }
    }

    Object attempt(UUID playerId) {
        synchronized (attemptLock) {
            return attempts.get(playerId);
        }
    }

    boolean retryable(UUID playerId) {
        synchronized (attemptLock) {
            return transitions.contains(playerId) && retryable.contains(playerId);
        }
    }

    Object ensureAttempt(UUID playerId) {
        synchronized (attemptLock) {
            return transitions.contains(playerId) ? attempts.computeIfAbsent(playerId, ignored -> new Object()) : null;
        }
    }

    // An attempt is an opaque identity capability; value equality must never grant ownership.
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    boolean owns(UUID playerId, Object attempt) {
        synchronized (attemptLock) {
            return attempt != null && attempts.get(playerId) == attempt && transitions.contains(playerId);
        }
    }

    boolean withAttempt(UUID playerId, Object attempt, Runnable operation) {
        synchronized (attemptLock) {
            if (!owns(playerId, attempt)) {
                return false;
            }
            operation.run();
            return true;
        }
    }

    boolean complete(UUID playerId, Object attempt, BooleanSupplier cleanup) {
        synchronized (attemptLock) {
            if (!owns(playerId, attempt) || !cleanup.getAsBoolean()) {
                return false;
            }
            clear(playerId);
            return true;
        }
    }

    boolean retry(UUID playerId, Object attempt) {
        synchronized (attemptLock) {
            if (!owns(playerId, attempt)) {
                return false;
            }
            retry(playerId);
            return true;
        }
    }

    void retry(UUID playerId) {
        synchronized (attemptLock) {
            Objects.requireNonNull(playerId, "playerId");
            attempts.remove(playerId);
            transitions.add(playerId);
            retryable.add(playerId);
        }
    }

    void clear(UUID playerId) {
        synchronized (attemptLock) {
            attempts.remove(playerId);
            retryable.remove(playerId);
            transitions.remove(playerId);
        }
    }
}
