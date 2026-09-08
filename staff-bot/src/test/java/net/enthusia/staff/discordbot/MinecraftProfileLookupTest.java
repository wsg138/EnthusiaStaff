package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MinecraftProfileLookupTest {
    private static final UUID PLAYER_ID = UUID.fromString("e6a94ee5-db9e-47b1-b32c-06b16d6781d5");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T17:00:00Z"), ZoneOffset.UTC);

    @Test
    void resolvesCurrentUsernameAndTrustedSkinThenCachesByUuid() {
        AtomicInteger calls = new AtomicInteger();
        MinecraftProfileLookup lookup = lookup(playerId -> {
            calls.incrementAndGet();
            return completed(200, profile("CurrentName", "http://textures.minecraft.net/texture/abc123"));
        });

        Map<UUID, MinecraftProfileLookup.Profile> first = lookup.profiles(List.of(PLAYER_ID));
        Map<UUID, MinecraftProfileLookup.Profile> second = lookup.profiles(List.of(PLAYER_ID));

        assertEquals("CurrentName", first.get(PLAYER_ID).username());
        assertEquals("https://textures.minecraft.net/texture/abc123",
                first.get(PLAYER_ID).skinTextureUrl().orElseThrow());
        assertEquals(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsUntrustedTextureHostWithoutDroppingValidUsername() {
        MinecraftProfileLookup lookup = lookup(playerId -> completed(
                200,
                profile("SafeName", "https://example.com/texture/not-allowed")));

        MinecraftProfileLookup.Profile profile = lookup.profiles(List.of(PLAYER_ID)).get(PLAYER_ID);

        assertEquals("SafeName", profile.username());
        assertTrue(profile.skinTextureUrl().isEmpty());
    }

    @Test
    void failedLookupIsNotCachedAndCanRetry() {
        AtomicInteger calls = new AtomicInteger();
        MinecraftProfileLookup lookup = lookup(playerId -> {
            if (calls.getAndIncrement() == 0) {
                return CompletableFuture.failedFuture(new IllegalStateException("temporary failure"));
            }
            return completed(200, profile("RecoveredName", null));
        });

        assertTrue(lookup.profiles(List.of(PLAYER_ID)).isEmpty());
        assertEquals("RecoveredName", lookup.profiles(List.of(PLAYER_ID)).get(PLAYER_ID).username());
        assertEquals(2, calls.get());
    }

    @Test
    void remoteTimeoutFailsSoftWithoutBlockingModerationRead() {
        MinecraftProfileLookup lookup = new MinecraftProfileLookup(
                playerId -> new CompletableFuture<>(),
                CLOCK,
                Duration.ofMillis(5));

        assertTrue(lookup.profiles(List.of(PLAYER_ID)).isEmpty());
    }

    @Test
    void lookupBatchIsBoundedAndDeduplicated() {
        AtomicInteger calls = new AtomicInteger();
        MinecraftProfileLookup lookup = lookup(playerId -> {
            calls.incrementAndGet();
            return completed(404, "");
        });
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            ids.add(UUID.nameUUIDFromBytes(("profile-" + index).getBytes(StandardCharsets.UTF_8)));
        }
        ids.add(ids.getFirst());

        assertFalse(ids.isEmpty());
        assertTrue(lookup.profiles(ids).isEmpty());
        assertEquals(32, calls.get());
    }

    private static MinecraftProfileLookup lookup(MinecraftProfileLookup.RemoteFetcher fetcher) {
        return new MinecraftProfileLookup(fetcher, CLOCK, Duration.ofMillis(100));
    }

    private static CompletableFuture<MinecraftProfileLookup.RemoteResponse> completed(int status, String body) {
        return CompletableFuture.completedFuture(new MinecraftProfileLookup.RemoteResponse(status, body));
    }

    private static String profile(String username, String skinUrl) {
        String properties = "";
        if (skinUrl != null) {
            String textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
            String encoded = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
            properties = ",\"properties\":[{\"name\":\"textures\",\"value\":\"" + encoded + "\"}]";
        }
        return "{\"id\":\"" + PLAYER_ID.toString().replace("-", "") + "\",\"name\":\""
                + username + "\"" + properties + "}";
    }
}
