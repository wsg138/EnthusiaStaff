package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionOwnership;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StaffSessionShutdownRecoveryIntegrationTest extends PunishmentRequestMariaDbSupport {
    private static final String SCOPED_SERVER = "smp_shutdown_scope";
    private static final String OTHER_SERVER = "hub_shutdown_scope";
    private static final String ROLLBACK_SERVER = "smp_shutdown_rollback";
    private static final String SHUTDOWN_REASON =
            "Paper runtime disabled before normal staff-mode exit";

    @Test
    void detachedNetworkSessionRebindsWithDestinationLocalSnapshot() {
        UUID staffId = identifier("staff-detach-rebind");
        byte[] destinationSnapshot = new byte[]{9};
        String destinationChecksum = "9".repeat(64);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot source = begin(runtime, staffId, OTHER_SERVER, 7);

            StaffSessionSnapshot detached = store.detach(
                    staffId,
                    source.sessionId(),
                    source.revision(),
                    OTHER_SERVER,
                    source.checksum(),
                    NOW.plusSeconds(1)
            ).orElseThrow();

            assertEquals(source.sessionId(), detached.sessionId());
            assertEquals(StaffSessionOwnership.DETACHED_SERVER_ID, detached.serverId());
            assertEquals(StaffSessionState.ACTIVE, detached.state());

            StaffSessionSnapshot rebound = store.begin(
                    staffId,
                    SCOPED_SERVER,
                    1,
                    destinationChecksum,
                    destinationSnapshot,
                    NOW.plusSeconds(2)
            );

            assertEquals(source.sessionId(), rebound.sessionId());
            assertEquals(SCOPED_SERVER, rebound.serverId());
            assertEquals(StaffSessionState.ACTIVE, rebound.state());
            assertEquals(destinationChecksum, rebound.checksum());
            assertEquals(1, rebound.schemaVersion());
            assertTrue(java.util.Arrays.equals(destinationSnapshot, rebound.snapshot()));
        }
    }

    @Test
    void detachAllowsUnrelatedVanishRevisionBump() {
        UUID staffId = identifier("staff-detach-vanish-revision");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot source = begin(runtime, staffId, OTHER_SERVER, 5);

            assertTrue(store.setVanish(staffId, true, NOW.plusSeconds(1)));
            assertTrue(store.active(staffId).orElseThrow().revision() > source.revision());

            StaffSessionSnapshot detached = store.detach(
                    staffId,
                    source.sessionId(),
                    source.revision(),
                    OTHER_SERVER,
                    source.checksum(),
                    NOW.plusSeconds(2)
            ).orElseThrow();

            assertEquals(StaffSessionOwnership.DETACHED_SERVER_ID, detached.serverId());
            assertEquals(StaffSessionState.ACTIVE, detached.state());
        }
    }

    @Test
    void detachRejectsWrongBackendWithoutChangingOwner() {
        UUID staffId = identifier("staff-detach-owner-fence");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot source = begin(runtime, staffId, OTHER_SERVER, 6);

            assertTrue(store.detach(
                    staffId,
                    source.sessionId(),
                    source.revision(),
                    SCOPED_SERVER,
                    source.checksum(),
                    NOW.plusSeconds(1)
            ).isEmpty());

            StaffSessionSnapshot remaining = store.active(staffId).orElseThrow();
            assertEquals(OTHER_SERVER, remaining.serverId());
            assertEquals(source.sessionId(), remaining.sessionId());
            assertEquals(StaffSessionState.ACTIVE, remaining.state());
        }
    }

    @Test
    void beginRejectsSnapshotOwnedByAnotherBackend() {
        UUID staffId = identifier("staff-cross-backend-ownership");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot source = begin(runtime, staffId, OTHER_SERVER, 7);

            assertThrows(ModerationPersistenceException.class, () -> store.begin(
                    staffId,
                    SCOPED_SERVER,
                    1,
                    "8".repeat(64),
                    new byte[]{8},
                    NOW.plusSeconds(1)
            ));

            StaffSessionSnapshot remaining = store.active(staffId).orElseThrow();
            assertEquals(source.sessionId(), remaining.sessionId());
            assertEquals(OTHER_SERVER, remaining.serverId());
            assertEquals(StaffSessionState.ACTIVE, remaining.state());
        }
    }

    @Test
    void cleanShutdownPreservesActiveNetworkDutyAndMarksOnlyExitsForRecovery() throws Exception {
        UUID activeStaff = identifier("staff-shutdown-active");
        UUID exitingStaff = identifier("staff-shutdown-exiting");
        UUID existingRecoveryStaff = identifier("staff-shutdown-existing-recovery");
        UUID otherServerStaff = identifier("staff-shutdown-other-server");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot active = begin(runtime, activeStaff, SCOPED_SERVER, 1);
            StaffSessionSnapshot exiting = begin(runtime, exitingStaff, SCOPED_SERVER, 2);
            store.beginExit(exitingStaff, NOW.plusSeconds(1)).orElseThrow();
            StaffSessionSnapshot existingRecovery = begin(runtime, existingRecoveryStaff, SCOPED_SERVER, 3);
            store.recoveryRequired(
                    existingRecovery.sessionId(),
                    "Pre-existing recovery condition",
                    NOW.plusSeconds(2)
            );
            begin(runtime, otherServerStaff, OTHER_SERVER, 4);

            assertEquals(1, store.recoveryRequiredForServer(
                    SCOPED_SERVER,
                    SHUTDOWN_REASON,
                    NOW.plusSeconds(3)
            ));

            assertEquals(StaffSessionState.ACTIVE, store.active(activeStaff).orElseThrow().state());
            assertEquals(StaffSessionState.RECOVERY_REQUIRED, store.active(exitingStaff).orElseThrow().state());
            assertEquals(
                    StaffSessionState.RECOVERY_REQUIRED,
                    store.active(existingRecoveryStaff).orElseThrow().state()
            );
            assertEquals(StaffSessionState.ACTIVE, store.active(otherServerStaff).orElseThrow().state());
            assertEquals(0, recoveryAuditCount(active.sessionId()));
            assertEquals(1, recoveryAuditCount(exiting.sessionId()));
            assertEquals(1, recoveryAuditCount(existingRecovery.sessionId()));

            assertEquals(0, store.recoveryRequiredForServer(
                    SCOPED_SERVER,
                    SHUTDOWN_REASON,
                    NOW.plusSeconds(4)
            ));
            assertEquals(0, recoveryAuditCount(active.sessionId()));
            assertEquals(1, recoveryAuditCount(exiting.sessionId()));
            assertEquals(1, recoveryAuditCount(existingRecovery.sessionId()));

            StaffSessionSnapshot restoring = store.beginExit(activeStaff, NOW.plusSeconds(5)).orElseThrow();
            assertEquals(StaffSessionState.EXITING, restoring.state());
            assertTrue(store.completeExit(
                    restoring.sessionId(),
                    active.checksum(),
                    NOW.plusSeconds(6)
            ));
            assertFalse(store.active(activeStaff).isPresent());
        }
    }

    @Test
    void detachedExitingSessionCanCloseWithoutRestoringSourceSnapshotAgain() {
        UUID staffId = identifier("staff-detached-exiting-close");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot source = begin(runtime, staffId, OTHER_SERVER, 8);
            StaffSessionSnapshot detached = store.detach(
                    staffId,
                    source.sessionId(),
                    source.revision(),
                    OTHER_SERVER,
                    source.checksum(),
                    NOW.plusSeconds(1)
            ).orElseThrow();

            StaffSessionSnapshot exiting = store.beginExit(staffId, NOW.plusSeconds(2)).orElseThrow();
            assertEquals(StaffSessionOwnership.DETACHED_SERVER_ID, exiting.serverId());
            assertEquals(StaffSessionState.EXITING, exiting.state());
            exiting = store.beginDetachedExit(exiting, NOW.plusSeconds(3)).orElseThrow();
            assertTrue(store.completeExit(
                    exiting.sessionId(),
                    detached.checksum(),
                    NOW.plusSeconds(3)
            ));
            assertFalse(store.active(staffId).isPresent());
        }
    }

    @Test
    void detachedRecoveryRequiredSessionCanRetireAfterVerifiedPriorDetach() {
        UUID staffId = identifier("staff-detached-recovery-close");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot source = begin(runtime, staffId, OTHER_SERVER, 9);
            StaffSessionSnapshot detached = store.detach(
                    staffId,
                    source.sessionId(),
                    source.revision(),
                    OTHER_SERVER,
                    source.checksum(),
                    NOW.plusSeconds(1)
            ).orElseThrow();

            store.recoveryRequired(
                    detached.sessionId(),
                    "Interrupted runtime after verified backend detach",
                    NOW.plusSeconds(2)
            );
            StaffSessionSnapshot recovery = store.active(staffId).orElseThrow();
            assertEquals(StaffSessionOwnership.DETACHED_SERVER_ID, recovery.serverId());
            assertEquals(StaffSessionState.RECOVERY_REQUIRED, recovery.state());

            StaffSessionSnapshot exiting = store.beginDetachedExit(recovery, NOW.plusSeconds(3)).orElseThrow();
            assertTrue(store.completeExit(
                    exiting.sessionId(),
                    detached.checksum(),
                    NOW.plusSeconds(4)
            ));
            assertFalse(store.active(staffId).isPresent());
        }
    }

    @Test
    void detachedExitFenceRejectsWrongRevisionChecksumAndReplacementSession() {
        UUID staffId = identifier("staff-detached-stale-fence");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot source = begin(runtime, staffId, OTHER_SERVER, 10);
            StaffSessionSnapshot detached = store.detach(staffId, source.sessionId(), source.revision(),
                    OTHER_SERVER, source.checksum(), NOW.plusSeconds(1)).orElseThrow();
            store.recoveryRequired(detached.sessionId(), "Interrupted verified detach", NOW.plusSeconds(2));
            StaffSessionSnapshot recovery = store.active(staffId).orElseThrow();
            assertThrows(IllegalArgumentException.class,
                    () -> store.beginDetachedExit(detached, NOW.plusSeconds(3)));
            StaffSessionSnapshot staleRevision = new StaffSessionSnapshot(recovery.sessionId(), staffId,
                    recovery.serverId(), recovery.state(), recovery.vanishActive(), recovery.schemaVersion(),
                    recovery.checksum(), recovery.snapshot(), recovery.startedAt(), recovery.revision() - 1);
            assertTrue(store.beginDetachedExit(staleRevision, NOW.plusSeconds(3)).isEmpty());
            StaffSessionSnapshot wrongChecksum = new StaffSessionSnapshot(recovery.sessionId(), staffId,
                    recovery.serverId(), recovery.state(), recovery.vanishActive(), recovery.schemaVersion(),
                    "e".repeat(64), recovery.snapshot(), recovery.startedAt(), recovery.revision());
            assertTrue(store.beginDetachedExit(wrongChecksum, NOW.plusSeconds(3)).isEmpty());
            assertEquals(StaffSessionState.RECOVERY_REQUIRED, store.active(staffId).orElseThrow().state());
            StaffSessionSnapshot exiting = store.beginDetachedExit(recovery, NOW.plusSeconds(4)).orElseThrow();
            assertTrue(store.completeExit(exiting.sessionId(), exiting.checksum(), NOW.plusSeconds(5)));
            StaffSessionSnapshot replacement = begin(runtime, staffId, SCOPED_SERVER, 11);
            assertTrue(store.beginDetachedExit(recovery, NOW.plusSeconds(6)).isEmpty());
            StaffSessionSnapshot current = store.active(staffId).orElseThrow();
            assertEquals(replacement.sessionId(), current.sessionId());
            assertEquals(replacement.revision(), current.revision());
            assertEquals(StaffSessionState.ACTIVE, current.state());
            assertEquals(SCOPED_SERVER, current.serverId());
        }
    }

    @Test
    void auditFailureRollsBackEveryServerSessionTransition() throws Exception {
        UUID firstStaff = identifier("staff-shutdown-rollback-first");
        UUID secondStaff = identifier("staff-shutdown-rollback-second");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            begin(runtime, firstStaff, ROLLBACK_SERVER, 5);
            begin(runtime, secondStaff, ROLLBACK_SERVER, 6);
            store.beginExit(firstStaff, NOW.plusSeconds(1)).orElseThrow();
            store.beginExit(secondStaff, NOW.plusSeconds(2)).orElseThrow();
            installAuditFailureTrigger();
            try {
                assertThrows(ModerationPersistenceException.class, () ->
                        store.recoveryRequiredForServer(
                                ROLLBACK_SERVER,
                                SHUTDOWN_REASON,
                                NOW.plusSeconds(5)
                        ));
            } finally {
                dropAuditFailureTrigger();
            }

            assertEquals(StaffSessionState.EXITING, store.active(firstStaff).orElseThrow().state());
            assertEquals(StaffSessionState.EXITING, store.active(secondStaff).orElseThrow().state());
        }
    }

    private static StaffSessionSnapshot begin(
            MariaDbRuntime runtime,
            UUID staffId,
            String serverId,
            int marker
    ) {
        runtime.playerDirectory().recordSeen(
                staffId,
                "Staff" + marker,
                PlayerPlatform.JAVA,
                serverId,
                NOW
        );
        return runtime.staffSessionStore().begin(
                staffId,
                serverId,
                1,
                Integer.toHexString(marker).repeat(64).substring(0, 64),
                new byte[]{(byte) marker},
                NOW
        );
    }

    private static int recoveryAuditCount(UUID sessionId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM audit_events
                     WHERE correlation_id = ? AND event_type = 'STAFF_MODE_RECOVERY_REQUIRED'
                     """)) {
            statement.setBytes(1, uuidBytes(sessionId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Expected a recovery audit count row");
                return result.getInt(1);
            }
        }
    }

    private static void installAuditFailureTrigger() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_fail_staff_shutdown_recovery");
            statement.execute("""
                    CREATE TRIGGER test_fail_staff_shutdown_recovery
                    BEFORE INSERT ON audit_events
                    FOR EACH ROW
                    BEGIN
                        IF NEW.event_type = 'STAFF_MODE_RECOVERY_REQUIRED' THEN
                            SIGNAL SQLSTATE '45000'
                                SET MESSAGE_TEXT = 'forced staff shutdown recovery rollback';
                        END IF;
                    END
                    """);
        }
    }

    private static void dropAuditFailureTrigger() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_fail_staff_shutdown_recovery");
        }
    }

    private static byte[] uuidBytes(UUID value) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }
}
