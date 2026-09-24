package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rosewood.rosechat.api.staff.StaffChannelConfiguration;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
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
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.paper.integration.ReputationRestrictionSynchronizer;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryOperationContext;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
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
    private static final String MARKET = "market";
    private static final String REPUTATION = "reputation";
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
        DiscordStaffAuthorityEndpoint.startIfConfigured(plugin())
                .ifPresent(endpoint -> discordStaffAuthority = endpoint);
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
        refreshRoseChatIntegration();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (isRoseChat(event.getPlugin().getName())) {
            refreshRoseChatIntegration();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (!isRoseChat(event.getPlugin().getName())) {
            return;
        }
        closeRoseChatIntegration();
        activateMuteFallback();
        clearIssue(ROSECHAT_COMMANDS);
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

    void closeChatBridge() {
        HandlerList.unregisterAll(this);
        roseChatLifecycleRegistered = false;
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
            activateMuteFallback();
            clearIssue(ROSECHAT_COMMANDS);
            issue(ROSECHAT, "RoseChat is absent; staff channel/chat bridge are unavailable; private-message mute fallback is active");
            return;
        }
        reconcileRoseChatCommands();
        try {
            RoseChatIntegration.Discovery discovery = RoseChatIntegration.discoverAndInstall(
                    plugin().getServer().getServicesManager(),
                    configuredStaffChannels(),
                    dependencies.policy().authoritativeMode(),
                    dependencies.evidence().muteEnforcement(),
                    dependencies.players().freeze(),
                    dependencies.players().visibility(),
                    dependencies.evidence().chatContext().get()
            );
            if (discovery.integration().isEmpty()) {
                activateMuteFallback();
                issue(ROSECHAT, discovery.issue());
                return;
            }
            closeRoseChatIntegration();
            roseChat = discovery.integration().orElseThrow();
            deactivateMuteFallback();
            clearIssue(ROSECHAT);
        } catch (IllegalArgumentException exception) {
            activateMuteFallback();
            issue(ROSECHAT, "RoseChat channel configuration is invalid");
            plugin().getLogger().log(Level.SEVERE, "RoseChat integration configuration failed", exception);
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

    private void closeRoseChatIntegration() {
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

    private StaffChannelConfiguration configuredStaffChannels() {
        return new StaffChannelConfiguration(
                plugin().getConfig().getString("rosechat.staff-channel", "staff"),
                plugin().getConfig().getString("rosechat.global-channel", "global"),
                Set.copyOf(plugin().getConfig().getStringList("rosechat.private-channels"))
        );
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
            Supplier<EconomyJournalStore> economyJournal,
            Supplier<InventoryJournalStore> inventoryJournal
    ) {
    }

    record PlayerComponents(
            FreezeManager freeze,
            DefaultStaffVisibilityService visibility,
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
