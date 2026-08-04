package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.staff.BridgeRegistration;
import dev.rosewood.rosechat.api.staff.BroadcastContext;
import dev.rosewood.rosechat.api.staff.ChannelRecipientContext;
import dev.rosewood.rosechat.api.staff.ModerationDecision;
import dev.rosewood.rosechat.api.staff.PresenceContext;
import dev.rosewood.rosechat.api.staff.PrivateMessageContext;
import dev.rosewood.rosechat.api.staff.RoseChatModerationBridge;
import dev.rosewood.rosechat.api.staff.RoseChatStaffService;
import dev.rosewood.rosechat.api.staff.StaffChannelConfiguration;
import dev.rosewood.rosechat.api.staff.TransmissionContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import org.bukkit.plugin.ServicesManager;

public final class RoseChatIntegration implements AutoCloseable {
    private static final String BRIDGE_OWNER = "EnthusiaStaff";

    private final RoseChatStaffService service;
    private final BridgeRegistration registration;

    private RoseChatIntegration(
            RoseChatStaffService service,
            BridgeRegistration registration
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.registration = Objects.requireNonNull(registration, "registration");
    }

    public static Discovery discoverAndInstall(
            ServicesManager services,
            StaffChannelConfiguration configuration,
            Supplier<OperationalMode> mode,
            Supplier<MuteEnforcementListener> mutes,
            FreezeManager freezes,
            StaffVisibilityService visibility,
            ChatContextBuffer chat
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(mutes, "mutes");
        Objects.requireNonNull(freezes, "freezes");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(chat, "chat");
        try {
            RoseChatStaffService service = services.load(RoseChatStaffService.class);
            if (service == null) {
                return Discovery.unavailable("RoseChat did not register its staff service");
            }
            if (service.apiVersion() != RoseChatStaffService.API_VERSION) {
                return Discovery.unavailable(
                        "RoseChat staff API version " + service.apiVersion()
                                + " is incompatible with required version "
                                + RoseChatStaffService.API_VERSION
                );
            }
            Optional<String> owner = service.getBridgeOwner();
            if (owner.isPresent()) {
                return Discovery.unavailable(
                        owner.orElseThrow().equals(BRIDGE_OWNER)
                                ? "RoseChat still has a stale EnthusiaStaff moderation bridge"
                                : "RoseChat moderation bridge is already owned by " + owner.orElseThrow()
                );
            }
            BridgeRegistration registration = service.installBridge(
                    BRIDGE_OWNER,
                    configuration,
                    new StaffBridge(mode, mutes, freezes, visibility, chat)
            );
            return new Discovery(
                    Optional.of(new RoseChatIntegration(service, registration)),
                    ""
            );
        } catch (LinkageError | RuntimeException exception) {
            return Discovery.unavailable(
                    "RoseChat staff API could not be linked: "
                            + exception.getClass().getSimpleName()
            );
        }
    }

    public boolean toggleStaffChannel(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return service.toggleStaffChannel(playerId);
    }

    public Optional<String> currentChannel(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return service.getCurrentChannel(playerId);
    }

    public int apiVersion() {
        return service.apiVersion();
    }

    public boolean bridgeActive() {
        return registration.isActive();
    }

    @Override
    public void close() {
        registration.close();
    }

    public record Discovery(Optional<RoseChatIntegration> integration, String issue) {
        public Discovery {
            integration = Objects.requireNonNull(integration, "integration");
            issue = Objects.requireNonNull(issue, "issue");
            if (integration.isPresent() == !issue.isEmpty()) {
                throw new IllegalArgumentException("successful RoseChat discovery cannot contain an issue");
            }
        }

        private static Discovery unavailable(String issue) {
            return new Discovery(Optional.empty(), issue);
        }
    }

    private static final class StaffBridge implements RoseChatModerationBridge {
        private final Supplier<OperationalMode> mode;
        private final Supplier<MuteEnforcementListener> mutes;
        private final FreezeManager freezes;
        private final StaffVisibilityService visibility;
        private final ChatContextBuffer chat;

        private StaffBridge(
                Supplier<OperationalMode> mode,
                Supplier<MuteEnforcementListener> mutes,
                FreezeManager freezes,
                StaffVisibilityService visibility,
                ChatContextBuffer chat
        ) {
            this.mode = mode;
            this.mutes = mutes;
            this.freezes = freezes;
            this.visibility = visibility;
            this.chat = chat;
        }

        @Override
        public ModerationDecision enforceMute(TransmissionContext context) {
            if (mode.get() != OperationalMode.ACTIVE) {
                return ModerationDecision.allow();
            }
            MuteEnforcementListener enforcement = mutes.get();
            if (enforcement == null) {
                return ModerationDecision.block(
                        "Your moderation status is still being verified. Please try again shortly."
                );
            }
            return switch (enforcement.cachedStatus(context.senderId())) {
                case CLEAR -> ModerationDecision.allow();
                case MUTED -> ModerationDecision.block("You are muted.");
                case UNVERIFIED -> ModerationDecision.block(
                        "Your moderation status is still being verified. Please try again shortly."
                );
            };
        }

        @Override
        public ModerationDecision beforeBroadcast(BroadcastContext context) {
            return freezes.isRestricted(context.senderId())
                    ? ModerationDecision.staffOnly()
                    : ModerationDecision.allow();
        }

        @Override
        public ModerationDecision beforePrivateMessage(PrivateMessageContext context) {
            return freezes.isRestricted(context.senderId())
                    ? ModerationDecision.staffOnly()
                    : ModerationDecision.allow();
        }

        @Override
        public void capturePrivateMessage(PrivateMessageContext context) {
            context.recipientId().ifPresent(recipientId -> chat.capturePrivate(
                    context.senderId(),
                    context.senderName(),
                    recipientId,
                    context.recipientName(),
                    context.message()
            ));
        }

        @Override
        public boolean canReceiveChannelMessage(ChannelRecipientContext context) {
            return context.senderId()
                    .map(senderId -> visibility.canSee(context.recipientId(), senderId))
                    .orElse(true);
        }

        @Override
        public boolean canRenderPresence(PresenceContext context) {
            return visibility.canSee(context.viewerId(), context.subjectId());
        }
    }
}
