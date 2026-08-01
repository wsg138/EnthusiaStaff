package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static net.enthusia.staff.velocity.WebsiteApiTestSignatures.BEARER;
import static net.enthusia.staff.velocity.WebsiteApiTestSignatures.HMAC;
import static net.enthusia.staff.velocity.WebsiteApiTestSignatures.signedHeaders;

import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import org.junit.jupiter.api.Test;

final class WebsiteApiAuthenticatorTest {
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Test
    void acceptsWorkerCanonicalSignatureOnceAndRejectsReplay() {
        NonceStore store = new NonceStore();
        WebsiteApiAuthenticator authenticator = authenticator(store);
        byte[] body = "{\"accountId\":\"value\"}".getBytes(StandardCharsets.UTF_8);
        Headers headers = signedHeaders(
                POST,
                "/v1/website/punishment-codes/claim",
                body,
                NOW,
                "915ee6c2-70d9-47fb-9370-bfc311c80b29"
        );

        assertDoesNotThrow(() -> authenticator.authenticate(
                POST,
                "/v1/website/punishment-codes/claim",
                headers,
                body,
                NOW
        ));
        WebsiteApiException replay = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        POST,
                        "/v1/website/punishment-codes/claim",
                        headers,
                        body,
                        NOW
                )
        );
        assertEquals("REPLAY_REJECTED", replay.code());
    }

    @Test
    void rejectsBodyTamperingBeforeRecordingNonce() {
        NonceStore store = new NonceStore();
        WebsiteApiAuthenticator authenticator = authenticator(store);
        byte[] signed = "{}".getBytes(StandardCharsets.UTF_8);
        Headers headers = signedHeaders(
                POST,
                "/v1/website/punishment-codes/revalidate",
                signed,
                NOW,
                "3562a8f3-d36e-4694-bf1b-99e7454c52bd"
        );

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        POST,
                        "/v1/website/punishment-codes/revalidate",
                        headers,
                        "{\"changed\":true}".getBytes(StandardCharsets.UTF_8),
                        NOW
                )
        );
        assertEquals("AUTHENTICATION_FAILED", error.code());
        assertEquals(0, store.recorded.size());
    }

    @Test
    void rejectsExpiredTimestampAndDuplicateAuthorization() {
        NonceStore store = new NonceStore();
        WebsiteApiAuthenticator authenticator = authenticator(store);
        byte[] body = new byte[0];
        Headers expired = signedHeaders(
                GET,
                "/v1/public/punishments?type=ALL&limit=30",
                body,
                NOW.minusSeconds(301),
                "a7789359-15e4-481d-b7f8-59520c65b3b8"
        );
        WebsiteApiException expiration = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        GET,
                        "/v1/public/punishments?type=ALL&limit=30",
                        expired,
                        body,
                        NOW
                )
        );
        assertEquals("REQUEST_EXPIRED", expiration.code());

        Headers duplicate = signedHeaders(
                GET,
                "/v1/public/punishments",
                body,
                NOW,
                "69bdb156-6a7c-4ed6-a665-4a70f00ba29e"
        );
        duplicate.add("authorization", "Bearer another-token");
        WebsiteApiException authentication = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        GET,
                        "/v1/public/punishments",
                        duplicate,
                        body,
                        NOW
                )
        );
        assertEquals("AUTHENTICATION_FAILED", authentication.code());
    }

    private static WebsiteApiAuthenticator authenticator(WebsiteModerationStore store) {
        return new WebsiteApiAuthenticator(BEARER, HMAC, Duration.ofMinutes(5), store);
    }

    private static final class NonceStore extends WebsiteModerationStoreStub {
        private final Set<String> recorded = new HashSet<>();

        @Override
        public boolean recordApiNonce(byte[] nonceHash, Instant expiresAt) {
            return recorded.add(Base64.getEncoder().encodeToString(nonceHash));
        }

    }
}
