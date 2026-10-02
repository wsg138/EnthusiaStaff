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
    static final String CANCEL_RESUME = "STAFF_MODE_HANDOFF_CANCEL";
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

    static Transport channelTransport(PersistentChannelServer channel) {
        if (channel == null) {
            return null;
        }
        return new Transport() {
            @Override
            public Set<String> connectedServers() {
                return channel.connectedServers();
            }

            @Override
            public PersistentChannelServer.DeliveryStatus send(
                    String backendId,
                    UUID messageId,
                    String messageType,
                    String payload,
                    Duration timeout
            ) {
                return channel.send(backendId, messageId, messageType, payload, timeout).join();
            }
        };
    }

    StaffModeBackendHandoffCoordinator(
            Supplier<Transport> transport,
            Function<UUID, Optional<StaffSessionSnapshot>> sessions
    ) {
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
    }

    Decision transfer(
            UUID playerId,
            StaffSessionSnapshot session,
            String current,
            String requested,
            UUID transferId
    ) {
        if (!StaffSessionTransferPolicy.activeHandoffAllowed(
                session.serverId(), session.state(), current, requested)) {
            return Decision.deny("Staff Mode can only transfer from the backend that owns its active snapshot.");
        }
        Transport channel = transport.get();
        if (!ready(channel, current, requested)) {
            return Decision.deny("Staff Mode transfer is unavailable because a backend control channel is offline.");
        }
        java.util.Objects.requireNonNull(transferId, "transferId");
        if (!closeSource(channel, playerId, session, transferId, current)) {
            return Decision.deny("Staff Mode could not safely restore and close its current snapshot.");
        }
        if (prepareDestination(channel, playerId, transferId, requested)) {
            return Decision.allow();
        }
        cancelDestination(channel, playerId, transferId, requested);
        return rollbackSource(channel, playerId, transferId, current);
    }

    Decision recoverFailedConnection(
            UUID playerId,
            String source,
            String destination,
            UUID transferId
    ) {
        Transport channel = transport.get();
        if (channel == null) {
            return Decision.deny("Staff Mode rollback could not start because the backend channel is offline.");
        }
        cancelDestination(channel, playerId, transferId, destination);
        return rollbackSource(channel, playerId, transferId, source);
    }

    boolean retryDestination(UUID playerId, String destination, UUID transferId) {
        Transport channel = transport.get();
        return channel != null
                && channel.send(destination, UUID.randomUUID(), ROLLBACK_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT)
                == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
    }

    void cancelPreparedDestination(UUID playerId, String destination, UUID transferId) {
        Transport channel = transport.get();
        if (channel != null) {
            cancelDestination(channel, playerId, transferId, destination);
        }
    }

    private boolean closeSource(
            Transport channel,
            UUID playerId,
            StaffSessionSnapshot session,
            UUID transferId,
            String current
    ) {
        channel.send(current, UUID.randomUUID(), EXIT_REQUEST,
                exitPayload(playerId, session, transferId), CHANNEL_TIMEOUT);
        Optional<StaffSessionSnapshot> remaining = sessions.apply(playerId);
        return remaining.isEmpty();
    }

    private boolean prepareDestination(Transport channel, UUID playerId, UUID transferId, String requested) {
        return channel.send(requested, UUID.randomUUID(), PREPARE_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT)
                == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
    }

    private void cancelDestination(Transport channel, UUID playerId, UUID transferId, String destination) {
        channel.send(destination, UUID.randomUUID(), CANCEL_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT);
    }

    private Decision rollbackSource(Transport channel, UUID playerId, UUID transferId, String current) {
        var status = channel.send(current, UUID.randomUUID(), ROLLBACK_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT);
        if (status == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED) {
            return Decision.deny("The destination was not ready; Staff Mode rollback was accepted on the current backend.");
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
