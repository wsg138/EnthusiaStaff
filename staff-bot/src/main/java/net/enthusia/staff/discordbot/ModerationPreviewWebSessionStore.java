package net.enthusia.staff.discordbot;

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

/** Bounded in-memory browser sessions created only by consuming a signed one-time launch ticket. */
final class ModerationPreviewWebSessionStore {
    private static final int MIN_CAPACITY = 1;
    private static final int SESSION_BYTES = 24;
    private static final int CSRF_BYTES = 24;

    record Session(
            String id,
            ModerationPreviewLaunchTicketService.Claims claims,
            String csrfToken,
            Instant expiresAt
    ) {
    }

    private final int capacity;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random;
    private final Object sessionLock = new Object();
    private final Map<String, Session> sessions = new LinkedHashMap<>();

    ModerationPreviewWebSessionStore(int capacity, Duration ttl) {
        this(capacity, ttl, Clock.systemUTC(), new SecureRandom());
    }

    ModerationPreviewWebSessionStore(int capacity, Duration ttl, Clock clock, SecureRandom random) {
        if (capacity < MIN_CAPACITY) {
            throw new IllegalArgumentException("session capacity must be positive");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("session ttl must be positive");
        }
        this.capacity = capacity;
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    Session create(ModerationPreviewLaunchTicketService.Claims claims) {
        Objects.requireNonNull(claims, "claims");
        synchronized (sessionLock) {
            Instant now = clock.instant();
            purgeExpired(now);
            ensureCapacity();
            String id = randomToken(SESSION_BYTES);
            Session session = new Session(id, claims, randomToken(CSRF_BYTES), now.plus(ttl));
            sessions.put(id, session);
            return session;
        }
    }

    Optional<Session> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        synchronized (sessionLock) {
            Instant now = clock.instant();
            Session session = sessions.get(id);
            if (session == null) {
                return Optional.empty();
            }
            if (!now.isBefore(session.expiresAt())) {
                sessions.remove(id);
                return Optional.empty();
            }
            return Optional.of(session);
        }
    }

    private void purgeExpired(Instant now) {
        sessions.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private void ensureCapacity() {
        if (sessions.size() < capacity) {
            return;
        }
        Iterator<String> iterator = sessions.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private String randomToken(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
