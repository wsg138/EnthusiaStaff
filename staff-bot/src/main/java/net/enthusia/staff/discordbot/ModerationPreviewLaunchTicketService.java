package net.enthusia.staff.discordbot;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Issues bounded, one-time, signed launch tickets for the staging moderation web console. */
final class ModerationPreviewLaunchTicketService {
    private static final String HMAC = "HmacSHA256";
    private static final int MIN_CAPACITY = 1;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 24;

    enum Status {
        ACCEPTED,
        MALFORMED,
        INVALID,
        EXPIRED,
        REPLAYED
    }

    record Claims(long actorId, long guildId, String targetKey, Instant issuedAt, Instant expiresAt) {
    }

    record ConsumeResult(Status status, Optional<Claims> claims) {
        static ConsumeResult rejected(Status status) {
            return new ConsumeResult(status, Optional.empty());
        }

        static ConsumeResult accepted(Claims claims) {
            return new ConsumeResult(Status.ACCEPTED, Optional.of(claims));
        }
    }

    private final int capacity;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random;
    private final byte[] signingKey;
    private final Object ticketLock = new Object();
    private final Map<String, Claims> tickets = new LinkedHashMap<>();

    ModerationPreviewLaunchTicketService(int capacity, Duration ttl) {
        this(capacity, ttl, Clock.systemUTC(), new SecureRandom());
    }

    ModerationPreviewLaunchTicketService(int capacity, Duration ttl, Clock clock, SecureRandom random) {
        if (capacity < MIN_CAPACITY) {
            throw new IllegalArgumentException("ticket capacity must be positive");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ticket ttl must be positive");
        }
        this.capacity = capacity;
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.signingKey = randomBytes(KEY_BYTES);
    }

    String issue(long actorId, long guildId, String targetKey) {
        validateClaims(actorId, guildId, targetKey);
        synchronized (ticketLock) {
            Instant issuedAt = clock.instant();
            Instant expiresAt = canonicalExpiry(issuedAt);
            purgeExpired(issuedAt);
            ensureCapacity();
            String nonce = encode(randomBytes(NONCE_BYTES));
            tickets.put(nonce, new Claims(actorId, guildId, targetKey, issuedAt, expiresAt));
            String body = nonce + "." + expiresAt.getEpochSecond();
            return body + "." + encode(sign(body));
        }
    }

    ConsumeResult consume(String token) {
        TokenParts parts = parse(token);
        if (parts == null) {
            return ConsumeResult.rejected(Status.MALFORMED);
        }
        if (!validSignature(parts)) {
            return ConsumeResult.rejected(Status.INVALID);
        }
        Instant now = clock.instant();
        if (!now.isBefore(parts.expiresAt())) {
            discard(parts.nonce());
            return ConsumeResult.rejected(Status.EXPIRED);
        }
        return consumeStored(parts);
    }

    private ConsumeResult consumeStored(TokenParts parts) {
        synchronized (ticketLock) {
            Claims claims = tickets.get(parts.nonce());
            if (claims == null) {
                return ConsumeResult.rejected(Status.REPLAYED);
            }
            if (!claims.expiresAt().equals(parts.expiresAt())) {
                return ConsumeResult.rejected(Status.INVALID);
            }
            Instant lockedNow = clock.instant();
            if (!lockedNow.isBefore(claims.expiresAt())) {
                tickets.remove(parts.nonce());
                return ConsumeResult.rejected(Status.EXPIRED);
            }
            tickets.remove(parts.nonce());
            return ConsumeResult.accepted(claims);
        }
    }

    private static void validateClaims(long actorId, long guildId, String targetKey) {
        if (actorId <= 0 || guildId <= 0 || targetKey == null || targetKey.isBlank() || targetKey.length() > 64) {
            throw new IllegalArgumentException("launch ticket claims are invalid");
        }
    }

    private Instant canonicalExpiry(Instant issuedAt) {
        return Instant.ofEpochSecond(issuedAt.plus(ttl).getEpochSecond());
    }

    private boolean validSignature(TokenParts parts) {
        byte[] expected = sign(parts.body());
        return MessageDigest.isEqual(expected, parts.signature());
    }

    private TokenParts parse(String token) {
        if (token == null || token.length() > 512) {
            return null;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        try {
            long epoch = Long.parseLong(parts[1]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            return new TokenParts(parts[0], Instant.ofEpochSecond(epoch), signature, parts[0] + "." + parts[1]);
        } catch (IllegalArgumentException exception) {
            return null;
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

    private void discard(String nonce) {
        synchronized (ticketLock) {
            tickets.remove(nonce);
        }
    }

    private void purgeExpired(Instant now) {
        tickets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private void ensureCapacity() {
        if (tickets.size() < capacity) {
            return;
        }
        Iterator<String> iterator = tickets.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private record TokenParts(String nonce, Instant expiresAt, byte[] signature, String body) {
    }
}
