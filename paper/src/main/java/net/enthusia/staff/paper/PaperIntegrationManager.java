package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.api.chatbridge.LegacyDiscordChatSuppression;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.api.chat.RichChatArtifactProvider;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.paper.auth.DiscordStaffAuthorityEndpoint;
import net.enthusia.staff.paper.automod.AutomodListener;
import net.enthusia.staff.paper.automod.StrictVariantMatcher;
import net.enthusia.staff.paper.economy.CurrencyAssetSource;
import net.enthusia.staff.paper.economy.CurrencyGateway;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.economy.EconomyCoordinatorRuntime;
import net.enthusia.staff.paper.economy.EnthusiaCurrencyGateway;
import net.enthusia.staff.paper.economy.InventoryOnlyCurrencyGateway;
import net.enthusia.staff.paper.enforcement.MuteCommandFallbackListener;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.enforcement.PaperBanEnforcementListener;
import net.enthusia.staff.paper.enforcement.PaperPunishmentCommitEffects;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.integration.IndependentRichChatArtifactProvider;
import net.enthusia.staff.paper.integration.InteractiveChatStagingArtifactProvider;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.paper.integration.ReputationRestrictionSynchronizer;
import net.enthusia.staff.paper.integration.RoseChatInboundBridgeIntegration;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.integration.RoseChatOutboundBridgeIntegration;
import net.enthusia.staff.paper.integration.RoseChatOutboundRenderBridgeIntegration;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryOperationContext;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.enthusia.staff.protocol.ChatBridgeHealthMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.PersistentChannelClient;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperIntegrationManager implements Listener {
    private static final String AUTOMOD = "automod";
    private static final String CURRENCY = "currency";
    private static final String ROSECHAT = "rosechat";
    private static final String ROSECHAT_COMMANDS = "rosechat-commands";
    private static final String ROSECHAT_OUTBOUND = "rosechat-discord-bridge";
    private static final String ROSECHAT_RENDER = "rosechat-discord-render";
    private static final String ROSECHAT_INBOUND = "rosechat-discord-ingress";
    private static final String ROSECHAT_AUTHORITY = "rosechat-discord-authority";
    private static final String INTERACTIVE_CHAT_RENDERER = "interactivechat-rich-renderer";
    private static final String MARKET = "market";
    private static final String REPUTATION = "reputation";
    private static final String PMD_NULL_ASSIGNMENT = "PMD.NullAssignment";
    private static final long CHAT_AUTHORITY_WATCHDOG_PERIOD_TICKS = 20L;
    private static final List<CurrencyAssetSource> DEFAULT_REMOVAL_ORDER = List.of(
            CurrencyAssetSource.BANK,
            CurrencyAssetSource.INVENTORY,
            CurrencyAssetSource.ENDER_CHEST
    );

    private final Dependencies dependencies;
    private final PaperResourceCloser resources;
    private EconomyCoordinator economy;
    private ConfiscationCoordinator confiscation;
    private RoseChatIntegration roseChat;
    private RoseChatOutboundBridgeIntegration roseChatOutbound;
    private RoseChatOutboundRenderBridgeIntegration roseChatOutboundRender;
    private RoseChatInboundBridgeIntegration roseChatInbound;
    private InteractiveChatStagingArtifactProvider interactiveChatRenderer;
    private IndependentRichChatArtifactProvider independentRichRenderer;
    private enum RendererFallback { DISABLED, READY, UNAVAILABLE }
    private final AtomicReference<LegacyDiscordChatSuppression.Registration> legacyDiscordSuppression =
            new AtomicReference<>();
    private volatile DiscordChatBridgeMode activeChatBridgeMode = DiscordChatBridgeMode.DISABLED;
    private final AtomicReference<PersistentChannelClient> chatChannel = new AtomicReference<>();
    private final AtomicLong chatPublishingReadyUntil = new AtomicLong();
    private final AtomicReference<ScheduledTask> chatAuthorityWatchdog = new AtomicReference<>();
    private RoseChatCommandOwnershipCoordinator roseChatCommands;
    private MuteCommandFallbackListener muteFallback;
    private boolean roseChatLifecycleRegistered;
    private MarketIntegration market;
    private ReputationIntegration reputation;
    private ReputationRestrictionSynchronizer reputationRestrictions;
    private PaperPunishmentCommitEffects punishmentEffects;
    private DiscordStaffAuthorityEndpoint discordStaffAuthority;

    PaperIntegrationManager(Dependencies dependencies) {
        this.dependencies = dependencies;
        resources = new PaperResourceCloser(dependencies.environment().plugin().getLogger());
    }

    void initializeEconomy() {
        if (!plugin().getServer().getPluginManager().isPluginEnabled("EnthusiaCurrency")) {
            issue(CURRENCY, "EnthusiaCurrency is absent; economy confiscation is unavailable; item confiscation remains available");
            installConfiscation(new InventoryOnlyCurrencyGateway());
            return;
        }
        EnthusiaCurrencyGateway.Discovery discovery =
                EnthusiaCurrencyGateway.discover(plugin().getServer().getServicesManager());
        if (discovery.gateway().isEmpty()) {
            issue(CURRENCY, discovery.issue() + "; confiscation is disabled fail-safe while the provider is present but unavailable");
            return;
        }
        CurrencyGateway gateway = discovery.gateway().orElseThrow();
        installConfiscation(gateway);
        try {
            installEconomy(gateway, configuredRemovalOrder());
            clearIssue(CURRENCY);
        } catch (IllegalArgumentException exception) {
            issue(CURRENCY, "Economy removal order is invalid; economy confiscation is unavailable; item confiscation remains available");
            plugin().getLogger().log(Level.SEVERE, "Economy integration configuration failed", exception);
        }
    }

    void initializeModerationProviders() {
        plugin().getServer().getPluginManager().registerEvents(
                new PaperBanEnforcementListener(
                        plugin(),
                        clock(),
                        dependencies.policy().authoritativeMode(),
                        dependencies.stores().punishmentService()
                ),
                plugin()
        );
        punishmentEffects = new PaperPunishmentCommitEffects(
                plugin(),
                dependencies.stores().punishmentService(),
                dependencies.evidence().muteEnforcement()
        );
        punishmentEffects.start();
        market = MarketIntegration.discover(
                plugin().getServer().getServicesManager(),
                plugin().getServer().getPluginManager().isPluginEnabled("EnthusiaMarket")
        );
        reputation = ReputationIntegration.discover(
                plugin().getServer().getServicesManager(),
                plugin().getServer().getPluginManager().isPluginEnabled("EnthusiaCommend"),
                dependencies.policy().authorization()
        );
        recordProviderIssue(MARKET, market.availability(), market.issue());
        recordProviderIssue(REPUTATION, reputation.availability(), reputation.issue());
        if (reputation.availability() == IntegrationAvailability.AVAILABLE) {
            reputationRestrictions = new ReputationRestrictionSynchronizer(
                    plugin(),
                    clock(),
                    reputation,
                    dependencies.stores().punishmentService(),
                    () -> {
                        PunishmentService service = dependencies.stores().punishmentService().get();
                        return service == null ? null : service::activeSanctions;
                    },
                    workers()
            );
            reputationRestrictions.start();
        }
        DiscordStaffAuthorityEndpoint.startIfConfigured(plugin(),
                new net.enthusia.staff.paper.auth.StaffWebPunishmentService.Dependencies(
                        clock(), dependencies.policy().writeMode(), dependencies.stores().punishmentDraftWorkflow(),
                        dependencies.stores().players(), dependencies.policy().reasons(),
                        dependencies.policy().authorization()))
                .ifPresent(endpoint -> discordStaffAuthority = endpoint);
    }

    void deliverNetworkPunishment(net.enthusia.staff.domain.network.PunishmentCommitNotification notification) {
        if (punishmentEffects == null) {
            throw new IllegalStateException("online punishment effects are unavailable");
        }
        punishmentEffects.onNetworkPunishmentCommitted(notification);
    }

    void initializeAutomod() {
        if (!plugin().getConfig().getBoolean("automod.enabled", false)) {
            issue(AUTOMOD, "Strict exact-variant public-chat enforcement is disabled");
            return;
        }
        StrictVariantMatcher matcher = configuredMatcher();
        if (matcher == null || !validateAutomodPolicy()) {
            return;
        }
        clearIssue(AUTOMOD);
        AutomodListener listener = new AutomodListener(
                plugin(), clock(), matcher, dependencies.policy().writeMode(),
                dependencies.stores().punishmentService(), workers(), this::invalidateMuteCache
        );
        plugin().getServer().getPluginManager().registerEvents(listener, plugin());
    }

    void initializeRoseChat() {
        registerRoseChatLifecycle();
        startChatAuthorityWatchdog();
        refreshRoseChatIntegration();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String pluginName = event.getPlugin().getName();
        if (isRoseChat(pluginName)) {
            refreshRoseChatIntegration();
            return;
        }
        if (isInteractiveChatRendererDependency(pluginName)) {
            refreshInteractiveChatRenderer();
            reconcileRoseChatAuthority();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        String pluginName = event.getPlugin().getName();
        if (InteractiveChatStagingArtifactProvider.INTERACTIVE_CHAT.equals(pluginName)) {
            releaseLegacyDiscordSuppression();
            closeInteractiveChatRenderer();
            clearIssue(INTERACTIVE_CHAT_RENDERER);
            if (activeChatBridgeMode.authoritative()) {
                issue(
                        ROSECHAT_AUTHORITY,
                        "InteractiveChat compatibility changed; legacy Discord chat restored until rich renderer readiness is revalidated"
                );
            }
        } else if (InteractiveChatStagingArtifactProvider.DISCORD_ADDON.equals(pluginName)) {
            closeInteractiveChatRenderer();
            registerIndependentRichRenderer();
            reconcileRoseChatAuthority();
        }
        if (!isRoseChat(pluginName)) {
            return;
        }
        closeRoseChatIntegration();
        activateMuteFallback();
        clearIssue(ROSECHAT_COMMANDS);
        issue(ROSECHAT_OUTBOUND, "RoseChat is absent; Discord chat relay is unavailable");
        issue(ROSECHAT_RENDER, "RoseChat is absent; styled Discord chat rendering is unavailable");
        issue(ROSECHAT_INBOUND, "RoseChat is absent; Discord-to-Minecraft chat ingress is unavailable");
        issue(ROSECHAT, "RoseChat is absent; staff channel/chat bridge are unavailable; private-message mute fallback is active");
    }

    EconomyCoordinator economy() {
        return economy;
    }

    ConfiscationCoordinator confiscation() {
        return confiscation;
    }

    RoseChatIntegration roseChat() {
        return roseChat;
    }

    MarketIntegration market() {
        return market;
    }

    ReputationIntegration reputation() {
        return reputation;
    }

    boolean handleInboundChat(String expectedProxyId, ProtocolEnvelope envelope) {
        if (envelope == null || !ChatBridgeMessages.INBOUND.equals(envelope.messageType())) {
            return false;
        }
        if (!activeChatBridgeMode.enabled()) {
            return false;
        }
        RoseChatInboundBridgeIntegration current = roseChatInbound;
        return current != null && current.accept(expectedProxyId, envelope);
    }

    boolean handleChatBridgeHealth(String expectedProxyId, ProtocolEnvelope envelope) {
        if (envelope == null
                || !ChatBridgeMessages.HEALTH.equals(envelope.messageType())
                || !java.util.Objects.equals(expectedProxyId, envelope.serverId())) {
            return false;
        }
        ChatBridgeHealthMessage health;
        try {
            health = ChatBridgeMessages.decodeHealth(envelope.payloadJson());
        } catch (IllegalArgumentException failure) {
            return false;
        }
        long now = clock().millis();
        if (health.isExpired(now)) {
            return false;
        }
        long boundedExpiry = Math.min(
                health.expiresAtEpochMillis(),
                now + ChatBridgeHealthMessage.MAX_LIFETIME_MILLIS
        );
        chatPublishingReadyUntil.set(health.ready() ? boundedExpiry : 0L);
        plugin().getServer().getGlobalRegionScheduler().execute(
                plugin(),
                this::reconcileRoseChatAuthority
        );
        return true;
    }

    void bindChatChannel(PersistentChannelClient client) {
        PersistentChannelClient required = java.util.Objects.requireNonNull(client, "client");
        chatChannel.set(required);
        RoseChatOutboundBridgeIntegration current = roseChatOutbound;
        if (current != null) {
            current.bindChannel(client);
        }
        RoseChatOutboundRenderBridgeIntegration currentRender = roseChatOutboundRender;
        if (currentRender != null) {
            currentRender.bindChannel(client);
        }
        reconcileRoseChatAuthority();
    }

    void unbindChatChannel(PersistentChannelClient client) {
        releaseLegacyDiscordSuppression();
        chatPublishingReadyUntil.set(0L);
        chatChannel.compareAndSet(client, null);
        RoseChatOutboundBridgeIntegration current = roseChatOutbound;
        if (current != null) {
            current.unbindChannel(client);
        }
        RoseChatOutboundRenderBridgeIntegration currentRender = roseChatOutboundRender;
        if (currentRender != null) {
            currentRender.unbindChannel(client);
        }
    }

    void closeChatBridge() {
        HandlerList.unregisterAll(this);
        roseChatLifecycleRegistered = false;
        ScheduledTask watchdog = chatAuthorityWatchdog.getAndSet(null);
        if (watchdog != null) {
            watchdog.cancel();
        }
        closeRoseChatIntegration();
        deactivateMuteFallback();
        closeModerationProviders();
    }

    void closeModerationProviders() {
        resources.close("punishment commit effects", punishmentEffects);
        resources.close("Discord staff authority endpoint", discordStaffAuthority);
        resources.close("reputation restriction synchronizer", reputationRestrictions);
    }

    void closeEconomyResources() {
        resources.close("economy coordinator", economy);
        resources.close("confiscation coordinator", confiscation);
    }

    private void registerRoseChatLifecycle() {
        if (roseChatLifecycleRegistered) {
            return;
        }
        plugin().getServer().getPluginManager().registerEvents(this, plugin());
        roseChatLifecycleRegistered = true;
    }

    private void refreshRoseChatIntegration() {
        if (!plugin().getServer().getPluginManager().isPluginEnabled("RoseChat")) {
            rollbackDiscordChatTransport();
            activateMuteFallback();
            clearIssue(ROSECHAT_COMMANDS);
            issue(ROSECHAT_RENDER, "RoseChat is absent; styled Discord chat rendering is unavailable");
            issue(ROSECHAT, "RoseChat is absent; staff channel/chat bridge are unavailable; private-message mute fallback is active");
            return;
        }
        reconcileRoseChatCommands();
        try {
            DiscordChatBridgeMode requestedChatMode = DiscordChatBridgeMode.from(
                    plugin().getConfig().getConfigurationSection("discord-chat-bridge")
            );
            RoseChatIntegration.Discovery discovery = RoseChatIntegration.discoverAndInstall(
                    plugin().getServer().getServicesManager(),
                    new RoseChatIntegration.ChannelSettings(
                            plugin().getConfig().getString("rosechat.staff-channel", "staff"),
                            plugin().getConfig().getString("rosechat.global-channel", "global"),
                            plugin().getConfig().getStringList("rosechat.private-channels")
                    ),
                    dependencies.policy().authoritativeMode(),
                    dependencies.evidence().muteEnforcement(),
                    dependencies.players().freeze(),
                    dependencies.players().visibility(),
                    dependencies.players().vanish()::presenceStateReady,
                    dependencies.evidence().chatContext().get(),
                    plugin(),
                    dependencies.stores().punishmentService(),
                    dependencies.policy().reasons()
            );
            if (discovery.integration().isEmpty()) {
                rollbackDiscordChatTransport();
                activateMuteFallback();
                issue(ROSECHAT, discovery.issue());
                return;
            }
            closeRoseChatIntegration();
            roseChat = discovery.integration().orElseThrow();
            activeChatBridgeMode = requestedChatMode;
            dependencies.players().vanish().setPresenceTransitionSink(
                    roseChat::renderPresenceTransition
            );
            installRoseChatOutboundBridge(requestedChatMode);
            installRoseChatOutboundRenderBridge(requestedChatMode);
            installRoseChatInboundBridge(requestedChatMode);
            reconcileRoseChatAuthority();
            deactivateMuteFallback();
            clearIssue(ROSECHAT);
        } catch (IllegalArgumentException exception) {
            rollbackDiscordChatTransport();
            activateMuteFallback();
            issue(ROSECHAT, "RoseChat or Discord chat bridge configuration is invalid");
            issue(ROSECHAT_AUTHORITY, "Discord chat authority configuration is invalid");
            plugin().getLogger().log(Level.SEVERE, "RoseChat integration configuration failed", exception);
        }
    }

    private void installRoseChatOutboundBridge(DiscordChatBridgeMode mode) {
        if (!mode.enabled()) {
            clearIssue(ROSECHAT_OUTBOUND);
            return;
        }
        RoseChatOutboundBridgeIntegration.Discovery discovery =
                RoseChatOutboundBridgeIntegration.discoverAndInstall(
                        dependencies.environment().serverId(),
                        clock()
                );
        if (discovery.integration().isEmpty()) {
            issue(ROSECHAT_OUTBOUND, discovery.issue());
            return;
        }
        roseChatOutbound = discovery.integration().orElseThrow();
        PersistentChannelClient currentChannel = chatChannel.get();
        if (currentChannel != null) {
            roseChatOutbound.bindChannel(currentChannel);
        }
        clearIssue(ROSECHAT_OUTBOUND);
    }

    private void installRoseChatOutboundRenderBridge(DiscordChatBridgeMode mode) {
        if (!mode.enabled()) {
            clearIssue(ROSECHAT_RENDER);
            return;
        }
        try {
            RoseChatOutboundRenderBridgeIntegration.Discovery discovery =
                    RoseChatOutboundRenderBridgeIntegration.discoverAndInstall(
                            plugin(),
                            dependencies.environment().serverId(),
                            clock()
                    );
            if (discovery.integration().isEmpty()) {
                issue(ROSECHAT_RENDER, discovery.issue());
                return;
            }
            roseChatOutboundRender = discovery.integration().orElseThrow();
            refreshInteractiveChatRenderer();
            PersistentChannelClient currentChannel = chatChannel.get();
            if (currentChannel != null) {
                roseChatOutboundRender.bindChannel(currentChannel);
            }
            clearIssue(ROSECHAT_RENDER);
        } catch (RuntimeException | LinkageError failure) {
            issue(
                    ROSECHAT_RENDER,
                    "RoseChat styled render bridge API is unavailable: "
                            + failure.getClass().getSimpleName()
            );
        }
    }

    private void installRoseChatInboundBridge(DiscordChatBridgeMode mode) {
        if (!mode.enabled()) {
            clearIssue(ROSECHAT_INBOUND);
            return;
        }
        try {
            RoseChatInboundBridgeIntegration.Discovery discovery =
                    RoseChatInboundBridgeIntegration.discover(
                            plugin(),
                            dependencies.environment().serverId(),
                            clock()
                    );
            if (discovery.integration().isEmpty()) {
                issue(ROSECHAT_INBOUND, discovery.issue());
                return;
            }
            roseChatInbound = discovery.integration().orElseThrow();
            clearIssue(ROSECHAT_INBOUND);
        } catch (RuntimeException | LinkageError failure) {
            issue(
                    ROSECHAT_INBOUND,
                    "RoseChat inbound bridge API is unavailable: " + failure.getClass().getSimpleName()
            );
        }
    }

    private void reconcileRoseChatAuthority() {
        if (!activeChatBridgeMode.authoritative()) {
            releaseLegacyDiscordSuppression();
            clearIssue(ROSECHAT_AUTHORITY);
            return;
        }

        PersistentChannelClient currentChannel = chatChannel.get();
        boolean interactiveChatEnabled = plugin().getServer().getPluginManager()
                .isPluginEnabled(InteractiveChatStagingArtifactProvider.INTERACTIVE_CHAT);
        boolean richArtifactProviderReady = plugin().getServer().getServicesManager()
                .getRegistration(RichChatArtifactProvider.class) != null;
        if (!authoritativeCutoverReady(
                roseChatOutbound != null,
                roseChatOutboundRender != null,
                roseChatInbound != null,
                currentChannel != null && currentChannel.connected(),
                chatPublishingReady(),
                interactiveChatEnabled,
                richArtifactProviderReady)) {
            releaseLegacyDiscordSuppression();
            issue(
                    ROSECHAT_AUTHORITY,
                    "Authoritative Discord chat is not fully ready; legacy Discord chat path remains active"
            );
            return;
        }

        if (legacyDiscordSuppression.get() != null) {
            clearIssue(ROSECHAT_AUTHORITY);
            return;
        }

        try {
            LegacyDiscordChatSuppression.Registration acquired =
                    RoseChatAPI.getInstance().suppressLegacyDiscordChat();
            if (!legacyDiscordSuppression.compareAndSet(null, acquired)) {
                resources.close("RoseChat redundant legacy Discord suppression", acquired);
            }
            clearIssue(ROSECHAT_AUTHORITY);
        } catch (RuntimeException | LinkageError failure) {
            issue(
                    ROSECHAT_AUTHORITY,
                    "RoseChat legacy Discord suppression is unavailable: "
                            + failure.getClass().getSimpleName()
            );
        }
    }

    private void releaseLegacyDiscordSuppression() {
        LegacyDiscordChatSuppression.Registration suppression = legacyDiscordSuppression.getAndSet(null);
        resources.close("RoseChat legacy Discord suppression", suppression);
    }

    static boolean authoritativeCutoverReady(
            boolean outboundReady,
            boolean renderReady,
            boolean inboundReady,
            boolean channelConnected,
            boolean discordPublishingReady,
            boolean richArtifactsRequired,
            boolean richArtifactProviderReady
    ) {
        return outboundReady
                && renderReady
                && inboundReady
                && channelConnected
                && discordPublishingReady
                && (!richArtifactsRequired || richArtifactProviderReady);
    }

    private boolean chatPublishingReady() {
        return chatPublishingReadyUntil.get() >= clock().millis();
    }

    private void startChatAuthorityWatchdog() {
        if (chatAuthorityWatchdog.get() != null) {
            return;
        }
        ScheduledTask scheduled = plugin().getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin(),
                ignored -> {
                    if (activeChatBridgeMode.authoritative()
                            && legacyDiscordSuppression.get() != null
                            && !chatPublishingReady()) {
                        reconcileRoseChatAuthority();
                    }
                },
                CHAT_AUTHORITY_WATCHDOG_PERIOD_TICKS,
                CHAT_AUTHORITY_WATCHDOG_PERIOD_TICKS
        );
        if (!chatAuthorityWatchdog.compareAndSet(null, scheduled)) {
            scheduled.cancel();
        }
    }

    private void reconcileRoseChatCommands() {
        if (roseChatCommands == null) {
            roseChatCommands = RoseChatCommandOwnershipCoordinator.forPlugin(plugin());
        }
        List<String> conflicts = roseChatCommands.reconcile();
        if (conflicts.isEmpty()) {
            clearIssue(ROSECHAT_COMMANDS);
            return;
        }
        issue(ROSECHAT_COMMANDS, "EnthusiaStaff command ownership conflict: " + String.join(", ", conflicts));
    }

    // Null is the explicit inactive state for these optional hot-reloadable provider slots.
    @SuppressWarnings(PMD_NULL_ASSIGNMENT)
    private void rollbackDiscordChatTransport() {
        releaseLegacyDiscordSuppression();
        chatPublishingReadyUntil.set(0L);
        activeChatBridgeMode = DiscordChatBridgeMode.DISABLED;
        closeInteractiveChatRenderer();
        resources.close("RoseChat outbound styled Discord bridge", roseChatOutboundRender);
        resources.close("RoseChat outbound Discord bridge", roseChatOutbound);
        resources.close("RoseChat inbound Discord bridge", roseChatInbound);
        roseChatOutboundRender = null;
        roseChatOutbound = null;
        roseChatInbound = null;
    }

    // Null is the explicit inactive state for this optional hot-reloadable provider slot.
    @SuppressWarnings(PMD_NULL_ASSIGNMENT)
    private void closeRoseChatIntegration() {
        rollbackDiscordChatTransport();
        dependencies.players().vanish().clearPresenceTransitionSink();
        resources.close("RoseChat bridge", roseChat);
        roseChat = null;
    }

    private void activateMuteFallback() {
        if (muteFallback != null) {
            return;
        }
        muteFallback = new MuteCommandFallbackListener(dependencies.evidence().muteEnforcement());
        plugin().getServer().getPluginManager().registerEvents(muteFallback, plugin());
    }

    // Null is the explicit inactive state after the listener has been unregistered.
    @SuppressWarnings(PMD_NULL_ASSIGNMENT)
    private void deactivateMuteFallback() {
        if (muteFallback == null) {
            return;
        }
        HandlerList.unregisterAll(muteFallback);
        muteFallback = null;
    }

    static boolean isRoseChat(String pluginName) {
        return "RoseChat".equals(pluginName);
    }

    static boolean isInteractiveChatRendererDependency(String pluginName) {
        return InteractiveChatStagingArtifactProvider.INTERACTIVE_CHAT.equals(pluginName)
                || InteractiveChatStagingArtifactProvider.DISCORD_ADDON.equals(pluginName);
    }

    private void refreshInteractiveChatRenderer() {
        closeInteractiveChatRenderer();
        if (roseChatOutboundRender == null || !activeChatBridgeMode.enabled()) {
            clearIssue(INTERACTIVE_CHAT_RENDERER);
            return;
        }
        InteractiveChatStagingArtifactProvider.Discovery discovery =
                InteractiveChatStagingArtifactProvider.discoverAndRegister(
                        plugin(),
                        clock(),
                        workers()
                );
        if (discovery.integration().isPresent()) {
            interactiveChatRenderer = discovery.integration().orElseThrow();
            clearIssue(INTERACTIVE_CHAT_RENDERER);
            return;
        }
        if (registerIndependentRichRenderer() != RendererFallback.DISABLED) {
            return;
        }
        if (discovery.issue().isEmpty()) {
            clearIssue(INTERACTIVE_CHAT_RENDERER);
        } else {
            issue(INTERACTIVE_CHAT_RENDERER, discovery.issue());
        }
    }

    private RendererFallback registerIndependentRichRenderer() {
        if (!activeChatBridgeMode.enabled() || roseChatOutboundRender == null
                || !plugin().getConfig().getBoolean(
                        "discord-chat-bridge.independent-rich-renderer-enabled", false)) {
            return RendererFallback.DISABLED;
        }
        IndependentRichChatArtifactProvider.Discovery discovered =
                IndependentRichChatArtifactProvider.discoverAndRegister(
                        plugin(), clock(), workers());
        if (discovered.integration().isPresent()) {
            independentRichRenderer = discovered.integration().orElseThrow();
            clearIssue(INTERACTIVE_CHAT_RENDERER);
            return RendererFallback.READY;
        }
        String failure = discovered.issue().isEmpty()
                ? "Independent InteractiveChat rich renderer requires an enabled InteractiveChat plugin"
                : discovered.issue();
        issue(INTERACTIVE_CHAT_RENDERER, failure);
        return RendererFallback.UNAVAILABLE;
    }

    @SuppressWarnings(PMD_NULL_ASSIGNMENT)
    private void closeInteractiveChatRenderer() {
        resources.close("InteractiveChat staging rich renderer", interactiveChatRenderer);
        interactiveChatRenderer = null;
        resources.close("Independent InteractiveChat rich renderer", independentRichRenderer);
        independentRichRenderer = null;
    }

    private void installEconomy(CurrencyGateway gateway, List<CurrencyAssetSource> removalOrder) {
        EconomyCoordinator discoveredEconomy = new EconomyCoordinator(
                new EconomyCoordinatorRuntime(
                        plugin(),
                        clock(),
                        dependencies.environment().serverId(),
                        dependencies.policy().writeMode(),
                        dependencies.policy().authorization(),
                        dependencies.stores().economyJournal(),
                        workers()
                ),
                gateway,
                removalOrder,
                dependencies.environment().json()
        );
        plugin().getServer().getPluginManager().registerEvents(discoveredEconomy, plugin());
        economy = discoveredEconomy;
    }

    private void installConfiscation(CurrencyGateway movementGateway) {
        ConfiscationCoordinator discoveredConfiscation = new ConfiscationCoordinator(
                plugin(), dependencies.players().inventoryContext(), dependencies.policy().writeMode(),
                dependencies.policy().authorization(), dependencies.stores().inventoryJournal(), workers(),
                dependencies.players().inventory(), movementGateway
        );
        plugin().getServer().getPluginManager().registerEvents(discoveredConfiscation, plugin());
        confiscation = discoveredConfiscation;
    }

    private List<CurrencyAssetSource> configuredRemovalOrder() {
        List<CurrencyAssetSource> configured = plugin().getConfig().getStringList("economy.removal-order").stream()
                .map(value -> CurrencyAssetSource.valueOf(value.toUpperCase(Locale.ROOT)))
                .toList();
        return configured.isEmpty() ? DEFAULT_REMOVAL_ORDER : configured;
    }

    private StrictVariantMatcher configuredMatcher() {
        try {
            StrictVariantMatcher matcher = new StrictVariantMatcher(
                    plugin().getConfig().getStringList("automod.exact-variants")
            );
            if (!matcher.enabled()) {
                issue(AUTOMOD, "No exact variants are configured; enforcement is disabled");
                return null;
            }
            return matcher;
        } catch (IllegalArgumentException exception) {
            issue(AUTOMOD, "Exact-variant configuration is invalid; enforcement is disabled");
            plugin().getLogger().log(Level.SEVERE, "Automod integration configuration failed", exception);
            return null;
        }
    }

    private boolean validateAutomodPolicy() {
        net.enthusia.staff.domain.escalation.ReasonPolicy policy =
                dependencies.policy().reasons().find("hate.full-slur-untargeted").orElse(null);
        if (policy == null || !policy.automaticDetectionAllowed()) {
            issue(AUTOMOD, "The automod reason is absent or not approved for automatic enforcement");
            return false;
        }
        return true;
    }

    private void invalidateMuteCache(java.util.UUID playerId) {
        MuteEnforcementListener enforcement = dependencies.evidence().muteEnforcement().get();
        if (enforcement != null) {
            enforcement.invalidate(playerId);
        }
    }

    private void recordProviderIssue(String component, IntegrationAvailability availability, String reason) {
        if (availability == IntegrationAvailability.INCOMPATIBLE) {
            issue(component, reason);
        }
    }

    private void issue(String component, String reason) {
        dependencies.featureIssues().put(component, reason);
    }

    private void clearIssue(String component) {
        dependencies.featureIssues().remove(component);
    }

    private JavaPlugin plugin() {
        return dependencies.environment().plugin();
    }

    private Clock clock() {
        return dependencies.environment().clock();
    }

    private ExecutorService workers() {
        return dependencies.environment().workers();
    }

    record Dependencies(
            Environment environment,
            Policy policy,
            Stores stores,
            PlayerComponents players,
            EvidenceComponents evidence,
            Map<String, String> featureIssues
    ) {
    }

    record Environment(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            ExecutorService workers,
            ObjectMapper json
    ) {
    }

    record Policy(
            Supplier<OperationalMode> authoritativeMode,
            Supplier<OperationalMode> writeMode,
            AuthorizationPolicy authorization,
            AtomicReasonPolicyRepository reasons
    ) {
    }

    record Stores(
            Supplier<PunishmentService> punishmentService,
            Supplier<net.enthusia.staff.domain.application.PunishmentDraftWorkflow> punishmentDraftWorkflow,
            Supplier<net.enthusia.staff.domain.ports.PlayerDirectory> players,
            Supplier<EconomyJournalStore> economyJournal,
            Supplier<InventoryJournalStore> inventoryJournal
    ) {
    }

    record PlayerComponents(
            FreezeManager freeze,
            DefaultStaffVisibilityService visibility,
            VanishManager vanish,
            InventoryOperationContext inventoryContext,
            InventoryCoordinator inventory
    ) {
    }

    record EvidenceComponents(
            Supplier<ChatContextBuffer> chatContext,
            Supplier<MuteEnforcementListener> muteEnforcement
    ) {
    }
}
