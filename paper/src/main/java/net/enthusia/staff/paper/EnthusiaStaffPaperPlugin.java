package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rosewood.rosechat.api.staff.StaffChannelConfiguration;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.crypto.SecretKey;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.OperationalStateStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.ClientEvidenceStore;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import net.enthusia.staff.paper.automod.AutomodListener;
import net.enthusia.staff.paper.automod.StrictVariantMatcher;
import net.enthusia.staff.paper.command.EstaffCommand;
import net.enthusia.staff.paper.command.CaseCommand;
import net.enthusia.staff.paper.command.ClientCommand;
import net.enthusia.staff.paper.command.PunishmentCommand;
import net.enthusia.staff.paper.command.SanctionChangeCommand;
import net.enthusia.staff.paper.command.ReportCommand;
import net.enthusia.staff.paper.command.ReportsCommand;
import net.enthusia.staff.paper.command.FreezeCommand;
import net.enthusia.staff.paper.command.InventoryCommand;
import net.enthusia.staff.paper.command.InspectCommand;
import net.enthusia.staff.paper.command.StaffChatCommand;
import net.enthusia.staff.paper.command.StaffModeCommand;
import net.enthusia.staff.paper.command.VanishCommand;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.economy.CurrencyAssetSource;
import net.enthusia.staff.paper.economy.CurrencyGateway;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.economy.EnthusiaCurrencyGateway;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.protocol.PersistentChannelClient;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnthusiaStaffPaperPlugin extends JavaPlugin {
    private final RuntimeHealth health = new RuntimeHealth();
    private final AtomicReference<OperationalMode> mode = new AtomicReference<>(OperationalMode.BOOTSTRAP);
    private final ConcurrentHashMap<String, String> featureIssues = new ConcurrentHashMap<>();

    private ExecutorService workers;
    private MariaDbRuntime databaseRuntime;
    private ModerationStore moderationStore;
    private PlayerDirectory playerDirectory;
    private PunishmentService punishmentService;
    private AtomicReasonPolicyRepository reasonPolicies;
    private SanctionLookup sanctionLookup;
    private MuteEnforcementListener muteEnforcement;
    private ScheduledTask operationalStateTask;
    private PersistentChannelClient channelClient;
    private final AtomicBoolean channelConnected = new AtomicBoolean();
    private final ObjectMapper json = new ObjectMapper();
    private SanctionChangeService sanctionChangeService;
    private CaseLookup caseLookup;
    private ReportStore reportStore;
    private ChatContextBuffer chatContext;
    private FreezeStore freezeStore;
    private FreezeManager freezeManager;
    private StaffSessionStore staffSessionStore;
    private StaffModeManager staffModeManager;
    private VanishStore vanishStore;
    private DefaultStaffVisibilityService visibilityService;
    private VanishManager vanishManager;
    private InventoryJournalStore inventoryJournalStore;
    private InventoryCoordinator inventoryCoordinator;
    private EconomyJournalStore economyJournalStore;
    private EconomyCoordinator economyCoordinator;
    private ConfiscationCoordinator confiscationCoordinator;
    private CurrencyGateway currencyGateway;
    private RoseChatIntegration roseChatIntegration;
    private ClientEvidenceCollector clientEvidenceCollector;
    private ClientEvidenceStore clientEvidenceStore;
    private MarketIntegration marketIntegration;
    private ReputationIntegration reputationIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("reason-policies.yml", false);
        boolean policiesReady = loadReasonPolicies();
        int threads = getConfig().getInt("workers.threads", 4);
        int queueCapacity = getConfig().getInt("workers.queue-capacity", 256);
        workers = BoundedExecutorFactory.create(threads, queueCapacity);
        freezeManager = new FreezeManager(this, Clock.systemUTC(), () -> freezeStore, workers);
        getServer().getPluginManager().registerEvents(freezeManager, this);
        staffModeManager = new StaffModeManager(
                this,
                Clock.systemUTC(),
                getConfig().getString("network.server-id", "SMP"),
                () -> staffSessionStore,
                workers
        );
        getServer().getPluginManager().registerEvents(staffModeManager, this);
        visibilityService = new DefaultStaffVisibilityService(loadVisibilityMatrix());
        vanishManager = new VanishManager(
                this,
                Clock.systemUTC(),
                visibilityService,
                () -> vanishStore,
                () -> staffSessionStore,
                staffModeManager,
                workers
        );
        staffModeManager.setExitListener(vanishManager::staffModeExited);
        getServer().getPluginManager().registerEvents(vanishManager, this);
        inventoryCoordinator = new InventoryCoordinator(
                this,
                Clock.systemUTC(),
                getConfig().getString("inventory.scope-id", getConfig().getString("network.server-id", "SMP")),
                getConfig().getString("network.server-id", "SMP"),
                this::effectiveWriteMode,
                () -> inventoryJournalStore,
                () -> playerDirectory,
                workers
        );
        getServer().getPluginManager().registerEvents(inventoryCoordinator, this);
        initializeEconomyIntegration();
        clientEvidenceCollector = ClientEvidenceCollector.discover(this, Clock.systemUTC());
        initializeModerationIntegrations();
        if (!staffModeManager.combat().availableWhenRequired()) {
            featureIssues.put("combatlogx", "CombatLogX is present but its combat-query API is unavailable");
        }
        if (policiesReady) {
            chatContext = new ChatContextBuffer(Clock.systemUTC());
            getServer().getPluginManager().registerEvents(chatContext, this);
            initializeAutomod();
        }
        if (policiesReady) {
            registerCommands();
        } else {
            registerStatusCommand();
        }
        registerServices();
        if (policiesReady) {
            muteEnforcement = new MuteEnforcementListener(
                    this,
                    Clock.systemUTC(),
                    getConfig().getString("network.server-id", "SMP"),
                    mode::get,
                    () -> sanctionLookup,
                    () -> playerDirectory,
                    workers
            );
            getServer().getPluginManager().registerEvents(muteEnforcement, this);
            muteEnforcement.start();
            initializeRoseChatIntegration();
            workers.execute(this::initializeStorage);
        }
    }

    @Override
    public void onDisable() {
        mode.set(OperationalMode.MAINTENANCE);
        publishHealth(OperationalMode.MAINTENANCE, Map.of("shutdown", "Runtime is shutting down"));
        if (roseChatIntegration != null) {
            try {
                roseChatIntegration.close();
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "RoseChat bridge cleanup failed", exception);
            }
        }
        if (muteEnforcement != null) {
            muteEnforcement.close();
        }
        if (inventoryCoordinator != null) {
            inventoryCoordinator.close();
        }
        if (economyCoordinator != null) {
            economyCoordinator.close();
        }
        if (confiscationCoordinator != null) {
            confiscationCoordinator.close();
        }
        if (operationalStateTask != null) {
            operationalStateTask.cancel();
        }
        if (channelClient != null) {
            channelClient.close();
        }
        if (workers != null) {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                    workers.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                workers.shutdownNow();
            }
        }
        if (databaseRuntime != null) {
            databaseRuntime.close();
        }
    }

    public OperationalMode operationalMode() {
        return mode.get();
    }

    public ModerationStore moderationStore() {
        return moderationStore;
    }

    public ExecutorService workers() {
        return workers;
    }

    private void initializeStorage() {
        String url = environment("storage.jdbc-url-environment");
        String username = environment("storage.username-environment");
        String password = environment("storage.password-environment");
        if (url == null || username == null || password == null) {
            setDegraded("mariadb", "Required database environment variables are missing; destructive actions are disabled");
            return;
        }
        try {
            DatabaseConfig database = new DatabaseConfig(
                    url,
                    username,
                    password,
                    getConfig().getInt("storage.maximum-pool-size", 8),
                    getConfig().getLong("storage.connection-timeout-millis", 5_000)
            );
            MariaDbRuntime opened = MariaDb.initialize(database);
            databaseRuntime = opened;
            moderationStore = opened.moderationStore();
            playerDirectory = opened.playerDirectory();
            sanctionLookup = opened.sanctionLookup();
            caseLookup = opened.caseLookup();
            reportStore = opened.reportStore();
            freezeStore = opened.freezeStore();
            staffSessionStore = opened.staffSessionStore();
            vanishStore = opened.vanishStore();
            inventoryJournalStore = opened.inventoryJournalStore();
            economyJournalStore = opened.economyJournalStore();
            clientEvidenceStore = opened.clientEvidenceStore();
            getServer().getOnlinePlayers().forEach(freezeManager::verify);
            getServer().getOnlinePlayers().forEach(staffModeManager::recover);
            vanishManager.initialize();
            punishmentService = new PunishmentService(
                    Clock.systemUTC(),
                    new SecureIdentifiers(new SecureRandom()),
                    new DefaultAuthorizationPolicy(),
                    reasonPolicies,
                    moderationStore,
                    new EscalationEngine()
            );
            sanctionChangeService = new SanctionChangeService(
                    new DefaultAuthorizationPolicy(), opened.sanctionMutationStore()
            );
            promoteAfterBootstrap();
            try {
                initializeChannel(opened);
            } catch (RuntimeException exception) {
                channelConnected.set(false);
                getLogger().log(Level.SEVERE,
                        "Persistent Velocity channel initialization failed; new punishment writes are disabled",
                        exception);
            }
            operationalStateTask = getServer().getAsyncScheduler().runAtFixedRate(
                    this,
                    ignored -> refreshOperationalState(),
                    5,
                    5,
                    TimeUnit.SECONDS
            );
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "MariaDB bootstrap failed; destructive actions are disabled", exception);
            setDegraded("mariadb", "Connection or schema validation failed; see the sanitized console error");
        }
    }

    private void refreshOperationalState() {
        MariaDbRuntime runtime = databaseRuntime;
        if (runtime == null) {
            return;
        }
        try {
            OperationalStateSnapshot state = runtime.operationalStateStore().current();
            if (state.mode() == OperationalMode.ACTIVE && !runtime.operationalStateStore().hasAuthorizedCutover()) {
                setDegraded("cutover", "ACTIVE has no authorized cutover record; enforcement remains blocked");
                return;
            }
            OperationalMode previous = mode.getAndSet(state.mode());
            if (previous != state.mode()) {
                getLogger().info("Operational mode changed from " + previous + " to " + state.mode());
            }
            publishHealth(state.mode(), state.mode() == OperationalMode.ACTIVE && channelConnected.get()
                    ? Map.of()
                    : state.mode() == OperationalMode.ACTIVE
                            ? Map.of("channel", "Persistent Velocity connection is unavailable; new punishments are disabled")
                    : Map.of("mode", "Sensitive actions are disabled in " + state.mode()));
        } catch (RuntimeException exception) {
            if (mode.get() == OperationalMode.ACTIVE) {
                publishHealth(OperationalMode.DEGRADED, Map.of(
                        "operational-state", "State refresh failed; cached active-sanction enforcement remains fail-closed"
                ));
            } else {
                setDegraded("operational-state", "Operational state refresh failed; enforcement is disabled");
            }
            getLogger().log(Level.SEVERE, "Operational state refresh failed", exception);
        }
    }

    private void promoteAfterBootstrap() {
        OperationalStateStore states = databaseRuntime.operationalStateStore();
        OperationalStateSnapshot persisted = states.current();
        OperationalMode next = persisted.mode();
        if (next == OperationalMode.ACTIVE && !states.hasAuthorizedCutover()) {
            setDegraded("cutover", "Persistent ACTIVE state has no authorized cutover record; enforcement is blocked");
            return;
        }
        if (next == OperationalMode.BOOTSTRAP) {
            boolean transitioned = states.transition(
                    persisted.revision(),
                    OperationalMode.SHADOW_MIGRATION,
                    null,
                    "Schema initialized; LiteBans remains authoritative during shadow migration",
                    Clock.systemUTC().instant()
            );
            if (!transitioned) {
                setDegraded("operational-state", "Concurrent bootstrap transition detected; retry after state review");
                return;
            }
            next = OperationalMode.SHADOW_MIGRATION;
        }
        mode.set(next);
        Map<String, String> issues = switch (next) {
            case ACTIVE -> Map.of();
            case SHADOW_MIGRATION -> Map.of(
                    "authority", "LiteBans remains authoritative; EnthusiaStaff enforcement is disabled");
            case MAINTENANCE -> Map.of("maintenance", "Maintenance mode blocks sensitive state changes");
            default -> Map.of("mode", "Sensitive actions are disabled in " + next);
        };
        publishHealth(next, issues);
        getLogger().info("Storage verified; EnthusiaStaff entered " + next);
    }

    private String environment(String configurationPath) {
        String variable = getConfig().getString(configurationPath);
        return variable == null || variable.isBlank() ? null : System.getenv(variable);
    }

    private void setDegraded(String component, String reason) {
        mode.set(OperationalMode.DEGRADED);
        publishHealth(OperationalMode.DEGRADED, Map.of(component, reason));
        getLogger().warning(reason);
    }

    private void registerStatusCommand() {
        PluginCommand command = Objects.requireNonNull(getCommand("estaff"), "estaff command is missing from plugin.yml");
        command.setExecutor(new EstaffCommand(health));
    }

    private void initializeAutomod() {
        boolean enabled = getConfig().getBoolean("automod.enabled", false);
        java.util.List<String> variants = getConfig().getStringList("automod.exact-variants");
        if (!enabled) {
            featureIssues.put("automod", "Strict exact-variant public-chat enforcement is disabled");
            return;
        }
        StrictVariantMatcher matcher;
        try {
            matcher = new StrictVariantMatcher(variants);
        } catch (IllegalArgumentException exception) {
            featureIssues.put("automod", "Exact-variant configuration is invalid; enforcement is disabled");
            getLogger().log(Level.SEVERE, "Automod configuration validation failed", exception);
            return;
        }
        if (!matcher.enabled()) {
            featureIssues.put("automod", "No exact variants are configured; enforcement is disabled");
            return;
        }
        net.enthusia.staff.domain.escalation.ReasonPolicy policy =
                reasonPolicies.find("hate.full-slur-untargeted").orElse(null);
        if (policy == null || !policy.automaticDetectionAllowed()) {
            featureIssues.put("automod", "The automod reason is absent or not approved for automatic enforcement");
            return;
        }
        featureIssues.remove("automod");
        AutomodListener listener = new AutomodListener(
                this,
                Clock.systemUTC(),
                matcher,
                this::effectiveWriteMode,
                () -> punishmentService,
                workers,
                playerId -> {
                    MuteEnforcementListener enforcement = muteEnforcement;
                    if (enforcement != null) {
                        enforcement.invalidate(playerId);
                    }
                }
        );
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void initializeEconomyIntegration() {
        if (!getServer().getPluginManager().isPluginEnabled("EnthusiaCurrency")) {
            featureIssues.put(
                    "currency",
                    "EnthusiaCurrency is absent; economy confiscation is unavailable"
            );
            return;
        }
        EnthusiaCurrencyGateway.Discovery discovery =
                EnthusiaCurrencyGateway.discover(getServer().getServicesManager());
        if (discovery.gateway().isEmpty()) {
            featureIssues.put("currency", discovery.issue());
            return;
        }
        List<CurrencyAssetSource> order;
        try {
            order = getConfig().getStringList("economy.removal-order").stream()
                    .map(value -> CurrencyAssetSource.valueOf(
                            value.toUpperCase(java.util.Locale.ROOT)
                    ))
                    .toList();
            if (order.isEmpty()) {
                order = List.of(
                        CurrencyAssetSource.BANK,
                        CurrencyAssetSource.INVENTORY,
                        CurrencyAssetSource.ENDER_CHEST
                );
            }
            economyCoordinator = new EconomyCoordinator(
                    this,
                    Clock.systemUTC(),
                    getConfig().getString("network.server-id", "SMP"),
                    this::effectiveWriteMode,
                    () -> economyJournalStore,
                    workers,
                    discovery.gateway().orElseThrow(),
                    order,
                    json
            );
            currencyGateway = discovery.gateway().orElseThrow();
            confiscationCoordinator = new ConfiscationCoordinator(
                    this,
                    Clock.systemUTC(),
                    getConfig().getString(
                            "inventory.scope-id",
                            getConfig().getString("network.server-id", "SMP")
                    ),
                    getConfig().getString("network.server-id", "SMP"),
                    this::effectiveWriteMode,
                    () -> inventoryJournalStore,
                    workers,
                    inventoryCoordinator,
                    currencyGateway
            );
            getServer().getPluginManager().registerEvents(economyCoordinator, this);
            getServer().getPluginManager().registerEvents(confiscationCoordinator, this);
            featureIssues.remove("currency");
        } catch (IllegalArgumentException exception) {
            featureIssues.put(
                    "currency",
                    "Economy removal order is invalid; economy confiscation is unavailable"
            );
            getLogger().log(Level.SEVERE, "Economy integration configuration failed", exception);
        }
    }

    private void initializeRoseChatIntegration() {
        if (!getServer().getPluginManager().isPluginEnabled("RoseChat")) {
            featureIssues.put(
                    "rosechat",
                    "RoseChat is absent; staff channel and chat bridge are unavailable"
            );
            return;
        }
        try {
            StaffChannelConfiguration configuration = new StaffChannelConfiguration(
                    getConfig().getString("rosechat.staff-channel", "staff"),
                    getConfig().getString("rosechat.global-channel", "global"),
                    java.util.Set.copyOf(getConfig().getStringList("rosechat.private-channels"))
            );
            RoseChatIntegration.Discovery discovery = RoseChatIntegration.discoverAndInstall(
                    getServer().getServicesManager(),
                    configuration,
                    mode::get,
                    () -> muteEnforcement,
                    freezeManager,
                    visibilityService,
                    chatContext
            );
            if (discovery.integration().isEmpty()) {
                featureIssues.put("rosechat", discovery.issue());
                return;
            }
            roseChatIntegration = discovery.integration().orElseThrow();
            featureIssues.remove("rosechat");
        } catch (IllegalArgumentException exception) {
            featureIssues.put("rosechat", "RoseChat channel configuration is invalid");
            getLogger().log(Level.SEVERE, "RoseChat integration configuration failed", exception);
        }
    }

    private void initializeModerationIntegrations() {
        marketIntegration = MarketIntegration.discover(
                getServer().getServicesManager(),
                getServer().getPluginManager().isPluginEnabled("EnthusiaMarket")
        );
        reputationIntegration = ReputationIntegration.discover(
                getServer().getServicesManager(),
                getServer().getPluginManager().isPluginEnabled("EnthusiaCommend")
        );
        if (marketIntegration.availability()
                == net.enthusia.staff.domain.evidence.IntegrationAvailability.INCOMPATIBLE) {
            featureIssues.put("market", marketIntegration.issue());
        }
        if (reputationIntegration.availability()
                == net.enthusia.staff.domain.evidence.IntegrationAvailability.INCOMPATIBLE) {
            featureIssues.put("reputation", reputationIntegration.issue());
        }
    }

    private void publishHealth(OperationalMode currentMode, Map<String, String> dynamicIssues) {
        Map<String, String> combined = new java.util.LinkedHashMap<>(featureIssues);
        combined.putAll(dynamicIssues);
        health.update(currentMode, combined);
    }

    private void registerCommands() {
        registerStatusCommand();
        PunishmentCommand punishment = new PunishmentCommand(
                this,
                Clock.systemUTC(),
                this::effectiveWriteMode,
                () -> punishmentService,
                () -> playerDirectory,
                reasonPolicies,
                workers
        );
        for (String name : java.util.List.of("punish", "ban", "mute", "warn", "kick", "ipban")) {
            PluginCommand command = Objects.requireNonNull(getCommand(name), name + " command is missing from plugin.yml");
            command.setExecutor(punishment);
            command.setTabCompleter(punishment);
        }
        SanctionChangeCommand changes = new SanctionChangeCommand(
                this,
                this::effectiveWriteMode,
                () -> sanctionChangeService,
                () -> playerDirectory,
                () -> caseLookup,
                workers
        );
        for (String name : java.util.List.of(
                "removepunishment", "unban", "unmute", "removewarning", "unwarn"
        )) {
            Objects.requireNonNull(getCommand(name), name + " command is missing from plugin.yml")
                    .setExecutor(changes);
        }
        ReportCommand report = new ReportCommand(
                this,
                Clock.systemUTC(),
                getConfig().getString("network.server-id", "SMP"),
                mode::get,
                () -> playerDirectory,
                () -> reportStore,
                () -> sanctionLookup,
                reasonPolicies,
                chatContext,
                clientEvidenceCollector,
                workers
        );
        PluginCommand reportCommand = Objects.requireNonNull(getCommand("report"), "report command is missing");
        reportCommand.setExecutor(report);
        reportCommand.setTabCompleter(report);
        ClientCommand client = new ClientCommand(
                this,
                clientEvidenceCollector,
                () -> clientEvidenceStore,
                workers
        );
        PluginCommand clientCommand = Objects.requireNonNull(
                getCommand("client"),
                "client command is missing"
        );
        clientCommand.setExecutor(client);
        clientCommand.setTabCompleter(client);
        ReportsCommand reports = new ReportsCommand(this, Clock.systemUTC(), () -> reportStore, workers);
        PluginCommand reportsCommand = Objects.requireNonNull(getCommand("reports"), "reports command is missing");
        reportsCommand.setExecutor(reports);
        reportsCommand.setTabCompleter(reports);
        FreezeCommand freezes = new FreezeCommand(
                this,
                Clock.systemUTC(),
                this::effectiveWriteMode,
                () -> playerDirectory,
                () -> freezeStore,
                freezeManager,
                workers
        );
        Objects.requireNonNull(getCommand("freeze"), "freeze command is missing").setExecutor(freezes);
        Objects.requireNonNull(getCommand("unfreeze"), "unfreeze command is missing").setExecutor(freezes);
        Objects.requireNonNull(getCommand("staff"), "staff command is missing")
                .setExecutor(new StaffModeCommand(this::effectiveWriteMode, staffModeManager));
        Objects.requireNonNull(getCommand("vanish"), "vanish command is missing")
                .setExecutor(new VanishCommand(this::effectiveWriteMode, vanishManager));
        Objects.requireNonNull(getCommand("staffchat"), "staffchat command is missing")
                .setExecutor(new StaffChatCommand(() -> roseChatIntegration));
        InventoryCommand inventory = new InventoryCommand(
                this,
                Clock.systemUTC(),
                () -> playerDirectory,
                inventoryCoordinator,
                workers
        );
        for (String name : java.util.List.of("invsee", "endersee")) {
            PluginCommand command = Objects.requireNonNull(getCommand(name), name + " command is missing");
            command.setExecutor(inventory);
            command.setTabCompleter(inventory);
        }
        InspectCommand inspect = new InspectCommand(
                this,
                Clock.systemUTC(),
                () -> playerDirectory,
                () -> caseLookup,
                () -> economyCoordinator,
                () -> confiscationCoordinator,
                inventoryCoordinator,
                () -> marketIntegration,
                () -> reputationIntegration,
                workers
        );
        PluginCommand inspectCommand = Objects.requireNonNull(
                getCommand("inspect"),
                "inspect command is missing"
        );
        inspectCommand.setExecutor(inspect);
        inspectCommand.setTabCompleter(inspect);
        Objects.requireNonNull(getCommand("case"), "case command is missing")
                .setExecutor(new CaseCommand(
                        this,
                        () -> caseLookup,
                        () -> confiscationCoordinator,
                        workers
                ));
    }

    private OperationalMode effectiveWriteMode() {
        OperationalMode authoritative = mode.get();
        return authoritative == OperationalMode.ACTIVE && !channelConnected.get()
                ? OperationalMode.DEGRADED
                : authoritative;
    }

    private void initializeChannel(MariaDbRuntime runtime) {
        if (!getConfig().getBoolean("channel.enabled", false)) {
            getLogger().warning("Persistent Velocity channel is disabled; new punishment writes remain disabled");
            return;
        }
        String backendId = getConfig().getString("network.server-id", "SMP");
        String proxyId = getConfig().getString("channel.proxy-id", "VELOCITY");
        String backendEnvironment = getConfig().getString("channel.backend-secret-environment");
        String proxyEnvironment = getConfig().getString("channel.proxy-secret-environment");
        if (backendId == null || proxyId == null || backendEnvironment == null || proxyEnvironment == null) {
            throw new IllegalArgumentException("Persistent channel identifiers and secret environments are required");
        }
        SecretKey backendKey = secretFromEnvironment(backendEnvironment);
        SecretKey proxyKey = secretFromEnvironment(proxyEnvironment);
        channelClient = new PersistentChannelClient(
                backendId,
                getConfig().getString("channel.host", "127.0.0.1"),
                getConfig().getInt("channel.port", 28_765),
                backendKey,
                proxyId,
                proxyKey,
                Clock.systemUTC(),
                envelope -> handleNetworkMessage(runtime, backendId, envelope),
                state -> channelConnected.set("CONNECTED".equals(state))
        );
        channelClient.start();
    }

    private boolean handleNetworkMessage(
            MariaDbRuntime runtime,
            String backendId,
            net.enthusia.staff.protocol.ProtocolEnvelope envelope
    ) {
        runtime.networkOutboxStore().recordInboxOnce(
                backendId,
                envelope.messageId(),
                envelope.messageType(),
                "{\"outcome\":\"applied\"}",
                Clock.systemUTC().instant()
        );
        if ("PUNISHMENT_CREATED".equals(envelope.messageType())
                || "SANCTION_CHANGED".equals(envelope.messageType())
                || "ALT_SANCTION_INHERITED".equals(envelope.messageType())) {
            try {
                JsonNode payload = json.readTree(envelope.payloadJson());
                UUID target = UUID.fromString(payload.path("targetId").asText());
                if (muteEnforcement != null) {
                    muteEnforcement.invalidate(target);
                }
            } catch (java.io.IOException | IllegalArgumentException exception) {
                throw new IllegalArgumentException("Network sanction message has an invalid target", exception);
            }
        }
        return true;
    }

    private static SecretKey secretFromEnvironment(String environment) {
        return SecretKeyMaterial.hmacSha256FromBase64(System.getenv(environment));
    }

    private boolean loadReasonPolicies() {
        File file = new File(getDataFolder(), "reason-policies.yml");
        try {
            ReasonPolicyConfigurationLoader.LoadedPolicies loaded = new ReasonPolicyConfigurationLoader().load(file.toPath());
            reasonPolicies = new AtomicReasonPolicyRepository(loaded.version(), loaded.policies());
            return true;
        } catch (ConfigurationValidationException exception) {
            setDegraded("reason-policies", exception.getMessage());
            getLogger().log(Level.SEVERE, "Punishment policy validation failed; punishment commands are disabled", exception);
            return false;
        }
    }

    private void registerServices() {
        getServer().getServicesManager().register(
                StaffVisibilityService.class,
                visibilityService,
                this,
                ServicePriority.Normal
        );
        getServer().getServicesManager().register(
                net.enthusia.staff.paper.api.InventoryLockService.class,
                inventoryCoordinator,
                this,
                ServicePriority.Normal
        );
        getServer().getServicesManager().register(
                net.enthusia.staff.paper.api.StaffModeQueryService.class,
                staffModeManager::active,
                this,
                ServicePriority.Normal
        );
        getServer().getServicesManager().register(
                net.enthusia.staff.paper.api.StaffSessionService.class,
                staffModeManager::active,
                this,
                ServicePriority.Normal
        );
    }

    private Map<net.enthusia.staff.domain.auth.StaffRank, java.util.Set<net.enthusia.staff.domain.auth.StaffRank>>
            loadVisibilityMatrix() {
        Map<net.enthusia.staff.domain.auth.StaffRank, java.util.Set<net.enthusia.staff.domain.auth.StaffRank>>
                defaults = DefaultStaffVisibilityService.defaultMatrix();
        Map<net.enthusia.staff.domain.auth.StaffRank, java.util.Set<net.enthusia.staff.domain.auth.StaffRank>>
                configured = new java.util.EnumMap<>(net.enthusia.staff.domain.auth.StaffRank.class);
        try {
            for (net.enthusia.staff.domain.auth.StaffRank viewer : new net.enthusia.staff.domain.auth.StaffRank[]{
                    net.enthusia.staff.domain.auth.StaffRank.MOD,
                    net.enthusia.staff.domain.auth.StaffRank.DEVELOPER,
                    net.enthusia.staff.domain.auth.StaffRank.ADMIN,
                    net.enthusia.staff.domain.auth.StaffRank.FOUNDER
            }) {
                java.util.Set<net.enthusia.staff.domain.auth.StaffRank> targets =
                        new java.util.LinkedHashSet<>();
                for (String value : getConfig().getStringList("visibility.matrix." + viewer.name())) {
                    net.enthusia.staff.domain.auth.StaffRank target =
                            net.enthusia.staff.domain.auth.StaffRank.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                    if (target == net.enthusia.staff.domain.auth.StaffRank.SYSTEM) {
                        throw new IllegalArgumentException("SYSTEM is not a visible staff rank");
                    }
                    targets.add(target);
                }
                configured.put(viewer, targets.isEmpty() ? defaults.get(viewer) : java.util.Set.copyOf(targets));
            }
            return Map.copyOf(configured);
        } catch (IllegalArgumentException exception) {
            featureIssues.put("visibility", "Configured rank visibility matrix is invalid; safe defaults are active");
            getLogger().log(Level.SEVERE, "Vanish visibility matrix validation failed", exception);
            return defaults;
        }
    }
}
