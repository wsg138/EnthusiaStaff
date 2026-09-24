package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private static final String SNAPSHOT_CHECKSUM = "a".repeat(64);
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString();

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_vanish_atomicity_test")
            .withUsername("enthusia_test")
            .withPassword(DATABASE_PASSWORD);

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
    void mirrorsRecoveryRequiredSessionInTheSameCommit() throws Exception {
        UUID staffId = identifier("vanish-atomic-recovery-required");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            StaffSessionSnapshot active = runtime.staffSessionStore().active(staffId).orElseThrow();
            runtime.staffSessionStore().recoveryRequired(
                    active.sessionId(),
                    "forced recovery-required test state",
                    NOW.plusSeconds(1)
            );
            assertEquals(
                    StaffSessionState.RECOVERY_REQUIRED,
                    runtime.staffSessionStore().active(staffId).orElseThrow().state()
            );

            assertEquals(VanishStore.WriteResult.COMMITTED, write(
                    runtime.vanishStore(), staffId, true, NOW.plusSeconds(2)
            ));
            assertStoredState(runtime, staffId, true);
            assertEquals(
                    StaffSessionState.RECOVERY_REQUIRED,
                    runtime.staffSessionStore().active(staffId).orElseThrow().state()
            );
            assertEquals(1, auditCount(staffId));
            assertEquals(1, outboxCount(staffId));
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
    void convergesStaleMirrorWithoutRepeatingCanonicalEvents() throws Exception {
        UUID staffId = identifier("vanish-atomic-stale-mirror");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(VanishStore.WriteResult.COMMITTED, write(store, staffId, true, NOW.plusSeconds(1)));
            forceSessionMirror(staffId, false);
            assertFalse(runtime.staffSessionStore().active(staffId).orElseThrow().vanishActive());

            assertEquals(VanishStore.WriteResult.COMMITTED, write(store, staffId, true, NOW.plusSeconds(2)));
            assertStoredState(runtime, staffId, true);
            assertEquals(1, auditCount(staffId));
            assertEquals(1, outboxCount(staffId));
        }
    }

    @Test
    void concurrentWritesKeepCanonicalAndSessionMirrorConverged() throws Exception {
        UUID staffId = identifier("vanish-atomic-concurrent-writes");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId);
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(VanishStore.WriteResult.COMMITTED, write(store, staffId, false, NOW.plusSeconds(1)));
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<VanishStore.WriteResult> enable = concurrentWrite(
                    executor, ready, start, store, staffId, true, NOW.plusSeconds(2));
            Future<VanishStore.WriteResult> disable = concurrentWrite(
                    executor, ready, start, store, staffId, false, NOW.plusSeconds(3));
            ready.await();
            start.countDown();
            enable.get();
            disable.get();

            StoredVanish canonical = storedVanish(staffId);
            boolean mirror = runtime.staffSessionStore().active(staffId).orElseThrow().vanishActive();
            assertEquals(canonical.vanished(), mirror);
        }
    }

    @Test
    void mirrorFailureSurvivesRestartAndRetryCommitsOnce() throws Exception {
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
        }

        try (MariaDbRuntime restarted = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            assertRolledBack(restarted, staffId);
            assertEquals(VanishStore.WriteResult.COMMITTED, write(
                    restarted.vanishStore(), staffId, true, NOW.plusSeconds(2)
            ));
            assertStoredState(restarted, staffId, true);
            assertEquals(VanishStore.WriteResult.UNCHANGED, write(
                    restarted.vanishStore(), staffId, true, NOW.plusSeconds(3)
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

    @Test
    void ordinaryStaffModeExitCanDisableCanonicalStateAfterSessionClosure() throws Exception {
        UUID staffId = identifier("vanish-atomic-ordinary-exit");
        try (MariaDbRuntime runtime = runtimeWithActiveSession(staffId)) {
            VanishStore store = runtime.vanishStore();
            assertEquals(VanishStore.WriteResult.COMMITTED, write(store, staffId, true, NOW.plusSeconds(1)));
            StaffSessionSnapshot exiting = runtime.staffSessionStore()
                    .beginExit(staffId, NOW.plusSeconds(2))
                    .orElseThrow();
            assertTrue(runtime.staffSessionStore().completeExit(
                    exiting.sessionId(), SNAPSHOT_CHECKSUM, NOW.plusSeconds(3)
            ));
            assertTrue(runtime.staffSessionStore().active(staffId).isEmpty());

            assertEquals(VanishStore.WriteResult.COMMITTED, write(
                    store, staffId, false, NOW.plusSeconds(4), false
            ));
            StoredVanish stored = storedVanish(staffId);
            assertFalse(stored.vanished());
            assertEquals(StaffRank.MOD, stored.rank());
            assertEquals(2, auditCount(staffId));
            assertEquals(2, outboxCount(staffId));
        }
    }

    private static Future<VanishStore.WriteResult> concurrentWrite(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            VanishStore store,
            UUID staffId,
            boolean vanished,
            Instant now
    ) {
        return executor.submit(() -> {
            ready.countDown();
            start.await();
            return write(store, staffId, vanished, now);
        });
    }

    private static VanishStore.WriteResult write(
            VanishStore store,
            UUID staffId,
            boolean vanished,
            Instant now
    ) {
        return write(store, staffId, vanished, now, true);
    }

    private static VanishStore.WriteResult write(
            VanishStore store,
            UUID staffId,
            boolean vanished,
            Instant now,
            boolean requireActiveSession
    ) {
        return store.set(staffId, StaffRank.MOD, vanished, staffId, now, requireActiveSession);
    }

    private static MariaDbRuntime runtimeWithActiveSession(UUID staffId) {
        MariaDbRuntime runtime = runtimeWithPlayer(staffId);
        runtime.staffSessionStore().begin(
                staffId,
                SERVER_ID,
                1,
                SNAPSHOT_CHECKSUM,
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
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM staff_vanish_states WHERE staff_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(staffId));
            return singleCount(statement);
        }
    }

    private static int auditCount(UUID staffId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM audit_events
                     WHERE event_type = 'VANISH_CHANGED' AND target_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(staffId));
            return singleCount(statement);
        }
    }

    private static int outboxCount(UUID staffId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM discord_outbox
                     WHERE event_type = 'VANISH_CHANGED' AND payload_json LIKE ?
                     """)) {
            statement.setString(1, "%" + staffId + "%");
            return singleCount(statement);
        }
    }

    private static int singleCount(PreparedStatement statement) throws Exception {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void forceSessionMirror(UUID staffId, boolean vanished) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE staff_sessions SET vanish_active = ?
                     WHERE staff_id = ? AND state IN ('ACTIVE', 'RECOVERY_REQUIRED')
                     """)) {
            statement.setBoolean(1, vanished);
            statement.setBytes(2, uuidBytes(staffId));
            assertEquals(1, statement.executeUpdate());
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
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
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
