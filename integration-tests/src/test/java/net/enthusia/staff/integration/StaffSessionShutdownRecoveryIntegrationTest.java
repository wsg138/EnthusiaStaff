package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import net.enthusia.staff.domain.ports.StaffSessionStore;
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
    void marksOnlyOpenSessionsForTheStoppingServerAndIsIdempotent() throws Exception {
        UUID activeStaff = identifier("staff-shutdown-active");
        UUID exitingStaff = identifier("staff-shutdown-exiting");
        UUID existingRecoveryStaff = identifier("staff-shutdown-existing-recovery");
        UUID otherServerStaff = identifier("staff-shutdown-other-server");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            StaffSessionSnapshot active = begin(store, activeStaff, SCOPED_SERVER, 1);
            StaffSessionSnapshot exiting = begin(store, exitingStaff, SCOPED_SERVER, 2);
            store.beginExit(exitingStaff, NOW.plusSeconds(1)).orElseThrow();
            StaffSessionSnapshot existingRecovery = begin(store, existingRecoveryStaff, SCOPED_SERVER, 3);
            store.recoveryRequired(
                    existingRecovery.sessionId(),
                    "Pre-existing recovery condition",
                    NOW.plusSeconds(2)
            );
            begin(store, otherServerStaff, OTHER_SERVER, 4);

            assertEquals(2, store.recoveryRequiredForServer(
                    SCOPED_SERVER,
                    SHUTDOWN_REASON,
                    NOW.plusSeconds(3)
            ));

            assertEquals(StaffSessionState.RECOVERY_REQUIRED, store.active(activeStaff).orElseThrow().state());
            assertEquals(StaffSessionState.RECOVERY_REQUIRED, store.active(exitingStaff).orElseThrow().state());
            assertEquals(
                    StaffSessionState.RECOVERY_REQUIRED,
                    store.active(existingRecoveryStaff).orElseThrow().state()
            );
            assertEquals(StaffSessionState.ACTIVE, store.active(otherServerStaff).orElseThrow().state());
            assertEquals(1, recoveryAuditCount(active.sessionId()));
            assertEquals(1, recoveryAuditCount(exiting.sessionId()));
            assertEquals(1, recoveryAuditCount(existingRecovery.sessionId()));

            assertEquals(0, store.recoveryRequiredForServer(
                    SCOPED_SERVER,
                    SHUTDOWN_REASON,
                    NOW.plusSeconds(4)
            ));
            assertEquals(1, recoveryAuditCount(active.sessionId()));
            assertEquals(1, recoveryAuditCount(exiting.sessionId()));
            assertEquals(1, recoveryAuditCount(existingRecovery.sessionId()));

            StaffSessionSnapshot restoring = store.beginExit(activeStaff, NOW.plusSeconds(5)).orElseThrow();
            assertEquals(StaffSessionState.EXITING, restoring.state());
            assertEquals(true, store.completeExit(
                    restoring.sessionId(),
                    active.checksum(),
                    NOW.plusSeconds(6)
            ));
            assertFalse(store.active(activeStaff).isPresent());
        }
    }

    @Test
    void auditFailureRollsBackEveryServerSessionTransition() throws Exception {
        UUID firstStaff = identifier("staff-shutdown-rollback-first");
        UUID secondStaff = identifier("staff-shutdown-rollback-second");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            StaffSessionStore store = runtime.staffSessionStore();
            begin(store, firstStaff, ROLLBACK_SERVER, 5);
            begin(store, secondStaff, ROLLBACK_SERVER, 6);
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

            assertEquals(StaffSessionState.ACTIVE, store.active(firstStaff).orElseThrow().state());
            assertEquals(StaffSessionState.ACTIVE, store.active(secondStaff).orElseThrow().state());
        }
    }

    private static StaffSessionSnapshot begin(
            StaffSessionStore store,
            UUID staffId,
            String serverId,
            int marker
    ) {
        return store.begin(
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
                result.next();
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
