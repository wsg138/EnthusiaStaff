package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.JdbcEconomyJournalStore;
import net.enthusia.staff.persistence.JdbcInventoryJournalStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AssetJournalIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);

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

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            insertPlayer(targetId, "InventoryTarget");
            insertPlayer(actorId, "InventoryActor");
            InventoryJournalStore store = runtime.inventoryJournalStore();
            var observation = store.recordObservation(
                    targetId,
                    "survival",
                    "paper-1",
                    beforeChecksum,
                    before,
                    NOW
            );
            UUID operationId = UUID.randomUUID();
            InventoryPrepareRequest request = new InventoryPrepareRequest(
                    operationId,
                    "inventory:test:" + operationId,
                    targetId,
                    "survival",
                    "paper-1",
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
            InventoryPatch claimed = store.claimForApply(
                    original.patchId(),
                    operationId,
                    LEASE,
                    NOW.plusSeconds(3)
            ).orElseThrow();
            assertEquals(InventoryOperationState.APPLYING, claimed.state());
            assertTrue(claimed.fencingToken() > original.fencingToken());
            assertTrue(store.claimForApply(
                    original.patchId(),
                    operationId,
                    LEASE,
                    NOW.plusSeconds(4)
            ).isEmpty());

            assertEquals(
                    InventoryFinalizeResult.Status.FENCE_LOST,
                    store.finalizeApplied(
                            original.patchId(),
                            operationId,
                            original.fencingToken(),
                            replacementChecksum,
                            replacement,
                            NOW.plusSeconds(5)
                    ).status()
            );
            InventoryFinalizeResult committed = store.finalizeApplied(
                    claimed.patchId(),
                    operationId,
                    claimed.fencingToken(),
                    replacementChecksum,
                    replacement,
                    NOW.plusSeconds(6)
            );
            assertEquals(InventoryFinalizeResult.Status.COMMITTED, committed.status());
            assertEquals(
                    InventoryOperationState.APPLIED,
                    store.claimForApply(
                            claimed.patchId(),
                            operationId,
                            LEASE,
                            NOW.plusSeconds(7)
                    ).orElseThrow().state()
            );

            store.quarantine(
                    claimed.patchId(),
                    operationId,
                    claimed.fencingToken(),
                    "LATE_DUPLICATE",
                    "A terminal patch must not be quarantined by a late callback",
                    NOW.plusSeconds(8)
            );
            assertEquals(
                    InventoryFinalizeResult.Status.REPLAYED,
                    store.finalizeApplied(
                            claimed.patchId(),
                            operationId,
                            claimed.fencingToken(),
                            replacementChecksum,
                            replacement,
                            NOW.plusSeconds(9)
                    ).status()
            );
            assertEquals("APPLIED", patchState(operationId));
            assertEquals("COMMITTED", inventoryOperationState(operationId));
            assertEquals(1L, auditCount(operationId, "INVENTORY_OPERATION_COMMITTED"));
            assertEquals(0L, quarantineCount(operationId, "INVENTORY"));
        }
    }

    @Test
    void inventoryRecoveryFinalizesAnAlreadyObservedReplacementOnce() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        byte[] before = {10, 11, 12};
        byte[] replacement = {20, 21, 22};

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            insertPlayer(targetId, "RecoveryTarget");
            insertPlayer(actorId, "RecoveryActor");
            InventoryJournalStore store = runtime.inventoryJournalStore();
            var observation = store.recordObservation(
                    targetId,
                    "survival",
                    "paper-1",
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
                    "survival",
                    "paper-1",
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
            assertEquals(1L, auditCount(operationId, "INVENTORY_OPERATION_COMMITTED"));
        }
    }

    @Test
    void economyJournalRejectsLiveReclaimAndCommitsExactEvidenceOnce() throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String caseId = "01J0000000000001";

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            insertPlayer(targetId, "EconomyTarget");
            insertPlayer(actorId, "EconomyActor");
            insertCase(caseId, targetId, actorId);
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
                    "paper-1",
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

        try (MariaDbRuntime migrationRuntime = MariaDb.initialize(databaseConfig())) {
            assertTrue(migrationRuntime.inventoryJournalStore() != null);
        }
        try (HikariDataSource dataSource = MariaDb.open(databaseConfig())) {
            insertPlayer(inventoryTarget, "ErrorInventory");
            insertPlayer(economyTarget, "ErrorEconomy");
            insertPlayer(actorId, "ErrorActor");
            insertCase(caseId, economyTarget, actorId);
            InventoryJournalStore normalInventory = new JdbcInventoryJournalStore(dataSource, new ObjectMapper());
            var observation = normalInventory.recordObservation(
                    inventoryTarget,
                    "survival",
                    "paper-1",
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
                            "survival",
                            "paper-1",
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
                "survival",
                "paper-1",
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
                "paper-1",
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

    private static void insertPlayer(UUID playerId, String username) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO players(
                         player_id, current_username, lowercase_username, platform,
                         first_seen_at, last_seen_at
                     ) VALUES (?, ?, ?, 'JAVA', ?, ?)
                     """)) {
            statement.setBytes(1, uuidBytes(playerId));
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(java.util.Locale.ROOT));
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.setTimestamp(5, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static void insertCase(String caseId, UUID targetId, UUID actorId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO cases(
                         case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                         public_reason, exact_reason_id, sanction_family, internal_explanation,
                         configuration_version, visibility, state, issued_at
                     ) VALUES (?, ?, ?, ?, 'P2wn', 'OWNER', 'Integration test', 'integration.test',
                         'TEST', 'Asset journal verification', 'integration-test-v1',
                         'PRIVATE', 'OPEN', ?)
                     """)) {
            statement.setString(1, caseId);
            statement.setString(2, "case:test:" + caseId);
            statement.setBytes(3, uuidBytes(targetId));
            statement.setBytes(4, uuidBytes(actorId));
            statement.setTimestamp(5, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static String patchState(UUID operationId) throws SQLException {
        return singleString(
                "SELECT state FROM inventory_pending_patches WHERE operation_id = ?",
                operationId
        );
    }

    private static String inventoryOperationState(UUID operationId) throws SQLException {
        return singleString(
                "SELECT state FROM inventory_operations WHERE operation_id = ?",
                operationId
        );
    }

    private static String singleString(String sql, UUID operationId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static long auditCount(UUID operationId, String eventType) throws SQLException {
        try (Connection connection = connection();
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
        try (Connection connection = connection();
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

    private static String checksum(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(),
                DATABASE.getPassword(),
                4,
                5_000
        );
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }
}
