package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.protocol.PersistentChannelServer;
import org.junit.jupiter.api.Test;

class StaffModeBackendHandoffCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRANSFER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SMP = "SMP";
    private static final String HUB = "HUB";

    @Test
    void closesSourceBeforePreparingDestination() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        FakeTransport transport = new FakeTransport(active, true);
        var coordinator = coordinator(transport, active);

        var decision = coordinator.transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertTrue(decision.allowed());
        assertTrue(active.get().isEmpty());
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                StaffModeBackendHandoffCoordinator.PREPARE_RESUME
        )));
    }

    @Test
    void lostExitAcknowledgementIsSafeWhenDurableSessionClosed() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        FakeTransport transport = new FakeTransport(active, true);
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                PersistentChannelServer.DeliveryStatus.TIMED_OUT
        );
        var decision = coordinator(transport, active).transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertTrue(decision.allowed());
    }

    @Test
    void sourceClosureFailureStopsBeforeDestinationPreparation() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        FakeTransport transport = new FakeTransport(active, false);
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                PersistentChannelServer.DeliveryStatus.REJECTED
        );

        var decision = coordinator(transport, active).transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertFalse(decision.allowed());
        assertFalse(decision.reconcile());
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                StaffModeBackendHandoffCoordinator.ABORT_SOURCE
        )));
    }

    @Test
    void unacknowledgedSourceAbortRequiresReconciliation() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        FakeTransport transport = new FakeTransport(active, false);
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                PersistentChannelServer.DeliveryStatus.TIMED_OUT
        );
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.ABORT_SOURCE,
                PersistentChannelServer.DeliveryStatus.TIMED_OUT
        );

        var decision = coordinator(transport, active).transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertFalse(decision.allowed());
        assertTrue(decision.reconcile());
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                StaffModeBackendHandoffCoordinator.ABORT_SOURCE
        )));
    }

    @Test
    void destinationFailureRequestsSourceRollback() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        FakeTransport transport = new FakeTransport(active, true);
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.PREPARE_RESUME,
                PersistentChannelServer.DeliveryStatus.REJECTED
        );

        var decision = coordinator(transport, active).transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertFalse(decision.allowed());
        assertTrue(decision.message().contains("rollback was accepted on the current backend"));
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                StaffModeBackendHandoffCoordinator.PREPARE_RESUME,
                StaffModeBackendHandoffCoordinator.CANCEL_RESUME,
                StaffModeBackendHandoffCoordinator.ROLLBACK_RESUME
        )));
    }

    @Test
    void failedConnectionCancelsDestinationBeforeRollingBackSource() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.empty());
        FakeTransport transport = new FakeTransport(active, false);

        var decision = coordinator(transport, active).recoverFailedConnection(PLAYER, SMP, HUB, TRANSFER);

        assertFalse(decision.allowed());
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.CANCEL_RESUME,
                StaffModeBackendHandoffCoordinator.ROLLBACK_RESUME
        )));
    }

    @Test
    void destinationRetryUsesThePreparedTransferIdentity() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.empty());
        FakeTransport transport = new FakeTransport(active, false);

        assertTrue(coordinator(transport, active).retryDestination(PLAYER, HUB, TRANSFER));
        assertTrue(transport.types.equals(List.of(StaffModeBackendHandoffCoordinator.ROLLBACK_RESUME)));
    }

    @Test
    void rollbackFailureReportsSafeOriginalStateWithoutClaimingModeRestored() {
        AtomicReference<Optional<StaffSessionSnapshot>> active = new AtomicReference<>(Optional.of(session()));
        FakeTransport transport = new FakeTransport(active, true);
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.PREPARE_RESUME,
                PersistentChannelServer.DeliveryStatus.REJECTED
        );
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.ROLLBACK_RESUME,
                PersistentChannelServer.DeliveryStatus.REJECTED
        );

        var decision = coordinator(transport, active).transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertFalse(decision.allowed());
        assertTrue(decision.message().contains("original state is safe"));
        assertFalse(decision.message().contains("was restored on"));
    }

    private static StaffModeBackendHandoffCoordinator coordinator(
            FakeTransport transport,
            AtomicReference<Optional<StaffSessionSnapshot>> active
    ) {
        return new StaffModeBackendHandoffCoordinator(() -> transport, ignored -> active.get());
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

    private static final class FakeTransport implements StaffModeBackendHandoffCoordinator.Transport {
        private final AtomicReference<Optional<StaffSessionSnapshot>> active;
        private final boolean closeDurably;
        private final Map<String, PersistentChannelServer.DeliveryStatus> statuses = new HashMap<>();
        private final List<String> types = new ArrayList<>();

        private FakeTransport(AtomicReference<Optional<StaffSessionSnapshot>> active, boolean closeDurably) {
            this.active = active;
            this.closeDurably = closeDurably;
        }

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
            if (StaffModeBackendHandoffCoordinator.EXIT_REQUEST.equals(messageType) && closeDurably) {
                active.set(Optional.empty());
            }
            return statuses.getOrDefault(messageType, PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED);
        }
    }
}
