package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.TransferSnapshotMessages;
import org.junit.jupiter.api.Test;

/**
 * Snapshot-authoritative cross-server handoff (overnight/cross-server): when the source
 * backend uploaded its in-memory transfer snapshot, the proxy proceeds without waiting for
 * the source's database persist and forwards the snapshot to the destination.
 */
class StaffModeTransferSnapshotHandoffTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRANSFER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SMP = "SMP";
    private static final String HUB = "HUB";

    @Test
    void snapshotPresentSkipsDatabaseWaitAndAllowsTransfer() {
        // The source's DB session row still lingers (persist not finished), but the
        // in-memory snapshot is authoritative, so the transfer must not get stuck.
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        PayloadTransport transport = new PayloadTransport();
        var coordinator = new StaffModeBackendHandoffCoordinator(() -> transport, ignored -> active.get());

        var decision = coordinator.transfer(PLAYER, session(), SMP, HUB, TRANSFER,
                (playerId, transferId) -> Optional.of(snapshot()));

        assertTrue(decision.allowed(), "transfer must proceed on the uploaded snapshot: " + decision.message());
        assertTrue(transport.types.contains(StaffModeBackendHandoffCoordinator.PREPARE_RESUME));
    }

    @Test
    void snapshotIsForwardedInsidePreparePayload() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        PayloadTransport transport = new PayloadTransport();
        var coordinator = new StaffModeBackendHandoffCoordinator(() -> transport, ignored -> active.get());

        var decision = coordinator.transfer(PLAYER, session(), SMP, HUB, TRANSFER,
                (playerId, transferId) -> Optional.of(snapshot()));

        assertTrue(decision.allowed());
        String prepare = transport.payloadFor(StaffModeBackendHandoffCoordinator.PREPARE_RESUME);
        assertTrue(prepare.contains("\"" + TransferSnapshotMessages.PAYLOAD_FIELD + "\":"),
                "prepare payload must nest the transfer snapshot, got: " + prepare);
        assertTrue(prepare.contains(PLAYER.toString()));
        assertTrue(prepare.contains("\"vanished\":true"));
    }

    @Test
    void missingSnapshotKeepsLegacyDatabaseClosePath() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        PayloadTransport transport = new PayloadTransport();
        var coordinator = new StaffModeBackendHandoffCoordinator(() -> transport, ignored -> active.get());

        // No snapshot uploaded and the DB row never closes: the transfer must still be denied.
        var decision = coordinator.transfer(PLAYER, session(), SMP, HUB, TRANSFER,
                (playerId, transferId) -> Optional.empty());

        assertTrue(!decision.allowed(), "legacy path must deny when the source never closes");
        String prepare = transport.payloadFor(StaffModeBackendHandoffCoordinator.PREPARE_RESUME);
        assertEquals("", prepare, "no prepare may be sent when the transfer is denied");
    }

    private static StaffTransferSnapshot snapshot() {
        return new StaffTransferSnapshot(PLAYER, TRANSFER, SMP, true, true, StaffRank.MOD, "SPECTATOR", 1L);
    }

    private static StaffSessionSnapshot session() {
        return new StaffSessionSnapshot(
                SESSION,
                PLAYER,
                SMP,
                StaffSessionState.ACTIVE,
                true,
                1,
                "a".repeat(64),
                new byte[]{1},
                Instant.parse("2026-10-01T00:00:00Z"),
                7L
        );
    }

    private static final class PayloadTransport implements StaffModeBackendHandoffCoordinator.Transport {
        private final List<String> types = new ArrayList<>();
        private final Map<String, String> payloads = new HashMap<>();

        @Override
        public Set<String> connectedServers() {
            return Set.of(SMP, HUB);
        }

        @Override
        public PersistentChannelServer.DeliveryStatus send(
                String backendId,
                UUID messageId,
                String messageType,
                String payload,
                Duration timeout
        ) {
            types.add(messageType);
            payloads.putIfAbsent(messageType, payload);
            return PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
        }

        private String payloadFor(String messageType) {
            return payloads.getOrDefault(messageType, "");
        }
    }
}
