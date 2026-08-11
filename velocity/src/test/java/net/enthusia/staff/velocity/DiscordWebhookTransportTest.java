package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class DiscordWebhookTransportTest {
    @Test
    void isolatedExchangeReceivesApprovedHttpsRouteAndMentionSafeBody() throws Exception {
        AtomicReference<URI> endpoint = new AtomicReference<>();
        AtomicReference<Duration> timeout = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        DiscordWebhookTransport.Jdk transport = new DiscordWebhookTransport.Jdk(
                Duration.ofSeconds(2),
                (uri, requestTimeout, jsonBody) -> {
                    endpoint.set(uri);
                    timeout.set(requestTimeout);
                    body.set(jsonBody);
                    return 204;
                }
        );
        DiscordWebhookRoute route = DiscordWebhookRoute.approvedStaging(
                "reports",
                URI.create("https://discord-staging.example.test/webhook/reports"),
                Set.of("discord-staging.example.test")
        );

        DiscordWebhookTransport.Delivery delivery = transport.send(
                route,
                "REPORT_CREATED\nreportId=r-1"
        );

        assertTrue(delivery.success());
        assertEquals(route.endpoint(), endpoint.get());
        assertEquals(Duration.ofSeconds(2), timeout.get());
        JsonNode payload = new ObjectMapper().readTree(body.get());
        assertEquals("REPORT_CREATED\nreportId=r-1", payload.path("content").asText());
        assertTrue(payload.path("allowed_mentions").path("parse").isArray());
        assertEquals(0, payload.path("allowed_mentions").path("parse").size());
    }

    @Test
    void successfulResponsesAreAccepted() {
        assertTrue(DiscordWebhookTransport.Jdk.classify(200).success());
        assertTrue(DiscordWebhookTransport.Jdk.classify(204).success());
        assertEquals("NONE", DiscordWebhookTransport.Jdk.classify(204).errorCode());
    }

    @Test
    void redirectsAreRejectedInsteadOfFollowed() {
        DiscordWebhookTransport.Delivery delivery = DiscordWebhookTransport.Jdk.classify(302);
        assertFalse(delivery.success());
        assertEquals("HTTP_REDIRECT_REJECTED", delivery.errorCode());
    }

    @Test
    void retryRelevantResponseClassesStayDistinct() {
        assertEquals("HTTP_429", DiscordWebhookTransport.Jdk.classify(429).errorCode());
        assertEquals("HTTP_5XX", DiscordWebhookTransport.Jdk.classify(503).errorCode());
        assertEquals("HTTP_4XX", DiscordWebhookTransport.Jdk.classify(404).errorCode());
        assertEquals("HTTP_INVALID_STATUS", DiscordWebhookTransport.Jdk.classify(101).errorCode());
    }
}
