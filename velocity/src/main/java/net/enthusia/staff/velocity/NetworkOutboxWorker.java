package net.enthusia.staff.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.network.NetworkOutboxMessage;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.protocol.PersistentChannelServer;
import org.slf4j.Logger;

final class NetworkOutboxWorker implements AutoCloseable {
    private static final int BATCH_SIZE = 25;
    private static final int MAX_ATTEMPTS = 12;
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(3);

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Clock clock;
    private final ExecutorService workers;
    private final NetworkOutboxStore store;
    private final PersistentChannelServer channel;
    private final Set<String> requiredBackends;
    private final String owner = "velocity:" + UUID.randomUUID();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledTask task;

    NetworkOutboxWorker(
            Object plugin,
            ProxyServer proxy,
            Logger logger,
            Clock clock,
            ExecutorService workers,
            NetworkOutboxStore store,
            PersistentChannelServer channel,
            Set<String> requiredBackends
    ) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.clock = clock;
        this.workers = workers;
        this.store = store;
        this.channel = channel;
        this.requiredBackends = Set.copyOf(requiredBackends);
        if (this.requiredBackends.isEmpty()) {
            throw new IllegalArgumentException("at least one required backend is needed");
        }
    }

    void start() {
        task = proxy.getScheduler().buildTask(plugin, this::scheduleRun)
                .repeat(1, TimeUnit.SECONDS)
                .schedule();
    }

    private void scheduleRun() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            workers.execute(() -> {
                try {
                    runOnce();
                } catch (RuntimeException exception) {
                    logger.error("Network outbox worker failed; leased messages remain recoverable", exception);
                } finally {
                    running.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            running.set(false);
            logger.warn("Network outbox pass skipped because the bounded worker queue is full");
        }
    }

    private void runOnce() {
        Instant now = clock.instant();
        for (NetworkOutboxMessage message : store.claimDue(owner, BATCH_SIZE, LEASE, now)) {
            deliver(message, now);
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // SLF4J placeholders defer formatting; arguments are cheap scalar accessors.
    private void deliver(NetworkOutboxMessage message, Instant now) {
        store.prepareDeliveries(message.messageId(), requiredBackends);
        for (String backend : store.pendingDestinations(message.messageId())) {
            PersistentChannelServer.DeliveryStatus status = channel.send(
                    backend,
                    message.messageId(),
                    message.messageType(),
                    message.payloadJson(),
                    ACK_TIMEOUT
            ).join();
            if (status == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED) {
                store.acknowledgeDelivery(message.messageId(), backend, clock.instant());
            }
        }
        if (store.pendingDestinations(message.messageId()).isEmpty()) {
            if (!store.complete(message.messageId(), owner, clock.instant())) {
                logger.warn("Network outbox completion lost its lease for message {}", message.messageId());
            }
            return;
        }
        int attempted = message.attemptCount() + 1;
        if (attempted >= MAX_ATTEMPTS) {
            store.deadLetter(message.messageId(), owner, "DELIVERY_ATTEMPTS_EXHAUSTED");
            logger.error("Network outbox message {} entered dead letter after {} attempts", message.messageId(), attempted);
            return;
        }
        long delaySeconds = Math.min(300, 1L << Math.min(attempted, 8));
        store.retry(message.messageId(), owner, now.plusSeconds(delaySeconds), "BACKEND_NOT_ACKNOWLEDGED");
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
        }
    }
}
