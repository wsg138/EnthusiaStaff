package net.enthusia.staff.velocity;

import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;

final class WebsiteApiAuthenticator {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] expectedAuthorization;
    private final SecretKeySpec signatureKey;
    private final Duration maximumSkew;
    private final WebsiteModerationStore store;

    WebsiteApiAuthenticator(
            String bearerToken,
            String hmacSecret,
            Duration maximumSkew,
            WebsiteModerationStore store
    ) {
        if (bearerToken == null || hmacSecret == null || maximumSkew == null || store == null
                || bearerToken.getBytes(StandardCharsets.UTF_8).length < 32
                || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || maximumSkew.isNegative() || maximumSkew.isZero()) {
            throw new IllegalArgumentException("Website API authentication dependencies are invalid");
        }
        this.expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
        this.signatureKey = new SecretKeySpec(
                hmacSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
        this.maximumSkew = maximumSkew;
        this.store = store;
    }

    void authenticate(
            String method,
            String requestTarget,
            Headers headers,
            byte[] body,
            Instant now
    ) {
        if (method == null || !method.matches("[A-Z]+") || requestTarget == null
                || requestTarget.isBlank() || requestTarget.length() > 2_048
                || headers == null || body == null || now == null) {
            throw authenticationFailed();
        }
        String authorization = singleHeader(headers, "authorization", 4_096);
        if (!MessageDigest.isEqual(
                expectedAuthorization,
                authorization.getBytes(StandardCharsets.UTF_8)
        )) {
            throw authenticationFailed();
        }

        String timestampValue = singleHeader(headers, "x-enthusia-timestamp", 32);
        Instant requestTime;
        try {
            if (!timestampValue.matches("[0-9]{10,17}")) {
                throw new NumberFormatException("Unexpected timestamp form");
            }
            requestTime = Instant.ofEpochMilli(Long.parseLong(timestampValue));
        } catch (NumberFormatException | DateTimeException exception) {
            throw authenticationFailed();
        }
        if (Duration.between(requestTime, now).abs().compareTo(maximumSkew) > 0) {
            throw new WebsiteApiException(
                    401,
                    "REQUEST_EXPIRED",
                    "The signed request is outside the accepted time window"
            );
        }

        String nonce = singleHeader(headers, "x-enthusia-nonce", 64);
        try {
            if (!nonce.matches("[0-9a-fA-F-]{36}")
                    || !UUID.fromString(nonce).toString().equalsIgnoreCase(nonce)) {
                throw authenticationFailed();
            }
        } catch (IllegalArgumentException exception) {
            throw authenticationFailed();
        }

        String suppliedContentHash = singleHeader(headers, "x-enthusia-content-sha256", 128);
        String expectedContentHash = base64Url(sha256(body));
        if (!constantTimeText(expectedContentHash, suppliedContentHash)) {
            throw authenticationFailed();
        }

        String canonical = method + '\n' + requestTarget + '\n' + timestampValue + '\n'
                + nonce + '\n' + expectedContentHash;
        String expectedSignature = base64Url(hmac(canonical.getBytes(StandardCharsets.UTF_8)));
        String suppliedSignature = singleHeader(headers, "x-enthusia-signature", 128);
        if (!constantTimeText(expectedSignature, suppliedSignature)) {
            throw authenticationFailed();
        }

        byte[] nonceHash = sha256(nonce.getBytes(StandardCharsets.US_ASCII));
        if (!store.recordApiNonce(nonceHash, now.plus(maximumSkew.multipliedBy(2)))) {
            throw new WebsiteApiException(
                    409,
                    "REPLAY_REJECTED",
                    "The signed request nonce has already been used"
            );
        }
    }

    private static String singleHeader(Headers headers, String name, int maximumLength) {
        List<String> values = headers.get(name);
        if (values == null || values.size() != 1) {
            throw authenticationFailed();
        }
        String value = values.getFirst();
        if (value == null || value.isBlank() || value.length() > maximumLength
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw authenticationFailed();
        }
        return value;
    }

    private byte[] hmac(byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signatureKey);
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static boolean constantTimeText(String expected, String supplied) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static WebsiteApiException authenticationFailed() {
        return new WebsiteApiException(
                401,
                "AUTHENTICATION_FAILED",
                "The signed request could not be authenticated"
        );
    }
}
