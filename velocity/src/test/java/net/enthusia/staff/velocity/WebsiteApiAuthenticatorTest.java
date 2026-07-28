package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import org.junit.jupiter.api.Test;

final class WebsiteApiAuthenticatorTest {
    private static final String BEARER = Character.toString('b').repeat(48);
    private static final String HMAC = Character.toString('h').repeat(48);
    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Test
    void acceptsWorkerCanonicalSignatureOnceAndRejectsReplay() {
        NonceStore store = new NonceStore();
        WebsiteApiAuthenticator authenticator = authenticator(store);
        byte[] body = "{\"accountId\":\"value\"}".getBytes(StandardCharsets.UTF_8);
        Headers headers = signedHeaders(
                "POST",
                "/v1/website/punishment-codes/claim",
                body,
                NOW,
                "915ee6c2-70d9-47fb-9370-bfc311c80b29"
        );

        assertDoesNotThrow(() -> authenticator.authenticate(
                "POST",
                "/v1/website/punishment-codes/claim",
                headers,
                body,
                NOW
        ));
        WebsiteApiException replay = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        "POST",
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
                "POST",
                "/v1/website/punishment-codes/revalidate",
                signed,
                NOW,
                "3562a8f3-d36e-4694-bf1b-99e7454c52bd"
        );

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        "POST",
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
                "GET",
                "/v1/public/punishments?type=ALL&limit=30",
                body,
                NOW.minusSeconds(301),
                "a7789359-15e4-481d-b7f8-59520c65b3b8"
        );
        WebsiteApiException expiration = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        "GET",
                        "/v1/public/punishments?type=ALL&limit=30",
                        expired,
                        body,
                        NOW
                )
        );
        assertEquals("REQUEST_EXPIRED", expiration.code());

        Headers duplicate = signedHeaders(
                "GET",
                "/v1/public/punishments",
                body,
                NOW,
                "69bdb156-6a7c-4ed6-a665-4a70f00ba29e"
        );
        duplicate.add("authorization", "Bearer another-token");
        WebsiteApiException authentication = assertThrows(
                WebsiteApiException.class,
                () -> authenticator.authenticate(
                        "GET",
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

    private static Headers signedHeaders(
            String method,
            String target,
            byte[] body,
            Instant timestamp,
            String nonce
    ) {
        String bodyHash = base64Url(sha256(body));
        String time = Long.toString(timestamp.toEpochMilli());
        String canonical = method + '\n' + target + '\n' + time + '\n' + nonce + '\n' + bodyHash;
        Headers headers = new Headers();
        headers.set("authorization", "Bearer " + BEARER);
        headers.set("x-enthusia-timestamp", time);
        headers.set("x-enthusia-nonce", nonce);
        headers.set("x-enthusia-content-sha256", bodyHash);
        headers.set("x-enthusia-signature", base64Url(hmac(canonical)));
        return headers;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static final class NonceStore implements WebsiteModerationStore {
        private final Set<String> recorded = new HashSet<>();

        @Override
        public boolean recordApiNonce(byte[] nonceHash, Instant expiresAt) {
            return recorded.add(Base64.getEncoder().encodeToString(nonceHash));
        }

        @Override
        public PublicPunishmentPage listPublic(
                PublicPunishmentFilter filter,
                Optional<String> cursor,
                int limit,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PublicPunishment> searchPublic(String query, int limit, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PublicPunishment> publicCase(CaseId caseId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PunishmentCodeBinding claimCode(
                String code,
                String accountId,
                String username,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PunishmentCodeBinding revalidateCode(
                UUID punishmentId,
                int codeGeneration,
                String accountId,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PunishmentCodeDisplay> codeForSanction(UUID punishmentId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PunishmentCodeDisplay> codesForCase(CaseId caseId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int ensureEligibleCodes(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PunishmentCodeDisplay rotateCode(UUID punishmentId, UUID actorId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean revokeCode(UUID punishmentId, UUID actorId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int purgeExpiredApiNonces(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppealAcceptancePreparation prepareAppealAcceptance(
                UUID appealId,
                UUID punishmentId,
                CaseId caseId,
                String accountId,
                String idempotencyKey,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completeAppealAcceptance(
                UUID appealId,
                String state,
                String outcomeCode,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
