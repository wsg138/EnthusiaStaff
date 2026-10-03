package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.staff.BridgeRegistration;
import dev.rosewood.rosechat.api.staff.BroadcastContext;
import dev.rosewood.rosechat.api.staff.ChannelRecipientContext;
import dev.rosewood.rosechat.api.staff.MessageSurface;
import dev.rosewood.rosechat.api.staff.ModerationDecision;
import dev.rosewood.rosechat.api.staff.PresenceContext;
import dev.rosewood.rosechat.api.staff.PrivateMessageContext;
import dev.rosewood.rosechat.api.staff.RoseChatModerationBridge;
import dev.rosewood.rosechat.api.staff.RoseChatStaffService;
import dev.rosewood.rosechat.api.staff.StaffChannelConfiguration;
import dev.rosewood.rosechat.api.staff.TransmissionContext;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class RoseChatIntegration implements AutoCloseable {
    private static final String BRIDGE_OWNER = "EnthusiaStaff";

    private final RoseChatStaffService service;
    private final BridgeRegistration registration;
    private final RoseChatAutomatedModerationProvider automatedModeration;

    private RoseChatIntegration(
            RoseChatStaffService service,
            BridgeRegistration registration
    ) {
        this(service, registration, null);
    }

    private RoseChatIntegration(
            RoseChatStaffService service,
            BridgeRegistration registration,
            RoseChatAutomatedModerationProvider automatedModeration
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.registration = Objects.requireNonNull(registration, "registration");
        this.automatedModeration = automatedModeration;
    }

    public static Discovery discoverAndInstall(
            ServicesManager services,
            ChannelSettings channels,
            Supplier<OperationalMode> mode,
            Supplier<MuteEnforcementListener> mutes,
            FreezeManager freezes,
            StaffVisibilityService visibility,
            ChatContextBuffer chat
    ) {
        Objects.requireNonNull(channels, "channels");
        try {
            return discoverAndInstall(
                    services,
                    new StaffChannelConfiguration(
                            channels.staffChannelId(),
                            channels.globalChannelId(),
                            Set.copyOf(channels.privateChannelIds())
                    ),
                    mode,
                    mutes,
                    freezes,
                    visibility,
                    chat
            );
        } catch (LinkageError exception) {
            return Discovery.unavailable(
                    "RoseChat staff API could not be linked: "
                            + exception.getClass().getSimpleName()
            );
        }
    }

    public static Discovery discoverAndInstall(
            ServicesManager services,
            ChannelSettings channels,
            Supplier<OperationalMode> mode,
            Supplier<MuteEnforcementListener> mutes,
            FreezeManager freezes,
            StaffVisibilityService visibility,
            ChatContextBuffer chat,
            JavaPlugin staffPlugin,
            Supplier<PunishmentService> punishments,
            AtomicReasonPolicyRepository reasons
    ) {
        Objects.requireNonNull(channels, "channels");
        try {
            return discoverAndInstall(
                    services,
                    new StaffChannelConfiguration(
                            channels.staffChannelId(),
                            channels.globalChannelId(),
                            Set.copyOf(channels.privateChannelIds())
                    ),
                    mode,
                    mutes,
                    freezes,
                    visibility,
                    chat,
                    staffPlugin,
                    punishments,
                    reasons
            );
        } catch (LinkageError exception) {
            return Discovery.unavailable(
                    "RoseChat staff API could not be linked: "
                            + exception.getClass().getSimpleName()
            );
        }
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
                    new StaffBridge(configuration, mode, mutes, freezes, visibility, chat)
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

    private static Discovery discoverAndInstall(
            ServicesManager services,
            StaffChannelConfiguration configuration,
            Supplier<OperationalMode> mode,
            Supplier<MuteEnforcementListener> mutes,
            FreezeManager freezes,
            StaffVisibilityService visibility,
            ChatContextBuffer chat,
            JavaPlugin staffPlugin,
            Supplier<PunishmentService> punishments,
            AtomicReasonPolicyRepository reasons
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(mutes, "mutes");
        Objects.requireNonNull(freezes, "freezes");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(chat, "chat");
        Objects.requireNonNull(staffPlugin, "staffPlugin");
        Objects.requireNonNull(punishments, "punishments");
        Objects.requireNonNull(reasons, "reasons");
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
                    new StaffBridge(configuration, mode, mutes, freezes, visibility, chat)
            );
            RoseChatAutomatedModerationProvider automated = null;
            try {
                automated = new RoseChatAutomatedModerationProvider(
                        staffPlugin,
                        services,
                        mode,
                        punishments,
                        reasons,
                        mutes
                );
            } catch (LinkageError exception) {
                staffPlugin.getLogger().warning(
                        "RoseChat does not expose the automated moderation contract; AI mute escalation is unavailable"
                );
            } catch (RuntimeException exception) {
                registration.close();
                throw exception;
            }
            return new Discovery(
                    Optional.of(new RoseChatIntegration(service, registration, automated)),
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
        if (automatedModeration != null) {
            automatedModeration.close();
        }
        registration.close();
    }

    public record ChannelSettings(
            String staffChannelId,
            String globalChannelId,
            List<String> privateChannelIds
    ) {
        public ChannelSettings {
            staffChannelId = Objects.requireNonNull(staffChannelId, "staffChannelId");
            globalChannelId = Objects.requireNonNull(globalChannelId, "globalChannelId");
            privateChannelIds = List.copyOf(Objects.requireNonNull(privateChannelIds, "privateChannelIds"));
        }
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
        private static final Logger log = Logger.getLogger(StaffBridge.class.getName());
        private final StaffChannelConfiguration channels;
        private final Supplier<OperationalMode> mode;
        private final Supplier<MuteEnforcementListener> mutes;
        private final FreezeManager freezes;
        private final StaffVisibilityService visibility;
        private final RoseChatPrivateMessageVisibility privateMessages;
        private final ChatContextBuffer chat;

        private StaffBridge(
                StaffChannelConfiguration channels,
                Supplier<OperationalMode> mode,
                Supplier<MuteEnforcementListener> mutes,
                FreezeManager freezes,
                StaffVisibilityService visibility,
                ChatContextBuffer chat
        ) {
            this.channels = channels;
            this.mode = mode;
            this.mutes = mutes;
            this.freezes = freezes;
            this.visibility = visibility;
            this.privateMessages = new RoseChatPrivateMessageVisibility(visibility);
            this.chat = chat;
        }

        @Override
        public ModerationDecision enforceMute(TransmissionContext context) {
            if (mode.get() != OperationalMode.ACTIVE) {
                return ModerationDecision.allow();
            }
            MuteEnforcementListener enforcement = mutes.get();
            if (enforcement == null) {
                return unverifiedDecision(context);
            }
            return switch (enforcement.cachedStatus(context.senderId())) {
                case CLEAR -> ModerationDecision.allow();
                case PUBLIC_MUTED -> isPublicTransmission(context)
                        ? ModerationDecision.block("You are muted from public chat.")
                        : ModerationDecision.allow();
                case MUTED -> ModerationDecision.block("You are muted.");
                case UNVERIFIED -> ModerationDecision.block(
                        "Your moderation status is still being verified. Please try again shortly."
                );
            };
        }

        private boolean isPublicTransmission(TransmissionContext context) {
            if (context.surface() == MessageSurface.PRIVATE_MESSAGE) {
                return false;
            }
            String destination = context.destinationId();
            if (destination.equalsIgnoreCase(channels.staffChannelId())) {
                return false;
            }
            return !channels.privateChannelIds().contains(destination.toLowerCase(Locale.ROOT));
        }

        @Override
        public ModerationDecision beforeBroadcast(BroadcastContext context) {
            return freezes.isRestricted(context.senderId())
                    ? ModerationDecision.staffOnly()
                    : ModerationDecision.allow();
        }

        @Override
        public ModerationDecision beforePrivateMessage(PrivateMessageContext context) {
            Optional<ModerationDecision> visibilityDecision = privateMessages.evaluate(context);
            if (visibilityDecision.isPresent()) {
                return visibilityDecision.orElseThrow();
            }
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
            return checkVisibility("presence", context.viewerId(), context.subjectId());
        }

        /**
         * H1: presence/channel rendering is decoupled from vanish-map inconsistencies. A subject
         * that is not vanished never consults the matrix; every suppression is logged; and a
         * failing visibility check fails open (renders) instead of silently swallowing
         * join/leave and channel chat.
         */
        private boolean checkVisibility(String surface, UUID viewerId, UUID subjectId) {
            if (subjectId == null || viewerId.equals(subjectId)) {
                return true;
            }
            boolean vanished;
            try {
                vanished = visibility.isVanished(subjectId);
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, exception,
                        () -> "Vanish visibility check failed for RoseChat " + surface + "; rendering anyway");
                return true;
            }
            if (!vanished) {
                return true;
            }
            boolean canSee;
            try {
                canSee = visibility.canSee(viewerId, subjectId);
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, exception,
                        () -> "Vanish visibility check failed for RoseChat " + surface + "; rendering anyway");
                return true;
            }
            if (!canSee) {
                log.log(Level.FINE,
                        "Suppressing RoseChat {0} for vanished subject {1} (viewer {2})",
                        new Object[]{surface, subjectId, viewerId});
            }
            return canSee;
        }

        /**
         * M1: fail-closed mute verification, with the operator override. A sender holding
         * {@code enthusiastaff.override.mute-verification} may chat while unverified so a
         * sanction-storage outage does not silence the whole server.
         */
        private ModerationDecision unverifiedDecision(TransmissionContext context) {
            UUID senderId = context.senderId();
            Player sender = senderId == null ? null : Bukkit.getPlayer(senderId);
            if (sender != null
                    && sender.hasPermission(MuteEnforcementListener.VERIFICATION_OVERRIDE_PERMISSION)) {
                String senderName = sender.getName();
                log.log(Level.INFO,
                        "Mute-verification override used by {0}; RoseChat message allowed while unverified",
                        senderName);
                return ModerationDecision.allow();
            }
            return ModerationDecision.block(
                    "Your moderation status is still being verified. Please try again shortly."
            );
        }
    }
}
