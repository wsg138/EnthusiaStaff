package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.ActivePlaytimeProvider;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.MirrorResult;
import org.junit.jupiter.api.Test;

class AccountLinkProviderAdaptersTest {
    @Test
    void missingPlayTimeProviderPreservesUnknownPlaytime() {
        ActivePlaytimeProvider provider = PlayTimeActivePlaytimeProvider.fromPlugin(null);
        assertTrue(provider.lifetimeActiveMinutes(UUID.randomUUID()).isEmpty());
    }

    @Test
    void presentPlayTimeProviderReadsPublicLifetimeActiveMinutes() {
        UUID playerId = UUID.randomUUID();
        FakePlayTimePlugin plugin = new FakePlayTimePlugin(playerId, 4321L);
        ActivePlaytimeProvider provider = PlayTimeActivePlaytimeProvider.fromPlugin(plugin);
        assertEquals(4321L, provider.lifetimeActiveMinutes(playerId).orElseThrow());
    }

    @Test
    void missingDiscordSrvProviderIsExplicitlyUnavailable() {
        assertTrue(DiscordSrvLinkProviderAdapter.fromPlugin(null).isEmpty());
    }

    @Test
    void presentDiscordSrvProviderMirrorsMainClearsAndRefusesForeignOwnership() {
        UUID oldMain = UUID.randomUUID();
        UUID nextMain = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        FakeDiscordSrvPlugin plugin = new FakeDiscordSrvPlugin(Map.of(
                "100000000000000001", oldMain,
                "100000000000000002", foreign
        ));
        DiscordSrvLinkProviderAdapter provider = DiscordSrvLinkProviderAdapter.fromPlugin(plugin).orElseThrow();

        assertEquals(MirrorResult.UPDATED, provider.mirrorMain("100000000000000001", nextMain));
        assertEquals(nextMain, provider.snapshotLinks().get("100000000000000001"));
        assertEquals(MirrorResult.CONFLICT, provider.mirrorMain("100000000000000001", foreign));
        assertEquals("100000000000000002", plugin.manager.ownerOf(foreign));
        assertEquals(MirrorResult.UPDATED, provider.clearMirror("100000000000000001"));
        assertTrue(provider.snapshotLinks().get("100000000000000001") == null);
        assertEquals(MirrorResult.UNCHANGED, provider.clearMirror("100000000000000001"));
    }

    public static final class FakePlayTimePlugin {
        private final FakePlaytimeService service;

        FakePlayTimePlugin(UUID playerId, long activeMinutes) {
            this.service = new FakePlaytimeService(playerId, activeMinutes);
        }

        public FakePlaytimeService getPlaytimeService() {
            return service;
        }
    }

    public static final class FakePlaytimeService {
        private final UUID playerId;
        private final long activeMinutes;

        FakePlaytimeService(UUID playerId, long activeMinutes) {
            this.playerId = playerId;
            this.activeMinutes = activeMinutes;
        }

        public Optional<FakeSnapshot> getLifetime(UUID requested) {
            return playerId.equals(requested)
                    ? Optional.of(new FakeSnapshot(activeMinutes))
                    : Optional.empty();
        }
    }

    public static final class FakeSnapshot {
        public final long activeMinutes;

        FakeSnapshot(long activeMinutes) {
            this.activeMinutes = activeMinutes;
        }
    }

    public static final class FakeDiscordSrvPlugin {
        final FakeAccountLinkManager manager;

        FakeDiscordSrvPlugin(Map<String, UUID> links) {
            this.manager = new FakeAccountLinkManager(links);
        }

        public FakeAccountLinkManager getAccountLinkManager() {
            return manager;
        }
    }

    public static final class FakeAccountLinkManager {
        private final Map<String, UUID> links = new LinkedHashMap<>();

        FakeAccountLinkManager(Map<String, UUID> initial) {
            links.putAll(initial);
        }

        public Map<String, UUID> getLinkedAccounts() {
            return new LinkedHashMap<>(links);
        }

        public void link(String discordId, UUID uuid) {
            links.put(discordId, uuid);
        }

        public void unlink(String discordId) {
            links.remove(discordId);
        }

        String ownerOf(UUID uuid) {
            return links.entrySet().stream()
                    .filter(entry -> uuid.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
    }
}
