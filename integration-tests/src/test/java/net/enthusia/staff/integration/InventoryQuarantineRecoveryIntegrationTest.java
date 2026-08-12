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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.inventory.InventoryRecoveryResult;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InventoryQuarantineRecoveryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-11T20:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String SCOPE = "survival";
    private static final String SERVER = "paper-1";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_inventory_recovery_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void authorizedRetryIsIdempotentAndASecondQuarantineReopensEvidence() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture("01J0000000008101");
            PreparedOperation prepared = quarantinedConfiscation(runtime, fixture, SCOPE, NOW);
            long revisionBefore = profileRevision(prepared.patch().profileId());

            InventoryRecoveryResult recovered = runtime.inventoryRecoveryStore().requeueCaseAssets(
                    fixture.caseId(), fixture.founderId(), NOW.plusSeconds(10)
            );

            assertEquals(InventoryRecoveryResult.Status.REQUEUED, recovered.status());
            assertEquals(prepared.operationId(), recovered.operationId().orElseThrow());
            assertState(prepared.operationId(), "PENDING", "PENDING");
            assertTrue(quarantineResolved(prepared.operationId()));
            assertEquals(fixture.founderId(), quarantineResolver(prepared.operationId()));
            assertEquals(1L, recoveryAuditCount(prepared.operationId()));
            assertEquals(revisionBefore, profileRevision(prepared.patch().profileId()));

            InventoryRecoveryResult replay = runtime.inventoryRecoveryStore().requeueCaseAssets(
                    fixture.caseId(), fixture.founderId(), NOW.plusSeconds(11)
            );
            assertEquals(InventoryRecoveryResult.Status.REPLAYED, replay.status());
            assertEquals(prepared.operationId(), replay.operationId().orElseThrow());
            assertEquals(1L, recoveryAuditCount(prepared.operationId()));

            InventoryPatch claimed = runtime.inventoryJournalStore().claimForApply(
                    prepared.patch().patchId(), prepared.operationId(), LEASE, NOW.plusSeconds(12)
            ).orElseThrow();
            assertTrue(claimed.fencingToken() > prepared.patch().fencingToken());
            runtime.inventoryJournalStore().quarantine(
                    claimed.patchId(),
                    claimed.operationId(),
                    claimed.fencingToken(),
                    "RETRY_STILL_AMBIGUOUS",
                    "test retry could not prove the live inventory image",
                    NOW.plusSeconds(13)
            );

            assertState(prepared.operationId(), "QUARANTINED", "QUARANTINED");
            assertFalse(quarantineResolved(prepared.operationId()));
            assertEquals("RETRY_STILL_AMBIGUOUS", quarantineReason(prepared.operationId()));

            InventoryRecoveryResult secondRecovery = runtime.inventoryRecoveryStore().requeueCaseAssets(
                    fixture.caseId(), fixture.founderId(), NOW.plusSeconds(14)
            );
            assertEquals(InventoryRecoveryResult.Status.REQUEUED, secondRecovery.status());
            assertEquals(2L, recoveryAuditCount(prepared.operationId()));
            assertTrue(quarantineResolved(prepared.operationId()));
        }
    }

    @Test
    void genericInventoryQuarantineCannotUseCaseAssetRecovery() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture("01J0000000008102");
            InventoryJournalStore store = runtime.inventoryJournalStore();
            byte[] before = {1, 2, 3};
            byte[] replacement = {4, 5, 6};
            var observation = store.recordObservation(
                    fixture.targetId(), SCOPE, SERVER, checksum(before), before, NOW
            );
            UUID operationId = UUID.randomUUID();
            InventoryPreparation preparation = store.prepare(
                    new InventoryPrepareRequest(
                            operationId,
                            "inventory:generic-recovery-test:" + operationId,
                            fixture.targetId(),
                            SCOPE,
                            SERVER,
                            fixture.actorId(),
                            Optional.of(fixture.caseId().value()),
                            "ONLINE_EDIT",
                            observation.revision(),
                            observation.checksum(),
                            observation.snapshot(),
                            checksum(replacement),
                            replacement,
                            List.of(1),
                            false
                    ),
                    LEASE,
                    NOW.plusSeconds(1)
            );
            InventoryPatch patch = preparation.patch().orElseThrow();
            store.quarantine(
                    patch.patchId(), operationId, patch.fencingToken(),
                    "GENERIC_TEST", "not an item-confiscation recovery candidate", NOW.plusSeconds(2)
            );

            InventoryRecoveryResult result = runtime.inventoryRecoveryStore().requeueCaseAssets(
                    fixture.caseId(), fixture.founderId(), NOW.plusSeconds(3)
            );

            assertEquals(InventoryRecoveryResult.Status.NOT_FOUND, result.status());
            assertState(operationId, "QUARANTINED", "QUARANTINED");
            assertFalse(quarantineResolved(operationId));
            assertEquals(0L, recoveryAuditCount(operationId));
        }
    }

    @Test
    void liveCompetingLeaseBlocksRecoveryWithoutMutation() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture("01J0000000008103");
            PreparedOperation prepared = quarantinedConfiscation(runtime, fixture, SCOPE, NOW);
            insertLiveLease(prepared.patch().playerId(), prepared.patch().scopeId(), NOW.plusSeconds(120));

            InventoryRecoveryResult result = runtime.inventoryRecoveryStore().requeueCaseAssets(
                    fixture.caseId(), fixture.founderId(), NOW.plusSeconds(10)
            );

            assertEquals(InventoryRecoveryResult.Status.AMBIGUOUS, result.status());
            assertState(prepared.operationId(), "QUARANTINED", "QUARANTINED");
            assertFalse(quarantineResolved(prepared.operationId()));
            assertEquals(0L, recoveryAuditCount(prepared.operationId()));
        }
    }

    @Test
    void divergentJournalStateRollsBackRecovery() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture("01J0000000008104");
            PreparedOperation prepared = quarantinedConfiscation(runtime, fixture, SCOPE, NOW);
            forceOperationState(prepared.operationId(), "PENDING");

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> runtime.inventoryRecoveryStore().requeueCaseAssets(
                            fixture.caseId(), fixture.founderId(), NOW.plusSeconds(10)
                    )
            );

            assertState(prepared.operationId(), "QUARANTINED", "PENDING");
            assertFalse(quarantineResolved(prepared.operationId()));
            assertEquals(0L, recoveryAuditCount(prepared.operationId()));
        }
    }

    @Test
    void caseTargetDivergenceFailsClosedInsteadOfHidingTheQuarantine() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture("01J0000000008105");
            PreparedOperation prepared = quarantinedConfiscation(runtime, fixture, SCOPE, NOW);
            UUID wrongTargetId = UUID.randomUUID();
            insertPlayer(DATABASE, wrongTargetId, username("RW", wrongTargetId), NOW);
            forceCaseTarget(fixture.caseId(), wrongTargetId);

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> runtime.inventoryRecoveryStore().requeueCaseAssets(
                            fixture.caseId(), fixture.founderId(), NOW.plusSeconds(10)
                    )
            );

            assertState(prepared.operationId(), "QUARANTINED", "QUARANTINED");
            assertFalse(quarantineResolved(prepared.operationId()));
            assertEquals(0L, recoveryAuditCount(prepared.operationId()));
        }
    }

    private static PreparedOperation quarantinedConfiscation(
            MariaDbRuntime runtime,
            Fixture fixture,
            String scope,
            Instant startedAt
    ) {
        InventoryJournalStore store = runtime.inventoryJournalStore();
        UUID operationId = UUID.randomUUID();
        byte[] before = {11, 12, 13};
        byte[] replacement = {21, 22};
        byte[] assets = {31, 32};
        InventoryConfiscationSession session = store.beginConfiscation(
                new InventoryConfiscationStartRequest(
                        operationId,
                        "inventory:quarantine-recovery:" + operationId,
                        fixture.targetId(),
                        scope,
                        SERVER,
                        fixture.actorId(),
                        fixture.caseId(),
                        checksum(before),
                        before,
                        startedAt
                ),
                LEASE,
                startedAt
        ).session().orElseThrow();
        InventoryPreparation preparation = store.prepareConfiscation(
                new InventoryConfiscationCommitRequest(
                        operationId,
                        session.fencingToken(),
                        session.expectedRevision(),
                        session.beforeChecksum(),
                        checksum(replacement),
                        replacement,
                        List.of(1),
                        checksum(assets),
                        assets,
                        List.of("1")
                ),
                startedAt.plusSeconds(1)
        );
        InventoryPatch patch = preparation.patch().orElseThrow();
        store.quarantine(
                patch.patchId(), operationId, patch.fencingToken(),
                "TEST_AMBIGUOUS", "test requires explicit owner recovery", startedAt.plusSeconds(2)
        );
        return new PreparedOperation(operationId, patch);
    }

    private static Fixture fixture(String caseId) throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        insertPlayer(DATABASE, targetId, username("RT", targetId), NOW);
        insertPlayer(DATABASE, actorId, username("RA", actorId), NOW);
        insertPlayer(DATABASE, founderId, username("RF", founderId), NOW);
        insertCase(DATABASE, caseId, targetId, actorId, NOW);
        return new Fixture(targetId, actorId, founderId, new CaseId(caseId));
    }

    private static void assertState(UUID operationId, String patchState, String operationState)
            throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT q.state AS patch_state, o.state AS operation_state
                     FROM inventory_pending_patches q
                     JOIN inventory_operations o ON o.operation_id = q.operation_id
                     WHERE q.operation_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(patchState, result.getString("patch_state"));
                assertEquals(operationState, result.getString("operation_state"));
            }
        }
    }

    private static boolean quarantineResolved(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT resolved_at
                     FROM recovery_quarantine
                     WHERE operation_type = 'INVENTORY' AND operation_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getTimestamp("resolved_at") != null;
            }
        }
    }

    private static UUID quarantineResolver(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT resolved_by
                     FROM recovery_quarantine
                     WHERE operation_type = 'INVENTORY' AND operation_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return bytesUuid(result.getBytes("resolved_by"));
            }
        }
    }

    private static String quarantineReason(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT reason_code
                     FROM recovery_quarantine
                     WHERE operation_type = 'INVENTORY' AND operation_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString("reason_code");
            }
        }
    }

    private static long recoveryAuditCount(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM audit_events
                     WHERE correlation_id = ? AND event_type = 'INVENTORY_QUARANTINE_REQUEUED'
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long profileRevision(UUID profileId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT current_revision FROM inventory_profiles WHERE profile_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(profileId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static void insertLiveLease(UUID playerId, String scopeId, Instant leaseUntil)
            throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO operation_leases(
                         resource_key, owner_id, fencing_token, lease_until, updated_at
                     ) VALUES (?, ?, 999, ?, ?)
                     """)) {
            statement.setString(1, "inventory:" + playerId + ':' + scopeId);
            statement.setString(2, UUID.randomUUID().toString());
            statement.setTimestamp(3, Timestamp.from(leaseUntil));
            statement.setTimestamp(4, Timestamp.from(NOW.plusSeconds(3)));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void forceOperationState(UUID operationId, String state) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE inventory_operations SET state = ? WHERE operation_id = ?
                     """)) {
            statement.setString(1, state);
            statement.setBytes(2, uuidBytes(operationId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void forceCaseTarget(CaseId caseId, UUID targetId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE cases SET target_id = ? WHERE case_id = ?
                     """)) {
            statement.setBytes(1, uuidBytes(targetId));
            statement.setString(2, caseId.value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static UUID bytesUuid(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static String username(String prefix, UUID playerId) {
        return prefix + playerId.toString().replace("-", "").substring(0, 10);
    }

    private record Fixture(UUID targetId, UUID actorId, UUID founderId, CaseId caseId) {
    }

    private record PreparedOperation(UUID operationId, InventoryPatch patch) {
    }
}
