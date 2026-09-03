package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.ports.CheatTesterJournalStore;
import net.enthusia.staff.domain.tester.CheatTesterJournalRecord;
import net.enthusia.staff.domain.tester.CheatTesterJournalStart;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;
import net.enthusia.staff.domain.tester.CheatTesterType;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CheatTesterJournalIntegrationTest {
    private static final String SMP_SERVER = "SMP";
    private static final String HUB_SERVER = "HUB";
    private static final Instant NOW = Instant.parse("2026-08-07T17:00:00Z");
    private static final UUID STAFF = UUID.fromString("9a000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("9a000000-0000-0000-0000-000000000002");
    private static final UUID SESSION = UUID.fromString("9a000000-0000-0000-0000-000000000003");
    private static final UUID SECOND_SESSION = UUID.fromString("9a000000-0000-0000-0000-000000000004");

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_cheat_tester_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void journalGloballyFencesTargetSurvivesRestartAndAuditsTerminalState() throws Exception {
        createActiveSessionAndCheckpointEvidence();
        recoverCompleteAndReuseTarget();
        assertEquals(4, testerAuditCount());
        assertTrue(latestMigrationVersion() >= 18,
                "the V18 cheat-tester journal migration must remain applied as later migrations are added");
    }

    private static void createActiveSessionAndCheckpointEvidence() throws Exception {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            MariaDbIntegrationSupport.insertPlayer(DATABASE, STAFF, "TesterStaff", NOW);
            MariaDbIntegrationSupport.insertPlayer(DATABASE, TARGET, "TesterTarget", NOW);
            CheatTesterJournalStore store = runtime.cheatTesterJournalStore();
            CheatTesterJournalRecord first = store.start(start(SESSION, SMP_SERVER, CheatTesterType.AUTO_ARMOR));
            assertInitialSession(runtime, store, first);
            assertGlobalDuplicateFence(store);
            CheatTesterJournalRecord checkpoint = store.checkpointEvidence(
                    SESSION,
                    first.revision(),
                    "{\"observed\":true}",
                    NOW.plusSeconds(1)
            ).orElseThrow();
            assertEquals(1L, checkpoint.revision());
            assertEquals("{\"observed\":true}", checkpoint.evidence());
        }
    }

    private static void assertInitialSession(
            MariaDbRuntime runtime,
            CheatTesterJournalStore store,
            CheatTesterJournalRecord first
    ) {
        assertEquals(SESSION, first.sessionId());
        assertEquals(0L, first.revision());
        assertTrue(first.active());
        assertEquals(first, store.activeForTarget(TARGET).orElseThrow());
        assertTrue(runtime.inventoryJournalStore().isLocked(TARGET, SMP_SERVER, NOW));
        assertEquals(SMP_SERVER, runtime.inventoryJournalStore().lockedOwningServer(TARGET, NOW).orElseThrow());
    }

    private static void assertGlobalDuplicateFence(CheatTesterJournalStore store) {
        CheatTesterJournalRecord duplicate = store.start(start(SECOND_SESSION, HUB_SERVER, CheatTesterType.NO_FALL));
        assertEquals(SESSION, duplicate.sessionId(),
                "another backend must receive the globally authoritative active row");
        assertEquals(SMP_SERVER, duplicate.serverId());
        assertNotEquals(SECOND_SESSION, duplicate.sessionId());
        assertFalse(store.activeForTarget(HUB_SERVER, TARGET).isPresent());
    }

    private static void recoverCompleteAndReuseTarget() {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            CheatTesterJournalStore store = runtime.cheatTesterJournalStore();
            CheatTesterJournalRecord recovered = store.activeForTarget(SMP_SERVER, TARGET).orElseThrow();
            assertRecoveredLock(runtime, store, recovered);
            assertTrue(store.complete(
                    SESSION,
                    recovered.revision(),
                    CheatTesterSessionState.RESTORED,
                    "exact state restored",
                    recovered.evidence(),
                    NOW.plusSeconds(2)
            ));
            assertFalse(store.activeForTarget(TARGET).isPresent());
            assertFalse(runtime.inventoryJournalStore().isLocked(TARGET, SMP_SERVER, NOW.plusSeconds(2)));
            completeSecondSession(store);
        }
    }

    private static void assertRecoveredLock(
            MariaDbRuntime runtime,
            CheatTesterJournalStore store,
            CheatTesterJournalRecord recovered
    ) {
        assertEquals(SESSION, recovered.sessionId());
        assertEquals(1L, recovered.revision());
        assertEquals(1, store.activeForServer(SMP_SERVER, 10).size());
        assertTrue(runtime.inventoryJournalStore().isLocked(TARGET, HUB_SERVER, NOW.plusSeconds(2)),
                "the durable tester row must block offline asset work regardless of requested scope");
    }

    private static void completeSecondSession(CheatTesterJournalStore store) {
        CheatTesterJournalRecord second = store.start(start(SECOND_SESSION, HUB_SERVER, CheatTesterType.FAKE_ENTITY));
        assertEquals(SECOND_SESSION, second.sessionId(), "terminal rows must release active-target uniqueness");
        assertEquals(HUB_SERVER, second.serverId());
        assertTrue(store.complete(
                SECOND_SESSION,
                second.revision(),
                CheatTesterSessionState.CANCELLED,
                "fake entity removed",
                "{\"attacks\":0}",
                NOW.plusSeconds(3)
        ));
    }

    private static CheatTesterJournalStart start(UUID sessionId, String serverId, CheatTesterType type) {
        return new CheatTesterJournalStart(
                sessionId,
                serverId,
                STAFF,
                TARGET,
                type,
                "{\"snapshot\":\"opaque\"}",
                "{\"tester\":\"" + type.id() + "\"}",
                NOW,
                NOW.plus(Duration.ofSeconds(10))
        );
    }

    private static int testerAuditCount() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM audit_events
                     WHERE event_type LIKE 'CHEAT_TESTER_%'
                     """)) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int latestMigrationVersion() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1
                     """)) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
