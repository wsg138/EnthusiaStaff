package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.InventoryRestorationTestSupport.checksum;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.connection;
import static net.enthusia.staff.integration.MariaDbIntegrationSupport.databaseConfig;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.inventory.InventoryCursorPhase;
import net.enthusia.staff.domain.inventory.InventoryCursorTransfer;
import net.enthusia.staff.domain.inventory.InventoryPatch;
import net.enthusia.staff.domain.inventory.InventoryPreparation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InventoryCursorJournalIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String SCOPE = "survival";
    private static final String SMP = "paper-smp";
    private static final String HUB = "paper-hub";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_cursor_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    @Test
    void cursorRollbackIsFencedTerminalAuditedAndIdempotent() throws SQLException {
        UUID target = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        try (MariaDbRuntime runtime = runtime(target, actor)) {
            InventoryJournalStore store = runtime.inventoryJournalStore();
            PreparedCursor prepared = prepare(store, target, actor, SMP, cursor(11, 12));

            assertEquals(
                    InventoryCursorPhase.PREPARED,
                    store.cursorTransfer(prepared.operationId()).orElseThrow().phase()
            );
            InventoryPatch claimed = store.claimForApply(
                    prepared.patch().patchId(), prepared.operationId(), LEASE, NOW.plusSeconds(2)
            ).orElseThrow();
            assertTrue(store.advanceCursorPhase(
                    claimed.patchId(), claimed.operationId(), claimed.fencingToken(),
                    InventoryCursorPhase.PREPARED, InventoryCursorPhase.SOURCE_ESCROWED,
                    NOW.plusSeconds(3)
            ));
            assertFalse(store.advanceCursorPhase(
                    claimed.patchId(), claimed.operationId(), claimed.fencingToken(),
                    InventoryCursorPhase.PREPARED, InventoryCursorPhase.SOURCE_ESCROWED,
                    NOW.plusSeconds(4)
            ));
            assertFalse(store.resolveCursorRollback(
                    claimed.patchId(), claimed.operationId(), claimed.fencingToken() + 1L,
                    NOW.plusSeconds(5)
            ));
            assertTrue(store.resolveCursorRollback(
                    claimed.patchId(), claimed.operationId(), claimed.fencingToken(),
                    NOW.plusSeconds(6)
            ));
            assertTrue(store.resolveCursorRollback(
                    claimed.patchId(), claimed.operationId(), claimed.fencingToken(),
                    NOW.plusSeconds(7)
            ));

            assertEquals("RESTORED", patchState(prepared.operationId()));
            assertEquals("RESTORED", operationState(prepared.operationId()));
            assertEquals(1L, rollbackAuditCount(prepared.operationId()));
            assertFalse(store.isLocked(target, SCOPE, NOW.plusSeconds(8)));
            assertTrue(store.pending(target, SCOPE, SMP, 2).isEmpty());
        }
    }

    @Test
    void actorRecoveryLookupIsRestrictedToOwningBackend() throws SQLException {
        UUID actor = UUID.randomUUID();
        UUID smpTarget = UUID.randomUUID();
        UUID hubTarget = UUID.randomUUID();
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE))) {
            insertPlayer(DATABASE, actor, name("CursorActor", actor), NOW);
            insertPlayer(DATABASE, smpTarget, name("CursorSmpTarget", smpTarget), NOW);
            insertPlayer(DATABASE, hubTarget, name("CursorHubTarget", hubTarget), NOW);
            InventoryJournalStore store = runtime.inventoryJournalStore();
            PreparedCursor smp = prepare(store, smpTarget, actor, SMP, cursor(21, 22));
            PreparedCursor hub = prepare(store, hubTarget, actor, HUB, cursor(31, 32));

            var smpRows = store.pendingCursorTransfersByActor(actor, SMP, 4);
            var hubRows = store.pendingCursorTransfersByActor(actor, HUB, 4);

            assertEquals(List.of(smp.operationId()), operationIds(smpRows));
            assertEquals(List.of(hub.operationId()), operationIds(hubRows));
        }
    }

    @Test
    void replayWithDifferentCursorEscrowIsRejectedWithoutReplacingMetadata() throws SQLException {
        UUID target = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        try (MariaDbRuntime runtime = runtime(target, actor)) {
            InventoryJournalStore store = runtime.inventoryJournalStore();
            InventoryCursorTransfer original = cursor(41, 42);
            PreparedCursor prepared = prepare(store, target, actor, SMP, original);
            InventoryPrepareRequest changed = request(
                    prepared.operationId(),
                    target,
                    actor,
                    SMP,
                    prepared.revision(),
                    prepared.before(),
                    prepared.replacement(),
                    cursor(41, 99)
            );

            assertThrows(RuntimeException.class, () ->
                    store.prepare(changed, LEASE, NOW.plusSeconds(2)));
            assertEquals(
                    original,
                    store.cursorTransfer(prepared.operationId()).orElseThrow().cursorTransfer()
            );
        }
    }

    @Test
    void cursorAppliedPhaseCannotBeRolledBack() throws SQLException {
        UUID target = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        try (MariaDbRuntime runtime = runtime(target, actor)) {
            InventoryJournalStore store = runtime.inventoryJournalStore();
            PreparedCursor prepared = prepare(store, target, actor, SMP, cursor(51, 52));
            InventoryPatch claimed = store.claimForApply(
                    prepared.patch().patchId(), prepared.operationId(), LEASE, NOW.plusSeconds(2)
            ).orElseThrow();

            assertTrue(advance(store, claimed, InventoryCursorPhase.PREPARED,
                    InventoryCursorPhase.SOURCE_ESCROWED, 3));
            assertTrue(advance(store, claimed, InventoryCursorPhase.SOURCE_ESCROWED,
                    InventoryCursorPhase.TARGET_APPLIED, 4));
            assertTrue(advance(store, claimed, InventoryCursorPhase.TARGET_APPLIED,
                    InventoryCursorPhase.CURSOR_APPLIED, 5));
            assertFalse(store.resolveCursorRollback(
                    claimed.patchId(), claimed.operationId(), claimed.fencingToken(),
                    NOW.plusSeconds(6)
            ));
            assertTrue(store.isLocked(target, SCOPE, NOW.plusSeconds(7)));
        }
    }

    private static MariaDbRuntime runtime(UUID target, UUID actor) throws SQLException {
        MariaDbRuntime runtime = MariaDb.initialize(databaseConfig(DATABASE));
        try {
            insertPlayer(DATABASE, target, name("CursorTarget", target), NOW);
            insertPlayer(DATABASE, actor, name("CursorActor", actor), NOW);
            return runtime;
        } catch (SQLException exception) {
            runtime.close();
            throw exception;
        }
    }

    private static String name(String prefix, UUID id) {
        return prefix + '-' + id.toString().substring(0, 8);
    }

    private static PreparedCursor prepare(
            InventoryJournalStore store,
            UUID target,
            UUID actor,
            String server,
            InventoryCursorTransfer cursor
    ) {
        byte[] before = {1, 2, 3, 4};
        byte[] replacement = {5, 6, 7, 8};
        var observation = store.recordObservation(
                target, SCOPE, server, checksum(before), before, NOW
        );
        UUID operationId = UUID.randomUUID();
        InventoryPreparation preparation = store.prepare(
                request(
                        operationId, target, actor, server, observation.revision(),
                        before, replacement, cursor
                ),
                LEASE,
                NOW.plusSeconds(1)
        );
        assertEquals(InventoryPreparation.Status.PREPARED, preparation.status());
        return new PreparedCursor(
                operationId,
                preparation.patch().orElseThrow(),
                observation.revision(),
                before,
                replacement
        );
    }

    private static InventoryPrepareRequest request(
            UUID operationId,
            UUID target,
            UUID actor,
            String server,
            long revision,
            byte[] before,
            byte[] replacement,
            InventoryCursorTransfer cursor
    ) {
        return new InventoryPrepareRequest(
                operationId,
                "inventory:cursor:" + operationId,
                target,
                SCOPE,
                server,
                actor,
                Optional.empty(),
                "ONLINE_CURSOR_PICKUP",
                revision,
                checksum(before),
                before,
                checksum(replacement),
                replacement,
                List.of(0),
                false,
                Optional.of(cursor)
        );
    }

    private static InventoryCursorTransfer cursor(int expected, int replacement) {
        byte[] expectedBytes = {(byte) expected};
        byte[] replacementBytes = {(byte) replacement};
        return new InventoryCursorTransfer(
                checksum(expectedBytes),
                expectedBytes,
                checksum(replacementBytes),
                replacementBytes
        );
    }

    private static boolean advance(
            InventoryJournalStore store,
            InventoryPatch patch,
            InventoryCursorPhase expected,
            InventoryCursorPhase next,
            long seconds
    ) {
        return store.advanceCursorPhase(
                patch.patchId(), patch.operationId(), patch.fencingToken(),
                expected, next, NOW.plusSeconds(seconds)
        );
    }

    private static List<UUID> operationIds(
            List<net.enthusia.staff.domain.inventory.InventoryCursorJournal> journals
    ) {
        return journals.stream().map(journal -> journal.patch().operationId()).toList();
    }

    private static String patchState(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM inventory_pending_patches WHERE operation_id = ?
                     """)) {
            return state(statement, operationId);
        }
    }

    private static String operationState(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state FROM inventory_operations WHERE operation_id = ?
                     """)) {
            return state(statement, operationId);
        }
    }

    private static String state(PreparedStatement statement, UUID operationId) throws SQLException {
        statement.setBytes(1, uuidBytes(operationId));
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static long rollbackAuditCount(UUID operationId) throws SQLException {
        try (Connection connection = connection(DATABASE);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM audit_events
                     WHERE correlation_id = ? AND event_type = 'INVENTORY_CURSOR_ROLLED_BACK'
                     """)) {
            statement.setBytes(1, uuidBytes(operationId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private record PreparedCursor(
            UUID operationId,
            InventoryPatch patch,
            long revision,
            byte[] before,
            byte[] replacement
    ) {
        private PreparedCursor {
            before = before.clone();
            replacement = replacement.clone();
        }

        @Override
        public byte[] before() {
            return before.clone();
        }

        @Override
        public byte[] replacement() {
            return replacement.clone();
        }
    }
}
