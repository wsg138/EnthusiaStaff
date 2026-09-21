package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FreezeNetworkReconciliationIntegrationTest {
    private static final String NETWORK_KEY_PREFIX = "freeze-reconcile:";
    private static final Instant NOW = Instant.parse("2026-09-20T20:00:00Z");
    private static final UUID ACTOR = UUID.fromString("44000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("44000000-0000-0000-0000-000000000002");
    private static final UUID APPLY_ROLLBACK = UUID.fromString("44000000-0000-0000-0000-000000000003");
    private static final UUID RELEASE_ROLLBACK = UUID.fromString("44000000-0000-0000-0000-000000000004");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_freeze_network_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void applyAndReleasePublishPrivacyMinimalDurableReconciliationMessages() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            insertPlayers(TARGET);
            FreezeStore store = runtime.freezeStore();

            store.apply(TARGET, ACTOR, "private investigation details", NOW);
            assertTrue(store.release(TARGET, ACTOR, "private release details", NOW.plusSeconds(1)));

            List<NetworkRow> rows = networkRows(TARGET);
            assertEquals(2, rows.size());
            assertEquals(networkKey(TARGET, 0L), rows.get(0).idempotencyKey());
            assertEquals(networkKey(TARGET, 1L), rows.get(1).idempotencyKey());
            for (NetworkRow row : rows) {
                assertEquals("broadcast", row.destination());
                assertEquals("FREEZE_CHANGED", row.messageType());
                assertEquals(1, row.protocolVersion());
                assertEquals("{\"targetId\":\"" + TARGET + "\"}", row.payload());
                assertFalse(row.payload().contains(ACTOR.toString()));
                assertFalse(row.payload().contains("investigation"));
                assertFalse(row.payload().contains("release"));
            }
        }
    }

    @Test
    void applyRollsBackAndRetryCommitsExactlyOnceWhenReconciliationCanBeEnqueued() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            insertPlayers(APPLY_ROLLBACK);
            insertConflictingKey(APPLY_ROLLBACK, 0L);
            FreezeStore store = runtime.freezeStore();

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.apply(APPLY_ROLLBACK, ACTOR, "must roll back", NOW)
            );
            assertEquals(0L, freezeCount(APPLY_ROLLBACK));
            assertEquals(0L, auditCount(APPLY_ROLLBACK, "PLAYER_FROZEN"));
            assertEquals(0L, discordCount(APPLY_ROLLBACK, "PLAYER_FROZEN"));

            deleteConflictingKey(APPLY_ROLLBACK, 0L);
            store.apply(APPLY_ROLLBACK, ACTOR, "retry succeeds", NOW.plusSeconds(1));
            assertEquals(1L, freezeCount(APPLY_ROLLBACK));
            assertEquals(1L, auditCount(APPLY_ROLLBACK, "PLAYER_FROZEN"));
            assertEquals(1L, discordCount(APPLY_ROLLBACK, "PLAYER_FROZEN"));
            assertEquals(1, networkRows(APPLY_ROLLBACK).size());
        }
    }

    @Test
    void releaseRollsBackAndRetryCommitsExactlyOnceWhenReconciliationCanBeEnqueued() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            insertPlayers(RELEASE_ROLLBACK);
            FreezeStore store = runtime.freezeStore();
            store.apply(RELEASE_ROLLBACK, ACTOR, "active", NOW);
            insertConflictingKey(RELEASE_ROLLBACK, 1L);

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.release(RELEASE_ROLLBACK, ACTOR, "must roll back", NOW.plusSeconds(1))
            );
            assertTrue(store.readActive(RELEASE_ROLLBACK, NOW.plusSeconds(2)).isPresent());
            assertEquals(0L, auditCount(RELEASE_ROLLBACK, "PLAYER_UNFROZEN"));
            assertEquals(0L, discordCount(RELEASE_ROLLBACK, "PLAYER_UNFROZEN"));

            deleteConflictingKey(RELEASE_ROLLBACK, 1L);
            assertTrue(store.release(RELEASE_ROLLBACK, ACTOR, "retry succeeds", NOW.plusSeconds(3)));
            assertFalse(store.readActive(RELEASE_ROLLBACK, NOW.plusSeconds(4)).isPresent());
            assertEquals(1L, auditCount(RELEASE_ROLLBACK, "PLAYER_UNFROZEN"));
            assertEquals(1L, discordCount(RELEASE_ROLLBACK, "PLAYER_UNFROZEN"));
            assertEquals(2, networkRows(RELEASE_ROLLBACK).size());
        }
    }

    private static void insertPlayers(UUID target) throws Exception {
        MariaDbIntegrationSupport.insertPlayer(DATABASE, ACTOR, "FreezeNetworkStaff", NOW);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, target, "FreezeNetworkTarget", NOW);
    }

    private static void insertConflictingKey(UUID target, long revision) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO network_outbox(
                         message_id, idempotency_key, destination, message_type,
                         protocol_version, payload_json, available_at, created_at
                     ) VALUES (?, ?, 'broadcast', 'TEST_CONFLICT', 1, '{}', ?, ?)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
            statement.setString(2, networkKey(target, revision));
            statement.setTimestamp(3, Timestamp.from(NOW));
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static void deleteConflictingKey(UUID target, long revision) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM network_outbox WHERE idempotency_key = ? AND message_type = 'TEST_CONFLICT'"
             )) {
            statement.setString(1, networkKey(target, revision));
            statement.executeUpdate();
        }
    }

    private static List<NetworkRow> networkRows(UUID target) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT idempotency_key, destination, message_type, protocol_version, payload_json
                     FROM network_outbox
                     WHERE idempotency_key LIKE ?
                     ORDER BY idempotency_key ASC
                     """)) {
            statement.setString(1, NETWORK_KEY_PREFIX + target + ":%");
            try (ResultSet result = statement.executeQuery()) {
                List<NetworkRow> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(new NetworkRow(
                            result.getString("idempotency_key"),
                            result.getString("destination"),
                            result.getString("message_type"),
                            result.getInt("protocol_version"),
                            result.getString("payload_json")
                    ));
                }
                return rows;
            }
        }
    }

    private static long freezeCount(UUID target) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM player_freezes WHERE player_id = ?"
             )) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(target));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long auditCount(UUID target, String eventType) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM audit_events WHERE target_id = ? AND event_type = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(target));
            statement.setString(2, eventType);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long discordCount(UUID target, String eventType) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM discord_outbox
                     WHERE event_type = ? AND payload_json LIKE ?
                     """)) {
            statement.setString(1, eventType);
            statement.setString(2, "%" + target + "%");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static String networkKey(UUID target, long revision) {
        return NETWORK_KEY_PREFIX + target + ':' + revision;
    }

    private record NetworkRow(
            String idempotencyKey,
            String destination,
            String messageType,
            int protocolVersion,
            String payload
    ) {
    }
}
