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
import java.util.function.UnaryOperator;

/** Bounded in-memory preview state. It has no persistence or destructive adapter. */
final class ModerationUiPreviewSessionStore {
    enum AccessStatus {
        OK,
        MISSING,
        EXPIRED,
        WRONG_OWNER,
        STALE,
        COMPLETE
    }

    record Access(AccessStatus status, ModerationUiPreviewModel.Snapshot snapshot) {
        static Access failure(AccessStatus status) {
            return new Access(status, null);
        }
    }

    private record Session(
            String id,
            long ownerId,
            int revision,
            Instant expiresAt,
            ModerationUiPreviewModel.State state
    ) {
        ModerationUiPreviewModel.Snapshot snapshot() {
            return new ModerationUiPreviewModel.Snapshot(id, revision, state);
        }
    }

    private static final int MIN_CAPACITY = 1;
    private static final int RANDOM_ID_BYTES = 12;
    private static final int ID_ATTEMPTS = 8;

    private final int capacity;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random;
    private final Object lock = new Object();
    private final Map<String, Session> sessions = new LinkedHashMap<>();

    ModerationUiPreviewSessionStore(int capacity, Duration ttl, Clock clock, SecureRandom random) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        if (capacity < MIN_CAPACITY) {
            throw new IllegalArgumentException("preview capacity must be positive");
        }
        if (this.ttl.isZero() || this.ttl.isNegative()) {
            throw new IllegalArgumentException("preview TTL must be positive");
        }
        this.capacity = capacity;
    }

    Optional<ModerationUiPreviewModel.Snapshot> create(long ownerId) {
        synchronized (lock) {
            purgeExpired();
            if (sessions.size() >= capacity) {
                return Optional.empty();
            }
            String id = newSessionId();
            Session session = new Session(
                    id,
                    ownerId,
                    0,
                    clock.instant().plus(ttl),
                    ModerationUiPreviewModel.State.initial()
            );
            sessions.put(id, session);
            return Optional.of(session.snapshot());
        }
    }

    Access inspect(String id, long ownerId, int expectedRevision) {
        synchronized (lock) {
            return inspectLocked(id, ownerId, expectedRevision);
        }
    }

    Access update(
            String id,
            long ownerId,
            int expectedRevision,
            UnaryOperator<ModerationUiPreviewModel.State> mutation
    ) {
        Objects.requireNonNull(mutation, "mutation");
        synchronized (lock) {
            Access inspected = inspectLocked(id, ownerId, expectedRevision);
            if (inspected.status() != AccessStatus.OK) {
                return inspected;
            }
            Session current = sessions.get(id);
            ModerationUiPreviewModel.State nextState = mutation.apply(current.state());
            Session next = new Session(id, ownerId, current.revision() + 1, current.expiresAt(), nextState);
            sessions.put(id, next);
            return new Access(AccessStatus.OK, next.snapshot());
        }
    }

    private Access inspectLocked(String id, long ownerId, int expectedRevision) {
        Session session = sessions.get(id);
        AccessStatus status = validate(session, ownerId, expectedRevision);
        if (status != AccessStatus.OK) {
            if (status == AccessStatus.EXPIRED) {
                sessions.remove(id);
            }
            return Access.failure(status);
        }
        return new Access(AccessStatus.OK, session.snapshot());
    }

    private AccessStatus validate(Session session, long ownerId, int expectedRevision) {
        if (session == null) {
            return AccessStatus.MISSING;
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            return AccessStatus.EXPIRED;
        }
        if (session.ownerId() != ownerId) {
            return AccessStatus.WRONG_OWNER;
        }
        if (session.revision() != expectedRevision) {
            return AccessStatus.STALE;
        }
        if (session.state().screen() == ModerationUiPreviewModel.Screen.COMPLETE) {
            return AccessStatus.COMPLETE;
        }
        return AccessStatus.OK;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        Iterator<Session> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().expiresAt())) {
                iterator.remove();
            }
        }
    }

    private String newSessionId() {
        byte[] bytes = new byte[RANDOM_ID_BYTES];
        for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
            random.nextBytes(bytes);
            String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!sessions.containsKey(id)) {
                return id;
            }
        }
        throw new IllegalStateException("could not allocate preview session id");
    }
}
