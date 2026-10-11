package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Staging-only non-enforcing publisher: attach exact JDA-posted message IDs.
 * Never delete messages or impose AI sanctions; failures are best effort.
 */
final class AiMirrorRegistrationPublisher implements DiscordChatPostObserver, AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(AiMirrorRegistrationPublisher.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final int QUEUE_CAPACITY = 64;
    private static final int HTTP_OK = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionalInterface
    interface Poster {
        int post(HttpRequest request) throws IOException, InterruptedException;
    }

    private final AiMirrorPublicationConfig config;
    private final Poster poster;
    private final ThreadPoolExecutor queue;
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    static AiMirrorRegistrationPublisher create(AiMirrorPublicationConfig config) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new AiMirrorRegistrationPublisher(config, request -> client.send(
                request, HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    AiMirrorRegistrationPublisher(AiMirrorPublicationConfig config, Poster poster) {
        this.config = Objects.requireNonNull(config, "config");
        this.poster = Objects.requireNonNull(poster, "poster");
        queue = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "StaffBot-AI-Mirror-Staging");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    void resume() {
        if (!closed.get()) {
            accepting.set(true);
        }
    }

    void pause() {
        accepting.set(false);
        queue.getQueue().clear();
    }

    @Override
    public void published(DiscordChatPublication publication) {
        if (!eligible(publication)) {
            return;
        }
        try {
            queue.execute(() -> publishIfActive(publication));
        } catch (RejectedExecutionException ignored) {
            // Fail open if the staging API is slow; never backpressure chat.
        }
    }

    private boolean eligible(DiscordChatPublication publication) {
        return publication != null && accepting.get() && !closed.get()
                && publication.guildId() == config.guildId()
                && config.allowedChannels().contains(publication.channelId());
    }

    private void publishIfActive(DiscordChatPublication publication) {
        if (!eligible(publication)) {
            return;
        }
        try {
            int status = poster.post(request(publication));
            if (status != HTTP_OK) {
                LOGGER.log(System.Logger.Level.WARNING, "ai_mirror_registration_not_confirmed");
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING, "ai_mirror_registration_unavailable");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpRequest request(DiscordChatPublication publication) throws JsonProcessingException {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("canonical_message_id", publication.canonicalMessageId());
        data.put("canonical_text", publication.canonicalPlainText());
        data.put("platform", "discord");
        data.put("channel_profile", config.channelProfile());
        data.put("scope_id", Long.toString(publication.guildId()));
        data.put("channel_id", Long.toString(publication.channelId()));
        data.put("external_message_id", publication.discordMessageId());
        return HttpRequest.newBuilder(config.endpoint())
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Client-Id", config.clientId())
                .header("Authorization", "Bearer " + config.token())
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(data)))
                .build();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pause();
            queue.shutdownNow();
        }
    }
}
