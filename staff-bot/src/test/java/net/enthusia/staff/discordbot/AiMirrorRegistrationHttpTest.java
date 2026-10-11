package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Loopback-only HTTP transport tests; no Discord, Minecraft, or production API. */
class AiMirrorRegistrationHttpTest {
    private static final String PATH = "/v1/mirror-aliases";
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String POSTED_ID = "1234567890123456787";
    private static final String CANONICAL_ID = "rosechat:canonical:network-test";
    private static final String ORIGINAL_TEXT = "original undecorated text";
    private static final String CLIENT = "staging-bridge-test";
    private static final String TOKEN = "synthetic-local-token";
    private static final String PROFILE = "discord_general";

    @Test
    void actualHttpClientPostsExactScopedPayloadAndCredential() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<RequestSnapshot> captured = new AtomicReference<>();
        HttpServer server = loopbackServer();
        server.createContext(PATH, exchange -> {
            try (exchange) {
                captured.set(new RequestSnapshot(
                        exchange.getRequestMethod(),
                        exchange.getRequestHeaders().getFirst("X-Client-Id"),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        new ObjectMapper().readTree(exchange.getRequestBody())));
                exchange.sendResponseHeaders(200, -1);
            } finally {
                received.countDown();
            }
        });
        server.start();
        try (AiMirrorRegistrationPublisher publisher =
                     AiMirrorRegistrationPublisher.create(configuration(server))) {
            publisher.resume();
            publisher.published(publication());
            assertTrue(received.await(4, TimeUnit.SECONDS));
            RequestSnapshot request = captured.get();
            assertNotNull(request);
            assertEquals("POST", request.method());
            assertEquals(CLIENT, request.clientId());
            assertEquals("Bearer " + TOKEN, request.authorization());
            JsonNode body = request.body();
            assertEquals(7, body.size());
            assertEquals(CANONICAL_ID, body.get("canonical_message_id").asText());
            assertEquals(ORIGINAL_TEXT, body.get("canonical_text").asText());
            assertEquals("discord", body.get("platform").asText());
            assertEquals(PROFILE, body.get("channel_profile").asText());
            assertEquals(Long.toString(GUILD), body.get("scope_id").asText());
            assertEquals(Long.toString(CHANNEL), body.get("channel_id").asText());
            assertEquals(POSTED_ID, body.get("external_message_id").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpRedirectCannotForwardBearerOrMessageToUnexpectedPath() throws Exception {
        CountDownLatch originalReceived = new CountDownLatch(1);
        CountDownLatch forbiddenReceived = new CountDownLatch(1);
        AtomicInteger originalCalls = new AtomicInteger();
        HttpServer server = loopbackServer();
        server.createContext(PATH, exchange -> {
            try (exchange) {
                originalCalls.incrementAndGet();
                exchange.getResponseHeaders().set(
                        "Location", "http://127.0.0.1:" + server.getAddress().getPort()
                                + "/unexpected");
                exchange.sendResponseHeaders(307, -1);
            } finally {
                originalReceived.countDown();
            }
        });
        server.createContext("/unexpected", exchange -> {
            forbiddenReceived.countDown();
            try (exchange) {
                exchange.sendResponseHeaders(200, -1);
            }
        });
        server.start();
        try (AiMirrorRegistrationPublisher publisher =
                     AiMirrorRegistrationPublisher.create(configuration(server))) {
            publisher.resume();
            publisher.published(publication());
            assertTrue(originalReceived.await(4, TimeUnit.SECONDS));
            assertFalse(forbiddenReceived.await(1, TimeUnit.SECONDS));
            assertEquals(1, originalCalls.get());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer loopbackServer() throws Exception {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static AiMirrorPublicationConfig configuration(HttpServer server) {
        URI endpoint = URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort() + PATH);
        return new AiMirrorPublicationConfig(
                endpoint, CLIENT, TOKEN, GUILD, Set.of(CHANNEL), PROFILE);
    }

    private static DiscordChatPublication publication() {
        return new DiscordChatPublication(
                GUILD, CHANNEL, POSTED_ID, CANONICAL_ID, ORIGINAL_TEXT);
    }

    private record RequestSnapshot(
            String method, String clientId, String authorization, JsonNode body) {
    }
}
