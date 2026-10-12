package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Inert nonblocking post-send receipt journal ingress. Not constructed or
 * installed by StaffBotRuntime. Discord delivery never waits for fsync/API.
 *
 * A bounded one-worker queue persists only actual post-send receipts.
 * It never sends HTTP, reposts Discord, classifies or enforces.
 */
final class AiMirrorDurableReceiptIngress implements DiscordChatPostObserver, AutoCloseable {
    private static final int PENDING_CAPACITY = 64;

    @FunctionalInterface
    interface Sink {
        void persist(DiscordChatPublication publication) throws IOException;
    }

    record Stats(long persisted, long capacityDrops, long storageFailures,
                 long cancelledPending) {}

    private final AiMirrorPublicationConfig config;
    private final Sink sink;
    private final ThreadPoolExecutor worker;
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong capacityDrops = new AtomicLong();
    private final AtomicLong storageFailures = new AtomicLong();
    private final AtomicLong cancelledPending = new AtomicLong();

    static AiMirrorDurableReceiptIngress withStore(
            AiMirrorPublicationConfig config, AiMirrorEncryptedOutbox store) {
        Objects.requireNonNull(store, "store");
        if (!store.accepts(config)) {
            throw new IllegalArgumentException("durable receipt journal scope mismatch");
        }
        return new AiMirrorDurableReceiptIngress(config, publication -> {
            store.save(publication);
        });
    }

    AiMirrorDurableReceiptIngress(AiMirrorPublicationConfig config, Sink sink) {
        this.config = Objects.requireNonNull(config, "config");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.worker = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(PENDING_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "StaffBot-AI-Mirror-Durable-Ingress");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    void resume() {
        if (!closed.get() && !failed.get()) {
            accepting.set(true);
        }
    }

    void pause() {
        accepting.set(false);
        int removed = worker.getQueue().size();
        worker.getQueue().clear();
        cancelledPending.addAndGet(removed);
    }

    @Override
    public void published(DiscordChatPublication publication) {
        if (!eligible(publication)) {
            return;
        }
        try {
            worker.execute(() -> persistOffCallback(publication));
        } catch (RejectedExecutionException fullOrClosed) {
            // Fail open for Discord chat; count the unregistered alias loss.
            capacityDrops.incrementAndGet();
        }
    }

    private boolean eligible(DiscordChatPublication publication) {
        return publication != null && accepting.get() && !closed.get() && !failed.get()
                && publication.guildId() == config.guildId()
                && config.allowedChannels().contains(publication.channelId());
    }

    private void persistOffCallback(DiscordChatPublication publication) {
        if (!eligible(publication)) {
            cancelledPending.incrementAndGet();
            return;
        }
        try {
            sink.persist(publication);
            persisted.incrementAndGet();
        } catch (IOException | RuntimeException failure) {
            // Full/read-only/insecure storage stops all new admission; never
            // leak payload, identifiers, paths, or the underlying exception.
            storageFailures.incrementAndGet();
            failed.set(true);
            accepting.set(false);
            cancelPending();
        }
    }

    private void cancelPending() {
        int removed = worker.getQueue().size();
        worker.getQueue().clear();
        cancelledPending.addAndGet(removed);
    }

    Stats stats() {
        return new Stats(persisted.get(), capacityDrops.get(),
                storageFailures.get(), cancelledPending.get());
    }

    boolean failed() {
        return failed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            accepting.set(false);
            cancelPending();
            worker.shutdownNow();
        }
    }
}
