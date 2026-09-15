package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordAuthorizationSnapshot;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentTermination;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** Bounded single-use confirmation drafts; no external effect is possible before actor-bound claim. */
final class DiscordPunishmentConfirmationStore {
    enum Kind {
        ISSUE,
        REMOVE
    }

    record Draft(
            Kind kind,
            DiscordUserId targetUserId,
            Optional<UUID> punishmentId,
            Optional<DiscordPunishmentIntent> intent,
            Optional<DiscordConsequenceType> removalType,
            DiscordPunishmentTermination termination,
            DiscordAuthorizationSnapshot authorization,
            Instant expiresAt
    ) {
        Draft {
            if (kind == null || targetUserId == null || punishmentId == null || intent == null
                    || removalType == null || termination == null || authorization == null || expiresAt == null) {
                throw new IllegalArgumentException("confirmation draft fields must be present");
            }
            boolean issue = kind == Kind.ISSUE;
            if (issue != intent.isPresent() || issue == punishmentId.isPresent() || issue == removalType.isPresent()) {
                throw new IllegalArgumentException("confirmation draft shape does not match its kind");
            }
            if (issue != (termination == DiscordPunishmentTermination.NONE)) {
                throw new IllegalArgumentException("only removal drafts may carry a termination reason");
            }
        }
    }

    private final Clock clock;
    private final Duration ttl;
    private final int capacity;
    private final Object lock = new Object();
    // Linked insertion order provides deterministic eviction; lock guards every access.
    @SuppressWarnings("PMD.DocumentMutableMapFieldConcurrency")
    private final LinkedHashMap<UUID, Draft> drafts = new LinkedHashMap<>();

    DiscordPunishmentConfirmationStore(Clock clock, Duration ttl, int capacity) {
        if (clock == null || ttl == null || ttl.isZero() || ttl.isNegative() || capacity < 1) {
            throw new IllegalArgumentException("confirmation store configuration is invalid");
        }
        this.clock = clock;
        this.ttl = ttl;
        this.capacity = capacity;
    }

    UUID put(DraftFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("draft factory must be present");
        }
        synchronized (lock) {
            purgeExpiredLocked();
            evictOldestIfFull();
            UUID token = UUID.randomUUID();
            drafts.put(token, factory.create(clock.instant().plus(ttl)));
            return token;
        }
    }

    Draft claimForActor(UUID token, UUID actorId) {
        if (token == null || actorId == null) {
            throw new IllegalArgumentException("confirmation token and actor must be present");
        }
        synchronized (lock) {
            Draft draft = drafts.get(token);
            requireAvailable(token, draft);
            if (!draft.authorization().actorId().equals(actorId)) {
                throw new SecurityException("confirmation belongs to another staff actor");
            }
            drafts.remove(token);
            return draft;
        }
    }

    int size() {
        synchronized (lock) {
            purgeExpiredLocked();
            return drafts.size();
        }
    }

    private void evictOldestIfFull() {
        if (drafts.size() < capacity) {
            return;
        }
        Iterator<Map.Entry<UUID, Draft>> iterator = drafts.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private void requireAvailable(UUID token, Draft draft) {
        if (draft == null) {
            throw unavailable();
        }
        if (!clock.instant().isBefore(draft.expiresAt())) {
            drafts.remove(token);
            throw unavailable();
        }
    }

    private void purgeExpiredLocked() {
        Instant now = clock.instant();
        drafts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("confirmation is missing, expired, or already used");
    }

    @FunctionalInterface
    interface DraftFactory {
        Draft create(Instant expiresAt);
    }
}
