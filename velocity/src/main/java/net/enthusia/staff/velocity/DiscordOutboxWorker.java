package net.enthusia.staff.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.discord.DiscordFailureOutcome;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import org.slf4j.Logger;

final class DiscordOutboxWorker implements AutoCloseable {
    private static final int BATCH_SIZE = 4;
    private static final Set<String> EXPECTED_DESTINATIONS = Set.of(
            "punishments", "reports", "logs-staffmode", "alerts"
    );

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Clock clock;
    private final ExecutorService workers;
    private final DiscordOutboxStore store;
    private final Map<String, DiscordWebhookRoute> routes;
    private final int maximumAttempts;
    private final int failureThreshold;
    private final Duration circuitDuration;
    private final Duration lease;
    private final DiscordEventRenderer renderer;
    private final DiscordWebhookTransport transport;
    private final String owner = "velocity-discord:" + UUID.randomUUID();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledTask task;

    DiscordOutboxWorker(
            Object plugin,
            ProxyServer proxy,
            Logger logger,
            Clock clock,
            ExecutorService workers,
            DiscordOutboxStore store,
            Map<String, DiscordWebhookRoute> routes,
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration,
            Duration requestTimeout
    ) {
        this(
                plugin,
                proxy,
                logger,
                clock,
                workers,
                store,
                routes,
                maximumAttempts,
                failureThreshold,
                circuitDuration,
                requestTimeout,
                new DiscordEventRenderer(),
                new DiscordWebhookTransport.Jdk(requestTimeout)
        );
    }

    DiscordOutboxWorker(
            Object plugin,
            ProxyServer proxy,
            Logger logger,
            Clock clock,
            ExecutorService workers,
            DiscordOutboxStore store,
            Map<String, DiscordWebhookRoute> routes,
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration,
            Duration requestTimeout,
            DiscordEventRenderer renderer,
            DiscordWebhookTransport transport
    ) {
        validateDependencies(logger, clock, store, renderer, transport);
        validatePolicy(maximumAttempts, failureThreshold, circuitDuration, requestTimeout);
        validateRoutes(routes);
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.clock = clock;
        this.workers = workers;
        this.store = store;
        this.routes = Map.copyOf(routes);
        this.maximumAttempts = maximumAttempts;
        this.failureThreshold = failureThreshold;
        this.circuitDuration = circuitDuration;
        this.lease = requestTimeout.multipliedBy(BATCH_SIZE).plusSeconds(10);
        this.renderer = renderer;
        this.transport = transport;
    }

    void start() {
        if (plugin == null || proxy == null || workers == null) {
            throw new IllegalStateException("Discord worker scheduling dependencies are unavailable");
        }
        task = proxy.getScheduler().buildTask(plugin, this::scheduleRun)
                .repeat(2, TimeUnit.SECONDS)
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
                    logger.error("Discord outbox worker failed; leased messages remain recoverable", exception);
                } finally {
                    running.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            running.set(false);
            logger.warn("Discord outbox pass skipped because the bounded worker queue is full");
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // SLF4J placeholders defer formatting; arguments are cheap scalar accessors.
    void runOnce() {
        Instant now = clock.instant();
        Map<String, Instant> openedThisPass = new HashMap<>();
        for (DiscordOutboxMessage message : store.claimDue(owner, BATCH_SIZE, lease, now)) {
            Instant blockedUntil = openedThisPass.get(message.destination());
            if (blockedUntil != null) {
                store.deferWithoutAttempt(message.messageId(), owner, blockedUntil);
                continue;
            }
            DiscordWebhookTransport.Delivery delivery = deliver(message);
            if (delivery.success()) {
                if (!store.delivered(message.messageId(), owner, clock.instant())) {
                    logger.warn("Discord outbox completion lost its lease for message {}", message.messageId());
                }
                continue;
            }
            int attempt = message.attemptCount() + 1;
            long delaySeconds = Math.min(900L, 1L << Math.min(attempt, 9));
            Instant failureTime = clock.instant();
            DiscordFailureOutcome outcome = store.failed(
                    message.messageId(), owner, delivery.errorCode(), failureTime.plusSeconds(delaySeconds),
                    failureTime, maximumAttempts, failureThreshold, circuitDuration
            );
            outcome.openUntil().filter(failureTime::isBefore)
                    .ifPresent(openUntil -> openedThisPass.put(message.destination(), openUntil));
            if (outcome.deadLettered()) {
                logger.error("Discord outbox message {} entered dead letter after {} attempts",
                        message.messageId(), attempt);
            } else if (outcome.circuitOpened()) {
                logger.error("Discord delivery circuit opened for destination {}", message.destination());
            }
        }
    }

    private DiscordWebhookTransport.Delivery deliver(DiscordOutboxMessage message) {
        DiscordWebhookRoute route = routes.get(message.destination());
        if (route == null) {
            return DiscordWebhookTransport.Delivery.failed("UNCONFIGURED_DESTINATION");
        }
        final String content;
        try {
            content = renderer.render(message);
        } catch (IllegalArgumentException exception) {
            return DiscordWebhookTransport.Delivery.failed("PAYLOAD_REJECTED");
        }
        return transport.send(route, content);
    }

    private static void validateDependencies(
            Logger logger,
            Clock clock,
            DiscordOutboxStore store,
            DiscordEventRenderer renderer,
            DiscordWebhookTransport transport
    ) {
        if (logger == null || clock == null || store == null || renderer == null || transport == null) {
            throw new IllegalArgumentException("Discord worker dependencies are invalid");
        }
    }

    private static void validatePolicy(
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration,
            Duration requestTimeout
    ) {
        if (maximumAttempts < 1 || failureThreshold < 1) {
            throw new IllegalArgumentException("Discord worker retry policy is invalid");
        }
        requirePositive(circuitDuration, "Discord worker circuit duration is invalid");
        requirePositive(requestTimeout, "Discord worker request timeout is invalid");
    }

    private static void requirePositive(Duration duration, String message) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateRoutes(Map<String, DiscordWebhookRoute> routes) {
        if (routes == null || !routes.keySet().equals(EXPECTED_DESTINATIONS)) {
            throw new IllegalArgumentException("Discord worker requires the complete approved destination matrix");
        }
        DiscordRouteEnvironment environment = null;
        for (Map.Entry<String, DiscordWebhookRoute> entry : routes.entrySet()) {
            DiscordWebhookRoute route = entry.getValue();
            if (route == null || !entry.getKey().equals(route.destination())) {
                throw new IllegalArgumentException("Discord route key and destination must match");
            }
            if (environment == null) {
                environment = route.environment();
            } else if (environment != route.environment()) {
                throw new IllegalArgumentException("Discord routes must not mix staging and production destinations");
            }
        }
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
        }
        if (transport instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Discord transport cleanup failed ({})", exception.getClass().getSimpleName());
                }
            }
        }
    }
}
