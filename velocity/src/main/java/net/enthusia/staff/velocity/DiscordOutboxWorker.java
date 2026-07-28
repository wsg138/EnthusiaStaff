package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Clock clock;
    private final ExecutorService workers;
    private final DiscordOutboxStore store;
    private final Map<String, URI> webhooks;
    private final int maximumAttempts;
    private final int failureThreshold;
    private final Duration circuitDuration;
    private final Duration requestTimeout;
    private final Duration lease;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
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
            Map<String, URI> webhooks,
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration,
            Duration requestTimeout
    ) {
        if (webhooks.size() != 4 || maximumAttempts < 1 || failureThreshold < 1
                || circuitDuration.isZero() || circuitDuration.isNegative()
                || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Discord worker configuration is invalid");
        }
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.clock = clock;
        this.workers = workers;
        this.store = store;
        this.webhooks = Map.copyOf(webhooks);
        this.maximumAttempts = maximumAttempts;
        this.failureThreshold = failureThreshold;
        this.circuitDuration = circuitDuration;
        this.requestTimeout = requestTimeout;
        this.lease = requestTimeout.multipliedBy(BATCH_SIZE).plusSeconds(10);
        this.http = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    void start() {
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
    private void runOnce() {
        Instant now = clock.instant();
        Map<String, Instant> openedThisPass = new HashMap<>();
        for (DiscordOutboxMessage message : store.claimDue(owner, BATCH_SIZE, lease, now)) {
            Instant blockedUntil = openedThisPass.get(message.destination());
            if (blockedUntil != null) {
                store.deferWithoutAttempt(message.messageId(), owner, blockedUntil);
                continue;
            }
            Delivery delivery = deliver(message);
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

    private Delivery deliver(DiscordOutboxMessage message) {
        URI webhook = webhooks.get(message.destination());
        if (webhook == null) {
            return Delivery.failed("UNCONFIGURED_DESTINATION");
        }
        try {
            ObjectNode body = json.createObjectNode();
            String summary = message.eventType() + "\n" + message.payloadJson();
            body.put("content", summary.length() <= 1900 ? summary : summary.substring(0, 1900));
            ObjectNode allowedMentions = body.putObject("allowed_mentions");
            ArrayNode parse = allowedMentions.putArray("parse");
            parse.removeAll();
            HttpRequest request = HttpRequest.newBuilder(webhook)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Delivery.delivered();
            }
            if (response.statusCode() == 429) {
                return Delivery.failed("HTTP_429");
            }
            if (response.statusCode() >= 500) {
                return Delivery.failed("HTTP_5XX");
            }
            return Delivery.failed("HTTP_4XX");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Delivery.failed("INTERRUPTED");
        } catch (IOException | RuntimeException exception) {
            return Delivery.failed("IO_OR_ENCODING_FAILURE");
        }
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
        }
    }

    private record Delivery(boolean success, String errorCode) {
        private static Delivery delivered() {
            return new Delivery(true, "NONE");
        }

        private static Delivery failed(String errorCode) {
            return new Delivery(false, errorCode);
        }
    }
}
