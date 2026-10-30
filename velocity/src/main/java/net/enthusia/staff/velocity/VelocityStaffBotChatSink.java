package net.enthusia.staff.velocity;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.PersistentChannelServer;

/** Best-effort authenticated Velocity -> StaffBot delivery sink for ephemeral chat. */
final class VelocityStaffBotChatSink implements VelocityChatBridgeRelay.Sink {
    static final String PEER_ID = "STAFFBOT";
    static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    @FunctionalInterface
    interface Sender {
        CompletableFuture<PersistentChannelServer.DeliveryStatus> send(
                String peerId,
                UUID messageId,
                String messageType,
                String payloadJson,
                Duration timeout
        );
    }

    private final Sender sender;

    VelocityStaffBotChatSink(PersistentChannelServer channel) {
        this(Objects.requireNonNull(channel, "channel")::send);
    }

    VelocityStaffBotChatSink(Sender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public boolean offer(ChatBridgeOutboundMessage message) {
        Objects.requireNonNull(message, "message");
        try {
            return sender.send(
                    VelocityStaffBotPeerRoute.target(message.sourceServerId()),
                    message.eventId(),
                    ChatBridgeMessages.OUTBOUND,
                    ChatBridgeMessages.encodeOutbound(message),
                    ACK_TIMEOUT
            ).join() == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
