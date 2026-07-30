package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.InventoryRestorationTestSupport.checksum;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertCase;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.insertPlayer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.InventoryConfiscationCommitRequest;
import net.enthusia.staff.domain.inventory.InventoryConfiscationSession;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStart;
import net.enthusia.staff.domain.inventory.InventoryConfiscationStartRequest;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InventoryConfiscationLifecycleIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String SCOPE = "survival";
    private static final String SERVER = "paper-1";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_confiscation_lifecycle_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void selectionRenewalAndCancellationRemainFencedAndIdempotent() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture("01J0000000007001");
            InventoryJournalStore store = runtime.inventoryJournalStore();
            UUID operationId = UUID.randomUUID();
            byte[] before = {1, 2, 3};
            InventoryConfiscationStartRequest request =
                    startRequest(operationId, fixture, before);

            InventoryConfiscationStart started = store.beginConfiscation(request, LEASE, NOW);
            assertEquals(InventoryConfiscationStart.Status.LOCKED, started.status());
            InventoryConfiscationSession session = started.session().orElseThrow();
            assertEquals(
                    InventoryConfiscationStart.Status.REPLAYED,
                    store.beginConfiscation(request, LEASE, NOW.plusSeconds(1)).status()
            );
            assertTrue(store.renewConfiscation(
                    operationId, session.fencingToken() + 1L, LEASE, NOW.plusSeconds(2)
            ).isEmpty());
            assertTrue(store.renewConfiscation(
                    operationId, session.fencingToken(), LEASE, NOW.plusSeconds(3)
            ).isPresent());
            assertFalse(store.cancelConfiscation(
                    operationId, session.fencingToken() + 1L, "VIEW_CLOSED", "viewer closed", NOW.plusSeconds(4)
            ));
            assertTrue(store.cancelConfiscation(
                    operationId, session.fencingToken(), "VIEW_CLOSED", "viewer closed", NOW.plusSeconds(5)
            ));
            assertTrue(store.cancelConfiscation(
                    operationId, session.fencingToken(), "VIEW_CLOSED", "viewer closed", NOW.plusSeconds(6)
            ));
            assertFalse(store.cancelConfiscation(
                    operationId, session.fencingToken() + 1L, "VIEW_CLOSED", "viewer closed", NOW.plusSeconds(7)
            ));
            assertTrue(store.renewConfiscation(
                    operationId, session.fencingToken(), LEASE, NOW.plusSeconds(8)
            ).isEmpty());
        }
    }

    @Test
    void preparationReplayRequiresTheExactRemovalPatch() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            Fixture fixture = fixture("01J0000000007002");
            InventoryJournalStore store = runtime.inventoryJournalStore();
            UUID operationId = UUID.randomUUID();
            byte[] before = {11, 12, 13};
            InventoryConfiscationSession session = store.beginConfiscation(
                    startRequest(operationId, fixture, before),
                    LEASE,
                    NOW
            ).session().orElseThrow();
            InventoryConfiscationCommitRequest request =
                    commitRequest(session, new byte[]{21, 22}, List.of(1));

            InventoryPreparation prepared = store.prepareConfiscation(request, NOW.plusSeconds(1));
            InventoryPreparation replay = store.prepareConfiscation(request, NOW.plusSeconds(2));

            assertEquals(InventoryPreparation.Status.PREPARED, prepared.status());
            assertEquals(InventoryPreparation.Status.REPLAYED, replay.status());
            assertEquals(
                    prepared.patch().orElseThrow().patchId(),
                    replay.patch().orElseThrow().patchId()
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.prepareConfiscation(
                            commitRequest(session, new byte[]{21, 22}, List.of(2)),
                            NOW.plusSeconds(3)
                    )
            );
        }
    }

    private static Fixture fixture(String caseId) throws SQLException {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        insertPlayer(DATABASE, targetId, username("CT", targetId), NOW);
        insertPlayer(DATABASE, actorId, username("CA", actorId), NOW);
        insertCase(DATABASE, caseId, targetId, actorId, NOW);
        return new Fixture(targetId, actorId, new CaseId(caseId));
    }

    private static String username(String prefix, UUID playerId) {
        return prefix + playerId.toString().replace("-", "").substring(0, 10);
    }

    private static InventoryConfiscationStartRequest startRequest(
            UUID operationId,
            Fixture fixture,
            byte[] before
    ) {
        return new InventoryConfiscationStartRequest(
                operationId,
                "inventory:confiscation-lifecycle:" + operationId,
                fixture.targetId(),
                SCOPE,
                SERVER,
                fixture.actorId(),
                fixture.caseId(),
                checksum(before),
                before,
                NOW
        );
    }

    private static InventoryConfiscationCommitRequest commitRequest(
            InventoryConfiscationSession session,
            byte[] replacement,
            List<Integer> changedSlots
    ) {
        byte[] assets = {31, 32};
        return new InventoryConfiscationCommitRequest(
                session.operationId(),
                session.fencingToken(),
                session.expectedRevision(),
                session.beforeChecksum(),
                checksum(replacement),
                replacement,
                changedSlots,
                checksum(assets),
                assets,
                List.of("1")
        );
    }

    private record Fixture(UUID targetId, UUID actorId, CaseId caseId) {
    }
}
