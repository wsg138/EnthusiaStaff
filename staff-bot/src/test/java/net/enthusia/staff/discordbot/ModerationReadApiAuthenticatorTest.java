package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ModerationReadApiAuthenticatorTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_788_000_000L);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] KEY = ModerationReadApiAuthenticator.deriveKey("staging-test-discord-token-value");
    private static final byte[] BODY = "{\"actorId\":\"123\"}".getBytes(StandardCharsets.UTF_8);
    private static final String NONCE = "A".repeat(32);
    private static final String PATH = "/v1/moderation/bootstrap";
    private static final String POST_METHOD = "POST";

    @Test
    void acceptsValidRequestAndRejectsReplay() {
        ModerationReadApiAuthenticator authenticator = authenticator();
        String timestamp = Long.toString(NOW.getEpochSecond());
        String signature = ModerationReadApiAuthenticator.signature(KEY, POST_METHOD, PATH, BODY, timestamp, NONCE);

        assertEquals(
                ModerationReadApiAuthenticator.Result.ACCEPTED,
                authenticator.verify(POST_METHOD, PATH, BODY, timestamp, NONCE, signature));
        assertEquals(
                ModerationReadApiAuthenticator.Result.REPLAYED,
                authenticator.verify(POST_METHOD, PATH, BODY, timestamp, NONCE, signature));
    }

    @Test
    void rejectsTamperingExpiryAndMalformedInputs() {
        ModerationReadApiAuthenticator authenticator = authenticator();
        String timestamp = Long.toString(NOW.getEpochSecond());
        String signature = ModerationReadApiAuthenticator.signature(KEY, POST_METHOD, PATH, BODY, timestamp, NONCE);

        assertEquals(
                ModerationReadApiAuthenticator.Result.INVALID_SIGNATURE,
                authenticator.verify(POST_METHOD, PATH, "{}".getBytes(StandardCharsets.UTF_8), timestamp, NONCE, signature));
        assertEquals(
                ModerationReadApiAuthenticator.Result.EXPIRED,
                authenticator.verify(POST_METHOD, PATH, BODY, Long.toString(NOW.minusSeconds(31).getEpochSecond()), NONCE, signature));
        assertEquals(
                ModerationReadApiAuthenticator.Result.MALFORMED,
                authenticator.verify("GET", PATH, BODY, timestamp, NONCE, signature));
        assertEquals(
                ModerationReadApiAuthenticator.Result.MALFORMED,
                authenticator.verify(POST_METHOD, "/v1/moderation/delete", BODY, timestamp, NONCE, signature));
    }

    private static ModerationReadApiAuthenticator authenticator() {
        return new ModerationReadApiAuthenticator(
                KEY,
                CLOCK,
                new ModerationReadReplayGuard(8, Duration.ofMinutes(2))
        );
    }
}
