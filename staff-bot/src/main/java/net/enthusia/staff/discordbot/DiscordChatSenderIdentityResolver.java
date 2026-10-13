package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Short-lived bounded cache for presentation-only Minecraft -> Discord link state.
 *
 * <p>Failures degrade to unlinked presentation. This resolver never authorizes chat, roles, or
 * moderation actions.</p>
 */
final class DiscordChatSenderIdentityResolver {
    static final int DEFAULT_CAPACITY = 4_096;
    static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private record Entry(Optional<String> discordUserId, long expiresAtEpochMillis) {
        private Entry {
            discordUserId = Objects.requireNonNull(discordUserId, "discordUserId");
        }
    }

    private final Clock clock;
    private final int capacity;
    private final long ttlMillis;
    private final Function<UUID, Optional<String>> lookup;
    private final Map<UUID, Entry> cache = new LinkedHashMap<>();
    private long generation;

    DiscordChatSenderIdentityResolver(Function<UUID, Optional<String>> lookup) {
        this(Clock.systemUTC(), DEFAULT_CAPACITY, DEFAULT_TTL, lookup);
    }

    DiscordChatSenderIdentityResolver(
            Clock clock,
            int capacity,
            Duration ttl,
            Function<UUID, Optional<String>> lookup
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (capacity < 1) {
            throw new IllegalArgumentException("chat sender identity cache capacity must be positive");
        }
        this.capacity = capacity;
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("chat sender identity cache TTL must be positive");
        }
        this.ttlMillis = ttl.toMillis();
        if (ttlMillis < 1) {
            throw new IllegalArgumentException("chat sender identity cache TTL is too small");
        }
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    Optional<String> resolve(UUID minecraftPlayerId) {
        Objects.requireNonNull(minecraftPlayerId, "minecraftPlayerId");
        long now = clock.millis();
        long lookupGeneration;
        synchronized (this) {
            lookupGeneration = generation;
            Entry existing = cache.get(minecraftPlayerId);
            if (existing != null && existing.expiresAtEpochMillis() >= now) {
                return existing.discordUserId();
            }
            if (existing != null) {
                cache.remove(minecraftPlayerId);
            }
            purgeExpired(now);
        }

        Optional<String> resolved;
        try {
            Optional<String> candidate = lookup.apply(minecraftPlayerId);
            resolved = candidate == null ? Optional.empty() : candidate.flatMap(
                    DiscordChatSenderIdentityResolver::validatedDiscordId);
        } catch (RuntimeException failure) {
            resolved = Optional.empty();
        }

        long expiresAt = Math.addExact(clock.millis(), ttlMillis);
        synchronized (this) {
            if (generation != lookupGeneration) {
                return Optional.empty();
            }
            Entry refreshed = cache.get(minecraftPlayerId);
            if (refreshed != null && refreshed.expiresAtEpochMillis() >= clock.millis()) {
                return refreshed.discordUserId();
            }
            if (cache.size() >= capacity) {
                Iterator<UUID> iterator = cache.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            cache.put(minecraftPlayerId, new Entry(resolved, expiresAt));
        }
        return resolved;
    }

    /**
     * Identity used for an actual Discord ping must not come from the presentation cache.
     * Main-account selection and links can change before the ordinary cache TTL expires.
     */
    Optional<String> resolveFresh(UUID minecraftPlayerId) {
        Objects.requireNonNull(minecraftPlayerId, "minecraftPlayerId");
        long lookupGeneration;
        synchronized (this) {
            lookupGeneration = generation;
        }
        Optional<String> resolved;
        try {
            Optional<String> candidate = lookup.apply(minecraftPlayerId);
            resolved = candidate == null ? Optional.empty() : candidate.flatMap(
                    DiscordChatSenderIdentityResolver::validatedDiscordId);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        synchronized (this) {
            return generation == lookupGeneration ? resolved : Optional.empty();
        }
    }

    synchronized void clear() {
        generation++;
        cache.clear();
    }

    synchronized int size() {
        return cache.size();
    }

    private void purgeExpired(long now) {
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() < now);
    }

    private static Optional<String> validatedDiscordId(String value) {
        if (value == null || !value.matches("[0-9]{15,20}")) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
