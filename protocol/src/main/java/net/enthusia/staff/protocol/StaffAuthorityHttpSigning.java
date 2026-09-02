package net.enthusia.staff.protocol;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Shared HMAC framing for the private Bloom split staff-authority HTTP bridge. */
public final class StaffAuthorityHttpSigning {
    public static final String TIMESTAMP_HEADER = "X-Enthusia-Authority-Timestamp";
    public static final String NONCE_HEADER = "X-Enthusia-Authority-Nonce";
    public static final String SIGNATURE_HEADER = "X-Enthusia-Authority-Signature";
    public static final String RESPONSE_SIGNATURE_HEADER = "X-Enthusia-Authority-Response-Signature";

    private static final String HMAC = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";
    private static final Duration MAX_SKEW = Duration.ofSeconds(30);
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,12}");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{32,64}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String REQUEST_DOMAIN = "enthusia-staff-authority-request-v1";
    private static final String RESPONSE_DOMAIN = "enthusia-staff-authority-response-v1";

    private StaffAuthorityHttpSigning() {
    }

    public static RequestProof signRequest(
            String credential,
            String method,
            String target,
            Instant timestamp,
            String nonce
    ) {
        validateInputs(credential, method, target, timestamp, nonce);
        String rawTimestamp = Long.toString(timestamp.getEpochSecond());
        String signature = encode(mac(credential, requestCanonical(method, target, rawTimestamp, nonce)));
        return new RequestProof(rawTimestamp, nonce, signature);
    }

    public static Verification verifyRequest(
            String credential,
            String method,
            String target,
            String rawTimestamp,
            String nonce,
            String signature,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock");
        Instant timestamp = parseTimestamp(rawTimestamp);
        if (timestamp == null || !validRequestText(method, target, nonce, signature)) {
            return Verification.MALFORMED;
        }
        if (Duration.between(timestamp, clock.instant()).abs().compareTo(MAX_SKEW) > 0) {
            return Verification.EXPIRED;
        }
        byte[] supplied = decode(signature);
        if (supplied == null) {
            return Verification.MALFORMED;
        }
        byte[] expected = mac(credential, requestCanonical(method, target, rawTimestamp, nonce));
        return MessageDigest.isEqual(expected, supplied)
                ? Verification.ACCEPTED
                : Verification.INVALID_SIGNATURE;
    }

    public static String signResponse(String credential, String nonce, int status, String body) {
        if (!validNonce(nonce) || status < 100 || status > 599 || body == null) {
            throw new IllegalArgumentException("response signing inputs are invalid");
        }
        return encode(mac(credential, responseCanonical(nonce, status, body)));
    }

    public static boolean verifyResponse(
            String credential,
            String nonce,
            int status,
            String body,
            String signature
    ) {
        if (!validNonce(nonce) || body == null || signature == null || !SIGNATURE.matcher(signature).matches()) {
            return false;
        }
        byte[] supplied = decode(signature);
        if (supplied == null) {
            return false;
        }
        byte[] expected = mac(credential, responseCanonical(nonce, status, body));
        return MessageDigest.isEqual(expected, supplied);
    }

    private static void validateInputs(
            String credential,
            String method,
            String target,
            Instant timestamp,
            String nonce
    ) {
        if (credential == null || credential.isBlank()
                || method == null || method.isBlank()
                || target == null || target.isBlank()
                || timestamp == null || !validNonce(nonce)) {
            throw new IllegalArgumentException("request signing inputs are invalid");
        }
    }

    private static boolean validRequestText(String method, String target, String nonce, String signature) {
        return method != null && !method.isBlank()
                && target != null && target.startsWith("/")
                && validNonce(nonce)
                && signature != null && SIGNATURE.matcher(signature).matches();
    }

    private static boolean validNonce(String nonce) {
        return nonce != null && NONCE.matcher(nonce).matches();
    }

    private static Instant parseTimestamp(String raw) {
        if (raw == null || !TIMESTAMP.matcher(raw).matches()) {
            return null;
        }
        try {
            long epoch = Long.parseLong(raw);
            return epoch <= 0 ? null : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException | DateTimeException exception) {
            return null;
        }
    }

    private static String requestCanonical(String method, String target, String timestamp, String nonce) {
        return REQUEST_DOMAIN + "\n" + method + "\n" + target + "\n" + timestamp + "\n" + nonce;
    }

    private static String responseCanonical(String nonce, int status, String body) {
        return RESPONSE_DOMAIN + "\n" + nonce + "\n" + status + "\n" + sha256(body);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance(SHA_256).digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static byte[] mac(String credential, String canonical) {
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("authority credential is required");
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(credential.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record RequestProof(String timestamp, String nonce, String signature) {
    }

    public enum Verification {
        ACCEPTED,
        MALFORMED,
        EXPIRED,
        INVALID_SIGNATURE
    }
}
