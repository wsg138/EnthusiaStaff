package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
<<<<<<< ours
import java.nio.file.Path;
=======
>>>>>>> theirs
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
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
<<<<<<< ours
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.CaseLookup;
=======
>>>>>>> theirs
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.OperationalStateStore;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
<<<<<<< ours
import net.enthusia.staff.paper.alert.PunishmentRequestAlertController;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertLifecycle;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.automod.AutomodListener;
import net.enthusia.staff.paper.automod.StrictVariantMatcher;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.command.CaseCommand;
import net.enthusia.staff.paper.command.ClientCommand;
import net.enthusia.staff.paper.command.EstaffCommand;
import net.enthusia.staff.paper.command.FreezeCommand;
import net.enthusia.staff.paper.command.InspectCommand;
import net.enthusia.staff.paper.command.InventoryCommand;
import net.enthusia.staff.paper.command.PunishmentCommand;
import net.enthusia.staff.paper.command.PunishmentRequestCommandHandler;
import net.enthusia.staff.paper.command.ReportCommand;
import net.enthusia.staff.paper.command.ReportsCommand;
import net.enthusia.staff.paper.command.SanctionChangeCommand;
import net.enthusia.staff.paper.command.StaffChatCommand;
import net.enthusia.staff.paper.command.StaffModeCommand;
import net.enthusia.staff.paper.command.VanishCommand;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.PaperConfigurationLoader;
import net.enthusia.staff.paper.config.PaperConfigurationSnapshot;
import net.enthusia.staff.paper.config.PaperConfigurationValidationException;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import net.enthusia.staff.paper.config.RestartRequiredConfiguration;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadCoordinator;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadResult;
import net.enthusia.staff.paper.economy.CurrencyAssetSource;
import net.enthusia.staff.paper.economy.CurrencyGateway;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.economy.EnthusiaCurrencyGateway;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.inventory.InventoryOperationContext;
import net.enthusia.staff.paper.punishment.PunishmentGuiController;
import net.enthusia.staff.paper.punishment.PunishmentRequestGuiController;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.sanction.SanctionChangeGuiController;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService;
import net.enthusia.staff.paper.visibility.VanishManager;
=======
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.report.ChatContextBuffer;
>>>>>>> theirs
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.protocol.PersistentChannelClient;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnthusiaStaffPaperPlugin extends JavaPlugin {
    private final AuthorizationPolicy authorizationPolicy = new DefaultAuthorizationPolicy();
    private final RuntimeHealth health = new RuntimeHealth();
    private final AtomicReference<OperationalMode> mode = new AtomicReference<>(OperationalMode.BOOTSTRAP);
    private final ConcurrentHashMap<String, String> featureIssues = new ConcurrentHashMap<>();
    private final AtomicReference<Map<String, String>> operationalIssues = new AtomicReference<>(
            Map.of("bootstrap", "Initialization has not completed")
    );
    private final PaperRuntimeLifecycle<PaperStorageBindings, ScheduledTask, PersistentChannelClient> lifecycle =
            new PaperRuntimeLifecycle<>();

    private ExecutorService workers;
    private AtomicReasonPolicyRepository reasonPolicies;
    private MuteEnforcementListener muteEnforcement;
    private final AtomicBoolean channelConnected = new AtomicBoolean();
    private final ObjectMapper json = new ObjectMapper();
    private ChatContextBuffer chatContext;
    private PaperRuntimeComponents runtimeComponents;
    private PaperIntegrationManager integrations;
    private ClientEvidenceCollector clientEvidenceCollector;
<<<<<<< ours
    private MarketIntegration marketIntegration;
    private ReputationIntegration reputationIntegration;
    private PaperStorageBootstrapCoordinator<PreparedStorage> storageBootstrap;
    private PaperConfigurationLoader configurationLoader;
    private PaperConfigurationSnapshot configurationSnapshot;
    private PunishmentRequestAlertController alertController;
    private ConfigurationReloadCoordinator reloadCoordinator;
=======
    private PaperResourceCloser resources;
>>>>>>> theirs

    @Override
    public void onEnable() {
        resources = new PaperResourceCloser(getLogger());
        saveDefaultConfig();
        configurationLoader = new PaperConfigurationLoader();
        try {
            configurationSnapshot = configurationLoader.load(configurationFile(), dataDirectory());
        } catch (PaperConfigurationValidationException exception) {
            publishStartupConfigurationFailure(exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveResource("reason-policies.yml", false);
        boolean policiesReady = loadReasonPolicies();
<<<<<<< ours
        RestartRequiredConfiguration bootstrap = configurationSnapshot.restartRequired();
        workers = BoundedExecutorFactory.create(bootstrap.workerThreads(), bootstrap.workerQueueCapacity());
        initializeAlertController();
        if (policiesReady) {
            reloadCoordinator = new ConfigurationReloadCoordinator(
                    configurationSnapshot,
                    () -> configurationLoader.load(configurationFile(), dataDirectory()),
                    () -> new ReasonPolicyConfigurationLoader().load(reasonPolicyFile()),
                    reasonPolicies,
                    alertController,
                    this::logRejectedConfiguration,
                    this::publishReloadIssue
            );
        }
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
=======
        int threads = getConfig().getInt("workers.threads", 4);
        int queueCapacity = getConfig().getInt("workers.queue-capacity", 256);
        workers = BoundedExecutorFactory.create(threads, queueCapacity);
        runtimeComponents = createRuntimeComponents();
        integrations = createIntegrationManager();
        integrations.initializeEconomy();
>>>>>>> theirs
        clientEvidenceCollector = ClientEvidenceCollector.discover(this, Clock.systemUTC());
        integrations.initializeModerationProviders();
        if (!runtimeComponents.staffMode().combat().availableWhenRequired()) {
            featureIssues.put("combatlogx", "CombatLogX is present but its combat-query API is unavailable");
        }
        if (policiesReady) {
            chatContext = new ChatContextBuffer(Clock.systemUTC());
            getServer().getPluginManager().registerEvents(chatContext, this);
            integrations.initializeAutomod();
        }
        if (policiesReady) {
            registerCommands();
        } else {
            PaperCommandRegistrar.registerStatus(this, health);
        }
        runtimeComponents.registerServices(this);
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
<<<<<<< ours
            initializeRoseChatIntegration();
            startStorageBootstrap();
=======
            integrations.initializeRoseChat();
            workers.execute(this::initializeStorage);
>>>>>>> theirs
        }
    }

    @Override
    public void onDisable() {
        stopOperationalRuntime();
        if (integrations != null) {
            integrations.closeChatBridge();
        }
        resources.close("mute enforcement", muteEnforcement);
        resources.close("inventory coordinator", runtimeComponents == null ? null : runtimeComponents.inventory());
        if (integrations != null) {
            integrations.closeEconomyResources();
        }
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

    private void initializeAlertController() {
        String owner = "paper:" + networkServerId() + ":" + UUID.randomUUID();
        alertController = new PunishmentRequestAlertController(
                owner,
                configurationSnapshot.punishmentRequestAlerts(),
                (leaseOwner, settings, storage) -> new PunishmentRequestAlertLifecycle(
                        this,
                        Clock.systemUTC(),
                        leaseOwner,
                        settings,
                        storage.alerts(),
                        storage.requests(),
                        storage.players(),
                        workers,
                        lifecycle::stopping
                ),
                this::publishAlertStatus
        );
    }

    private Path dataDirectory() {
        return getDataFolder().toPath().toAbsolutePath().normalize();
    }

    private Path configurationFile() {
        return dataDirectory().resolve("config.yml");
    }

    private Path reasonPolicyFile() {
        return dataDirectory().resolve("reason-policies.yml");
    }

    private void publishStartupConfigurationFailure(PaperConfigurationValidationException exception) {
        List<String> errors = exception.errors();
        featureIssues.put("configuration", "Startup configuration validation failed");
        health.update(OperationalMode.DEGRADED, Map.copyOf(featureIssues));
        getLogger().severe("EnthusiaStaff configuration is invalid; the plugin will be disabled");
        for (String error : errors) {
            getLogger().severe("Configuration error: " + error);
        }
<<<<<<< ours
    }

    private ConfigurationReloadAction reloadAction() {
        ConfigurationReloadCoordinator coordinator = reloadCoordinator;
        if (coordinator != null) {
            return coordinator;
        }
        return () -> new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.APPLY_FAILED,
                "Reload is unavailable because initial reason-policy validation did not succeed",
                List.of("Correct reason-policies.yml and perform a full server restart"),
                false
        );
    }

    private void logRejectedConfiguration(List<String> details) {
        getLogger().warning("EnthusiaStaff configuration reload was rejected");
        for (String detail : details) {
            getLogger().warning("Reload detail: " + detail);
        }
    }

    private void publishReloadIssue(String issue) {
        if (issue == null || issue.isBlank()) {
            featureIssues.remove("configuration-reload");
        } else {
            featureIssues.put("configuration-reload", issue);
        }
        refreshHealth(mode.get());
    }

    private void publishAlertStatus(PunishmentRequestAlertController.Status status) {
        if (status.state() == PunishmentRequestAlertController.State.ACTIVE
                || status.state() == PunishmentRequestAlertController.State.CLOSED) {
            featureIssues.remove("punishment-request-alerts");
        } else {
            featureIssues.put("punishment-request-alerts", status.issue());
        }
        refreshHealth(mode.get());
    }

    private void startStorageBootstrap() {
        DatabaseConfig database = databaseConfiguration();
        if (database == null) {
=======
        Optional<DatabaseConfig> database = new PaperDatabaseConfiguration(getConfig(), System::getenv).load();
        if (database.isEmpty()) {
>>>>>>> theirs
            degradeBootstrap(
                    "Required database environment variables are missing; destructive actions are disabled"
            );
            return;
        }
        PaperPersistentChannelFactory.Settings channel = channelSettings();
        StorageBootstrapConfiguration configuration = new StorageBootstrapConfiguration(database, channel);
        PaperStorageBootstrapCoordinator<PreparedStorage> coordinator =
                new PaperStorageBootstrapCoordinator<>(
                        lifecycle::stopping,
                        workers::execute,
                        task -> getServer().getGlobalRegionScheduler().execute(this, task),
                        () -> openStorage(configuration),
                        prepared -> lifecycle.publishStorage(prepared.bindings()),
                        this::completeBukkitStorageInitialization,
                        this::finishStorageInitializationAsynchronously,
                        this::discardPublishedStorage,
                        this::closeUnpublishedStorage,
                        this::handleStorageBootstrapFailure
                );
        storageBootstrap = coordinator;
        coordinator.start();
    }

    private DatabaseConfig databaseConfiguration() {
        RestartRequiredConfiguration bootstrap = configurationSnapshot.restartRequired();
        String url = environmentVariable(bootstrap.storageJdbcUrlEnvironment());
        String username = environmentVariable(bootstrap.storageUsernameEnvironment());
        String password = environmentVariable(bootstrap.storagePasswordEnvironment());
        if (url == null || username == null || password == null) {
            return null;
        }
        return new DatabaseConfig(
                url,
                username,
                password,
                bootstrap.storageMaximumPoolSize(),
                bootstrap.storageConnectionTimeoutMillis()
        );
    }

    private PaperPersistentChannelFactory.Settings channelSettings() {
        try {
<<<<<<< ours
            PaperPersistentChannelFactory.Settings settings = PaperPersistentChannelFactory.snapshot(
                    configurationSnapshot.restartRequired(),
                    dataDirectory()
            );
            if (!settings.enabled()) {
                getLogger().warning(
                        "Persistent Velocity channel is disabled; new punishment writes remain disabled"
                );
            }
            return settings;
        } catch (RuntimeException exception) {
            featureIssues.put("channel", "Persistent Velocity channel configuration is invalid");
            getLogger().log(Level.SEVERE,
                    "Persistent Velocity channel configuration is invalid; new punishment writes are disabled",
                    exception);
            return new PaperPersistentChannelFactory.Settings(
                    false,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    getDataFolder().toPath().toAbsolutePath().normalize()
            );
        }
    }

    private PreparedStorage openStorage(StorageBootstrapConfiguration configuration) {
        MariaDbRuntime opened = null;
        try {
            opened = MariaDb.initialize(configuration.database());
=======
            opened = MariaDb.initialize(database.orElseThrow());
>>>>>>> theirs
            PaperStorageBindings bindings = PaperStorageBindings.create(
                    opened,
                    authorizationPolicy,
                    reasonPolicies
            );
<<<<<<< ours
            BootstrapModeSnapshot bootstrapMode = resolveBootstrapMode(opened);
            return new PreparedStorage(bindings, bootstrapMode, configuration.channel());
=======
            if (!lifecycle.publishStorage(bindings)) {
                resources.close("MariaDB runtime opened during shutdown", opened);
                return;
            }
            published = true;
            finishStorageInitialization(bindings);
>>>>>>> theirs
        } catch (RuntimeException exception) {
            closeSafely("partially initialized MariaDB runtime", opened);
            throw exception;
        }
    }

    private BootstrapModeSnapshot resolveBootstrapMode(MariaDbRuntime runtime) {
        OperationalStateStore states = runtime.operationalStateStore();
        OperationalStateSnapshot persisted = states.current();
        OperationalMode next = persisted.mode();
        if (next == OperationalMode.ACTIVE && !states.hasAuthorizedCutover()) {
            return new BootstrapModeSnapshot(
                    OperationalMode.DEGRADED,
                    Map.of("cutover", "Persistent ACTIVE state has no authorized cutover record; enforcement is blocked")
            );
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
                return new BootstrapModeSnapshot(
                        OperationalMode.DEGRADED,
                        Map.of(
                                "operational-state",
                                "Concurrent bootstrap transition detected; retry after state review"
                        )
                );
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
        return new BootstrapModeSnapshot(next, issues);
    }

<<<<<<< ours
    private void completeBukkitStorageInitialization(PreparedStorage prepared) {
        if (lifecycle.stopping()) {
            return;
        }
        List<PlayerBootstrapSnapshot> online = getServer().getOnlinePlayers().stream()
                .map(player -> new PlayerBootstrapSnapshot(
                        player.getUniqueId(),
                        player.getName(),
                        PaperStaffRankResolver.resolve(player::hasPermission).orElse(null)
                ))
                .toList();
        for (PlayerBootstrapSnapshot player : online) {
            if (lifecycle.stopping()) {
                return;
            }
            freezeManager.verify(player.playerId(), player.playerName());
        }
        for (PlayerBootstrapSnapshot player : online) {
            if (lifecycle.stopping()) {
                return;
            }
            staffModeManager.recover(player.playerId(), player.rank());
        }
        if (lifecycle.stopping()) {
            return;
        }
        vanishManager.initialize();
        if (lifecycle.stopping()) {
            return;
        }
        attachPunishmentRequestAlertStorage(prepared.bindings());
        if (lifecycle.stopping()) {
=======
    private void finishStorageInitialization(PaperStorageBindings bindings) {
        if (!runtimeComponents.recoverOnlinePlayers(
                getServer().getOnlinePlayers(),
                () -> !lifecycle.stopping()
        )) {
>>>>>>> theirs
            return;
        }
        publishBootstrapMode(prepared.bootstrapMode().mode(), prepared.bootstrapMode().issues());
    }

    private void finishStorageInitializationAsynchronously(PreparedStorage prepared) {
        if (lifecycle.stopping()) {
            return;
        }
        try {
            startChannelClient(prepared.bindings().runtime(), prepared.channel());
        } catch (RuntimeException exception) {
            channelConnected.set(false);
            featureIssues.put("channel", "Persistent Velocity connection is unavailable");
            getLogger().log(Level.SEVERE,
                    "Persistent Velocity channel initialization failed; new punishment writes are disabled",
                    exception);
        }
        if (lifecycle.stopping()) {
            return;
        }
        try {
            registerOperationalStateTask();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Operational-state task registration failed", exception);
            setDegraded("operational-state", "Operational state polling could not be scheduled");
        }
    }

    private void attachPunishmentRequestAlertStorage(PaperStorageBindings bindings) {
        if (alertController == null || lifecycle.stopping()) {
            return;
        }
        PunishmentRequestAlertController.ApplyResult result = alertController.attachStorage(
                new PunishmentRequestAlertController.Storage(
                        bindings.punishmentRequestAlertStore(),
                        bindings.punishmentRequestStore(),
                        bindings.playerDirectory()
                )
        );
        if (result.failure() != null) {
            getLogger().log(Level.SEVERE, "Punishment-request alert subsystem startup failed", result.failure());
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
            workers.execute(runtimeComponents.reportEvidenceMaintenance());
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

    private void discardPublishedStorage(PreparedStorage prepared) {
        Optional<PaperStorageBindings> removed = lifecycle.removeStorageIf(
                bindings -> bindings == prepared.bindings()
        );
        if (removed.isEmpty()) {
            return;
        }
<<<<<<< ours
        if (alertController != null) {
            alertController.detachStorage();
        }
        cancelOperationalStateTask();
        closeChannelClient();
        closeSafely("partially published MariaDB runtime", prepared.bindings().runtime());
    }

    private void closeUnpublishedStorage(PreparedStorage prepared) {
        closeSafely("MariaDB runtime opened during shutdown", prepared.bindings().runtime());
    }

    private void handleStorageBootstrapFailure(RuntimeException exception) {
        if (lifecycle.stopping()) {
            return;
        }
        getLogger().log(Level.SEVERE, "MariaDB bootstrap failed; destructive actions are disabled", exception);
        try {
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                if (!lifecycle.stopping()) {
                    setDegraded(
                            "mariadb",
                            "Connection, schema, or Bukkit bootstrap validation failed; see the sanitized console error"
                    );
                }
            });
        } catch (RuntimeException schedulingFailure) {
            exception.addSuppressed(schedulingFailure);
            getLogger().log(Level.SEVERE, "Could not schedule degraded bootstrap publication", schedulingFailure);
=======
        if (!published) {
            resources.close("partially initialized MariaDB runtime", opened);
            return;
        }
        Optional<PaperStorageBindings> removed =
                lifecycle.removeStorageIf(bindings -> bindings.runtime() == opened);
        if (removed.isPresent()) {
            cancelOperationalStateTask();
            closeChannelClient();
            resources.close("partially initialized MariaDB runtime", opened);
>>>>>>> theirs
        }
    }

    private void stopOperationalRuntime() {
        lifecycle.beginShutdown(() -> {
            mode.set(OperationalMode.MAINTENANCE);
            publishHealth(OperationalMode.MAINTENANCE, Map.of("shutdown", "Runtime is shutting down"));
        });
        closeSafely("punishment-request alert controller", alertController);
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
                .ifPresent(client -> resources.close("persistent Velocity channel", client));
    }

    private void shutdownWorkers() {
        if (workers == null) {
            return;
        }
        try {
            workers.shutdown();
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
                if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("Worker tasks did not terminate within the shutdown deadline");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        } catch (RuntimeException exception) {
            try {
                workers.shutdownNow();
            } catch (RuntimeException forcedFailure) {
                exception.addSuppressed(forcedFailure);
            }
            getLogger().log(Level.WARNING, "Worker executor cleanup failed", exception);
        }
    }

    private void closeDatabaseRuntime() {
        Optional<PaperStorageBindings> current = lifecycle.removeStorage();
        current.ifPresent(bindings -> resources.close("MariaDB runtime", bindings.runtime()));
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
            if (!acceptOperationalState(runtime.operationalStateStore(), state)) {
                return;
            }
            OperationalMode previous = mode.getAndSet(state.mode());
            logModeChange(previous, state.mode());
            publishHealth(state.mode(), operationalIssues(state.mode()));
        } catch (RuntimeException exception) {
            handleOperationalStateFailure();
            getLogger().log(Level.SEVERE, "Operational state refresh failed", exception);
        }
    }

<<<<<<< ours
=======
    private boolean acceptOperationalState(OperationalStateStore states, OperationalStateSnapshot state) {
        if (state.mode() != OperationalMode.ACTIVE || states.hasAuthorizedCutover()) {
            return true;
        }
        setDegraded("cutover", "ACTIVE has no authorized cutover record; enforcement remains blocked");
        return false;
    }

    private void logModeChange(OperationalMode previous, OperationalMode current) {
        if (previous != current && getLogger().isLoggable(Level.INFO)) {
            getLogger().info("Operational mode changed from " + previous + " to " + current);
        }
    }

    private Map<String, String> operationalIssues(OperationalMode current) {
        if (current != OperationalMode.ACTIVE) {
            return Map.of("mode", "Sensitive actions are disabled in " + current);
        }
        if (channelConnected.get()) {
            return Map.of();
        }
        return Map.of("channel", "Persistent Velocity connection is unavailable; new punishments are disabled");
    }

    private void handleOperationalStateFailure() {
        if (mode.get() == OperationalMode.ACTIVE) {
            publishHealth(OperationalMode.DEGRADED, Map.of(
                    "operational-state", "State refresh failed; cached active-sanction enforcement remains fail-closed"
            ));
            return;
        }
        setDegraded("operational-state", "Operational state refresh failed; enforcement is disabled");
    }

    private void promoteAfterBootstrap(MariaDbRuntime runtime) {
        OperationalStateStore states = runtime.operationalStateStore();
        OperationalStateSnapshot persisted = states.current();
        Optional<OperationalMode> next = promotionMode(states, persisted);
        next.ifPresent(value -> publishBootstrapMode(value, bootstrapIssues(value)));
    }

    private Optional<OperationalMode> promotionMode(
            OperationalStateStore states,
            OperationalStateSnapshot persisted
    ) {
        if (persisted.mode() == OperationalMode.ACTIVE && !states.hasAuthorizedCutover()) {
            setDegraded("cutover", "Persistent ACTIVE state has no authorized cutover record; enforcement is blocked");
            return Optional.empty();
        }
        if (persisted.mode() != OperationalMode.BOOTSTRAP) {
            return Optional.of(persisted.mode());
        }
        boolean transitioned = states.transition(
                persisted.revision(),
                OperationalMode.SHADOW_MIGRATION,
                null,
                "Schema initialized; LiteBans remains authoritative during shadow migration",
                Clock.systemUTC().instant()
        );
        if (!transitioned) {
            setDegraded("operational-state", "Concurrent bootstrap transition detected; retry after state review");
            return Optional.empty();
        }
        return Optional.of(OperationalMode.SHADOW_MIGRATION);
    }

    private Map<String, String> bootstrapIssues(OperationalMode next) {
        return switch (next) {
            case ACTIVE -> Map.of();
            case SHADOW_MIGRATION -> Map.of(
                    "authority", "LiteBans remains authoritative; EnthusiaStaff enforcement is disabled");
            case MAINTENANCE -> Map.of("maintenance", "Maintenance mode blocks sensitive state changes");
            default -> Map.of("mode", "Sensitive actions are disabled in " + next);
        };
    }

>>>>>>> theirs
    private void publishBootstrapMode(OperationalMode next, Map<String, String> issues) {
        lifecycle.runIfRunning(() -> {
            mode.set(next);
            publishHealth(next, issues);
            if (getLogger().isLoggable(Level.INFO)) {
                getLogger().info("Storage verified; EnthusiaStaff entered " + next);
            }
        });
    }

<<<<<<< ours
    private static String environmentVariable(String variable) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? null : value;
    }

=======
>>>>>>> theirs
    private void setDegraded(String component, String reason) {
        lifecycle.runIfRunning(() -> {
            mode.set(OperationalMode.DEGRADED);
            publishHealth(OperationalMode.DEGRADED, Map.of(component, reason));
            getLogger().warning(reason);
        });
    }

<<<<<<< ours
    private void registerStatusCommand() {
        PluginCommand command = Objects.requireNonNull(getCommand("estaff"), "estaff command is missing from plugin.yml");
        EstaffCommand executor = new EstaffCommand(health, reloadAction());
        command.setExecutor(executor);
        command.setTabCompleter(executor);
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

=======
>>>>>>> theirs
    private void publishHealth(OperationalMode currentMode, Map<String, String> dynamicIssues) {
        operationalIssues.set(Map.copyOf(dynamicIssues));
        refreshHealth(currentMode);
    }

    private void refreshHealth(OperationalMode currentMode) {
        Map<String, String> combined = new java.util.LinkedHashMap<>(featureIssues);
        combined.putAll(operationalIssues.get());
        health.update(currentMode, combined);
    }

    private void registerCommands() {
        PaperCommandRegistrar.registerStatus(this, health);
        new PaperCommandRegistrar(new PaperCommandRegistrar.Dependencies(
                new PaperCommandRegistrar.Environment(this, Clock.systemUTC(), networkServerId(), workers),
                new PaperCommandRegistrar.Policy(mode::get, this::effectiveWriteMode, authorizationPolicy, reasonPolicies),
                lifecycle::storage,
                new PaperCommandRegistrar.PlayerComponents(
                        runtimeComponents.freeze(),
                        runtimeComponents.staffMode(),
                        runtimeComponents.vanish(),
                        runtimeComponents.inventory()
                ),
                new PaperCommandRegistrar.IntegrationSuppliers(
                        integrations::economy,
                        integrations::confiscation,
                        integrations::roseChat,
                        integrations::market,
                        integrations::reputation
                ),
                new PaperCommandRegistrar.EvidenceComponents(chatContext, clientEvidenceCollector)
        )).register();
    }

    private PaperRuntimeComponents createRuntimeComponents() {
        return PaperRuntimeComponents.create(new PaperRuntimeComponents.Dependencies(
                new PaperRuntimeComponents.Environment(
                        this, Clock.systemUTC(), networkServerId(), inventoryScopeId(), workers
                ),
                new PaperRuntimeComponents.Policy(this::effectiveWriteMode),
                new PaperRuntimeComponents.Stores(
                        () -> storageValue(PaperStorageBindings::reportStore),
                        () -> storageValue(PaperStorageBindings::freezeStore),
                        () -> storageValue(PaperStorageBindings::staffSessionStore),
                        () -> storageValue(PaperStorageBindings::vanishStore),
                        () -> storageValue(PaperStorageBindings::inventoryJournalStore),
                        () -> storageValue(PaperStorageBindings::playerDirectory)
                ),
                featureIssues
        ));
    }

    private PaperIntegrationManager createIntegrationManager() {
        return new PaperIntegrationManager(new PaperIntegrationManager.Dependencies(
                new PaperIntegrationManager.Environment(
                        this, Clock.systemUTC(), networkServerId(), workers, json
                ),
                new PaperIntegrationManager.Policy(
                        mode::get, this::effectiveWriteMode, authorizationPolicy, reasonPolicies
                ),
                new PaperIntegrationManager.Stores(
                        () -> storageValue(PaperStorageBindings::punishmentService),
                        () -> storageValue(PaperStorageBindings::economyJournalStore),
                        () -> storageValue(PaperStorageBindings::inventoryJournalStore)
                ),
                new PaperIntegrationManager.PlayerComponents(
                        runtimeComponents.freeze(),
                        runtimeComponents.visibility(),
                        runtimeComponents.inventoryContext(),
                        runtimeComponents.inventory()
                ),
                new PaperIntegrationManager.EvidenceComponents(
                        () -> chatContext, () -> muteEnforcement
                ),
                featureIssues
        ));
    }

    private String networkServerId() {
        return configurationSnapshot.restartRequired().networkServerId();
    }

    private String inventoryScopeId() {
        return configurationSnapshot.restartRequired().inventoryScopeId();
    }

    private OperationalMode effectiveWriteMode() {
        OperationalMode authoritative = mode.get();
        return authoritative == OperationalMode.ACTIVE && !channelConnected.get()
                ? OperationalMode.DEGRADED
                : authoritative;
    }

<<<<<<< ours
    private void startChannelClient(
            MariaDbRuntime runtime,
            PaperPersistentChannelFactory.Settings settings
    ) {
        PaperPersistentChannelFactory.start(
                settings,
                (backendId, envelope) -> handleNetworkMessage(runtime, backendId, envelope),
=======
    private void startChannelClient(MariaDbRuntime runtime) {
        PaperNetworkMessageHandler messageHandler = new PaperNetworkMessageHandler(
                json,
                Clock.systemUTC(),
                playerId -> {
                    MuteEnforcementListener enforcement = muteEnforcement;
                    if (enforcement != null) {
                        enforcement.invalidate(playerId);
                    }
                }
        );
        PaperPersistentChannelFactory.start(
                this,
                (backendId, envelope) -> messageHandler.handle(runtime.networkOutboxStore(), backendId, envelope),
>>>>>>> theirs
                state -> channelConnected.set(!lifecycle.stopping() && "CONNECTED".equals(state))
        ).ifPresent(started -> {
            if (!lifecycle.publishChannel(started)) {
                resources.close("persistent Velocity channel opened during shutdown", started);
            }
        });
    }

    private boolean loadReasonPolicies() {
<<<<<<< ours
        try {
            ReasonPolicyConfigurationLoader.LoadedPolicies loaded =
                    new ReasonPolicyConfigurationLoader().load(reasonPolicyFile());
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
=======
        Optional<AtomicReasonPolicyRepository> loaded = new PaperReasonPolicyBootstrap(getLogger()).load(
                getDataFolder().toPath().resolve("reason-policies.yml"),
                reason -> setDegraded("reason-policies", reason)
>>>>>>> theirs
        );
        loaded.ifPresent(policy -> reasonPolicies = policy);
        return loaded.isPresent();
    }

    private record StorageBootstrapConfiguration(
            DatabaseConfig database,
            PaperPersistentChannelFactory.Settings channel
    ) {
    }

    private record BootstrapModeSnapshot(OperationalMode mode, Map<String, String> issues) {
        private BootstrapModeSnapshot {
            issues = Map.copyOf(issues);
        }
    }

    private record PreparedStorage(
            PaperStorageBindings bindings,
            BootstrapModeSnapshot bootstrapMode,
            PaperPersistentChannelFactory.Settings channel
    ) {
    }

    private record PlayerBootstrapSnapshot(UUID playerId, String playerName, StaffRank rank) {
    }
}
