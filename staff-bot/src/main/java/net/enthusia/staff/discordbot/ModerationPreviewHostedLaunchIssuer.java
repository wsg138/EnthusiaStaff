package net.enthusia.staff.discordbot;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Issues short-lived stateless launch tickets for the externally hosted staging moderation site. */
final class ModerationPreviewHostedLaunchIssuer {
    private static final String HMAC = "HmacSHA256";
    private static final String DIGEST = "SHA-256";
    private static final String TARGET_KEY_PATTERN = "[A-Za-z0-9:_-]{1,96}";
    private static final byte[] DOMAIN_SEPARATOR = "enthusia-staff-moderation-launch-v1\0"
            .getBytes(StandardCharsets.UTF_8);
    private static final Duration TICKET_TTL = Duration.ofMinutes(2);
    private static final int NONCE_BYTES = 24;
    private static final int SIGNING_KEY_BYTES = 32;
    private static final long MIN_DISCORD_ID = 1L;

    private final URI publicBaseUri;
    private final byte[] signingKey;
    private final Clock clock;
    private final SecureRandom random;

    ModerationPreviewHostedLaunchIssuer(URI publicBaseUri, String discordBotToken) {
        this(publicBaseUri, deriveSigningKey(discordBotToken), Clock.systemUTC(), new SecureRandom());
    }

    ModerationPreviewHostedLaunchIssuer(URI publicBaseUri, byte[] signingKey, Clock clock, SecureRandom random) {
        this.publicBaseUri = Objects.requireNonNull(publicBaseUri, "publicBaseUri");
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey").clone();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        if (this.signingKey.length != SIGNING_KEY_BYTES) {
            throw new IllegalArgumentException("hosted launch signing key must be 32 bytes");
        }
    }

    URI issueUserLaunchUri(long actorId, long guildId, long targetUserId) {
        return issueLaunchUri(actorId, guildId, userTarget(targetUserId));
    }

    URI issueMessageLaunchUri(long actorId, long guildId, long channelId, long messageId, long targetUserId) {
        requireSnowflake(channelId, "channel");
        requireSnowflake(messageId, "message");
        requireSnowflake(targetUserId, "target user");
        return issueLaunchUri(
                actorId,
                guildId,
                "message:" + Long.toUnsignedString(channelId)
                        + ":" + Long.toUnsignedString(messageId)
                        + ":" + Long.toUnsignedString(targetUserId));
    }

    private URI issueLaunchUri(long actorId, long guildId, String targetKey) {
        String token = issueToken(actorId, guildId, targetKey);
        return URI.create(publicBaseUri + "/launch?t=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    private String issueToken(long actorId, long guildId, String targetKey) {
        if (!validClaims(actorId, guildId, targetKey)) {
            throw new IllegalArgumentException("hosted launch claims are invalid");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(TICKET_TTL);
        String body = "v1|staging|" + nonce()
                + "|" + Long.toUnsignedString(actorId)
                + "|" + Long.toUnsignedString(guildId)
                + "|" + targetKey
                + "|" + issuedAt.getEpochSecond()
                + "|" + expiresAt.getEpochSecond();
        String encodedBody = encode(body.getBytes(StandardCharsets.UTF_8));
        return encodedBody + "." + encode(sign(encodedBody));
    }

    private static String userTarget(long targetUserId) {
        requireSnowflake(targetUserId, "target user");
        return "discord:" + Long.toUnsignedString(targetUserId);
    }

    private static void requireSnowflake(long value, String label) {
        if (value < MIN_DISCORD_ID) {
            throw new IllegalArgumentException(label + " ID is invalid");
        }
    }

    private static boolean validClaims(long actorId, long guildId, String targetKey) {
        if (actorId < MIN_DISCORD_ID || guildId < MIN_DISCORD_ID) {
            return false;
        }
        return targetKey != null && targetKey.matches(TARGET_KEY_PATTERN);
    }

    static byte[] deriveSigningKey(String discordBotToken) {
        String token = Objects.requireNonNull(discordBotToken, "discordBotToken");
        if (token.isBlank()) {
            throw new IllegalArgumentException("Discord bot token is required for hosted launch signing");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST);
            digest.update(DOMAIN_SEPARATOR);
            digest.update(token.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(signingKey, HMAC));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    private String nonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        return encode(bytes);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
