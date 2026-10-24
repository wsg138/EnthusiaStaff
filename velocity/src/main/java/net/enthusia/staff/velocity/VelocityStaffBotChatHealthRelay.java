package net.enthusia.staff.velocity;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeHealthMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/** Forwards authenticated StaffBot Discord/JDA readiness to every configured Paper backend. */
final class VelocityStaffBotChatHealthRelay {
    static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(2);

    @FunctionalInterface
    interface Sender {
        CompletableFuture<PersistentChannelServer.DeliveryStatus> send(
                String peerId,
                java.util.UUID messageId,
                String messageType,
                String payloadJson,
                Duration timeout
        );
    }

    private final Set<String> paperBackendIds;
    private final Clock clock;
    private final AtomicReference<Sender> sender = new AtomicReference<>();

    VelocityStaffBotChatHealthRelay(Set<String> paperBackendIds, Clock clock) {
        this.paperBackendIds = Set.copyOf(Objects.requireNonNull(paperBackendIds, "paperBackendIds"));
        this.clock = Objects.requireNonNull(clock, "clock");
        if (this.paperBackendIds.isEmpty()) {
            throw new IllegalArgumentException("chat health relay requires at least one Paper backend");
        }
    }

    VelocityStaffBotChatHealthRelay(Set<String> paperBackendIds, Clock clock, Sender sender) {
        this(paperBackendIds, clock);
        this.sender.set(Objects.requireNonNull(sender, "sender"));
    }

    void bind(PersistentChannelServer channel) {
        sender.set(Objects.requireNonNull(channel, "channel")::send);
    }

    boolean handles(ProtocolEnvelope envelope) {
        return envelope != null && ChatBridgeMessages.HEALTH.equals(envelope.messageType());
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!VelocityStaffBotPeerRoute.isStaffBotPeer(envelope.serverId()) || !handles(envelope)) {
            return false;
        }
        ChatBridgeHealthMessage health;
        try {
            health = ChatBridgeMessages.decodeHealth(envelope.payloadJson());
        } catch (IllegalArgumentException failure) {
            return false;
        }
        if (health.isExpired(clock.millis())) {
            return false;
        }
        Sender current = sender.get();
        if (current == null) {
            return false;
        }
        boolean isolatedTest = VelocityStaffBotPeerRoute.TEST_PEER.equals(envelope.serverId());
        if (isolatedTest && !paperBackendIds.contains(VelocityStaffBotPeerRoute.TEST_BACKEND)) {
            return false;
        }
        Set<String> targets = isolatedTest
                ? Set.of(VelocityStaffBotPeerRoute.TEST_BACKEND)
                : paperBackendIds.stream()
                        .filter(id -> !VelocityStaffBotPeerRoute.TEST_BACKEND.equals(id))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (targets.isEmpty()) {
            return false;
        }
        for (String backendId : targets) {
            current.send(
                    backendId,
                    envelope.messageId(),
                    ChatBridgeMessages.HEALTH,
                    envelope.payloadJson(),
                    DELIVERY_TIMEOUT
            );
        }
        return true;
    }
}
