package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.InventoryRestorationTestSupport.checksum;
import static net.enthusia.staff.integration.InventoryRestorationTestSupport.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.ConfiscatedAssetReservation;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStart;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryFinalizeResult;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InventoryRestorationIntegrityIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String SCOPE = "survival";
    private static final String SERVER = "paper-1";
    private static final String RESTORATION_TYPE = "RESTORE_CONFISCATED";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_restoration_integrity_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");
    private static final InventoryRestorationTestDatabase TEST_DATABASE =
            new InventoryRestorationTestDatabase(DATABASE, NOW);

    @Test
    void uncommittedConfiscationCannotBeReserved() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture(
                    runtime,
                    new CaseId("01J0000000001001"),
                    new CaseId("01J0000000001002"),
                    false
            );
            UUID restorationId = UUID.randomUUID();

            ConfiscatedAssetReservation reservation = runtime.inventoryJournalStore()
                    .reserveRestoration(fixture.caseId(), restorationId, NOW.plusSeconds(10));

            assertEquals(ConfiscatedAssetReservation.Status.LOCKED, reservation.status());
            assertEquals(1L, TEST_DATABASE.unreservedSnapshotCount(fixture.caseId()));
            assertEquals(0L, TEST_DATABASE.reservedSnapshotCount(fixture.caseId(), restorationId));
        }
    }

    @Test
    void reservationRejectsPreexistingOperationBindings() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture(
                    runtime,
                    new CaseId("01J0000000002001"),
                    new CaseId("01J0000000002002"),
                    true
            );
            UUID unrelatedId = UUID.randomUUID();
            TEST_DATABASE.insertOperation(
                    unrelatedId,
                    fixture.otherTarget(),
                    fixture.otherCaseId(),
                    fixture.actorId(),
                    "ONLINE_EDIT",
                    "COMMITTED"
            );
            assertEquals(
                    ConfiscatedAssetReservation.Status.LOCKED,
                    runtime.inventoryJournalStore().reserveRestoration(
                            fixture.caseId(), unrelatedId, NOW.plusSeconds(10)
                    ).status()
            );

            UUID preexistingRestorationId = UUID.randomUUID();
            TEST_DATABASE.insertOperation(
                    preexistingRestorationId,
                    fixture.target(),
                    fixture.caseId(),
                    fixture.actorId(),
                    RESTORATION_TYPE,
                    "PENDING"
            );
            assertEquals(
                    ConfiscatedAssetReservation.Status.LOCKED,
                    runtime.inventoryJournalStore().reserveRestoration(
                            fixture.caseId(), preexistingRestorationId, NOW.plusSeconds(11)
                    ).status()
            );
            assertEquals(1L, TEST_DATABASE.unreservedSnapshotCount(fixture.caseId()));
        }
    }

    @Test
    void preparationRequiresTheExactReservedCaseAndInventory() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture(
                    runtime,
                    new CaseId("01J0000000003001"),
                    new CaseId("01J0000000003002"),
                    true
            );
            InventoryJournalStore store = runtime.inventoryJournalStore();
            UUID restorationId = UUID.randomUUID();
            byte[] replacement = {31, 32, 33};
            assertRestorationReserved(store, fixture.caseId(), restorationId);

            assertEquals(
                    InventoryPreparation.Status.LOCKED,
                    prepareStatus(
                            store, restorationId, fixture.target(), fixture.caseId(),
                            fixture.actorId(), "ONLINE_EDIT", replacement, NOW.plusSeconds(11)
                    )
            );
            assertEquals(
                    InventoryPreparation.Status.STALE,
                    prepareStatus(
                            store, restorationId, fixture.target(), fixture.otherCaseId(),
                            fixture.actorId(), RESTORATION_TYPE, replacement, NOW.plusSeconds(12)
                    )
            );
            assertEquals(
                    InventoryPreparation.Status.STALE,
                    prepareStatus(
                            store, restorationId, fixture.otherTarget(), fixture.caseId(),
                            fixture.actorId(), RESTORATION_TYPE, replacement, NOW.plusSeconds(13)
                    )
            );
            assertEquals(0L, TEST_DATABASE.operationCount(restorationId));

            InventoryPreparation prepared = store.prepare(
                    request(
                            restorationId,
                            fixture.target(),
                            fixture.caseId(),
                            fixture.actorId(),
                            RESTORATION_TYPE,
                            replacement
                    ),
                    LEASE,
                    NOW.plusSeconds(14)
            );
            assertEquals(InventoryPreparation.Status.PREPARED, prepared.status());
            assertEquals(1L, TEST_DATABASE.operationCount(restorationId));
        }
    }

    @Test
    void matchingRestorationCommitsAndFinalizesIdempotently() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture(
                    runtime,
                    new CaseId("01J0000000003501"),
                    new CaseId("01J0000000003502"),
                    true
            );
            InventoryJournalStore store = runtime.inventoryJournalStore();
            UUID restorationId = UUID.randomUUID();
            byte[] replacement = {41, 42, 43};
            String replacementChecksum = checksum(replacement);
            assertRestorationReserved(store, fixture.caseId(), restorationId);

            assertEquals(
                    InventoryFinalizeResult.Status.COMMITTED,
                    commitRestoration(
                            store, fixture, restorationId, replacement, replacementChecksum
                    ).status()
            );
            assertEquals(
                    1L,
                    TEST_DATABASE.appliedSnapshotCount(
                            fixture.caseId(), restorationId, replacementChecksum
                    )
            );
            assertTrue(store.finalizeRestoration(
                    fixture.caseId(),
                    restorationId,
                    replacementChecksum,
                    NOW.plusSeconds(14)
            ));
        }
    }

    @Test
    void recoveryFinalizesOnlyTheMatchingRestorationOperation() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture(
                    runtime,
                    new CaseId("01J0000000004001"),
                    new CaseId("01J0000000004002"),
                    true
            );
            InventoryJournalStore store = runtime.inventoryJournalStore();
            UUID restorationId = UUID.randomUUID();
            assertEquals(
                    ConfiscatedAssetReservation.Status.RESERVED,
                    store.reserveRestoration(fixture.caseId(), restorationId, NOW.plusSeconds(10)).status()
            );
            TEST_DATABASE.insertOperation(
                    restorationId,
                    fixture.target(),
                    fixture.caseId(),
                    fixture.actorId(),
                    "ONLINE_EDIT",
                    "COMMITTED"
            );
            TEST_DATABASE.insertAppliedPatch(
                    restorationId,
                    fixture.target(),
                    fixture.caseId(),
                    fixture.actorId()
            );

            assertEquals(
                    ConfiscatedAssetReservation.Status.LOCKED,
                    store.reserveRestoration(
                            fixture.caseId(), UUID.randomUUID(), NOW.plusSeconds(11)
                    ).status()
            );
            assertEquals(1L, TEST_DATABASE.reservedSnapshotCount(fixture.caseId(), restorationId));

            TEST_DATABASE.updateOperationType(restorationId, RESTORATION_TYPE);
            assertEquals(
                    ConfiscatedAssetReservation.Status.NOT_FOUND,
                    store.reserveRestoration(
                            fixture.caseId(), UUID.randomUUID(), NOW.plusSeconds(12)
                    ).status()
            );
            assertEquals(
                    1L,
                    TEST_DATABASE.appliedSnapshotCount(
                            fixture.caseId(), restorationId, fixture.target().checksum()
                    )
            );
        }
    }

    @Test
    void committedRestoreCannotFinalizeAnotherProfilesReservation() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture(
                    runtime,
                    new CaseId("01J0000000005001"),
                    new CaseId("01J0000000005002"),
                    true
            );
            InventoryJournalStore store = runtime.inventoryJournalStore();
            UUID restorationId = UUID.randomUUID();
            assertEquals(
                    ConfiscatedAssetReservation.Status.RESERVED,
                    store.reserveRestoration(fixture.caseId(), restorationId, NOW.plusSeconds(10)).status()
            );
            TEST_DATABASE.insertOperation(
                    restorationId,
                    fixture.otherTarget(),
                    fixture.caseId(),
                    fixture.actorId(),
                    RESTORATION_TYPE,
                    "COMMITTED"
            );
            UUID patchId = TEST_DATABASE.insertAppliedPatch(
                    restorationId,
                    fixture.otherTarget(),
                    fixture.caseId(),
                    fixture.actorId()
            );

            assertFalse(store.finalizeRestoration(
                    fixture.caseId(),
                    restorationId,
                    fixture.otherTarget().checksum(),
                    NOW.plusSeconds(11)
            ));
            assertEquals(
                    InventoryFinalizeResult.Status.REPLAYED,
                    store.finalizeApplied(
                            patchId,
                            restorationId,
                            1L,
                            fixture.otherTarget().checksum(),
                            fixture.otherTarget().snapshot(),
                            NOW.plusSeconds(12)
                    ).status()
            );
            assertEquals(1L, TEST_DATABASE.reservedSnapshotCount(fixture.caseId(), restorationId));
        }
    }

    private static Fixture fixture(
            MariaDbRuntime runtime,
            CaseId caseId,
            CaseId otherCaseId,
            boolean commitConfiscation
    ) throws SQLException {
        PlayerIds players = registerPlayersAndCases(runtime, caseId, otherCaseId);
        InventoryJournalStore store = runtime.inventoryJournalStore();
        PreparedConfiscation confiscation = prepareSourceConfiscation(
                store, caseId, players.targetId(), players.actorId()
        );
        if (commitConfiscation) {
            commitSourceConfiscation(store, confiscation);
        }

        InventoryObservation target = store.latest(players.targetId(), SCOPE).orElseThrow();
        byte[] otherSnapshot = {21, 22, 23};
        InventoryObservation otherTarget = store.recordObservation(
                players.otherTargetId(),
                SCOPE,
                SERVER,
                checksum(otherSnapshot),
                otherSnapshot,
                NOW.plusSeconds(4)
        );
        return new Fixture(caseId, otherCaseId, players.actorId(), target, otherTarget);
    }

    private static PlayerIds registerPlayersAndCases(
            MariaDbRuntime runtime,
            CaseId caseId,
            CaseId otherCaseId
    ) throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID otherTargetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        runtime.playerDirectory().recordSeen(
                targetId, "RestoreTargetA", PlayerPlatform.JAVA, SERVER, NOW
        );
        runtime.playerDirectory().recordSeen(
                otherTargetId, "RestoreTargetB", PlayerPlatform.JAVA, SERVER, NOW
        );
        runtime.playerDirectory().recordSeen(
                actorId, "RestoreActor", PlayerPlatform.JAVA, SERVER, NOW
        );
        TEST_DATABASE.insertCase(caseId, targetId, actorId);
        TEST_DATABASE.insertCase(otherCaseId, otherTargetId, actorId);
        return new PlayerIds(targetId, otherTargetId, actorId);
    }

    private static PreparedConfiscation prepareSourceConfiscation(
            InventoryJournalStore store,
            CaseId caseId,
            UUID targetId,
            UUID actorId
    ) {
        byte[] before = {1, 2, 3, 4};
        byte[] replacement = {5, 6, 7};
        byte[] assets = {8, 9, 10};
        UUID confiscationId = UUID.randomUUID();
        InventoryConfiscationStart start = store.beginConfiscation(
                new InventoryConfiscationStartRequest(
                        confiscationId,
                        "inventory:confiscation-test:" + confiscationId,
                        targetId,
                        SCOPE,
                        SERVER,
                        actorId,
                        caseId,
                        checksum(before),
                        before,
                        NOW
                ),
                LEASE,
                NOW
        );
        assertEquals(InventoryConfiscationStart.Status.LOCKED, start.status());
        InventoryConfiscationSession session = start.session().orElseThrow();
        InventoryPreparation prepared = store.prepareConfiscation(
                new InventoryConfiscationCommitRequest(
                        confiscationId,
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
                NOW.plusSeconds(1)
        );
        assertEquals(InventoryPreparation.Status.PREPARED, prepared.status());
        return new PreparedConfiscation(confiscationId, prepared, replacement);
    }

    private static void commitSourceConfiscation(
            InventoryJournalStore store,
            PreparedConfiscation confiscation
    ) {
        InventoryPatch claimed = store.claimForApply(
                confiscation.preparation().patch().orElseThrow().patchId(),
                confiscation.operationId(),
                LEASE,
                NOW.plusSeconds(2)
        ).orElseThrow();
        assertEquals(
                InventoryFinalizeResult.Status.COMMITTED,
                store.finalizeApplied(
                        claimed.patchId(),
                        confiscation.operationId(),
                        claimed.fencingToken(),
                        checksum(confiscation.replacement()),
                        confiscation.replacement(),
                        NOW.plusSeconds(3)
                ).status()
        );
    }

    private static InventoryFinalizeResult commitRestoration(
            InventoryJournalStore store,
            Fixture fixture,
            UUID restorationId,
            byte[] replacement,
            String replacementChecksum
    ) {
        InventoryPreparation prepared = store.prepare(
                request(
                        restorationId,
                        fixture.target(),
                        fixture.caseId(),
                        fixture.actorId(),
                        RESTORATION_TYPE,
                        replacement
                ),
                LEASE,
                NOW.plusSeconds(11)
        );
        InventoryPatch claimed = store.claimForApply(
                prepared.patch().orElseThrow().patchId(),
                restorationId,
                LEASE,
                NOW.plusSeconds(12)
        ).orElseThrow();
        return store.finalizeApplied(
                claimed.patchId(),
                restorationId,
                claimed.fencingToken(),
                replacementChecksum,
                replacement,
                NOW.plusSeconds(13)
        );
    }

    private static void assertRestorationReserved(
            InventoryJournalStore store,
            CaseId caseId,
            UUID restorationId
    ) {
        assertEquals(
                ConfiscatedAssetReservation.Status.RESERVED,
                store.reserveRestoration(caseId, restorationId, NOW.plusSeconds(10)).status()
        );
    }

    private static InventoryPreparation.Status prepareStatus(
            InventoryJournalStore store,
            UUID operationId,
            InventoryObservation observation,
            CaseId caseId,
            UUID actorId,
            String operationType,
            byte[] replacement,
            Instant now
    ) {
        return store.prepare(
                request(
                        operationId,
                        observation,
                        caseId,
                        actorId,
                        operationType,
                        replacement
                ),
                LEASE,
                now
        ).status();
    }

    private record Fixture(
            CaseId caseId,
            CaseId otherCaseId,
            UUID actorId,
            InventoryObservation target,
            InventoryObservation otherTarget
    ) {
    }

    private record PlayerIds(UUID targetId, UUID otherTargetId, UUID actorId) {
    }

    private record PreparedConfiscation(
            UUID operationId,
            InventoryPreparation preparation,
            byte[] replacement
    ) {
    }
}
