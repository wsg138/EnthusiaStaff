package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DiscordWebhookTransportTest {
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
