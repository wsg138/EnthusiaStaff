package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Privacy-safe process health state; no user, message, punishment, or credential data is stored here. */
public final class StaffBotHealth {
    public enum Phase {
        STARTING,
        CONNECTING,
        READY,
        DISCONNECTED,
        FAILED,
        STOPPING,
        STOPPED
    }

    private final StaffBotEnvironment environment;
    private final AtomicReference<Snapshot> snapshot;
    private final AtomicLong rejectedWork = new AtomicLong();
    private final AtomicBoolean failedEver = new AtomicBoolean();

    public StaffBotHealth(StaffBotEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.snapshot = new AtomicReference<>(new Snapshot(Phase.STARTING, "process_starting", Instant.now(), 0));
    }

    public void transition(Phase phase, String reason) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(reason, "reason");
        if (phase == Phase.FAILED) {
            failedEver.set(true);
        }
        snapshot.set(new Snapshot(phase, reason, Instant.now(), rejectedWork.get()));
    }

    public void recordRejectedWork() {
        long rejected = rejectedWork.incrementAndGet();
        snapshot.updateAndGet(current ->
                new Snapshot(current.phase(), current.reason(), current.changedAt(), rejected));
    }

    public Snapshot snapshot() {
        Snapshot current = snapshot.get();
        long rejected = rejectedWork.get();
        if (current.rejectedWork() == rejected) {
            return current;
        }
        return new Snapshot(current.phase(), current.reason(), current.changedAt(), rejected);
    }

    public StaffBotEnvironment environment() {
        return environment;
    }

    public boolean isReady() {
        return snapshot.get().phase() == Phase.READY;
    }

    public boolean isLive() {
        Phase phase = snapshot.get().phase();
        return phase != Phase.FAILED && phase != Phase.STOPPED;
    }

    public boolean failedEver() {
        return failedEver.get();
    }

    public record Snapshot(Phase phase, String reason, Instant changedAt, long rejectedWork) {
        public Snapshot {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(changedAt, "changedAt");
            if (rejectedWork < 0) {
                throw new IllegalArgumentException("rejected work count cannot be negative");
            }
        }
    }
}
