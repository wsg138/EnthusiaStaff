package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiMirrorRegistrationPublisherTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String SNOWFLAKE = "1234567890123456787";

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "staffbot-bridge", "example-private-token", GUILD, Set.of(CHANNEL),
                "discord_general");
    }

    private static DiscordChatPublication receipt(long guild, long channel) {
        return new DiscordChatPublication(
                guild, channel, SNOWFLAKE, "rosechat:canonical:1", "hello original");
    }

    @Test
    void staysDisabledUntilExplicitlyResumedAndRejectsOtherChannels() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (AiMirrorRegistrationPublisher sender = new AiMirrorRegistrationPublisher(
                config(), request -> calls.incrementAndGet())) {
            sender.published(receipt(GUILD, CHANNEL));
            sender.resume();
            sender.published(receipt(GUILD, CHANNEL + 1));
            sender.published(receipt(GUILD + 1, CHANNEL));
            Thread.sleep(40);
            assertEquals(0, calls.get());
            sender.pause();
            sender.published(receipt(GUILD, CHANNEL));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void emitsScopedAuthenticatedRequestOnlyAfterActualReceipt() throws Exception {
        CountDownLatch posted = new CountDownLatch(1);
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        try (AiMirrorRegistrationPublisher sender = new AiMirrorRegistrationPublisher(
                config(), request -> {
                    captured.set(request);
                    posted.countDown();
                    return 200;
                })) {
            sender.resume();
            sender.published(receipt(GUILD, CHANNEL));
            assertTrue(posted.await(3, TimeUnit.SECONDS));
            HttpRequest request = captured.get();
            assertEquals("POST", request.method());
            assertEquals(config().endpoint(), request.uri());
            assertEquals("staffbot-bridge", request.headers()
                    .firstValue("X-Client-Id").orElseThrow());
            assertEquals("Bearer example-private-token", request.headers()
                    .firstValue("Authorization").orElseThrow());
            JsonNode body = new ObjectMapper().readTree(bodyText(request));
            assertEquals("rosechat:canonical:1", body.get("canonical_message_id").asText());
            assertEquals("hello original", body.get("canonical_text").asText());
            assertEquals("discord", body.get("platform").asText());
            assertEquals("discord_general", body.get("channel_profile").asText());
            assertEquals(Long.toString(GUILD), body.get("scope_id").asText());
            assertEquals(Long.toString(CHANNEL), body.get("channel_id").asText());
            assertEquals(SNOWFLAKE, body.get("external_message_id").asText());
            assertEquals(7, body.size());
        }
    }

    @Test
    void failedRegistrationDoesNotRetryOrThrowIntoDiscordDelivery() throws Exception {
        CountDownLatch posted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (AiMirrorRegistrationPublisher sender = new AiMirrorRegistrationPublisher(
                config(), request -> {
                    calls.incrementAndGet();
                    posted.countDown();
                    throw new IOException("test-only unavailable");
                })) {
            sender.resume();
            sender.published(receipt(GUILD, CHANNEL));
            assertTrue(posted.await(3, TimeUnit.SECONDS));
            sender.pause();
            assertEquals(1, calls.get());
        }
    }

    @Test
    void rejectsProductionEvenWithExplicitOptIn() {
        assertThrows(IllegalArgumentException.class, () ->
                AiMirrorPublicationConfig.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, null,
                        Map.of(AiMirrorPublicationConfig.ENABLED, "true")));
        assertThrows(IllegalArgumentException.class, () ->
                AiMirrorPublicationConfig.fromEnvironment(
                        StaffBotEnvironment.STAGING, null,
                        Map.of(AiMirrorPublicationConfig.ENABLED, "true")));
    }

    @Test
    void defaultEnvironmentConfigDoesNotOpenAnyPublisher() throws Exception {
        assertTrue(AiMirrorPublicationConfig.fromEnvironment(
                StaffBotEnvironment.STAGING, null, Map.of()).isEmpty());
        assertTrue(AiMirrorPublicationConfig.fromEnvironment(
                StaffBotEnvironment.PRODUCTION, null, Map.of()).isEmpty());
    }

    private static String bodyText(HttpRequest request) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable error) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void rejectsUnsafeOrUnexpectedEndpointAndProfiles() {
        assertThrows(IllegalArgumentException.class, () -> new AiMirrorPublicationConfig(
                URI.create("http://example.com:1234/v1/mirror-aliases"),
                "bridge", "token", GUILD, Set.of(CHANNEL), "discord_general"));
        assertThrows(IllegalArgumentException.class, () -> new AiMirrorPublicationConfig(
                URI.create("https://example.com:1234/v1/moderate"),
                "bridge", "token", GUILD, Set.of(CHANNEL), "discord_general"));
        assertThrows(IllegalArgumentException.class, () -> new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:1234/v1/mirror-aliases"),
                "bridge", "token", GUILD, Set.of(CHANNEL), "discord_staff_exempt"));
        assertFalse(config().token().isBlank());
    }
}
