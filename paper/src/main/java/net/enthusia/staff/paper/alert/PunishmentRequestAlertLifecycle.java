package net.enthusia.staff.paper.alert;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentRequestAlertLifecycle implements PunishmentRequestAlertManagedLifecycle {
    private static final long MAINTENANCE_LOG_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final PunishmentRequestAlertRuntime runtime;
    private final Clock clock;
    private final PunishmentRequestAlertWorkerSettings settings;
    private final PunishmentRequestStore requests;
    private final PunishmentRequestAlertStore alerts;
    private final PunishmentRequestAlertWorker worker;
    private final Executor asynchronous;
    private final BooleanSupplier pluginStopping;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final Set<UUID> recipientFlights = ConcurrentHashMap.newKeySet();
    private final Set<PunishmentRequestAlertRuntime.Cancellable> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicReference<AutoCloseable> joinRegistration = new AtomicReference<>();

    public PunishmentRequestAlertLifecycle(
            JavaPlugin plugin,
            Clock clock,
            String owner,
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players,
            Executor asynchronous,
            BooleanSupplier pluginStopping
    ) {
        this(
                new BukkitPunishmentRequestAlertRuntime(plugin),
                clock,
                owner,
                settings,
                alerts,
                requests,
                players,
                asynchronous,
                pluginStopping
        );
    }

    PunishmentRequestAlertLifecycle(
            PunishmentRequestAlertRuntime runtime,
            Clock clock,
            String owner,
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players,
            Executor asynchronous,
            BooleanSupplier pluginStopping
    ) {
        if (runtime == null || clock == null || settings == null || alerts == null || requests == null
                || players == null || asynchronous == null || pluginStopping == null) {
            throw new IllegalArgumentException("punishment request alert lifecycle dependencies must be present");
        }
        this.runtime = runtime;
        this.clock = clock;
        this.settings = settings;
        this.alerts = alerts;
        this.requests = requests;
        this.asynchronous = asynchronous;
        this.pluginStopping = pluginStopping;
        PunishmentRequestAlertPresenter presenter = new PunishmentRequestAlertPresenter() {
            @Override
            public Optional<PunishmentRequestAlertRecipient> current(UUID playerId) {
                return runtime.currentRecipient(playerId);
            }

            @Override
            public boolean present(
                    PunishmentRequestAlertRecipient recipient,
                    PunishmentRequestAlertPresentation presentation
            ) {
                return !isStopping() && runtime.present(recipient, presentation);
            }
        };
        this.worker = new PunishmentRequestAlertWorker(
                clock,
                owner,
                settings,
                alerts,
                requests,
                players,
                new PunishmentRequestAlertRenderer(),
                presenter,
                asynchronous,
                runtime::executeForRecipient,
                this::isStopping,
                runtime.logger()
        );
    }

    // Ownership transfers to joinRegistration on success and close() releases it.
    @SuppressWarnings("PMD.CloseResource")
    public boolean start() {
        if (!settings.enabled() || isStopping() || !active.compareAndSet(false, true)) {
            return false;
        }
        try {
            AutoCloseable registration = runtime.registerJoinListener(this::scheduleJoin);
            if (!joinRegistration.compareAndSet(null, registration)) {
                closeQuietly(registration);
                throw new IllegalStateException("punishment request alert join listener is already registered");
            }
            addTask(runtime.scheduleSynchronousRepeating(
                    this::poll,
                    Duration.ofMillis(50),
                    settings.pollInterval()
            ));
            scheduleMaintenance(
                    "request expiration",
                    settings.requestExpirationInterval(),
                    () -> requests.expire(clock.instant(), settings.requestExpirationBatch())
            );
            scheduleMaintenance(
                    "alert intent expiration",
                    settings.intentExpirationInterval(),
                    () -> alerts.expireIntents(clock.instant(), settings.intentExpirationBatch())
            );
            scheduleMaintenance(
                    "expired delivery reclaim",
                    settings.leaseReclaimInterval(),
                    () -> alerts.reclaimExpiredDeliveries(clock.instant(), settings.leaseReclaimBatch())
            );
            scheduleMaintenance(
                    "terminal alert retention",
                    settings.retentionInterval(),
                    () -> alerts.deleteTerminalIntentsBefore(
                            clock.instant().minus(settings.retentionDuration()),
                            settings.retentionBatch()
                    )
            );
            return true;
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public boolean active() {
        return active.get() && !isStopping();
    }

    private void poll() {
        if (isStopping() || !polling.compareAndSet(false, true)) {
            return;
        }
        List<PunishmentRequestAlertRecipient> recipients;
        try {
            recipients = runtime.onlineRecipients(settings.recipientLimit());
        } catch (RuntimeException exception) {
            polling.set(false);
            runtime.logger().log(Level.WARNING,
                    "Punishment request alert recipient snapshot failed",
                    exception);
            return;
        }
        int cycleLimit = Math.min(settings.totalClaimLimit(), settings.presentationLimit());
        PunishmentRequestAlertWorker.ClaimBudget budget =
                new PunishmentRequestAlertWorker.ClaimBudget(cycleLimit);
        AtomicInteger remaining = new AtomicInteger(1);
        for (PunishmentRequestAlertRecipient recipient : recipients) {
            if (isStopping() || budget.remaining() == 0) {
                break;
            }
            if (!recipientFlights.add(recipient.playerId())) {
                continue;
            }
            remaining.incrementAndGet();
            worker.deliver(recipient, budget, () -> completeRecipient(recipient.playerId(), remaining));
        }
        if (remaining.decrementAndGet() == 0) {
            polling.set(false);
        }
    }

    private void completeRecipient(UUID playerId, AtomicInteger remaining) {
        recipientFlights.remove(playerId);
        if (remaining.decrementAndGet() == 0) {
            polling.set(false);
        }
    }

    private void scheduleJoin(UUID playerId) {
        if (isStopping()) {
            return;
        }
        AtomicReference<PunishmentRequestAlertRuntime.Cancellable> reference = new AtomicReference<>();
        AtomicBoolean executed = new AtomicBoolean();
        try {
            PunishmentRequestAlertRuntime.Cancellable task = runtime.scheduleSynchronousDelayed(() -> {
                executed.set(true);
                PunishmentRequestAlertRuntime.Cancellable scheduled = reference.get();
                if (scheduled != null) {
                    tasks.remove(scheduled);
                }
                deliverJoined(playerId);
            }, settings.joinDelay());
            reference.set(task);
            if (isStopping()) {
                cancel(task);
            } else if (!executed.get()) {
                addTask(task);
                if (executed.get()) {
                    tasks.remove(task);
                }
            }
        } catch (RuntimeException exception) {
            runtime.logger().log(Level.WARNING,
                    "Punishment request reconnect delivery could not be scheduled",
                    exception);
        }
    }

    private void deliverJoined(UUID playerId) {
        if (isStopping() || !recipientFlights.add(playerId)) {
            return;
        }
        Optional<PunishmentRequestAlertRecipient> recipient;
        try {
            recipient = runtime.snapshotRecipient(playerId);
        } catch (RuntimeException exception) {
            recipientFlights.remove(playerId);
            runtime.logger().log(Level.WARNING,
                    "Punishment request reconnect recipient snapshot failed",
                    exception);
            return;
        }
        if (recipient.isEmpty()) {
            recipientFlights.remove(playerId);
            return;
        }
        int cycleLimit = Math.min(settings.totalClaimLimit(), settings.presentationLimit());
        worker.deliver(
                recipient.orElseThrow(),
                new PunishmentRequestAlertWorker.ClaimBudget(cycleLimit),
                () -> recipientFlights.remove(playerId)
        );
    }

    private void scheduleMaintenance(String name, Duration interval, MaintenanceOperation operation) {
        MaintenanceState state = new MaintenanceState(name);
        addTask(runtime.scheduleAsynchronousRepeating(
                () -> submitMaintenance(state, operation),
                interval,
                interval
        ));
    }

    private void submitMaintenance(MaintenanceState state, MaintenanceOperation operation) {
        if (isStopping() || !state.running.compareAndSet(false, true)) {
            return;
        }
        try {
            asynchronous.execute(() -> runMaintenance(state, operation));
        } catch (RuntimeException exception) {
            state.running.set(false);
            if (!isStopping()) {
                runtime.logger().log(Level.FINE,
                        "Punishment request alert maintenance submission was rejected: " + state.name,
                        exception);
            }
        }
    }

    private void runMaintenance(MaintenanceState state, MaintenanceOperation operation) {
        try {
            if (isStopping()) {
                return;
            }
            operation.run();
        } catch (RuntimeException exception) {
            long now = System.nanoTime();
            long previous = state.lastFailureLog.get();
            if (previous == Long.MIN_VALUE || now - previous >= MAINTENANCE_LOG_INTERVAL_NANOS) {
                if (state.lastFailureLog.compareAndSet(previous, now)) {
                    runtime.logger().log(Level.WARNING,
                            "Punishment request alert maintenance failed: " + state.name,
                            exception);
                }
            }
        } finally {
            state.running.set(false);
        }
    }

    private void addTask(PunishmentRequestAlertRuntime.Cancellable task) {
        if (task == null) {
            throw new IllegalArgumentException("scheduled task must be present");
        }
        if (isStopping()) {
            cancel(task);
            return;
        }
        tasks.add(task);
        if (isStopping() && tasks.remove(task)) {
            cancel(task);
        }
    }

    private boolean isStopping() {
        return stopping.get() || pluginStopping.getAsBoolean();
    }

    @Override
    public void close() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        active.set(false);
        closeQuietly(joinRegistration.getAndSet(null));
        tasks.forEach(this::cancel);
        tasks.clear();
        polling.set(false);
        recipientFlights.clear();
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            runtime.logger().log(Level.FINE,
                    "Punishment request alert listener cleanup was interrupted",
                    exception);
        } catch (Exception exception) {
            runtime.logger().log(Level.FINE,
                    "Punishment request alert listener cleanup failed",
                    exception);
        }
    }

    private void cancel(PunishmentRequestAlertRuntime.Cancellable task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException exception) {
            runtime.logger().log(Level.FINE,
                    "Punishment request alert task cleanup failed",
                    exception);
        }
    }

    private static final class MaintenanceState {
        private final String name;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicLong lastFailureLog = new AtomicLong(Long.MIN_VALUE);

        private MaintenanceState(String name) {
            this.name = name;
        }
    }

    @FunctionalInterface
    private interface MaintenanceOperation {
        int run();
    }
}
