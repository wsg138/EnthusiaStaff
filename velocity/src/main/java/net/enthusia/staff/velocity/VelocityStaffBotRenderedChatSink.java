package net.enthusia.staff.velocity;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.PersistentChannelServer;

/** Authenticated Velocity -> StaffBot delivery sink for styled ephemeral chat. */
final class VelocityStaffBotRenderedChatSink implements VelocityRenderedChatBridgeRelay.Sink {
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

    VelocityStaffBotRenderedChatSink(PersistentChannelServer channel) {
        this(Objects.requireNonNull(channel, "channel")::send);
    }

    VelocityStaffBotRenderedChatSink(Sender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public boolean offer(ChatBridgeRenderedMessage message) {
        Objects.requireNonNull(message, "message");
        try {
            return sender.send(
                    VelocityStaffBotPeerRoute.target(message.sourceServerId()),
                    message.eventId(),
                    ChatRenderMessages.RENDERED,
                    ChatRenderMessages.encode(message),
                    ACK_TIMEOUT
            ).join() == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
