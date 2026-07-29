package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.InventoryRestorationTestSupport.checksum;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.uuidBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryOperationState;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.persistence.JdbcEconomyJournalStore;
import net.enthusia.staff.persistence.JdbcInventoryJournalStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AssetJournalIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String SCOPE_ID = "survival";
    private static final String SERVER_ID = "paper-1";
    private static final String COMMIT_EVENT = "INVENTORY_OPERATION_COMMITTED";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_assets_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void inventoryCommitIsFencedIdempotentAndTerminal() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        byte[] before = {1, 2, 3, 4};
        byte[] replacement = {5, 6, 7, 8};
        String beforeChecksum = checksum(before);
        String replacementChecksum = checksum(replacement);

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertPlayer(DATABASE, targetId, "InventoryTarget", NOW);
            insertPlayer(DATABASE, actorId, "InventoryActor", NOW);
            InventoryJournalStore store = runtime.inventoryJournalStore();
            var observation = store.recordObservation(
                    targetId,
                    SCOPE_ID,
                    SERVER_ID,
                    beforeChecksum,
                    before,
                    NOW
            );
            UUID operationId = UUID.randomUUID();
            InventoryPrepareRequest request = new InventoryPrepareRequest(
                    operationId,
                    "inventory:test:" + operationId,
                    targetId,
                    SCOPE_ID,
                    SERVER_ID,
                    actorId,
                    Optional.empty(),
                    "ONLINE_EDIT",
                    observation.revision(),
                    beforeChecksum,
                    before,
                    replacementChecksum,
                    replacement,
                    java.util.List.of(4),
                    false
            );

            InventoryPreparation prepared = store.prepare(request, LEASE, NOW.plusSeconds(1));
            assertEquals(InventoryPreparation.Status.PREPARED, prepared.status());
            assertEquals(
                    InventoryPreparation.Status.REPLAYED,
                    store.prepare(request, LEASE, NOW.plusSeconds(2)).status()
            );

            InventoryPatch original = prepared.patch().orElseThrow();
            assertEquals(
                    InventoryFinalizeResult.Status.FENCE_LOST,
                    store.finalizeApplied(
                            original.patchId(),
                            operationId,
                            original.fencingToken(),
                            replacementChecksum,
                            replacement,
                            NOW.plusSeconds(3)
                    ).status()
            );
            assertEquals("PENDING", patchState(operationId));
            assertEquals("PENDING", inventoryOperationState(operationId));
            assertEquals(0L, auditCount(operationId, COMMIT_EVENT));

            InventoryPatch claimed = store.claimForApply(
                    original.patchId(),
                    operationId,
                    LEASE,
                    NOW.plusSeconds(4)
            ).orElseThrow();
            assertEquals(InventoryOperationState.APPLYING, claimed.state());
            assertTrue(claimed.fencingToken() > original.fencingToken());
            assertTrue(store.claimForApply(
                    original.patchId(),
                    operationId,
                    LEASE,
                    NOW.plusSeconds(5)
            ).isEmpty());

            assertEquals(
                    InventoryFinalizeResult.Status.FENCE_LOST,
                    store.finalizeApplied(
                            original.patchId(),
                            operationId,
                            original.fencingToken(),
                            replacementChecksum,
                            replacement,
                            NOW.plusSeconds(6)
                    ).status()
            );
            InventoryFinalizeResult committed = store.finalizeApplied(
                    claimed.patchId(),
                    operationId,
                    claimed.fencingToken(),
                    replacementChecksum,
                    replacement,
                    NOW.plusSeconds(7)
            );
            assertEquals(InventoryFinalizeResult.Status.COMMITTED, committed.status());
            assertEquals(
                    InventoryOperationState.APPLIED,
                    store.claimForApply(
                            claimed.patchId(),
                            operationId,
                            LEASE,
                            NOW.plusSeconds(8)
                    ).orElseThrow().state()
            );

            store.quarantine(
                    claimed.patchId(),
                    operationId,
                    claimed.fencingToken(),
                    "LATE_DUPLICATE",
                    "A terminal patch must not be quarantined by a late callback",
                    NOW.plusSeconds(9)
            );
            assertEquals(
                    InventoryFinalizeResult.Status.REPLAYED,
                    store.finalizeApplied(
                            claimed.patchId(),
                            operationId,
                            claimed.fencingToken(),
                            replacementChecksum,
                            replacement,
                            NOW.plusSeconds(10)
                    ).status()
            );
            assertEquals("APPLIED", patchState(operationId));
            assertEquals("COMMITTED", inventoryOperationState(operationId));
            assertEquals(1L, auditCount(operationId, COMMIT_EVENT));
            assertEquals(0L, quarantineCount(operationId, "INVENTORY"));
        }
    }

    @Test
    void inventoryFinalizationFailsClosedWhenPairedJournalRowsDiverge() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        byte[] before = {51, 52, 53};
        byte[] replacement = {61, 62, 63};

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertPlayer(DATABASE, targetId, "DivergenceTarget", NOW);
            insertPlayer(DATABASE, actorId, "DivergenceActor", NOW);
            InventoryJournalStore store = runtime.inventoryJournalStore();
            var observation = store.recordObservation(
                    targetId,
                    SCOPE_ID,
                    SERVER_ID,
                    checksum(before),
                    before,
                    NOW
            );
            UUID operationId = UUID.randomUUID();
            InventoryPreparation prepared = store.prepare(
                    inventoryRequest(operationId, targetId, actorId, observation.revision(), before, replacement),
                    LEASE,
                    NOW.plusSeconds(1)
            );
            InventoryPatch claimed = store.claimForApply(
                    prepared.patch().orElseThrow().patchId(),
                    operationId,
                    LEASE,
                    NOW.plusSeconds(2)
            ).orElseThrow();

            setInventoryOperationState(operationId, "COMMITTED");

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> store.finalizeApplied(
                            claimed.patchId(),
                            operationId,
                            claimed.fencingToken(),
                            checksum(replacement),
                            replacement,
                            NOW.plusSeconds(3)
                    )
            );
            assertEquals("APPLYING", patchState(operationId));
            assertEquals("COMMITTED", inventoryOperationState(operationId));
            assertEquals(checksum(before), observationChecksum(targetId, SCOPE_ID));
            assertEquals(0L, auditCount(operationId, COMMIT_EVENT));
        }
    }

    @Test
    void quarantinedInventoryPatchRemainsARecoveryLock() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        byte[] before = {71, 72, 73};
        byte[] replacement = {81, 82, 83};

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertPlayer(DATABASE, targetId, "QuarantineTarget", NOW);
            insertPlayer(DATABASE, actorId, "QuarantineActor", NOW);
            InventoryJournalStore store = runtime.inventoryJournalStore();
            var observation = store.recordObservation(
                    targetId,
                    SCOPE_ID,
                    SERVER_ID,
                    checksum(before),
                    before,
                    NOW
            );
            UUID operationId = UUID.randomUUID();
            InventoryPatch patch = store.prepare(
                    inventoryRequest(operationId, targetId, actorId, observation.revision(), before, replacement),
                    LEASE,
                    NOW.plusSeconds(1)
            ).patch().orElseThrow();

            store.quarantine(
                    patch.patchId(),
                    operationId,
                    patch.fencingToken(),
                    "MANUAL_RECOVERY_REQUIRED",
                    "The durable patch requires operator review",
                    NOW.plusSeconds(2)
            );

            assertEquals("QUARANTINED", patchState(operationId));
            assertEquals("QUARANTINED", inventoryOperationState(operationId));
            assertEquals(1L, quarantineCount(operationId, "INVENTORY"));
            assertEquals(0L, leaseCount(targetId, SCOPE_ID));
            assertTrue(store.isLocked(targetId, SCOPE_ID, NOW.plusSeconds(3)));
            assertNextInventoryPrepareIsLocked(
                    store,
                    targetId,
                    actorId,
                    observation.revision(),
                    before,
                    replacement
            );
        }
    }

    @Test
    void inventoryRecoveryFinalizesAnAlreadyObservedReplacementOnce() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        byte[] before = {10, 11, 12};
        byte[] replacement = {20, 21, 22};

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertPlayer(DATABASE, targetId, "RecoveryTarget", NOW);
            insertPlayer(DATABASE, actorId, "RecoveryActor", NOW);
            InventoryJournalStore store = runtime.inventoryJournalStore();
            var observation = store.recordObservation(
                    targetId,
                    SCOPE_ID,
                    SERVER_ID,
                    checksum(before),
                    before,
                    NOW
            );
            UUID operationId = UUID.randomUUID();
            InventoryPreparation prepared = store.prepare(
                    inventoryRequest(operationId, targetId, actorId, observation.revision(), before, replacement),
                    LEASE,
                    NOW.plusSeconds(1)
            );
            InventoryPatch claimed = store.claimForApply(
                    prepared.patch().orElseThrow().patchId(),
                    operationId,
                    LEASE,
                    NOW.plusSeconds(2)
            ).orElseThrow();

            var alreadyObserved = store.recordObservation(
                    targetId,
                    SCOPE_ID,
                    SERVER_ID,
                    checksum(replacement),
                    replacement,
                    NOW.plusSeconds(3)
            );
            InventoryFinalizeResult recovered = store.finalizeApplied(
                    claimed.patchId(),
                    operationId,
                    claimed.fencingToken(),
                    checksum(replacement),
                    replacement,
                    NOW.plusSeconds(4)
            );

            assertEquals(InventoryFinalizeResult.Status.REPLAYED, recovered.status());
            assertEquals(alreadyObserved.revision(), recovered.resultingRevision());
            assertEquals("APPLIED", patchState(operationId));
            assertEquals(1L, auditCount(operationId, COMMIT_EVENT));
        }
    }

    @Test
    void economyJournalRejectsLiveReclaimAndCommitsExactEvidenceOnce() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String caseId = "01J0000000000001";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertPlayer(DATABASE, targetId, "EconomyTarget", NOW);
            insertPlayer(DATABASE, actorId, "EconomyActor", NOW);
            insertCase(DATABASE, caseId, targetId, actorId, NOW);
            EconomyJournalStore store = runtime.economyJournalStore();
            UUID operationId = UUID.randomUUID();
            EconomyPrepareRequest request = new EconomyPrepareRequest(
                    operationId,
                    "economy:test:" + operationId,
                    caseId,
                    targetId,
                    actorId,
                    EconomyAmountMode.CUSTOM,
                    OptionalLong.of(25L),
                    SERVER_ID,
                    NOW
            );
            EconomyPreparation prepared = store.prepare(request, LEASE, NOW.plusSeconds(1));
            EconomyOperation operation = prepared.operation().orElseThrow();
            assertEquals(EconomyPreparation.Status.PREPARED, prepared.status());
            assertEquals(
                    EconomyPreparation.Status.REPLAYED,
                    store.prepare(request, LEASE, NOW.plusSeconds(2)).status()
            );
            assertTrue(store.reclaim(operationId, LEASE, NOW.plusSeconds(3)).isEmpty());

            EconomyValidatedPlan plan = new EconomyValidatedPlan(
                    100L,
                    25L,
                    "a".repeat(64),
                    "b".repeat(64),
                    "{\"total\":100}",
                    "{\"remove\":25}"
            );
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    store.saveValidatedPlan(
                            operationId,
                            operation.fencingToken(),
                            plan,
                            NOW.plusSeconds(4)
                    ).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.REPLAYED,
                    store.saveValidatedPlan(
                            operationId,
                            operation.fencingToken(),
                            plan,
                            NOW.plusSeconds(5)
                    ).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    store.markApplying(operationId, operation.fencingToken(), NOW.plusSeconds(6)).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.REPLAYED,
                    store.markApplying(operationId, operation.fencingToken(), NOW.plusSeconds(7)).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.FENCE_LOST,
                    store.markApplying(operationId, operation.fencingToken() + 1L, NOW.plusSeconds(8)).status()
            );

            EconomyTerminalUpdate committed = EconomyTerminalUpdate.committed(
                    75L,
                    plan.replacementChecksum(),
                    "{\"total\":75}"
            );
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    store.finish(
                            operationId,
                            operation.fencingToken(),
                            committed,
                            NOW.plusSeconds(9)
                    ).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.REPLAYED,
                    store.finish(
                            operationId,
                            operation.fencingToken(),
                            committed,
                            NOW.plusSeconds(10)
                    ).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.INVALID_STATE,
                    store.finish(
                            operationId,
                            operation.fencingToken(),
                            EconomyTerminalUpdate.committed(75L, plan.replacementChecksum(), "{\"different\":true}"),
                            NOW.plusSeconds(11)
                    ).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.UPDATED,
                    store.release(operationId, operation.fencingToken(), NOW.plusSeconds(12)).status()
            );
            assertEquals(
                    EconomyJournalResult.Status.REPLAYED,
                    store.release(operationId, operation.fencingToken(), NOW.plusSeconds(13)).status()
            );
            assertFalse(store.find(operationId).orElseThrow().unresolved());
            assertEquals(EconomyOperationState.UNLOCKED, store.find(operationId).orElseThrow().state());
            assertEquals(1L, auditCount(operationId, "ECONOMY_OPERATION_COMMITTED"));
            assertEquals(1L, auditCount(operationId, "ECONOMY_OPERATION_UNLOCKED"));
        }
    }

    @Test
    void unexpectedErrorsRollBackAssetLeasesAndInvalidSnapshotsNeverReachStorage() throws SQLException {
        UUID inventoryTarget = UUID.randomUUID();
        UUID economyTarget = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String caseId = "01J0000000000002";
        byte[] before = {31, 32};
        byte[] replacement = {41, 42};

        try (MariaDbRuntime migrationRuntime = MariaDb.initialize(databaseConfig(DATABASE))) {
            assertTrue(migrationRuntime.inventoryJournalStore() != null);
        }
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig(DATABASE))) {
            insertPlayer(DATABASE, inventoryTarget, "ErrorInventory", NOW);
            insertPlayer(DATABASE, economyTarget, "ErrorEconomy", NOW);
            insertPlayer(DATABASE, actorId, "ErrorActor", NOW);
            insertCase(DATABASE, caseId, economyTarget, actorId, NOW);
            InventoryJournalStore normalInventory = new JdbcInventoryJournalStore(dataSource, new ObjectMapper());
            var observation = normalInventory.recordObservation(
                    inventoryTarget,
                    SCOPE_ID,
                    SERVER_ID,
                    checksum(before),
                    before,
                    NOW
            );
            UUID rejectedId = UUID.randomUUID();
            InventoryPrepareRequest rejected = inventoryRequest(
                    rejectedId,
                    inventoryTarget,
                    actorId,
                    observation.revision(),
                    before,
                    replacement
            );
            assertThrows(
                    AssertionError.class,
                    () -> new JdbcInventoryJournalStore(dataSource, failingJson())
                            .prepare(rejected, LEASE, NOW.plusSeconds(1))
            );
            UUID acceptedId = UUID.randomUUID();
            assertEquals(
                    InventoryPreparation.Status.PREPARED,
                    normalInventory.prepare(
                            inventoryRequest(
                                    acceptedId,
                                    inventoryTarget,
                                    actorId,
                                    observation.revision(),
                                    before,
                                    replacement
                            ),
                            LEASE,
                            NOW.plusSeconds(2)
                    ).status()
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> normalInventory.recordObservation(
                            UUID.randomUUID(),
                            SCOPE_ID,
                            SERVER_ID,
                            checksum(before),
                            replacement,
                            NOW
                    )
            );

            EconomyJournalStore brokenEconomy = new JdbcEconomyJournalStore(dataSource, failingJson());
            EconomyPrepareRequest brokenRequest = economyRequest(
                    UUID.randomUUID(), caseId, economyTarget, actorId
            );
            assertThrows(
                    AssertionError.class,
                    () -> brokenEconomy.prepare(brokenRequest, LEASE, NOW.plusSeconds(3))
            );
            EconomyPrepareRequest acceptedRequest = economyRequest(
                    UUID.randomUUID(), caseId, economyTarget, actorId
            );
            assertEquals(
                    EconomyPreparation.Status.PREPARED,
                    new JdbcEconomyJournalStore(dataSource, new ObjectMapper())
                            .prepare(acceptedRequest, LEASE, NOW.plusSeconds(4)).status()
            );
        }
    }

    private static InventoryPrepareRequest inventoryRequest(
            UUID operationId,
            UUID targetId,
            UUID actorId,
            long revision,
            byte[] before,
            byte[] replacement
    ) {
        return new InventoryPrepareRequest(
                operationId,
                "inventory:test:" + operationId,
                targetId,
                SCOPE_ID,
                SERVER_ID,
                actorId,
                Optional.empty(),
                "ONLINE_EDIT",
                revision,
                checksum(before),
                before,
                checksum(replacement),
                replacement,
                java.util.List.of(1),
                false
        );
    }

    private static void assertNextInventoryPrepareIsLocked(
            InventoryJournalStore store,
            UUID targetId,
            UUID actorId,
            long revision,
            byte[] before,
            byte[] replacement
    ) throws SQLException {
        UUID blockedOperationId = UUID.randomUUID();
        assertEquals(
                InventoryPreparation.Status.LOCKED,
                store.prepare(
                        inventoryRequest(
                                blockedOperationId,
                                targetId,
                                actorId,
                                revision,
                                before,
                                replacement
                        ),
                        LEASE,
                        NOW.plusSeconds(4)
                ).status()
        );
        assertEquals(0L, inventoryOperationCount(blockedOperationId));
    }

    private static EconomyPrepareRequest economyRequest(
            UUID operationId,
            String caseId,
            UUID targetId,
            UUID actorId
    ) {
        return new EconomyPrepareRequest(
                operationId,
                "economy:test:" + operationId,
                caseId,
                targetId,
                actorId,
                EconomyAmountMode.ALL,
                OptionalLong.empty(),
                SERVER_ID,
                NOW
        );
    }

    private static ObjectMapper failingJson() {
        return new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new AssertionError("simulated process failure while journaling");
            }
        };
    }

    private static String patchState(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM inventory_pending_patches WHERE operation_id = ?
                     """)) {
            return singleString(statement, operationId);
        }
    }

    private static String inventoryOperationState(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM inventory_operations WHERE operation_id = ?
                     """)) {
            return singleString(statement, operationId);
        }
    }

    private static void setInventoryOperationState(UUID operationId, String state) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE inventory_operations SET state = ? WHERE operation_id = ?
                     """)) {
            statement.setString(1, state);
            statement.setBytes(2, uuidBytes(operationId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String observationChecksum(UUID playerId, String scopeId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT o.checksum
                     FROM inventory_observations o
                     JOIN inventory_profiles p ON p.profile_id = o.profile_id
                     WHERE p.player_id = ? AND p.scope_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(playerId));
            statement.setString(2, scopeId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static long inventoryOperationCount(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM inventory_operations WHERE operation_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static long leaseCount(UUID playerId, String scopeId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM operation_leases WHERE resource_key = ?
                     """)) {
            statement.setString(1, "inventory:" + playerId + ':' + scopeId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static String singleString(PreparedStatement statement, UUID operationId) throws SQLException {
        statement.setBytes(1, uuidBytes(operationId));
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static long auditCount(UUID operationId, String eventType) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM audit_events
                     WHERE correlation_id = ? AND event_type = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            statement.setString(2, eventType);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static long quarantineCount(UUID operationId, String operationType) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM recovery_quarantine
                     WHERE operation_id = ? AND operation_type = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            statement.setString(2, operationType);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

}
