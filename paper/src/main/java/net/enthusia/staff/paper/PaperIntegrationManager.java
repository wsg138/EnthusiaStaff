package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rosewood.rosechat.api.staff.StaffChannelConfiguration;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.MarketComplianceStore;
import net.enthusia.staff.paper.automod.AutomodListener;
import net.enthusia.staff.paper.automod.StrictVariantMatcher;
import net.enthusia.staff.paper.economy.CurrencyAssetSource;
import net.enthusia.staff.paper.economy.CurrencyGateway;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.economy.EconomyCoordinatorRuntime;
import net.enthusia.staff.paper.economy.EnthusiaCurrencyGateway;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.market.MarketComplianceCoordinator;
import net.enthusia.staff.paper.market.MarketCoordinatorRuntime;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryOperationContext;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

final class PaperIntegrationManager {
    private static final String AUTOMOD = "automod";
    private static final String CURRENCY = "currency";
    private static final String ROSECHAT = "rosechat";
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
    private MarketIntegration market;
    private MarketComplianceCoordinator marketCompliance;
    private ScheduledTask marketMaintenance;
    private ReputationIntegration reputation;

    PaperIntegrationManager(Dependencies dependencies) {
        this.dependencies = dependencies;
        resources = new PaperResourceCloser(dependencies.environment().plugin().getLogger());
    }

    void initializeEconomy() {
        if (!plugin().getServer().getPluginManager().isPluginEnabled("EnthusiaCurrency")) {
            issue(CURRENCY, "EnthusiaCurrency is absent; economy confiscation is unavailable");
            return;
        }
        EnthusiaCurrencyGateway.Discovery discovery =
                EnthusiaCurrencyGateway.discover(plugin().getServer().getServicesManager());
        if (discovery.gateway().isEmpty()) {
            issue(CURRENCY, discovery.issue());
            return;
        }
        try {
            installEconomy(discovery.gateway().orElseThrow(), configuredRemovalOrder());
            clearIssue(CURRENCY);
        } catch (IllegalArgumentException exception) {
            issue(CURRENCY, "Economy removal order is invalid; economy confiscation is unavailable");
            plugin().getLogger().log(Level.SEVERE, "Economy integration configuration failed", exception);
        }
    }

    void initializeModerationProviders() {
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
        marketCompliance = new MarketComplianceCoordinator(
                new MarketCoordinatorRuntime(
                        clock(),
                        dependencies.policy().writeMode(),
                        dependencies.policy().authorization(),
                        dependencies.stores().marketCompliance(),
                        dependencies.stores().cases(),
                        workers()
                ),
                market,
                java.util.UUID::randomUUID
        );
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
        if (!plugin().getServer().getPluginManager().isPluginEnabled("RoseChat")) {
            issue(ROSECHAT, "RoseChat is absent; staff channel and chat bridge are unavailable");
            return;
        }
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
                issue(ROSECHAT, discovery.issue());
                return;
            }
            roseChat = discovery.integration().orElseThrow();
            clearIssue(ROSECHAT);
        } catch (IllegalArgumentException exception) {
            issue(ROSECHAT, "RoseChat channel configuration is invalid");
            plugin().getLogger().log(Level.SEVERE, "RoseChat integration configuration failed", exception);
        }
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

    MarketComplianceCoordinator marketCompliance() {
        return marketCompliance;
    }

    ReputationIntegration reputation() {
        return reputation;
    }

    void closeChatBridge() {
        resources.close("RoseChat bridge", roseChat);
    }

    void closeEconomyResources() {
        if (marketMaintenance != null) {
            marketMaintenance.cancel();
        }
        resources.close("economy coordinator", economy);
        resources.close("confiscation coordinator", confiscation);
    }

    void storageReady() {
        if (marketCompliance == null) {
            return;
        }
        runMarketMaintenance();
        marketMaintenance = plugin().getServer().getAsyncScheduler().runAtFixedRate(
                plugin(),
                ignored -> runMarketMaintenance(),
                1L,
                5L,
                TimeUnit.MINUTES
        );
    }

    private void runMarketMaintenance() {
        marketCompliance.recoverPending().whenComplete((count, failure) -> {
            if (failure != null) {
                plugin().getLogger().log(Level.SEVERE, "Market journal recovery failed safely", failure);
            } else if (count > 0) {
                plugin().getLogger().info("Reconciled " + count + " durable market operation(s)");
            }
        });
        marketCompliance.emitDueReviewAlerts().whenComplete((count, failure) -> {
            if (failure != null) {
                plugin().getLogger().log(Level.WARNING, "Market review alert scan failed", failure);
            }
        });
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
                removalOrder, dependencies.environment().json()
        );
        ConfiscationCoordinator discoveredConfiscation = new ConfiscationCoordinator(
                plugin(), dependencies.players().inventoryContext(), dependencies.policy().writeMode(),
                dependencies.policy().authorization(), dependencies.stores().inventoryJournal(), workers(),
                dependencies.players().inventory(), gateway
        );
        plugin().getServer().getPluginManager().registerEvents(discoveredEconomy, plugin());
        plugin().getServer().getPluginManager().registerEvents(discoveredConfiscation, plugin());
        economy = discoveredEconomy;
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
            plugin().getLogger().log(Level.SEVERE, "Automod configuration validation failed", exception);
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
            Supplier<InventoryJournalStore> inventoryJournal,
            Supplier<MarketComplianceStore> marketCompliance,
            Supplier<CaseLookup> cases
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
