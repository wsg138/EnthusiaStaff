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

/** Bounded single-use confirmation drafts; no external effect is possible before claim. */
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
    private final LinkedHashMap<UUID, Draft> drafts = new LinkedHashMap<>();

    DiscordPunishmentConfirmationStore(Clock clock, Duration ttl, int capacity) {
        if (clock == null || ttl == null || ttl.isZero() || ttl.isNegative() || capacity < 1) {
            throw new IllegalArgumentException("confirmation store configuration is invalid");
        }
        this.clock = clock;
        this.ttl = ttl;
        this.capacity = capacity;
    }

    synchronized UUID put(DraftFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("draft factory must be present");
        }
        purgeExpired();
        if (drafts.size() >= capacity) {
            Iterator<Map.Entry<UUID, Draft>> iterator = drafts.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        UUID token = UUID.randomUUID();
        drafts.put(token, factory.create(clock.instant().plus(ttl)));
        return token;
    }

    synchronized Draft claim(UUID token) {
        if (token == null) {
            throw new IllegalArgumentException("confirmation token must be present");
        }
        Draft draft = drafts.remove(token);
        if (draft == null || !clock.instant().isBefore(draft.expiresAt())) {
            throw new IllegalStateException("confirmation is missing, expired, or already used");
        }
        return draft;
    }

    synchronized int size() {
        purgeExpired();
        return drafts.size();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        drafts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    @FunctionalInterface
    interface DraftFactory {
        Draft create(Instant expiresAt);
    }
}
