package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class VelocitabStaffBridgeTest {
    private static final String MOD = "Mod";
    private static final String ADMIN = "Admin";
    private static final String MOD_RANK = "mod";
    private static final String SMP_SERVER = "SMP";

    @Test
    void presenceQueryUsesAcceptedJdbcLimit() {
        java.util.concurrent.atomic.AtomicBoolean connected = new java.util.concurrent.atomic.AtomicBoolean();
        javax.sql.DataSource source = (javax.sql.DataSource) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{javax.sql.DataSource.class}, (ignored, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        connected.set(true);
                        throw new java.sql.SQLException("test database unavailable");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        assertThrows(net.enthusia.staff.persistence.ModerationPersistenceException.class,
                () -> VelocitabStaffBridge.loadVanished(new net.enthusia.staff.persistence.JdbcVanishStore(source)));
        assertTrue(connected.get(), "the bounded query must reach storage rather than fail its argument check");
    }

    @Test
    void fullPresencePageFailsClosedInsteadOfPublishingATruncatedSet() {
        var record = new net.enthusia.staff.domain.staff.VanishRecord(
                UUID.randomUUID(), StaffRank.MOD, java.time.Instant.now(), 1);
        var store = (net.enthusia.staff.domain.ports.VanishStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{net.enthusia.staff.domain.ports.VanishStore.class},
                (ignored, method, args) -> {
                    assertEquals("active", method.getName());
                    assertEquals(10_000, args[0]);
                    return java.util.Collections.nCopies(10_000, record);
                });
        assertThrows(IllegalStateException.class, () -> VelocitabStaffBridge.loadVanished(store));
    }

    public interface VanishIntegration {
        boolean canSee(String viewer, String target);
        boolean isVanished(String name);
    }

    public static final class FakeApi {
        private VanishIntegration integration = new VanishIntegration() {
            public boolean canSee(String viewer, String target) { return true; }
            public boolean isVanished(String name) { return false; }
        };
        private String name;
        public VanishIntegration getVanishIntegration() { return integration; }
        public void setVanishIntegration(VanishIntegration value) { integration = value; }
        public Optional<String> getCustomPlayerName(Player player) { return Optional.ofNullable(name); }
        public void setCustomPlayerName(Player player, String value) { name = value; }
    }

    @Test
    void unknownAndStalePresenceFailClosedAndVerifiedMatrixApplies() throws ReflectiveOperationException {
        UUID modId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Player mod = player(modId, MOD, MOD_RANK);
        Player admin = player(adminId, ADMIN, "admin");
        FakeApi api = new FakeApi();
        VelocitabStaffBridge bridge = bridge(api, List.of(mod, admin));
        assertTrue(api.integration.isVanished(ADMIN));
        assertFalse(api.integration.canSee(MOD, ADMIN));
        var presence = VelocitabStaffBridge.class.getDeclaredField("presence");
        presence.trySetAccessible();
        presence.set(bridge, new StaffTabPresence(Map.of(adminId, StaffRank.ADMIN), Set.of()));
        var verifiedAt = VelocitabStaffBridge.class.getDeclaredField("verifiedAt");
        verifiedAt.trySetAccessible();
        verifiedAt.setLong(bridge, System.nanoTime());
        assertFalse(api.integration.canSee(MOD, ADMIN));
        assertTrue(api.integration.canSee(ADMIN, ADMIN));
        assertFalse(api.integration.isVanished(MOD));
        verifiedAt.setLong(bridge, System.nanoTime() - java.time.Duration.ofSeconds(6).toNanos());
        assertTrue(api.integration.isVanished(MOD));
        assertFalse(api.integration.canSee(ADMIN, MOD));
        bridge.close();
    }

    @Test
    void markersPreserveForeignNamesAndCleanupRestoresOnlyOwnedValues() throws ReflectiveOperationException {
        Player player = player(UUID.randomUUID(), "Staff", MOD_RANK);
        FakeApi api = new FakeApi();
        VanishIntegration previous = api.integration;
        VelocitabStaffBridge bridge = bridge(api, List.of(player));
        var update = VelocitabStaffBridge.class.getDeclaredMethod("updateName", Player.class, String.class);
        update.trySetAccessible();
        update.invoke(bridge, player, "<aqua>[V]</aqua> ");
        assertEquals("<aqua>[V]</aqua> Staff", api.name);
        update.invoke(bridge, player, "<aqua>[V]</aqua> ");
        assertEquals("<aqua>[V]</aqua> Staff", api.name);
        update.invoke(bridge, player, "");
        assertEquals(Optional.empty(), api.getCustomPlayerName(player));
        api.name = "Existing nickname";
        update.invoke(bridge, player, "[STAFF] ");
        assertEquals("[STAFF] Existing nickname", api.name);
        bridge.close();
        assertEquals("Existing nickname", api.name);
        assertSame(previous, api.integration);
    }

    @Test
    void cleanupDoesNotOverwriteANewerIntegrationOrName() throws ReflectiveOperationException {
        Player player = player(UUID.randomUUID(), "Staff", MOD_RANK);
        FakeApi api = new FakeApi();
        VelocitabStaffBridge bridge = bridge(api, List.of(player));
        var update = VelocitabStaffBridge.class.getDeclaredMethod("updateName", Player.class, String.class);
        update.trySetAccessible();
        update.invoke(bridge, player, "[V] ");
        api.name = "New nickname";
        VanishIntegration newer = new VanishIntegration() {
            public boolean canSee(String viewer, String target) { return false; }
            public boolean isVanished(String name) { return true; }
        };
        api.integration = newer;
        bridge.close();
        assertEquals("New nickname", api.name);
        assertSame(newer, api.integration);
    }

    @Test
    void localPublicCountUsesViewerBackendButNotViewerRankAndFailsClosed() throws ReflectiveOperationException {
        UUID ordinaryId = UUID.randomUUID();
        UUID hiddenId = UUID.randomUUID();
        UUID ordinaryViewerId = UUID.randomUUID();
        UUID founderViewerId = UUID.randomUUID();
        Player ordinary = player(ordinaryId, "Ordinary", "none", SMP_SERVER);
        Player hidden = player(hiddenId, "Hidden", MOD_RANK, SMP_SERVER);
        Player ordinaryViewer = player(ordinaryViewerId, "Viewer", "none", SMP_SERVER);
        Player founderViewer = player(founderViewerId, "Founder", "founder", SMP_SERVER);
        VelocitabStaffBridge bridge = bridge(new FakeApi(),
                List.of(ordinary, hidden, ordinaryViewer, founderViewer));

        var presence = VelocitabStaffBridge.class.getDeclaredField("presence");
        presence.trySetAccessible();
        presence.set(bridge, new StaffTabPresence(Map.of(hiddenId, StaffRank.MOD), Set.of()));
        var verifiedAt = VelocitabStaffBridge.class.getDeclaredField("verifiedAt");
        verifiedAt.trySetAccessible();
        verifiedAt.setLong(bridge, System.nanoTime());
        assertEquals(3, bridge.localPublicOnlineCount(ordinaryViewer));
        assertEquals(3, bridge.localPublicOnlineCount(founderViewer));
        assertEquals(0, bridge.localPublicOnlineCount(player(UUID.randomUUID(), "Connecting", "founder")));
        verifiedAt.setLong(bridge, System.nanoTime() - java.time.Duration.ofSeconds(6).toNanos());
        assertEquals(0, bridge.localPublicOnlineCount(founderViewer));
        bridge.close();
    }

    private static VelocitabStaffBridge bridge(FakeApi api, List<Player> players) throws ReflectiveOperationException {
        ProxyServer proxy = (ProxyServer) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{ProxyServer.class}, (ignored, method, args) -> switch (method.getName()) {
                    case "getAllPlayers" -> players;
                    case "getPlayer" -> players.stream().filter(player -> player.getUsername().equals(args[0])).findFirst();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new VelocitabStaffBridge(proxy, LoggerFactory.getLogger(VelocitabStaffBridgeTest.class),
                () -> null, Runnable::run, api, VanishIntegration.class);
    }

    private static Player player(UUID id, String name, String rank) {
        return player(id, name, rank, null);
    }

    private static Player player(UUID id, String name, String rank, String serverName) {
        return (Player) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{Player.class},
                (ignored, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getUsername" -> name;
                    case "getCurrentServer" -> serverName == null
                            ? Optional.empty() : Optional.of(serverConnection(serverName));
                    case "hasPermission" -> args[0].equals("enthusiastaff.rank." + rank);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ServerConnection serverConnection(String serverName) {
        return (ServerConnection) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{ServerConnection.class},
                (ignored, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> new ServerInfo(
                            serverName,
                            InetSocketAddress.createUnresolved("127.0.0.1", 25565)
                    );
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
