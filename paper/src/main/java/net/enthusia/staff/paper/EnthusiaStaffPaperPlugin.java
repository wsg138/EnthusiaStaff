package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rosewood.rosechat.api.staff.StaffChannelConfiguration;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.OperationalStateStore;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import net.enthusia.staff.paper.automod.AutomodListener;
import net.enthusia.staff.paper.automod.StrictVariantMatcher;
import net.enthusia.staff.paper.command.EstaffCommand;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.economy.CurrencyAssetSource;
import net.enthusia.staff.paper.economy.CurrencyGateway;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.economy.EnthusiaCurrencyGateway;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.report.ReportEvidenceMaintenance;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryOperationContext;
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
    private final AuthorizationPolicy authorizationPolicy = new DefaultAuthorizationPolicy();
    private final RuntimeHealth health = new RuntimeHealth();
    private final AtomicReference<OperationalMode> mode = new AtomicReference<>(OperationalMode.BOOTSTRAP);
    private final ConcurrentHashMap<String, String> featureIssues = new ConcurrentHashMap<>();
    private final PaperRuntimeLifecycle<PaperStorageBindings, ScheduledTask, PersistentChannelClient> lifecycle =
            new PaperRuntimeLifecycle<>();

    private ExecutorService workers;
    private AtomicReasonPolicyRepository reasonPolicies;
    private MuteEnforcementListener muteEnforcement;
    private final AtomicBoolean channelConnected = new AtomicBoolean();
    private final ObjectMapper json = new ObjectMapper();
    private ChatContextBuffer chatContext;
    private FreezeManager freezeManager;
    private StaffModeManager staffModeManager;
    private DefaultStaffVisibilityService visibilityService;
    private VanishManager vanishManager;
    private InventoryOperationContext inventoryOperationContext;
    private InventoryCoordinator inventoryCoordinator;
    private EconomyCoordinator economyCoordinator;
    private ConfiscationCoordinator confiscationCoordinator;
    private RoseChatIntegration roseChatIntegration;
    private ClientEvidenceCollector clientEvidenceCollector;
    private MarketIntegration marketIntegration;
    private ReputationIntegration reputationIntegration;
    private ReportEvidenceMaintenance reportEvidenceMaintenance;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("reason-policies.yml", false);
        boolean policiesReady = loadReasonPolicies();
        int threads = getConfig().getInt("workers.threads", 4);
        int queueCapacity = getConfig().getInt("workers.queue-capacity", 256);
        workers = BoundedExecutorFactory.create(threads, queueCapacity);
        reportEvidenceMaintenance = new ReportEvidenceMaintenance(
                Clock.systemUTC(),
                () -> storageValue(PaperStorageBindings::reportStore),
                getLogger()
        );
        freezeManager = new FreezeManager(
                this,
                Clock.systemUTC(),
                () -> storageValue(PaperStorageBindings::freezeStore),
                workers
        );
        getServer().getPluginManager().registerEvents(freezeManager, this);
        staffModeManager = new StaffModeManager(
                this,
                Clock.systemUTC(),
                networkServerId(),
                () -> storageValue(PaperStorageBindings::staffSessionStore),
                workers
        );
        getServer().getPluginManager().registerEvents(staffModeManager, this);
        visibilityService = new DefaultStaffVisibilityService(loadVisibilityMatrix());
        vanishManager = new VanishManager(
                this,
                Clock.systemUTC(),
                visibilityService,
                () -> storageValue(PaperStorageBindings::vanishStore),
                () -> storageValue(PaperStorageBindings::staffSessionStore),
                staffModeManager,
                workers
        );
        staffModeManager.setExitListener(vanishManager::staffModeExited);
        getServer().getPluginManager().registerEvents(vanishManager, this);
        inventoryOperationContext = new InventoryOperationContext(
                Clock.systemUTC(),
                inventoryScopeId(),
                networkServerId()
        );
        inventoryCoordinator = new InventoryCoordinator(
                this,
                inventoryOperationContext,
                this::effectiveWriteMode,
                () -> storageValue(PaperStorageBindings::inventoryJournalStore),
                () -> storageValue(PaperStorageBindings::playerDirectory),
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
                    networkServerId(),
                    mode::get,
                    () -> storageValue(PaperStorageBindings::sanctionLookup),
                    () -> storageValue(PaperStorageBindings::playerDirectory),
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
        stopOperationalRuntime();
        closeSafely("RoseChat bridge", roseChatIntegration);
        closeSafely("mute enforcement", muteEnforcement);
        closeSafely("inventory coordinator", inventoryCoordinator);
        closeSafely("economy coordinator", economyCoordinator);
        closeSafely("confiscation coordinator", confiscationCoordinator);
        shutdownWorkers();
        closeDatabaseRuntime();
    }

    public OperationalMode operationalMode() {
        return mode.get();
    }

    public ModerationStore moderationStore() {
        return storageValue(PaperStorageBindings::moderationStore);
    }

    public ExecutorService workers() {
        return workers;
    }

    private void initializeStorage() {
        if (lifecycle.stopping()) {
            return;
        }
        String url = environment("storage.jdbc-url-environment");
        String username = environment("storage.username-environment");
        String password = environment("storage.password-environment");
        if (url == null || username == null || password == null) {
            degradeBootstrap(
                    "Required database environment variables are missing; destructive actions are disabled"
            );
            return;
        }
        MariaDbRuntime opened = null;
        boolean published = false;
        try {
            DatabaseConfig database = new DatabaseConfig(
                    url,
                    username,
                    password,
                    getConfig().getInt("storage.maximum-pool-size", 8),
                    getConfig().getLong("storage.connection-timeout-millis", 5_000)
            );
            opened = MariaDb.initialize(database);
            PaperStorageBindings bindings = PaperStorageBindings.create(
                    opened,
                    authorizationPolicy,
                    reasonPolicies
            );
            if (!lifecycle.publishStorage(bindings)) {
                closeSafely("MariaDB runtime opened during shutdown", opened);
                return;
            }
            published = true;
            finishStorageInitialization(bindings);
        } catch (RuntimeException exception) {
            discardFailedStorageRuntime(opened, published);
            if (!lifecycle.stopping()) {
                getLogger().log(Level.SEVERE, "MariaDB bootstrap failed; destructive actions are disabled", exception);
                setDegraded("mariadb", "Connection or schema validation failed; see the sanitized console error");
            }
        }
    }

    private void finishStorageInitialization(PaperStorageBindings bindings) {
        if (lifecycle.stopping()) {
            return;
        }
        getServer().getOnlinePlayers().forEach(freezeManager::verify);
        if (lifecycle.stopping()) {
            return;
        }
        getServer().getOnlinePlayers().forEach(staffModeManager::recover);
        if (lifecycle.stopping()) {
            return;
        }
        vanishManager.initialize();
        if (lifecycle.stopping()) {
            return;
        }
        promoteAfterBootstrap(bindings.runtime());
        if (lifecycle.stopping()) {
            return;
        }
        try {
            startChannelClient(bindings.runtime());
        } catch (RuntimeException exception) {
            channelConnected.set(false);
            getLogger().log(Level.SEVERE,
                    "Persistent Velocity channel initialization failed; new punishment writes are disabled",
                    exception);
        }
        if (!lifecycle.stopping()) {
            registerOperationalStateTask();
        }
    }

    private void registerOperationalStateTask() {
        ScheduledTask task = getServer().getAsyncScheduler().runAtFixedRate(
                this,
                ignored -> runOperationalTasks(),
                5,
                5,
                TimeUnit.SECONDS
        );
        if (!lifecycle.publishTask(task)) {
            cancelTask(task);
        }
    }

    private void runOperationalTasks() {
        refreshOperationalState();
        if (workers == null || workers.isShutdown()) {
            return;
        }
        try {
            workers.execute(reportEvidenceMaintenance);
        } catch (RejectedExecutionException exception) {
            getLogger().log(
                    Level.FINE,
                    "Report evidence maintenance was rejected; the next operational tick will retry",
                    exception
            );
        }
    }

    private void degradeBootstrap(String reason) {
        if (!lifecycle.stopping()) {
            setDegraded("mariadb", reason);
        }
    }

    private void discardFailedStorageRuntime(MariaDbRuntime opened, boolean published) {
        if (opened == null) {
            return;
        }
        if (!published) {
            closeSafely("partially initialized MariaDB runtime", opened);
            return;
        }
        Optional<PaperStorageBindings> removed =
                lifecycle.removeStorageIf(bindings -> bindings.runtime() == opened);
        if (removed.isPresent()) {
            cancelOperationalStateTask();
            closeChannelClient();
            closeSafely("partially initialized MariaDB runtime", opened);
        }
    }

    private void stopOperationalRuntime() {
        lifecycle.beginShutdown(() -> {
            mode.set(OperationalMode.MAINTENANCE);
            publishHealth(OperationalMode.MAINTENANCE, Map.of("shutdown", "Runtime is shutting down"));
        });
        cancelOperationalStateTask();
        closeChannelClient();
    }

    private void cancelOperationalStateTask() {
        lifecycle.removeTask().ifPresent(this::cancelTask);
    }

    private void cancelTask(ScheduledTask task) {
        try {
            task.cancel();
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Operational-state task cleanup failed", exception);
        }
    }

    private void closeChannelClient() {
        channelConnected.set(false);
        lifecycle.removeChannel()
                .ifPresent(client -> closeSafely("persistent Velocity channel", client));
    }

    private void shutdownWorkers() {
        ExecutorService executor = workers;
        if (executor == null) {
            return;
        }
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("Worker tasks did not terminate within the shutdown deadline");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } catch (RuntimeException exception) {
            try {
                executor.shutdownNow();
            } catch (RuntimeException forcedFailure) {
                exception.addSuppressed(forcedFailure);
            }
            getLogger().log(Level.WARNING, "Worker executor cleanup failed", exception);
        }
    }

    private void closeDatabaseRuntime() {
        Optional<PaperStorageBindings> current = lifecycle.removeStorage();
        current.ifPresent(bindings -> closeSafely("MariaDB runtime", bindings.runtime()));
    }

    private void closeSafely(String component, AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            getLogger().log(Level.WARNING, component + " cleanup was interrupted", exception);
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, component + " cleanup failed", exception);
        }
    }

    private <T> T storageValue(Function<PaperStorageBindings, T> selector) {
        return lifecycle.storageValue(selector);
    }

    private void refreshOperationalState() {
        Optional<PaperStorageBindings> current = lifecycle.storage();
        if (current.isEmpty() || lifecycle.stopping()) {
            return;
        }
        MariaDbRuntime runtime = current.orElseThrow().runtime();
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

    private void promoteAfterBootstrap(MariaDbRuntime runtime) {
        OperationalStateStore states = runtime.operationalStateStore();
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
        Map<String, String> issues = switch (next) {
            case ACTIVE -> Map.of();
            case SHADOW_MIGRATION -> Map.of(
                    "authority", "LiteBans remains authoritative; EnthusiaStaff enforcement is disabled");
            case MAINTENANCE -> Map.of("maintenance", "Maintenance mode blocks sensitive state changes");
            default -> Map.of("mode", "Sensitive actions are disabled in " + next);
        };
        publishBootstrapMode(next, issues);
    }

    private void publishBootstrapMode(OperationalMode next, Map<String, String> issues) {
        lifecycle.runIfRunning(() -> {
            mode.set(next);
            publishHealth(next, issues);
            getLogger().info("Storage verified; EnthusiaStaff entered " + next);
        });
    }

    private String environment(String configurationPath) {
        String variable = getConfig().getString(configurationPath);
        return variable == null || variable.isBlank() ? null : System.getenv(variable);
    }

    private void setDegraded(String component, String reason) {
        lifecycle.runIfRunning(() -> {
            mode.set(OperationalMode.DEGRADED);
            publishHealth(OperationalMode.DEGRADED, Map.of(component, reason));
            getLogger().warning(reason);
        });
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
                () -> storageValue(PaperStorageBindings::punishmentService),
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
                    networkServerId(),
                    this::effectiveWriteMode,
                    authorizationPolicy,
                    () -> storageValue(PaperStorageBindings::economyJournalStore),
                    workers,
                    discovery.gateway().orElseThrow(),
                    order,
                    json
            );
            CurrencyGateway currencyGateway = discovery.gateway().orElseThrow();
            confiscationCoordinator = new ConfiscationCoordinator(
                    this,
                    inventoryOperationContext,
                    this::effectiveWriteMode,
                    authorizationPolicy,
                    () -> storageValue(PaperStorageBindings::inventoryJournalStore),
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
                getServer().getPluginManager().isPluginEnabled("EnthusiaCommend"),
                authorizationPolicy
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
        new PaperCommandRegistrar(new PaperCommandRegistrar.Dependencies(
                new PaperCommandRegistrar.Environment(this, Clock.systemUTC(), networkServerId(), workers),
                new PaperCommandRegistrar.Policy(mode::get, this::effectiveWriteMode, authorizationPolicy, reasonPolicies),
                lifecycle::storage,
                new PaperCommandRegistrar.PlayerComponents(
                        freezeManager, staffModeManager, vanishManager, inventoryCoordinator
                ),
                new PaperCommandRegistrar.IntegrationSuppliers(
                        () -> economyCoordinator,
                        () -> confiscationCoordinator,
                        () -> roseChatIntegration,
                        () -> marketIntegration,
                        () -> reputationIntegration
                ),
                new PaperCommandRegistrar.EvidenceComponents(chatContext, clientEvidenceCollector)
        )).register();
    }

    private String networkServerId() {
        return getConfig().getString("network.server-id", "SMP");
    }

    private String inventoryScopeId() {
        return getConfig().getString("inventory.scope-id", networkServerId());
    }

    private OperationalMode effectiveWriteMode() {
        OperationalMode authoritative = mode.get();
        return authoritative == OperationalMode.ACTIVE && !channelConnected.get()
                ? OperationalMode.DEGRADED
                : authoritative;
    }

    private void startChannelClient(MariaDbRuntime runtime) {
        PaperPersistentChannelFactory.start(
                this,
                (backendId, envelope) -> handleNetworkMessage(runtime, backendId, envelope),
                state -> channelConnected.set(!lifecycle.stopping() && "CONNECTED".equals(state))
        ).ifPresent(started -> {
            if (!lifecycle.publishChannel(started)) {
                closeSafely("persistent Velocity channel opened during shutdown", started);
            }
        });
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
