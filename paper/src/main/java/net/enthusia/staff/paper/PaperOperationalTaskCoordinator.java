package net.enthusia.staff.paper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;

final class PaperOperationalTaskCoordinator implements AutoCloseable {
    private final Executor workers;
    private final BooleanSupplier stopping;
    private final Runnable refreshOperationalState;
    private final Runnable reportEvidenceMaintenance;
    private final Supplier<PunishmentRequestStore> requestStore;
    private final Supplier<PunishmentRequestAlertWorkerSettings> settings;
    private final Clock clock;
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile Duration expirationInterval;
    private volatile Instant nextExpiration = Instant.MIN;

    PaperOperationalTaskCoordinator(
            Executor workers,
            BooleanSupplier stopping,
            Runnable refreshOperationalState,
            Runnable reportEvidenceMaintenance,
            Supplier<PunishmentRequestStore> requestStore,
            Supplier<PunishmentRequestAlertWorkerSettings> settings,
            Clock clock,
            Logger logger
    ) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.refreshOperationalState = Objects.requireNonNull(refreshOperationalState, "refreshOperationalState");
        this.reportEvidenceMaintenance = Objects.requireNonNull(
                reportEvidenceMaintenance, "reportEvidenceMaintenance");
        this.requestStore = Objects.requireNonNull(requestStore, "requestStore");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void trigger() {
        if (isStopping() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            workers.execute(this::runWorkerCycle);
        } catch (RejectedExecutionException exception) {
            running.set(false);
            logger.log(Level.FINE,
                    "Operational maintenance was rejected; the next scheduler tick will retry",
                    exception);
        } catch (RuntimeException exception) {
            running.set(false);
            logger.log(Level.WARNING, "Operational maintenance submission failed", exception);
        }
    }

    private void runWorkerCycle() {
        try {
            runOperation("Operational state refresh failed", refreshOperationalState);
            runOperation("Report evidence maintenance failed", reportEvidenceMaintenance);
            runOperation("Punishment request expiration failed", this::expireRequestsIfDue);
        } finally {
            running.set(false);
        }
    }

    private void runOperation(String failureMessage, Runnable operation) {
        if (isStopping()) {
            return;
        }
        try {
            operation.run();
        } catch (RuntimeException exception) {
            if (!isStopping()) {
                logger.log(Level.SEVERE, failureMessage, exception);
            }
        }
    }

    private void expireRequestsIfDue() {
        PunishmentRequestAlertWorkerSettings current = settings.get();
        Duration currentInterval = current.requestExpirationInterval();
        Instant now = clock.instant();
        if (!currentInterval.equals(expirationInterval)) {
            expirationInterval = currentInterval;
            nextExpiration = now;
        }
        if (now.isBefore(nextExpiration)) {
            return;
        }
        requestStore.get().expire(now, current.requestExpirationBatch());
        nextExpiration = now.plus(currentInterval);
    }

    private boolean isStopping() {
        return closed.get() || stopping.getAsBoolean();
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
