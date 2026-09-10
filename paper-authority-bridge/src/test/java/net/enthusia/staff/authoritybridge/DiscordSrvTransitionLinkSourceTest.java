package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscordSrvTransitionLinkSourceTest {
    @Test
    void absentProviderIsOptionalAndValidSnapshotIsCopied() {
        assertTrue(DiscordSrvTransitionLinkSource.fromPlugin(null).isEmpty());
        UUID player = UUID.randomUUID();
        FakeDiscordSrv plugin = new FakeDiscordSrv(Map.of("123456789012345678", player));

        Map<String, UUID> snapshot = DiscordSrvTransitionLinkSource.fromPlugin(plugin)
                .orElseThrow().snapshotLinks();

        assertEquals(Map.of("123456789012345678", player), snapshot);
    }

    @Test
    void malformedAndOversizedProviderSnapshotsFailClosed() {
        FakeDiscordSrv malformed = new FakeDiscordSrv(Map.of("not-a-discord-id", UUID.randomUUID()));
        assertThrows(IllegalStateException.class,
                () -> DiscordSrvTransitionLinkSource.fromPlugin(malformed).orElseThrow().snapshotLinks());

        Map<String, UUID> oversized = new LinkedHashMap<>();
        for (int index = 0; index <= DiscordSrvTransitionLinkSource.MAX_LINKS; index++) {
            oversized.put(Long.toString(100_000_000_000_000_000L + index), UUID.randomUUID());
        }
        FakeDiscordSrv tooMany = new FakeDiscordSrv(oversized);
        assertThrows(IllegalStateException.class,
                () -> DiscordSrvTransitionLinkSource.fromPlugin(tooMany).orElseThrow().snapshotLinks());
    }

    public static final class FakeDiscordSrv {
        private final FakeAccountLinkManager manager;

        public FakeDiscordSrv(Map<?, ?> links) {
            this.manager = new FakeAccountLinkManager(links);
        }

        public FakeAccountLinkManager getAccountLinkManager() {
            return manager;
        }
    }

    public static final class FakeAccountLinkManager {
        private final Map<?, ?> links;

        public FakeAccountLinkManager(Map<?, ?> links) {
            this.links = links;
        }

        public Map<?, ?> getLinkedAccounts() {
            return links;
        }
    }
}
