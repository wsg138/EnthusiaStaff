package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.domain.economy.EconomyAmountMode;
import net.enthusia.staff.domain.economy.EconomyJournalResult;
import net.enthusia.staff.domain.economy.EconomyOperation;
import net.enthusia.staff.domain.economy.EconomyOperationState;
import net.enthusia.staff.domain.economy.EconomyPreparation;
import net.enthusia.staff.domain.economy.EconomyPrepareRequest;
import net.enthusia.staff.domain.economy.EconomyTerminalUpdate;
import net.enthusia.staff.domain.economy.EconomyValidatedPlan;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EconomyRollbackIntegrityIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final long BEFORE_TOTAL = 100L;
    private static final long REQUESTED_AMOUNT = 25L;
    private static final String BEFORE_CHECKSUM = "a".repeat(64);
    private static final String REPLACEMENT_CHECKSUM = "b".repeat(64);
    private static final String WRONG_CHECKSUM = "c".repeat(64);
    private static final String BEFORE_SNAPSHOT = "{\"total\":100,\"state\":\"before\"}";
    private static final String PLAN_JSON = "{\"remove\":25}";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_economy_rollback_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void verifiedRollbackRequiresASavedBeforeState() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String caseId = "01J0000000000003";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertFixtures(caseId, targetId, actorId);
            EconomyJournalStore store = runtime.economyJournalStore();
            EconomyOperation operation = prepare(store, caseId, targetId, actorId);
            EconomyTerminalUpdate verified = verifiedRollback(BEFORE_CHECKSUM);

            assertEquals(
                    EconomyJournalResult.Status.INVALID_STATE,
                    store.finish(
                            operation.operationId(),
                            operation.fencingToken(),
                            verified,
                            NOW.plusSeconds(2)
                    ).status()
            );
            EconomyTerminalUpdate unapplied = unappliedRollback();
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    store.finish(
                            operation.operationId(),
                            operation.fencingToken(),
                            unapplied,
                            NOW.plusSeconds(3)
                    ).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.REPLAYED,
                    store.finish(
                            operation.operationId(),
                            operation.fencingToken(),
                            unapplied,
                            NOW.plusSeconds(4)
                    ).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    store.release(operation.operationId(), operation.fencingToken(), NOW.plusSeconds(5)).status()
            );
            assertEquals(
                    EconomyOperationState.UNLOCKED,
                    store.find(operation.operationId()).orElseThrow().state()
            );
        }
    }

    @Test
    void verifiedRollbackRequiresTheExactBeforeChecksum() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String caseId = "01J0000000000004";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertFixtures(caseId, targetId, actorId);
            EconomyJournalStore store = runtime.economyJournalStore();
            EconomyOperation operation = prepareApplying(store, caseId, targetId, actorId);

            assertEquals(
                    EconomyJournalResult.Status.INVALID_STATE,
                    finish(store, operation, unappliedRollback(), NOW.plusSeconds(4))
            );
            assertEquals(
                    EconomyJournalResult.Status.STALE,
                    finish(store, operation, verifiedRollback(WRONG_CHECKSUM), NOW.plusSeconds(5))
            );
            EconomyOperation unchanged = store.find(operation.operationId()).orElseThrow();
            assertEquals(EconomyOperationState.APPLYING, unchanged.state());
            assertTrue(unchanged.terminalOutcome().isEmpty());

            EconomyTerminalUpdate exact = verifiedRollback(BEFORE_CHECKSUM);
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    finish(store, operation, exact, NOW.plusSeconds(6))
            );
            assertEquals(
                    EconomyJournalResult.Status.REPLAYED,
                    finish(store, operation, exact, NOW.plusSeconds(7))
            );
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    store.release(operation.operationId(), operation.fencingToken(), NOW.plusSeconds(8)).status()
            );
            assertEquals(
                    EconomyOperationState.UNLOCKED,
                    store.find(operation.operationId()).orElseThrow().state()
            );
        }
    }

    @Test
    void unappliedRollbackRejectsPartialDurablePlanEvidence() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String caseId = "01J0000000000005";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertFixtures(caseId, targetId, actorId);
            EconomyJournalStore store = runtime.economyJournalStore();
            EconomyOperation operation = prepare(store, caseId, targetId, actorId);
            savePartialBeforeEvidence(operation.operationId());

            assertEquals(
                    EconomyJournalResult.Status.INVALID_STATE,
                    store.finish(
                            operation.operationId(),
                            operation.fencingToken(),
                            unappliedRollback(),
                            NOW.plusSeconds(2)
                    ).status()
            );
            EconomyOperation unchanged = store.find(operation.operationId()).orElseThrow();
            assertEquals(EconomyOperationState.LOCKED, unchanged.state());
            assertTrue(unchanged.terminalOutcome().isEmpty());
        }
    }

    private static EconomyOperation prepare(
            EconomyJournalStore store,
            String caseId,
            UUID targetId,
            UUID actorId
    ) {
        UUID operationId = UUID.randomUUID();
        EconomyPreparation prepared = store.prepare(
                new EconomyPrepareRequest(
                        operationId,
                        "economy:rollback-test:" + operationId,
                        caseId,
                        targetId,
                        actorId,
                        EconomyAmountMode.CUSTOM,
                        OptionalLong.of(REQUESTED_AMOUNT),
                        "paper-1",
                        NOW
                ),
                LEASE,
                NOW.plusSeconds(1)
        );
        assertEquals(EconomyPreparation.Status.PREPARED, prepared.status());
        return prepared.operation().orElseThrow();
    }

    private static EconomyOperation prepareApplying(
            EconomyJournalStore store,
            String caseId,
            UUID targetId,
            UUID actorId
    ) {
        EconomyOperation operation = prepare(store, caseId, targetId, actorId);
        EconomyValidatedPlan plan = new EconomyValidatedPlan(
                BEFORE_TOTAL,
                REQUESTED_AMOUNT,
                BEFORE_CHECKSUM,
                REPLACEMENT_CHECKSUM,
                BEFORE_SNAPSHOT,
                PLAN_JSON
        );
        assertEquals(
                EconomyJournalResult.Status.UPDATED,
                store.saveValidatedPlan(
                        operation.operationId(),
                        operation.fencingToken(),
                        plan,
                        NOW.plusSeconds(2)
                ).status()
        );
        assertEquals(
                EconomyJournalResult.Status.UPDATED,
                store.markApplying(
                        operation.operationId(),
                        operation.fencingToken(),
                        NOW.plusSeconds(3)
                ).status()
        );
        return operation;
    }

    private static EconomyTerminalUpdate verifiedRollback(String checksum) {
        return EconomyTerminalUpdate.rolledBack(
                OptionalLong.of(BEFORE_TOTAL),
                Optional.of(checksum),
                Optional.of(BEFORE_SNAPSHOT),
                "ROLLBACK_VERIFIED",
                "Rollback restored the exact before state"
        );
    }

    private static EconomyTerminalUpdate unappliedRollback() {
        return EconomyTerminalUpdate.rolledBack(
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                "ROLLBACK_UNAPPLIED",
                "No assets changed"
        );
    }

    private static EconomyJournalResult.Status finish(
            EconomyJournalStore store,
            EconomyOperation operation,
            EconomyTerminalUpdate update,
            Instant now
    ) {
        return store.finish(
                operation.operationId(),
                operation.fencingToken(),
                update,
                now
        ).status();
    }

    private static void insertFixtures(
            String caseId,
            UUID targetId,
            UUID actorId
    ) throws SQLException {
        insertPlayer(DATABASE, targetId, "EconomyTarget", NOW);
        insertPlayer(DATABASE, actorId, "EconomyActor", NOW);
        insertCase(DATABASE, caseId, targetId, actorId, NOW);
    }

    private static void savePartialBeforeEvidence(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE economy_operations
                     SET authoritative_total = ?
                     WHERE operation_id = ?
                     """)) {
            statement.setLong(1, BEFORE_TOTAL);
            statement.setBytes(2, uuidBytes(operationId));
            assertEquals(1, statement.executeUpdate());
        }
    }
}
