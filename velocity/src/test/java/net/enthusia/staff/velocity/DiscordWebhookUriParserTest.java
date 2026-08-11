package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class DiscordWebhookUriParserTest {
    @Test
    void validWebhookUriIsParsedWithoutMutation() {
        URI uri = DiscordWebhookUriParser.parse("  https://discord-staging.example.test/webhook/reports  ");

        assertEquals(URI.create("https://discord-staging.example.test/webhook/reports"), uri);
    }

    @Test
    void malformedUriDoesNotRetainSecretInExceptionChain() {
        String secretBearingValue = "https://discord.com/api/webhooks/123/super-secret token";

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DiscordWebhookUriParser.parse(secretBearingValue)
        );

        assertEquals("A Discord webhook environment variable is not a valid URI", exception.getMessage());
        assertNull(exception.getCause());
    }
}
