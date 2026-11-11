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
    private static final String NO_STAFF_RANK = "none";
    private static final String FOUNDER_RANK = "founder";
    private static final String SMP_SERVER = "SMP";
    private static final String ORDINARY = "Ordinary";
    private static final String VIEWER = "Viewer";
    private static final String STAFF = "Staff";
    private static final String HIDDEN = "Hidden";

    @Test
    void stalePresenceNeverRevealsUnknownPromotedOrPreviouslyStaffTargets() throws ReflectiveOperationException {
        UUID ordinaryId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Player ordinary = player(ordinaryId, ORDINARY, NO_STAFF_RANK);
        Player demoted = player(staffId, "Demoted", NO_STAFF_RANK);
        Player unknown = player(UUID.randomUUID(), "Unknown", NO_STAFF_RANK);
        Player promoted = player(UUID.randomUUID(), "Promoted", MOD_RANK);
        Player identityOnly = player(UUID.randomUUID(), "IdentityOnly", "identity.mod");
        Player unrestricted = player(UUID.randomUUID(), "Unrestricted", "identity.unrestricted");
        Player staffMode = player(UUID.randomUUID(), "StaffMode", NO_STAFF_RANK);
        Player savedVanish = player(UUID.randomUUID(), "SavedVanish", NO_STAFF_RANK);
        FakeApi api = new FakeApi();
        VelocitabStaffBridge bridge = bridge(api,
                List.of(ordinary, demoted, unknown, promoted, identityOnly, unrestricted, staffMode, savedVanish));
        assertTrue(api.integration.isVanished(ORDINARY), "unverified startup must stay closed");
        seedPresence(bridge,
                new StaffTabPresence(Map.of(savedVanish.getUniqueId(), StaffRank.MOD), Set.of(staffMode.getUniqueId())),
                Map.of(staffId, StaffRank.MOD),
                Set.of(ordinaryId, staffId, promoted.getUniqueId(), identityOnly.getUniqueId(), unrestricted.getUniqueId(),
                        staffMode.getUniqueId(), savedVanish.getUniqueId()));
        for (String target : List.of("Unknown", "Promoted", "IdentityOnly", "Unrestricted", "Demoted", "StaffMode", "SavedVanish")) {
            assertTrue(api.integration.isVanished(target), target);
            assertFalse(api.integration.canSee(ORDINARY, target), target);
        }
        assertFalse(api.integration.isVanished(ORDINARY));
        bridge.close();
    }

    @Test
    void ordinaryFallbackPreservesPreviousProviderAndFreshRecovery() throws ReflectiveOperationException {
        Player ordinary = player(UUID.randomUUID(), ORDINARY, NO_STAFF_RANK);
        Player viewer = player(UUID.randomUUID(), VIEWER, FOUNDER_RANK);
        Player staff = player(UUID.randomUUID(), STAFF, MOD_RANK);
        FakeApi api = new FakeApi();
        api.integration = new VanishIntegration() {
            public boolean canSee(String from, String target) { return !target.equals(ORDINARY); }
            public boolean isVanished(String target) { return target.equals(ORDINARY); }
        };
        VelocitabStaffBridge bridge = bridge(api, List.of(ordinary, viewer, staff));
        StaffTabPresence snapshot = new StaffTabPresence(Map.of(staff.getUniqueId(), StaffRank.MOD), Set.of());
        Map<UUID, StaffRank> ranks = Map.of(viewer.getUniqueId(), StaffRank.FOUNDER, staff.getUniqueId(), StaffRank.MOD);
        Set<UUID> players = Set.of(ordinary.getUniqueId(), viewer.getUniqueId(), staff.getUniqueId());
        seedPresence(bridge, snapshot, ranks, players);
        assertTrue(api.integration.isVanished(ORDINARY));
        assertFalse(api.integration.canSee(VIEWER, ORDINARY));
        assertFalse(api.integration.canSee(VIEWER, STAFF), "stale staff visibility remains closed even for founders");
        setPresence(bridge, snapshot, ranks, players, System.nanoTime());
        assertTrue(api.integration.isVanished(ORDINARY));
        assertTrue(api.integration.isVanished(STAFF));
        assertTrue(api.integration.canSee(VIEWER, STAFF), "fresh verified hierarchy may reveal staff to an authorized viewer");
        assertFalse(api.integration.canSee(ORDINARY, STAFF));
        bridge.close();
    }

    @Test
    void verifiedOrdinaryPlayerSurvivesDelayedRefreshWithoutRevealingStaff() throws ReflectiveOperationException {
        UUID ordinaryId = UUID.randomUUID();
        UUID hiddenId = UUID.randomUUID();
        Player ordinary = player(ordinaryId, ORDINARY, NO_STAFF_RANK);
        Player hidden = player(hiddenId, HIDDEN, MOD_RANK);
        FakeApi api = new FakeApi();
        VelocitabStaffBridge bridge = bridge(api, List.of(ordinary, hidden));
        seedPresence(bridge, new StaffTabPresence(Map.of(hiddenId, StaffRank.MOD), Set.of()),
                Map.of(hiddenId, StaffRank.MOD), Set.of(ordinaryId, hiddenId));

        assertFalse(api.integration.isVanished(ORDINARY));
        assertTrue(api.integration.canSee(HIDDEN, ORDINARY));
        assertTrue(api.integration.isVanished(HIDDEN));
        assertFalse(api.integration.canSee(ORDINARY, HIDDEN));
        bridge.close();
    }

    private static void seedPresence(VelocitabStaffBridge bridge, StaffTabPresence snapshot,
            Map<UUID, StaffRank> ranks, Set<UUID> players) throws ReflectiveOperationException {
        setPresence(bridge, snapshot, ranks, players,
                System.nanoTime() - java.time.Duration.ofSeconds(6).toNanos());
    }

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
        StaffTabPresence snapshot = new StaffTabPresence(Map.of(adminId, StaffRank.ADMIN), Set.of());
        setPresence(bridge, snapshot, Map.of(modId, StaffRank.MOD, adminId, StaffRank.ADMIN),
                Set.of(modId, adminId), System.nanoTime());
        assertFalse(api.integration.canSee(MOD, ADMIN));
        assertTrue(api.integration.canSee(ADMIN, ADMIN));
        assertFalse(api.integration.isVanished(MOD));
        seedPresence(bridge, snapshot, Map.of(modId, StaffRank.MOD, adminId, StaffRank.ADMIN),
                Set.of(modId, adminId));
        assertTrue(api.integration.isVanished(MOD));
        assertFalse(api.integration.canSee(ADMIN, MOD));
        bridge.close();
    }

    @Test
    void markersPreserveForeignNamesAndCleanupRestoresOnlyOwnedValues() throws ReflectiveOperationException {
        Player player = player(UUID.randomUUID(), STAFF, MOD_RANK);
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
        Player player = player(UUID.randomUUID(), STAFF, MOD_RANK);
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
        Player ordinary = player(ordinaryId, ORDINARY, NO_STAFF_RANK, SMP_SERVER);
        Player hidden = player(hiddenId, HIDDEN, MOD_RANK, SMP_SERVER);
        Player ordinaryViewer = player(ordinaryViewerId, VIEWER, NO_STAFF_RANK, SMP_SERVER);
        Player founderViewer = player(founderViewerId, "Founder", FOUNDER_RANK, SMP_SERVER);
        VelocitabStaffBridge bridge = bridge(new FakeApi(),
                List.of(ordinary, hidden, ordinaryViewer, founderViewer));

        StaffTabPresence snapshot = new StaffTabPresence(Map.of(hiddenId, StaffRank.MOD), Set.of());
        setPresence(bridge, snapshot, Map.of(hiddenId, StaffRank.MOD, founderViewerId, StaffRank.FOUNDER),
                Set.of(ordinaryId, hiddenId, ordinaryViewerId, founderViewerId), System.nanoTime());
        assertEquals(3, bridge.localPublicOnlineCount(ordinaryViewer));
        assertEquals(3, bridge.localPublicOnlineCount(founderViewer));
        assertEquals(0, bridge.localPublicOnlineCount(player(UUID.randomUUID(), "Connecting", FOUNDER_RANK)));
        seedPresence(bridge, snapshot, Map.of(hiddenId, StaffRank.MOD, founderViewerId, StaffRank.FOUNDER),
                Set.of(ordinaryId, hiddenId, ordinaryViewerId, founderViewerId));
        assertEquals(0, bridge.localPublicOnlineCount(founderViewer));
        bridge.close();
    }

    private static void setPresence(VelocitabStaffBridge bridge, StaffTabPresence snapshot,
            Map<UUID, StaffRank> ranks, Set<UUID> players, long verifiedAt) throws ReflectiveOperationException {
        var field = VelocitabStaffBridge.class.getDeclaredField("verifiedPresence");
        field.trySetAccessible();
        field.set(bridge, new VelocitabStaffBridge.VerifiedPresence(snapshot, ranks, players, verifiedAt));
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
                    case "hasPermission" -> args[0].equals(rank.startsWith("identity.")
                            ? "enthusiastaff." + rank : "enthusiastaff.rank." + rank);
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
