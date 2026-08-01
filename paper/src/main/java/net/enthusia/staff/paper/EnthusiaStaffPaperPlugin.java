package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.OperationalStateStore;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import net.enthusia.staff.paper.report.ChatContextBuffer;
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
    private PaperResourceCloser resources;

    @Override
    public void onEnable() {
        resources = new PaperResourceCloser(getLogger());
        saveDefaultConfig();
        saveResource("reason-policies.yml", false);
        boolean policiesReady = loadReasonPolicies();
        int threads = getConfig().getInt("workers.threads", 4);
        int queueCapacity = getConfig().getInt("workers.queue-capacity", 256);
        workers = BoundedExecutorFactory.create(threads, queueCapacity);
        runtimeComponents = createRuntimeComponents();
        integrations = createIntegrationManager();
        integrations.initializeEconomy();
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
            integrations.initializeRoseChat();
            workers.execute(this::initializeStorage);
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

    private void initializeStorage() {
        if (lifecycle.stopping()) {
            return;
        }
        Optional<DatabaseConfig> database = new PaperDatabaseConfiguration(getConfig(), System::getenv).load();
        if (database.isEmpty()) {
            degradeBootstrap(
                    "Required database environment variables are missing; destructive actions are disabled"
            );
            return;
        }
        MariaDbRuntime opened = null;
        boolean published = false;
        try {
            opened = MariaDb.initialize(database.orElseThrow());
            PaperStorageBindings bindings = PaperStorageBindings.create(
                    opened,
                    authorizationPolicy,
                    reasonPolicies
            );
            if (!lifecycle.publishStorage(bindings)) {
                resources.close("MariaDB runtime opened during shutdown", opened);
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
        if (!runtimeComponents.recoverOnlinePlayers(
                getServer().getOnlinePlayers(),
                () -> !lifecycle.stopping()
        )) {
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

    private void discardFailedStorageRuntime(MariaDbRuntime opened, boolean published) {
        if (opened == null) {
            return;
        }
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

    private void publishBootstrapMode(OperationalMode next, Map<String, String> issues) {
        lifecycle.runIfRunning(() -> {
            mode.set(next);
            publishHealth(next, issues);
            if (getLogger().isLoggable(Level.INFO)) {
                getLogger().info("Storage verified; EnthusiaStaff entered " + next);
            }
        });
    }

    private void setDegraded(String component, String reason) {
        lifecycle.runIfRunning(() -> {
            mode.set(OperationalMode.DEGRADED);
            publishHealth(OperationalMode.DEGRADED, Map.of(component, reason));
            getLogger().warning(reason);
        });
    }

    private void publishHealth(OperationalMode currentMode, Map<String, String> dynamicIssues) {
        Map<String, String> combined = new java.util.LinkedHashMap<>(featureIssues);
        combined.putAll(dynamicIssues);
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
                state -> channelConnected.set(!lifecycle.stopping() && "CONNECTED".equals(state))
        ).ifPresent(started -> {
            if (!lifecycle.publishChannel(started)) {
                resources.close("persistent Velocity channel opened during shutdown", started);
            }
        });
    }

    private boolean loadReasonPolicies() {
        Optional<AtomicReasonPolicyRepository> loaded = new PaperReasonPolicyBootstrap(getLogger()).load(
                getDataFolder().toPath().resolve("reason-policies.yml"),
                reason -> setDegraded("reason-policies", reason)
        );
        loaded.ifPresent(policy -> reasonPolicies = policy);
        return loaded.isPresent();
    }

}
