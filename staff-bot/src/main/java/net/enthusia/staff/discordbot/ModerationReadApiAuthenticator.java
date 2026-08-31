package net.enthusia.staff.discordbot;

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

/** Authenticates short-lived Cloudflare-to-Bloom moderation read API requests. */
final class ModerationReadApiAuthenticator {
    static final String TIMESTAMP_HEADER = "X-Enthusia-Read-Timestamp";
    static final String NONCE_HEADER = "X-Enthusia-Read-Nonce";
    static final String SIGNATURE_HEADER = "X-Enthusia-Read-Signature";

    private static final byte[] DOMAIN_SEPARATOR = "enthusia-staff-moderation-read-api-v1\0"
            .getBytes(StandardCharsets.UTF_8);
    private static final String HMAC = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";
    private static final Duration MAX_SKEW = Duration.ofSeconds(30);
    private static final int KEY_BYTES = 32;
    private static final Pattern PATH = Pattern.compile("/v1/moderation/(bootstrap|messages)");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{32,64}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{43,44}");
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,12}");

    enum Result {
        ACCEPTED,
        MALFORMED,
        EXPIRED,
        INVALID_SIGNATURE,
        REPLAYED
    }

    private final byte[] key;
    private final Clock clock;
    private final ModerationReadReplayGuard replayGuard;

    ModerationReadApiAuthenticator(String discordBotToken) {
        this(
                deriveKey(discordBotToken),
                Clock.systemUTC(),
                new ModerationReadReplayGuard(4096, Duration.ofMinutes(2))
        );
    }

    ModerationReadApiAuthenticator(byte[] key, Clock clock, ModerationReadReplayGuard replayGuard) {
        this.key = Objects.requireNonNull(key, "key").clone();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.replayGuard = Objects.requireNonNull(replayGuard, "replayGuard");
        if (this.key.length != KEY_BYTES) {
            throw new IllegalArgumentException("read API signing key must be 32 bytes");
        }
    }

    Result verify(
            String method,
            String path,
            byte[] body,
            String timestampHeader,
            String nonce,
            String signature
    ) {
        ParsedTimestamp timestamp = parseTimestamp(timestampHeader);
        if (timestamp == null || !validText(method, path, nonce, signature)) {
            return Result.MALFORMED;
        }
        Instant now = clock.instant();
        if (Duration.between(timestamp.instant(), now).abs().compareTo(MAX_SKEW) > 0) {
            return Result.EXPIRED;
        }
        byte[] suppliedSignature = decodeSignature(signature);
        if (suppliedSignature == null) {
            return Result.MALFORMED;
        }
        byte[] expected = sign(canonical(method, path, body, timestamp.raw(), nonce));
        if (!MessageDigest.isEqual(expected, suppliedSignature)) {
            return Result.INVALID_SIGNATURE;
        }
        return replayGuard.claim(nonce, now) ? Result.ACCEPTED : Result.REPLAYED;
    }

    static byte[] deriveKey(String discordBotToken) {
        String token = Objects.requireNonNull(discordBotToken, "discordBotToken");
        if (token.isBlank()) {
            throw new IllegalArgumentException("Discord bot token is required for read API signing");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            digest.update(DOMAIN_SEPARATOR);
            digest.update(token.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static String signature(byte[] key, String method, String path, byte[] body, String timestamp, String nonce) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                signWithKey(key, canonical(method, path, body, timestamp, nonce))
        );
    }

    private static boolean validText(String method, String path, String nonce, String signature) {
        return "POST".equals(method)
                && path != null && PATH.matcher(path).matches()
                && nonce != null && NONCE.matcher(nonce).matches()
                && signature != null && SIGNATURE.matcher(signature).matches();
    }

    private static ParsedTimestamp parseTimestamp(String raw) {
        if (raw == null || !TIMESTAMP.matcher(raw).matches()) {
            return null;
        }
        try {
            long epoch = Long.parseLong(raw);
            return epoch <= 0 ? null : new ParsedTimestamp(raw, Instant.ofEpochSecond(epoch));
        } catch (NumberFormatException | DateTimeException exception) {
            return null;
        }
    }

    private static byte[] decodeSignature(String signature) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(signature);
            return decoded.length == KEY_BYTES ? decoded : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private byte[] sign(String canonical) {
        return signWithKey(key, canonical);
    }

    private static byte[] signWithKey(byte[] signingKey, String canonical) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(signingKey, HMAC));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    private static String canonical(String method, String path, byte[] body, String timestamp, String nonce) {
        return "v1\n" + method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyDigest(body);
    }

    private static String bodyDigest(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(body == null ? new byte[0] : body);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ParsedTimestamp(String raw, Instant instant) {
    }
}
