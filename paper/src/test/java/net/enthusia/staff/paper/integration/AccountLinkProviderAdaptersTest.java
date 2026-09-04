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
    private static final String PRIMARY_DISCORD_ID = "discord-test-primary";
    private static final String FOREIGN_DISCORD_ID = "discord-test-foreign";
    private static final String MUTATION_DISCORD_ID = "discord-test-mutation";

    @Test
    void discoveryUsesActualEnthusiaPlaytimePluginName() {
        assertEquals("EnthusiaPlaytime", PlayTimeActivePlaytimeProvider.PROVIDER_PLUGIN_NAME);
    }

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
                PRIMARY_DISCORD_ID, oldMain,
                FOREIGN_DISCORD_ID, foreign
        ));
        DiscordSrvLinkProviderAdapter provider = DiscordSrvLinkProviderAdapter.fromPlugin(plugin).orElseThrow();

        assertEquals(MirrorResult.UPDATED, provider.mirrorMain(PRIMARY_DISCORD_ID, nextMain));
        assertEquals(nextMain, provider.snapshotLinks().get(PRIMARY_DISCORD_ID));
        assertEquals(MirrorResult.CONFLICT, provider.mirrorMain(PRIMARY_DISCORD_ID, foreign));
        assertEquals(FOREIGN_DISCORD_ID, plugin.manager.ownerOf(foreign));
        assertEquals(MirrorResult.UPDATED, provider.clearMirror(PRIMARY_DISCORD_ID));
        assertTrue(provider.snapshotLinks().get(PRIMARY_DISCORD_ID) == null);
        assertEquals(MirrorResult.UNCHANGED, provider.clearMirror(PRIMARY_DISCORD_ID));
    }

    @Test
    void discordSrvAdapterVerifiesSilentProviderMutationFailures() {
        UUID oldMain = UUID.randomUUID();
        UUID nextMain = UUID.randomUUID();
        FakeDiscordSrvPlugin plugin = new FakeDiscordSrvPlugin(Map.of(MUTATION_DISCORD_ID, oldMain));
        DiscordSrvLinkProviderAdapter provider = DiscordSrvLinkProviderAdapter.fromPlugin(plugin).orElseThrow();

        plugin.manager.ignoreLinkMutations = true;
        assertEquals(MirrorResult.UNAVAILABLE, provider.mirrorMain(MUTATION_DISCORD_ID, nextMain));
        assertEquals(oldMain, provider.snapshotLinks().get(MUTATION_DISCORD_ID));

        plugin.manager.ignoreLinkMutations = false;
        plugin.manager.ignoreUnlinkMutations = true;
        assertEquals(MirrorResult.UNAVAILABLE, provider.clearMirror(MUTATION_DISCORD_ID));
        assertEquals(oldMain, provider.snapshotLinks().get(MUTATION_DISCORD_ID));
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
        boolean ignoreLinkMutations;
        boolean ignoreUnlinkMutations;

        FakeAccountLinkManager(Map<String, UUID> initial) {
            links.putAll(initial);
        }

        public Map<String, UUID> getLinkedAccounts() {
            return new LinkedHashMap<>(links);
        }

        public void link(String discordId, UUID uuid) {
            if (ignoreLinkMutations) {
                return;
            }
            links.entrySet().removeIf(entry -> uuid.equals(entry.getValue()));
            links.put(discordId, uuid);
        }

        public void unlink(String discordId) {
            if (!ignoreUnlinkMutations) {
                links.remove(discordId);
            }
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
