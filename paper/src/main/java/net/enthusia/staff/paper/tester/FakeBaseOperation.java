package net.enthusia.staff.paper.tester;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class FakeBaseOperation {
    final UUID operationId;
    final UUID staffId;
    final UUID targetId;
    final UUID worldId;
    final FakeBasePlacementPlanner.Anchor anchor;
    final Instant createdAt;

    private final AtomicReference<Instant> expiresAt;
    private final AtomicBoolean warned = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private volatile ScheduledTask lifecycleTask;

    FakeBaseOperation(
            UUID operationId,
            UUID staffId,
            UUID targetId,
            UUID worldId,
            FakeBasePlacementPlanner.Anchor anchor,
            Instant createdAt,
            Duration lifetime
    ) {
        this.operationId = java.util.Objects.requireNonNull(operationId, "operationId");
        this.staffId = java.util.Objects.requireNonNull(staffId, "staffId");
        this.targetId = java.util.Objects.requireNonNull(targetId, "targetId");
        this.worldId = java.util.Objects.requireNonNull(worldId, "worldId");
        this.anchor = java.util.Objects.requireNonNull(anchor, "anchor");
        this.createdAt = java.util.Objects.requireNonNull(createdAt, "createdAt");
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("fake-base lifetime must be positive");
        }
        this.expiresAt = new AtomicReference<>(createdAt.plus(lifetime));
    }

    boolean open() {
        return !closed.get();
    }

    boolean extend(Instant now, Duration lifetime) {
        if (!open() || now == null || lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            return false;
        }
        expiresAt.set(now.plus(lifetime));
        warned.set(false);
        return open();
    }

    boolean expired(Instant now) {
        return !now.isBefore(expiresAt.get());
    }

    boolean markWarningIfDue(Instant now, Duration warningLead) {
        Instant deadline = expiresAt.get();
        Instant warningAt = deadline.minus(warningLead);
        return open() && !now.isBefore(warningAt) && now.isBefore(deadline)
                && warned.compareAndSet(false, true);
    }

    long remainingSeconds(Instant now) {
        Instant deadline = expiresAt.get();
        if (!now.isBefore(deadline)) {
            return 0L;
        }
        return Math.max(1L, Duration.between(now, deadline).toSeconds());
    }

    boolean addViewerIfOpen(UUID viewerId) {
        if (!open()) {
            return false;
        }
        viewers.add(viewerId);
        if (!open()) {
            viewers.remove(viewerId);
            return false;
        }
        return true;
    }

    /**
     * Reconciles the viewer after the client render. If a close won the race after
     * admission, remove the viewer so the caller can immediately restore real blocks.
     */
    boolean retainViewerAfterRender(UUID viewerId) {
        if (open()) {
            return true;
        }
        viewers.remove(viewerId);
        return false;
    }

    void removeViewer(UUID viewerId) {
        viewers.remove(viewerId);
    }

    Set<UUID> viewersSnapshot() {
        return Set.copyOf(viewers);
    }

    boolean close() {
        boolean changed = closed.compareAndSet(false, true);
        if (changed) {
            cancelTask();
        }
        return changed;
    }

    void setLifecycleTask(ScheduledTask task) {
        lifecycleTask = task;
        if (!open()) {
            cancelTask();
        }
    }

    void cancelTask() {
        ScheduledTask task = lifecycleTask;
        if (task != null) {
            task.cancel();
        }
    }
}
