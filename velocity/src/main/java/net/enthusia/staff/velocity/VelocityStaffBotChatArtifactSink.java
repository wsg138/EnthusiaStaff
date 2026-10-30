package net.enthusia.staff.velocity;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.enthusia.staff.protocol.ChatArtifactMessages;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.PersistentChannelServer;

/** Authenticated Velocity -> StaffBot delivery sink for rich ephemeral chat artifacts. */
final class VelocityStaffBotChatArtifactSink implements VelocityChatArtifactRelay.Sink {
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

    VelocityStaffBotChatArtifactSink(PersistentChannelServer channel) {
        this(Objects.requireNonNull(channel, "channel")::send);
    }

    VelocityStaffBotChatArtifactSink(Sender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public boolean offer(ChatBridgeArtifactBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        try {
            return sender.send(
                    VelocityStaffBotPeerRoute.target(bundle.sourceServerId()),
                    ChatArtifactMessages.transportMessageId(bundle.eventId()),
                    ChatArtifactMessages.ARTIFACTS,
                    ChatArtifactMessages.encode(bundle),
                    ACK_TIMEOUT
            ).join() == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
