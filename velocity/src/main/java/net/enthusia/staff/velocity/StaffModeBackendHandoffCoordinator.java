package net.enthusia.staff.velocity;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.protocol.PersistentChannelServer;

final class StaffModeBackendHandoffCoordinator {
    static final String EXIT_REQUEST = "STAFF_MODE_HANDOFF_EXIT";
    static final String PREPARE_RESUME = "STAFF_MODE_HANDOFF_PREPARE";
    static final String ROLLBACK_RESUME = "STAFF_MODE_HANDOFF_ROLLBACK";
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(9);

    interface Transport {
        Set<String> connectedServers();

        PersistentChannelServer.DeliveryStatus send(
                String backendId, UUID messageId, String messageType, String payload, Duration timeout);
    }

    record Decision(boolean allowed, String message) {
        static Decision allow() {
            return new Decision(true, "");
        }

        static Decision deny(String message) {
            return new Decision(false, message);
        }
    }

    private final Supplier<Transport> transport;
    private final Function<UUID, Optional<StaffSessionSnapshot>> sessions;
    private final Supplier<UUID> transferIds;

    StaffModeBackendHandoffCoordinator(
            Supplier<Transport> transport,
            Function<UUID, Optional<StaffSessionSnapshot>> sessions,
            Supplier<UUID> transferIds
    ) {
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
        this.transferIds = java.util.Objects.requireNonNull(transferIds, "transferIds");
    }

    Decision transfer(UUID playerId, StaffSessionSnapshot session, String current, String requested) {
        if (!StaffSessionTransferPolicy.activeHandoffAllowed(
                session.serverId(), session.state(), current, requested)) {
            return Decision.deny("Staff Mode can only transfer from the backend that owns its active snapshot.");
        }
        Transport channel = transport.get();
        if (!ready(channel, current, requested)) {
            return Decision.deny("Staff Mode transfer is unavailable because a backend control channel is offline.");
        }
        UUID transferId = transferIds.get();
        if (!closeSource(channel, playerId, session, transferId, current)) {
            return Decision.deny("Staff Mode could not safely restore and close its current snapshot.");
        }
        if (prepareDestination(channel, playerId, transferId, requested)) {
            return Decision.allow();
        }
        return rollbackSource(channel, playerId, transferId, current);
    }

    private boolean closeSource(
            Transport channel,
            UUID playerId,
            StaffSessionSnapshot session,
            UUID transferId,
            String current
    ) {
        var status = channel.send(current, UUID.randomUUID(), EXIT_REQUEST,
                exitPayload(playerId, session, transferId), CHANNEL_TIMEOUT);
        Optional<StaffSessionSnapshot> remaining = sessions.apply(playerId);
        if (remaining.isEmpty()) {
            return true;
        }
        return status == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED
                && !sameSession(remaining.orElseThrow(), session);
    }

    private boolean prepareDestination(Transport channel, UUID playerId, UUID transferId, String requested) {
        return channel.send(requested, UUID.randomUUID(), PREPARE_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT)
                == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
    }

    private Decision rollbackSource(Transport channel, UUID playerId, UUID transferId, String current) {
        var status = channel.send(current, UUID.randomUUID(), ROLLBACK_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT);
        if (status == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED) {
            return Decision.deny("The destination was not ready; Staff Mode was restored on the current backend.");
        }
        return Decision.deny(
                "The destination was not ready. Your original state is safe, but Staff Mode could not be resumed automatically.");
    }

    private static boolean ready(Transport channel, String current, String requested) {
        if (channel == null) {
            return false;
        }
        Set<String> connected = channel.connectedServers();
        return containsIgnoreCase(connected, current) && containsIgnoreCase(connected, requested);
    }

    private static boolean containsIgnoreCase(Set<String> values, String expected) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private static boolean sameSession(StaffSessionSnapshot left, StaffSessionSnapshot right) {
        return left.sessionId().equals(right.sessionId());
    }

    private static String exitPayload(
            UUID playerId, StaffSessionSnapshot session, UUID transferId
    ) {
        return "{\"playerId\":\"" + playerId + "\",\"sessionId\":\"" + session.sessionId()
                + "\",\"revision\":" + session.revision() + ",\"transferId\":\"" + transferId + "\"}";
    }

    private static String resumePayload(UUID playerId, UUID transferId) {
        return "{\"playerId\":\"" + playerId + "\",\"transferId\":\"" + transferId + "\"}";
    }
}
