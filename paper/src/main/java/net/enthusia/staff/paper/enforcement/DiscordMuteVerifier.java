package net.enthusia.staff.paper.enforcement;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener.CachedMuteStatus;

/** Authoritative Discord ingress checks, independent of the online-session cache. */
final class DiscordMuteVerifier implements AutoCloseable {
    private static final Set<SanctionType> TYPES = Set.of(SanctionType.MUTE, SanctionType.PUBLIC_MUTE);
    private final Supplier<OperationalMode> mode;
    private final Supplier<SanctionLookup> sanctions;
    private final Executor workers;
    private final Clock clock;
    private final Logger logger;
    private final Duration timeout;
    private final Semaphore admission;
    private final Set<CompletableFuture<CachedMuteStatus>> pending = ConcurrentHashMap.newKeySet();
    // Private monitor so callers cannot contend on, or deadlock with, the verifier instance.
    private final Object lifecycle = new Object();
    private boolean closed;

    DiscordMuteVerifier(Supplier<OperationalMode> mode, Supplier<SanctionLookup> sanctions,
                        Executor workers, Clock clock, Logger logger, Duration timeout, int capacity) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.toMillis() < 1 || capacity < 1) {
            throw new IllegalArgumentException("timeout and capacity must be positive");
        }
        this.admission = new Semaphore(capacity);
    }

    CompletionStage<CachedMuteStatus> verify(UUID senderId) {
        Objects.requireNonNull(senderId, "senderId");
        synchronized (lifecycle) {
            if (closed) {
                return CompletableFuture.completedFuture(CachedMuteStatus.UNVERIFIED);
            }
            if (mode.get() != OperationalMode.ACTIVE) {
                return CompletableFuture.completedFuture(CachedMuteStatus.CLEAR);
            }
            if (!admission.tryAcquire()) {
                return CompletableFuture.completedFuture(CachedMuteStatus.UNVERIFIED);
            }
            CompletableFuture<CachedMuteStatus> result = new CompletableFuture<>();
            pending.add(result);
            result.completeOnTimeout(CachedMuteStatus.UNVERIFIED, timeout.toMillis(), TimeUnit.MILLISECONDS);
            result.whenComplete((status, failure) -> pending.remove(result));
            try {
                workers.execute(() -> lookup(senderId, result));
            } catch (RejectedExecutionException exception) {
                admission.release();
                result.complete(CachedMuteStatus.UNVERIFIED);
            }
            return result.minimalCompletionStage();
        }
    }

    private void lookup(UUID senderId, CompletableFuture<CachedMuteStatus> result) {
        try {
            if (result.isDone()) {
                return;
            }
            SanctionLookup lookup = sanctions.get();
            CachedMuteStatus status = lookup == null ? CachedMuteStatus.UNVERIFIED
                    : status(lookup.activeFor(senderId, TYPES, clock.instant()));
            result.complete(status);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Discord mute verification failed; message remains blocked", exception);
            result.complete(CachedMuteStatus.UNVERIFIED);
        } finally {
            // A timeout never frees admission for work that is still running/queued.
            admission.release();
        }
    }

    static CachedMuteStatus status(List<ActiveSanction> sanctions) {
        if (sanctions.stream().anyMatch(sanction -> sanction.type() == SanctionType.MUTE)) {
            return CachedMuteStatus.MUTED;
        }
        return sanctions.isEmpty() ? CachedMuteStatus.CLEAR : CachedMuteStatus.PUBLIC_MUTED;
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            closed = true;
            pending.forEach(result -> result.complete(CachedMuteStatus.UNVERIFIED));
            pending.clear();
        }
    }
}
