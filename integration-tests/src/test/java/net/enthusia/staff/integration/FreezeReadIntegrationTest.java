package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FreezeReadIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-08T12:30:00Z");
    private static final UUID ACTOR = UUID.fromString("43000000-0000-0000-0000-000000000001");
    private static final UUID ONLINE = UUID.fromString("43000000-0000-0000-0000-000000000002");
    private static final UUID EXPIRED = UUID.fromString("43000000-0000-0000-0000-000000000003");
    private static final UUID HELD = UUID.fromString("43000000-0000-0000-0000-000000000004");
    private static final UUID OFFLINE = UUID.fromString("43000000-0000-0000-0000-000000000005");
    private static final UUID RECONNECT = UUID.fromString("43000000-0000-0000-0000-000000000006");
    private static final UUID EXPIRED_RECONNECT = UUID.fromString("43000000-0000-0000-0000-000000000007");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_freeze_read_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void readQueriesReturnCurrentRowsWithoutTransitioningExpiredState() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            clearFreezeRows();
            insertPlayers();
            FreezeStore store = runtime.freezeStore();
            store.apply(ONLINE, ACTOR, "Online investigation", NOW);
            FreezeRecord expired = store.apply(EXPIRED, ACTOR, "Expired investigation", NOW.plusSeconds(1));
            store.disconnected(EXPIRED, expired.revision(), NOW.plusSeconds(5), NOW.plusSeconds(2)).orElseThrow();
            store.apply(HELD, ACTOR, "Held investigation", NOW.plusSeconds(3));
            store.keepActive(HELD, ACTOR, "Staff handoff", NOW.plusSeconds(4));
            FreezeRecord offline = store.apply(OFFLINE, ACTOR, "Offline investigation", NOW.plusSeconds(5));
            store.disconnected(OFFLINE, offline.revision(), NOW.plusSeconds(30), NOW.plusSeconds(6)).orElseThrow();

            Instant queryTime = NOW.plusSeconds(10);
            List<FreezeRecord> listed = store.listActive(queryTime, 10);

            assertEquals(List.of(ONLINE, HELD, OFFLINE), listed.stream().map(FreezeRecord::playerId).toList());
            assertTrue(store.readActive(ONLINE, queryTime).isPresent());
            assertTrue(store.readActive(HELD, queryTime).orElseThrow().keepActive());
            assertTrue(store.readActive(OFFLINE, queryTime).isPresent());
            assertFalse(store.readActive(EXPIRED, queryTime).isPresent());
            assertEquals("ACTIVE", freezeState(EXPIRED), "read-only lookup must not transition state");

            assertFalse(store.active(EXPIRED, queryTime).isPresent());
            assertEquals("EXPIRED", freezeState(EXPIRED), "recovery lookup owns the expiry transition");
            assertThrows(IllegalArgumentException.class, () -> store.listActive(queryTime, 0));
            assertThrows(IllegalArgumentException.class, () -> store.listActive(queryTime, 101));
        }
    }

    @Test
    void disconnectAndReconnectTransitionsAreRevisionFenced() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            clearFreezeRows();
            MariaDbIntegrationSupport.insertPlayer(DATABASE, RECONNECT, "ReconnectFrozen", NOW);
            MariaDbIntegrationSupport.insertPlayer(DATABASE, EXPIRED_RECONNECT, "ExpiredReconnect", NOW);
            FreezeStore store = runtime.freezeStore();

            FreezeRecord initial = store.apply(RECONNECT, ACTOR, "Reconnect investigation", NOW.plusSeconds(40));
            FreezeRecord disconnected = store.disconnected(
                    RECONNECT,
                    initial.revision(),
                    NOW.plusSeconds(100),
                    NOW.plusSeconds(41)
            ).orElseThrow();
            assertTrue(disconnected.offlineExpiresAt().isPresent());
            assertFalse(store.disconnected(
                    RECONNECT,
                    initial.revision(),
                    NOW.plusSeconds(110),
                    NOW.plusSeconds(42)
            ).isPresent());
            assertFalse(store.connected(RECONNECT, initial.revision(), NOW.plusSeconds(43)).isPresent());

            FreezeRecord connected = store.connected(
                    RECONNECT,
                    disconnected.revision(),
                    NOW.plusSeconds(43)
            ).orElseThrow();
            assertTrue(connected.offlineExpiresAt().isEmpty());
            assertFalse(store.disconnected(
                    RECONNECT,
                    disconnected.revision(),
                    NOW.plusSeconds(120),
                    NOW.plusSeconds(44)
            ).isPresent());

            FreezeRecord newerDisconnect = store.disconnected(
                    RECONNECT,
                    connected.revision(),
                    NOW.plusSeconds(130),
                    NOW.plusSeconds(45)
            ).orElseThrow();
            assertFalse(store.connected(RECONNECT, connected.revision(), NOW.plusSeconds(46)).isPresent());
            FreezeRecord reconnected = store.connected(
                    RECONNECT,
                    newerDisconnect.revision(),
                    NOW.plusSeconds(46)
            ).orElseThrow();
            assertTrue(reconnected.offlineExpiresAt().isEmpty());

            FreezeRecord expiring = store.apply(
                    EXPIRED_RECONNECT,
                    ACTOR,
                    "Expired reconnect investigation",
                    NOW.plusSeconds(50)
            );
            FreezeRecord expiringOffline = store.disconnected(
                    EXPIRED_RECONNECT,
                    expiring.revision(),
                    NOW.plusSeconds(55),
                    NOW.plusSeconds(51)
            ).orElseThrow();
            assertFalse(store.active(EXPIRED_RECONNECT, NOW.plusSeconds(56)).isPresent());
            assertFalse(store.connected(
                    EXPIRED_RECONNECT,
                    expiringOffline.revision(),
                    NOW.plusSeconds(56)
            ).isPresent());
        }
    }

    private static void clearFreezeRows() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("DELETE FROM player_freezes")) {
            statement.executeUpdate();
        }
    }

    private static void insertPlayers() throws Exception {
        MariaDbIntegrationSupport.insertPlayer(DATABASE, ACTOR, "FreezeStaff", NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, ONLINE, "OnlineFrozen", NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, EXPIRED, "ExpiredFrozen", NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, HELD, "HeldFrozen", NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, OFFLINE, "OfflineFrozen", NOW);
    }

    private static String freezeState(UUID playerId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state FROM player_freezes WHERE player_id = ?"
             )) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }
}
