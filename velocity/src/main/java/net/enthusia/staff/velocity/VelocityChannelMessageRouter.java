package net.enthusia.staff.velocity;

import java.util.Objects;
import java.util.Set;
import net.enthusia.staff.protocol.ChannelMessageHandler;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/**
 * Application-message authorization boundary for authenticated persistent-channel peers.
 *
 * <p>Only configured Paper backend IDs may reach the existing application handlers. Auxiliary
 * peers such as StaffBot remain transport-authenticated but application-denied on ingress.</p>
 */
final class VelocityChannelMessageRouter implements ChannelMessageHandler {
    private final Set<String> paperBackendIds;
    private final VelocityChatBridgeRelay chatRelay;
    private final VelocityChatArtifactRelay artifactRelay;
    private final VelocityRenderedChatBridgeRelay renderedChatRelay;
    private final VelocityDiscordChatIngressRelay discordIngressRelay;
    private final VelocityStaffBotChatHealthRelay chatHealthRelay;
    private final ChannelMessageHandler paperHandler;

    VelocityChannelMessageRouter(
            Set<String> paperBackendIds,
            VelocityChatBridgeRelay chatRelay,
            VelocityChatArtifactRelay artifactRelay,
            VelocityRenderedChatBridgeRelay renderedChatRelay,
            VelocityDiscordChatIngressRelay discordIngressRelay,
            VelocityStaffBotChatHealthRelay chatHealthRelay,
            ChannelMessageHandler paperHandler
    ) {
        this.paperBackendIds = Set.copyOf(Objects.requireNonNull(paperBackendIds, "paperBackendIds"));
        if (this.paperBackendIds.isEmpty()) {
            throw new IllegalArgumentException("at least one Paper backend is required");
        }
        this.chatRelay = Objects.requireNonNull(chatRelay, "chatRelay");
        this.artifactRelay = Objects.requireNonNull(artifactRelay, "artifactRelay");
        this.renderedChatRelay = Objects.requireNonNull(renderedChatRelay, "renderedChatRelay");
        this.discordIngressRelay = Objects.requireNonNull(discordIngressRelay, "discordIngressRelay");
        this.chatHealthRelay = Objects.requireNonNull(chatHealthRelay, "chatHealthRelay");
        this.paperHandler = Objects.requireNonNull(paperHandler, "paperHandler");
    }

    @Override
    public boolean handle(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (VelocityStaffBotPeerRoute.isStaffBotPeer(envelope.serverId())) {
            if (chatHealthRelay.handles(envelope)) {
                return chatHealthRelay.accept(envelope);
            }
            // Only the original STAFFBOT is eligible for explicitly configured
            // Discord-to-Minecraft ingress. The TEST peer is outbound-only.
            return VelocityStaffBotChatSink.PEER_ID.equals(envelope.serverId())
                    && discordIngressRelay.handles(envelope)
                    && discordIngressRelay.accept(envelope);
        }
        if (!paperBackendIds.contains(envelope.serverId())) {
            return false;
        }
        if (artifactRelay.handles(envelope)) {
            return artifactRelay.accept(envelope);
        }
        if (renderedChatRelay.handles(envelope)) {
            return renderedChatRelay.accept(envelope);
        }
        if (chatRelay.handles(envelope)) {
            return chatRelay.accept(envelope);
        }
        return paperHandler.handle(envelope);
    }
}
