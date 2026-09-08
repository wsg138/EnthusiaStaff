package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Bounded, fail-soft lookup of public Minecraft profile names and skin textures by UUID. */
final class MinecraftProfileLookup {
    private static final URI PROFILE_ROOT = URI.create(
            "https://sessionserver.mojang.com/session/minecraft/profile/");
    private static final String TEXTURE_HOST = "textures.minecraft.net";
    private static final String TEXTURES_PROPERTY = "textures";
    private static final int HTTP_OK = 200;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int MAX_REMOTE_LOOKUPS = 32;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REMOTE_BUDGET = Duration.ofSeconds(3);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    record Profile(String username, Optional<String> skinTextureUrl) {
        Profile {
            if (username == null || !USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException("Minecraft profile username is invalid");
            }
            skinTextureUrl = skinTextureUrl == null ? Optional.empty() : skinTextureUrl;
        }
    }

    record RemoteResponse(int statusCode, String body) {
        RemoteResponse {
            body = body == null ? "" : body;
        }
    }

    @FunctionalInterface
    interface RemoteFetcher {
        CompletableFuture<RemoteResponse> fetch(UUID playerId);

        default void shutdown() {
            // Test/custom fetchers normally have no owned transport resource.
        }
    }

    private static final class MojangFetcher implements RemoteFetcher {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

        @Override
        public CompletableFuture<RemoteResponse> fetch(UUID playerId) {
            return client.sendAsync(request(playerId), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> new RemoteResponse(response.statusCode(), response.body()));
        }

        @Override
        public void shutdown() {
            client.shutdownNow();
        }
    }

    private record CachedProfile(Profile profile, Instant expiresAt) {
    }

    private final RemoteFetcher fetcher;
    private final Clock clock;
    private final Duration remoteBudget;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object cacheLock = new Object();
    // Access-order LinkedHashMap requires one lock across lookup, expiry, insertion, and eviction.
    @SuppressWarnings("PMD.DocumentMutableMapFieldConcurrency")
    private final LinkedHashMap<UUID, CachedProfile> cache = new LinkedHashMap<>(16, 0.75f, true);

    static MinecraftProfileLookup mojang() {
        return new MinecraftProfileLookup(new MojangFetcher(), Clock.systemUTC(), REMOTE_BUDGET);
    }

    MinecraftProfileLookup(RemoteFetcher fetcher, Clock clock, Duration remoteBudget) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.remoteBudget = Objects.requireNonNull(remoteBudget, "remoteBudget");
        if (remoteBudget.isNegative() || remoteBudget.isZero()) {
            throw new IllegalArgumentException("remote budget must be positive");
        }
    }

    Map<UUID, Profile> profiles(List<UUID> playerIds) {
        List<UUID> bounded = boundedIds(playerIds);
        Map<UUID, Profile> result = new LinkedHashMap<>();
        Map<UUID, CompletableFuture<Optional<Profile>>> pending = new LinkedHashMap<>();
        Instant now = clock.instant();
        for (UUID playerId : bounded) {
            Optional<Profile> cached = cached(playerId, now);
            if (cached.isPresent()) {
                result.put(playerId, cached.orElseThrow());
            } else {
                pending.put(playerId, fetch(playerId));
            }
        }
        await(pending.values());
        collectCompleted(result, pending);
        return Map.copyOf(result);
    }

    void close() {
        fetcher.shutdown();
    }

    private static List<UUID> boundedIds(List<UUID> playerIds) {
        if (playerIds == null) {
            return List.of();
        }
        List<UUID> bounded = new ArrayList<>();
        for (UUID playerId : playerIds) {
            if (playerId != null && !bounded.contains(playerId)) {
                bounded.add(playerId);
                if (bounded.size() == MAX_REMOTE_LOOKUPS) {
                    break;
                }
            }
        }
        return List.copyOf(bounded);
    }

    private CompletableFuture<Optional<Profile>> fetch(UUID playerId) {
        try {
            return fetcher.fetch(playerId)
                    .thenApply(this::parse)
                    .exceptionally(ignored -> Optional.empty());
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private void await(Iterable<CompletableFuture<Optional<Profile>>> futures) {
        List<CompletableFuture<Optional<Profile>>> pending = new ArrayList<>();
        futures.forEach(pending::add);
        if (pending.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                    .get(remoteBudget.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
            // Profile enrichment is optional; authoritative moderation reads must continue.
        }
    }

    private void collectCompleted(
            Map<UUID, Profile> result,
            Map<UUID, CompletableFuture<Optional<Profile>>> pending
    ) {
        Instant expiresAt = clock.instant().plus(CACHE_TTL);
        pending.forEach((playerId, future) -> {
            if (!future.isDone() || future.isCompletedExceptionally()) {
                return;
            }
            future.getNow(Optional.empty()).ifPresent(profile -> {
                result.put(playerId, profile);
                remember(playerId, profile, expiresAt);
            });
        });
    }

    private Optional<Profile> parse(RemoteResponse response) {
        if (response.statusCode() != HTTP_OK) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            String username = root.path("name").asText("");
            if (!USERNAME.matcher(username).matches()) {
                return Optional.empty();
            }
            return Optional.of(new Profile(username, skinTextureUrl(root)));
        } catch (java.io.IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> skinTextureUrl(JsonNode profile) throws java.io.IOException {
        for (JsonNode property : profile.path("properties")) {
            if (TEXTURES_PROPERTY.equals(property.path("name").asText())) {
                return texturePropertyUrl(property);
            }
        }
        return Optional.empty();
    }

    private Optional<String> texturePropertyUrl(JsonNode property) throws java.io.IOException {
        String encoded = property.path("value").asText("");
        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        String rawUrl = mapper.readTree(decoded).path(TEXTURES_PROPERTY).path("SKIN").path("url").asText("");
        return trustedTextureUrl(rawUrl);
    }

    private static Optional<String> trustedTextureUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getRawPath();
            if (!TEXTURE_HOST.equalsIgnoreCase(uri.getHost()) || path == null || !path.startsWith("/texture/")) {
                return Optional.empty();
            }
            return Optional.of("https://" + TEXTURE_HOST + path);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<Profile> cached(UUID playerId, Instant now) {
        synchronized (cacheLock) {
            CachedProfile entry = cache.get(playerId);
            if (entry == null) {
                return Optional.empty();
            }
            if (!entry.expiresAt().isAfter(now)) {
                cache.remove(playerId);
                return Optional.empty();
            }
            return Optional.of(entry.profile());
        }
    }

    private void remember(UUID playerId, Profile profile, Instant expiresAt) {
        synchronized (cacheLock) {
            cache.put(playerId, new CachedProfile(profile, expiresAt));
            while (cache.size() > MAX_CACHE_ENTRIES) {
                cache.remove(cache.keySet().iterator().next());
            }
        }
    }

    private static HttpRequest request(UUID playerId) {
        String compact = playerId.toString().replace("-", "");
        return HttpRequest.newBuilder(PROFILE_ROOT.resolve(compact))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
    }
}
