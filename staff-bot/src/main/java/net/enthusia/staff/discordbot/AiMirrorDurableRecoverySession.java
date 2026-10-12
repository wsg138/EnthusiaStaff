package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Staging-only lifecycle composition; NOT installed or loaded by StaffBotRuntime.
 * A future reviewed runtime gate must choose this OR volatile registration.
 * Never subscribe both to the one actual JDA post-send observer.
 */
final class AiMirrorDurableRecoverySession
        implements DiscordChatPostObserver, StaffBotChatLifecycle {
    private static final Duration REQUEST_DEADLINE = Duration.ofSeconds(2);
    private static final Duration SHUTDOWN_DEADLINE = Duration.ofSeconds(3);
    private static final String ERROR_HEADER = "X-Moderation-Mirror-Error";

    private final Object lifecycleLock = new Object();
    private final StaffBotChatLifecycle chat;
    private final AiMirrorEncryptedOutbox store;
    private final AiMirrorDurableReceiptIngress ingress;
    private final AiMirrorRecoveryScheduler recovery;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean recoveryStarted = new AtomicBoolean();
    private final AtomicBoolean identityReady = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    static AiMirrorDurableRecoverySession open(
            AiMirrorDurableStagingSettings settings,
            AiMirrorPublicationConfig transport,
            StaffBotChatLifecycle chat,
            Clock clock) throws IOException {
        Objects.requireNonNull(settings, "settings");
        if (!settings.boundTo(transport)) {
            throw new IllegalArgumentException("durable journal destination configuration mismatch");
        }
        AiMirrorEncryptedOutbox store = settings.openOutbox(clock);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(REQUEST_DEADLINE)
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            return new AiMirrorDurableRecoverySession(chat, store, transport, request -> {
                HttpResponse<Void> response = client.send(
                        request, HttpResponse.BodyHandlers.discarding());
                return new AiMirrorRegistrationPublisher.PostResult(
                        response.statusCode(),
                        response.headers().firstValue(ERROR_HEADER).orElse(""));
            });
        } catch (RuntimeException failure) {
            store.close();
            throw failure;
        }
    }

    AiMirrorDurableRecoverySession(
            StaffBotChatLifecycle chat, AiMirrorEncryptedOutbox store,
            AiMirrorPublicationConfig config,
            AiMirrorRegistrationPublisher.DetailedPoster poster) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.store = Objects.requireNonNull(store, "store");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(poster, "poster");
        this.ingress = AiMirrorDurableReceiptIngress.withStore(config, store);
        this.recovery = new AiMirrorRecoveryScheduler(
                store, new AiMirrorReceiptReplay(store, config, poster));
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
    if (closed.get() || !started.compareAndSet(false, true)) {
                throw new IllegalStateException("durable staging chat already started or closed");
            }
            chat.start();
            // The verified JDA callback can arrive before chat.start() completes.
            if (identityReady.get()) {
                resumeAfterStart();
            }
        }
    }

    @Override
    public void resume() {
        synchronized (lifecycleLock) {
    if (closed.get()) {
                throw new IllegalStateException("durable staging chat already closed");
            }
            identityReady.set(true);
            if (started.get()) {
                resumeAfterStart();
            }
        }
    }

    private void resumeAfterStart() {
        if (ingress.failed() || recovery.failed()) {
            throw new IllegalStateException("durable staging mirror storage unavailable");
        }
        try {
            chat.resume();
            ingress.resume();
            resumeRecovery();
        } catch (RuntimeException failure) {
            ingress.pause();
            recovery.pause();
            throw failure;
        }
    }

    private void resumeRecovery() {
        boolean scheduled = recoveryStarted.compareAndSet(false, true)
                ? recovery.start() : recovery.resume();
        if (!scheduled && (ingress.failed() || recovery.failed())) {
            throw new IllegalStateException("durable staging mirror recovery unavailable");
        }
    }

    @Override
    public void pause() {
        synchronized (lifecycleLock) {
    identityReady.set(false);
            ingress.pause();
            recovery.pause();
            chat.pause();
        }
    }

    @Override
    public void published(DiscordChatPublication publication) {
        if (!closed.get()) {
            ingress.published(publication);
        }
    }

    boolean unhealthy() {
        return ingress.failed() || recovery.failed();
    }

    AiMirrorDurableReceiptIngress.Stats ingressStats() {
        return ingress.stats();
    }

    AiMirrorRecoveryScheduler.Stats recoveryStats() {
        return recovery.stats();
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
    if (!closed.compareAndSet(false, true)) {
                return;
            }
            identityReady.set(false);
            ingress.close();
            recovery.close();
            try {
                // If a worker cannot terminate, retain the exclusive file lock.
                // A second process must never race the old disk writer or HTTP ACK.
                if (!ingress.awaitStopped(SHUTDOWN_DEADLINE)
                        || !recovery.awaitStopped(SHUTDOWN_DEADLINE)) {
                    throw new IllegalStateException("durable mirror workers did not terminate");
                }
                store.close();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("durable mirror shutdown interrupted");
            } catch (IOException failure) {
                throw new IllegalStateException("encrypted mirror journal could not close");
            } finally {
                chat.close();
            }
        }
    }

}
