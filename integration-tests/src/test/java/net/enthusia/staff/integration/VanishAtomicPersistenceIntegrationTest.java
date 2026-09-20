package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class VanishAtomicPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-20T20:00:00Z");
    private static final String SERVER_ID = "SMP";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_vanish_atomicity_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void commitsBothDurableStatesAndMakesRetriesIdempotent() throws Exception {
        UUID staffId = identifier("vanish-atomic-success");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(VanishStore.WriteResult.COMMITTED, write(store, staffId, true, NOW.plusSeconds(1)));
            assertStoredState(runtime, staffId, true);
            assertEquals(1, auditCount(staffId));
            assertEquals(1, outboxCount(staffId));

            assertEquals(VanishStore.WriteResult.UNCHANGED, write(store, staffId, true, NOW.plusSeconds(2)));
            assertEquals(1, auditCount(staffId));
            assertEquals(1, outboxCount(staffId));

            assertEquals(VanishStore.WriteResult.COMMITTED, write(store, staffId, false, NOW.plusSeconds(3)));
            assertStoredState(runtime, staffId, false);
            assertEquals(VanishStore.WriteResult.UNCHANGED, write(store, staffId, false, NOW.plusSeconds(4)));
            assertEquals(2, auditCount(staffId));
            assertEquals(2, outboxCount(staffId));
        }
    }

    @Test
    void rejectsRequiredMirrorWhenNoActiveSessionExists() throws Exception {
        UUID staffId = identifier("vanish-atomic-no-session");
        try (MariaDbRuntime runtime = runtimeWithPlayer(staffId)) {
            assertEquals(
                    VanishStore.WriteResult.STAFF_SESSION_NOT_ACTIVE,
                    write(runtime.vanishStore(), staffId, true, NOW.plusSeconds(1))
            );
            assertEquals(0, vanishRowCount(staffId));
            assertEquals(0, auditCount(staffId));
            assertEquals(0, outboxCount(staffId));
        }
    }

    @Test
    void sessionTransitionPreventsSplitCommit() throws Exception {
        UUID staffId = identifier("vanish-atomic-exiting-session");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            StaffSessionSnapshot exiting = runtime.staffSessionStore()
                    .beginExit(staffId, NOW.plusSeconds(1))
                    .orElseThrow();
            assertEquals(StaffSessionState.EXITING, exiting.state());
            assertEquals(
                    VanishStore.WriteResult.STAFF_SESSION_NOT_ACTIVE,
                    write(runtime.vanishStore(), staffId, true, NOW.plusSeconds(2))
            );
            assertEquals(0, vanishRowCount(staffId));
            assertFalse(runtime.staffSessionStore().active(staffId).orElseThrow().vanishActive());
            assertEquals(0, auditCount(staffId));
            assertEquals(0, outboxCount(staffId));
        }
    }

    @Test
    void mirrorFailureRollsBackEverythingAndRetryCommitsOnce() throws Exception {
        UUID staffId = identifier("vanish-atomic-rollback");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            installMirrorFailureTrigger();
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> write(runtime.vanishStore(), staffId, true, NOW.plusSeconds(1))
                );
            } finally {
                dropMirrorFailureTrigger();
            }
            assertRolledBack(runtime, staffId);

            assertEquals(VanishStore.WriteResult.COMMITTED, write(
                    runtime.vanishStore(), staffId, true, NOW.plusSeconds(2)
            ));
            assertStoredState(runtime, staffId, true);
            assertEquals(VanishStore.WriteResult.UNCHANGED, write(
                    runtime.vanishStore(), staffId, true, NOW.plusSeconds(3)
            ));
            assertEquals(1, auditCount(staffId));
            assertEquals(1, outboxCount(staffId));
        }
    }

    @Test
    void committedStateSurvivesRuntimeRestartWithoutMirrorDivergence() throws Exception {
        UUID staffId = identifier("vanish-atomic-restart");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            assertEquals(VanishStore.WriteResult.COMMITTED, write(
                    runtime.vanishStore(), staffId, true, NOW.plusSeconds(1)
            ));
        }
        try (MariaDbRuntime restarted = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            assertEquals(StaffRank.MOD, restarted.vanishStore().active(10).stream()
                    .filter(record -> record.staffId().equals(staffId))
                    .findFirst()
                    .orElseThrow()
                    .rank());
            assertTrue(restarted.staffSessionStore().active(staffId).orElseThrow().vanishActive());
            assertEquals(1, auditCount(staffId));
            assertEquals(1, outboxCount(staffId));
        }
    }

    private static VanishStore.WriteResult write(
            VanishStore store,
            UUID staffId,
            boolean vanished,
            Instant now
    ) {
        return store.set(staffId, StaffRank.MOD, vanished, staffId, now, true);
    }

    private static MariaDbRuntime runtimeWithActiveSession(UUID staffId) {
        MariaDbRuntime runtime = runtimeWithPlayer(staffId);
        runtime.staffSessionStore().begin(
                staffId,
                SERVER_ID,
                1,
                "a".repeat(64),
                new byte[]{1},
                NOW
        );
        return runtime;
    }

    private static MariaDbRuntime runtimeWithPlayer(UUID staffId) {
        MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE));
        runtime.playerDirectory().recordSeen(
                staffId,
                playerName(staffId),
                PlayerPlatform.JAVA,
                SERVER_ID,
                NOW
        );
        return runtime;
    }

    private static String playerName(UUID staffId) {
        return "Vanish" + staffId.toString().replace("-", "").substring(0, 8);
    }

    private static void assertStoredState(MariaDbRuntime runtime, UUID staffId, boolean vanished) throws Exception {
        StoredVanish state = storedVanish(staffId);
        assertEquals(vanished, state.vanished());
        assertEquals(StaffRank.MOD, state.rank());
        assertEquals(vanished, runtime.staffSessionStore().active(staffId).orElseThrow().vanishActive());
    }

    private static void assertRolledBack(MariaDbRuntime runtime, UUID staffId) throws Exception {
        assertEquals(0, vanishRowCount(staffId));
        assertFalse(runtime.staffSessionStore().active(staffId).orElseThrow().vanishActive());
        assertEquals(0, auditCount(staffId));
        assertEquals(0, outboxCount(staffId));
    }

    private static StoredVanish storedVanish(UUID staffId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT active, staff_rank FROM staff_vanish_states WHERE staff_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(staffId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return new StoredVanish(result.getBoolean(1), StaffRank.valueOf(result.getString(2)));
            }
        }
    }

    private static int vanishRowCount(UUID staffId) throws Exception {
        return count("SELECT COUNT(*) FROM staff_vanish_states WHERE staff_id = ?", staffId);
    }

    private static int auditCount(UUID staffId) throws Exception {
        return count("""
                SELECT COUNT(*) FROM audit_events
                WHERE event_type = 'VANISH_CHANGED' AND target_id = ?
                """, staffId);
    }

    private static int outboxCount(UUID staffId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM discord_outbox
                     WHERE event_type = 'VANISH_CHANGED' AND payload_json LIKE ?
                     """)) {
            statement.setString(1, "%" + staffId + "%");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int count(String sql, UUID staffId) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, uuidBytes(staffId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static void installMirrorFailureTrigger() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_fail_vanish_session_mirror");
            statement.execute("""
                    CREATE TRIGGER test_fail_vanish_session_mirror
                    BEFORE UPDATE ON staff_sessions
                    FOR EACH ROW
                    BEGIN
                        IF NEW.vanish_active <> OLD.vanish_active THEN
                            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced vanish mirror rollback';
                        END IF;
                    END
                    """);
        }
    }

    private static void dropMirrorFailureTrigger() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_fail_vanish_session_mirror");
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }

    private static UUID identifier(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private record StoredVanish(boolean vanished, StaffRank rank) {
    }
}
