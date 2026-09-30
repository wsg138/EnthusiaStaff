package net.enthusia.staff.protocol;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Body-bound HMAC framing dedicated to Discord-to-Minecraft command requests. */
public final class CommandBridgeHttpSigning {
    public static final String REQUEST_PATH = "/v1/discord-command";
    public static final String TIMESTAMP_HEADER = "X-Enthusia-Command-Timestamp";
    public static final String NONCE_HEADER = "X-Enthusia-Command-Nonce";
    public static final String SIGNATURE_HEADER = "X-Enthusia-Command-Signature";
    public static final String RESPONSE_SIGNATURE_HEADER = "X-Enthusia-Command-Response-Signature";

    private static final Duration MAX_SKEW = Duration.ofSeconds(30);
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,12}");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{32,64}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String HMAC = "HmacSHA256";
    private static final String REQUEST_DOMAIN = "enthusia-discord-command-request-v1";
    private static final String RESPONSE_DOMAIN = "enthusia-discord-command-response-v1";

    private CommandBridgeHttpSigning() {
    }

    public static RequestProof signRequest(
            String credential,
            String method,
            String target,
            String body,
            Instant timestamp,
            String nonce
    ) {
        validateSigningInputs(credential, method, target, body, timestamp, nonce);
        String rawTimestamp = Long.toString(timestamp.getEpochSecond());
        String signature = encode(mac(credential, requestCanonical(method, target, body, rawTimestamp, nonce)));
        return new RequestProof(rawTimestamp, nonce, signature);
    }

    public static Verification verifyRequest(
            String credential,
            String method,
            String target,
            String body,
            String rawTimestamp,
            String nonce,
            String signature,
            Clock clock
    ) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        Instant timestamp = parseTimestamp(rawTimestamp);
        if (timestamp == null || !validRequestText(method, target, body, nonce, signature)) {
            return Verification.MALFORMED;
        }
        if (Duration.between(timestamp, clock.instant()).abs().compareTo(MAX_SKEW) > 0) {
            return Verification.EXPIRED;
        }
        Optional<byte[]> supplied = decode(signature);
        if (supplied.isEmpty()) {
            return Verification.MALFORMED;
        }
        byte[] expected = mac(credential, requestCanonical(method, target, body, rawTimestamp, nonce));
        return MessageDigest.isEqual(expected, supplied.orElseThrow())
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
        Optional<byte[]> supplied = decode(signature);
        if (supplied.isEmpty()) {
            return false;
        }
        byte[] expected = mac(credential, responseCanonical(nonce, status, body));
        return MessageDigest.isEqual(expected, supplied.orElseThrow());
    }

    private static void validateSigningInputs(
            String credential,
            String method,
            String target,
            String body,
            Instant timestamp,
            String nonce
    ) {
        if (credential == null || credential.isBlank() || timestamp == null
                || !validUnsignedRequest(method, target, body, nonce)) {
            throw new IllegalArgumentException("request signing inputs are invalid");
        }
    }

    private static boolean validUnsignedRequest(String method, String target, String body, String nonce) {
        return method != null && !method.isBlank()
                && target != null && target.startsWith("/")
                && body != null
                && validNonce(nonce);
    }

    private static boolean validRequestText(
            String method,
            String target,
            String body,
            String nonce,
            String signature
    ) {
        return validUnsignedRequest(method, target, body, nonce)
                && signature != null
                && SIGNATURE.matcher(signature).matches();
    }

    private static Instant parseTimestamp(String raw) {
        if (raw == null || !TIMESTAMP.matcher(raw).matches()) {
            return null;
        }
        try {
            long epoch = Long.parseLong(raw);
            return epoch <= 0 ? null : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException | DateTimeException failure) {
            return null;
        }
    }

    private static String requestCanonical(
            String method,
            String target,
            String body,
            String timestamp,
            String nonce
    ) {
        return REQUEST_DOMAIN + "\n" + method + "\n" + target + "\n" + timestamp + "\n" + nonce
                + "\n" + sha256(body);
    }

    private static String responseCanonical(String nonce, int status, String body) {
        return RESPONSE_DOMAIN + "\n" + nonce + "\n" + status + "\n" + sha256(body);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static byte[] mac(String credential, String canonical) {
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("command bridge credential is required");
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(credential.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC unavailable", failure);
        }
    }

    private static boolean validNonce(String nonce) {
        return nonce != null && NONCE.matcher(nonce).matches();
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static Optional<byte[]> decode(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 32 ? Optional.of(decoded) : Optional.empty();
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
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
