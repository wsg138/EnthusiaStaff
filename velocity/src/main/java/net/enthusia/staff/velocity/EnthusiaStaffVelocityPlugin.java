package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.net.InetAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.HmacTokenService;
import net.enthusia.staff.common.security.NetworkIdentityProtector;
import net.enthusia.staff.common.security.PrivateRuntimeSecrets;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.migration.CutoverAssessment;
import net.enthusia.staff.domain.migration.CutoverEvidence;
import net.enthusia.staff.domain.migration.DecisionComparison;
import net.enthusia.staff.domain.migration.FounderOverride;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.domain.moderation.CurrentLinkedMinecraftAccount;
import net.enthusia.staff.domain.player.PlayerNames;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.AccountLinkingStore;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.migration.CutoverOutcome;
import net.enthusia.staff.persistence.migration.LiteBansMigrationService;
import net.enthusia.staff.persistence.migration.MigrationExecutionReport;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import net.enthusia.staff.protocol.TlsContextLoader;
import net.enthusia.staff.protocol.TransferSnapshotMessages;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@Plugin(
        id = "enthusiastaff",
        name = "EnthusiaStaff",
        version = "0.1.0-SNAPSHOT",
        description = "Enthusia Network staff and moderation runtime for Velocity",
        authors = {"P2wn"},
        dependencies = {
                @Dependency(id = "velocitab", optional = true),
                @Dependency(id = "luckperms", optional = true)
        }
)
public final class EnthusiaStaffVelocityPlugin {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);
    private static final Set<SanctionType> LOGIN_BLOCKS = Set.of(
            SanctionType.BAN, SanctionType.NETWORK_BAN, SanctionType.NETWORK_IDENTITY_BAN
    );
    private static final String CONFIGURATION_RELOAD_ISSUE = "configuration-reload";
    private static final int INITIAL_BOOTSTRAP_ATTEMPT = 1;
    private static final int ROOT_OPERATION_INDEX = 0;
    private static final int SUB_OPERATION_INDEX = 1;
    private static final int DETAIL_OPERATION_INDEX = 2;
    private static final int TARGET_INDEX = 3;
    private static final int CONFIRMATION_INDEX = 4;
    private static final int SINGLE_OPERATION_ARGUMENT = 1;
    private static final int MINIMUM_ALT_ARGUMENTS = 4;
    private static final int MAX_LINKED_ACCOUNTS_SHOWN = 20;
    private static final int WEBSITE_STATUS_ARGUMENTS = 2;
    private static final int WEBSITE_SHOW_ARGUMENTS = 4;
    private static final int WEBSITE_MUTATION_ARGUMENTS = 5;
    private static final int MIGRATION_ARGUMENTS = 2;
    private static final int MAX_REJECTED_ROWS_SHOWN = 20;
    private static final int CUTOVER_MINIMUM_ARGUMENTS = 2;
    private static final int CUTOVER_ACTIVATION_ARGUMENTS = 3;
    private static final int CUTOVER_REASON_MINIMUM_ARGUMENTS = 4;
    private static final int DISCORD_STATUS_ARGUMENTS = 2;
    private static final int DISCORD_RETRY_ARGUMENTS = 4;
    private static final int DISCORD_RETRY_LIMIT = 500;
    private static final Set<String> DISCORD_DESTINATIONS =
            Set.of("punishments", "reports", "logs-staffmode", "alerts");
    private static final UUID CONSOLE_ACTOR_ID = new UUID(0L, 0L);
    private static final String STAFF_MODE_READY = "STAFF_MODE_READY";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final VelocityRuntimeHealth health = new VelocityRuntimeHealth();
    private final AtomicReference<OperationalMode> authorityMode = new AtomicReference<>(OperationalMode.BOOTSTRAP);
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final AtomicBoolean reloadRunning = new AtomicBoolean();
    private final AtomicBoolean migrationRunning = new AtomicBoolean();
    private final VelocitySecurityEventDispatcher securityEventDispatcher;
    private final VelocityNetworkVerifier networkVerifier;
    private final ObjectMapper json = new ObjectMapper();
    private final java.util.concurrent.ConcurrentHashMap<UUID, CompletableFuture<Void>> presenceUpdates =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final StaffModeReconnectCoordinator staffReconnects = new StaffModeReconnectCoordinator();
    private final StaffModeHandoffTracker staffHandoffs = new StaffModeHandoffTracker();
    private final StaffTransferSnapshotCache transferSnapshots =
            new StaffTransferSnapshotCache(Clock.systemUTC());

    private volatile ExecutorService workers;
    private volatile VelocityConfiguration configuration;
    private volatile MariaDbRuntime databaseRuntime;
    private VelocitabStaffBridge staffTabBridge;
    private VelocityStaffDutyContext staffDutyContext;
    private volatile SanctionLookup sanctionLookup;
    private volatile PlayerDirectory playerDirectory;
    private final VelocityPlayerSuggestions playerSuggestions;
    private volatile FreezeStore freezeStore;
    private volatile StaffSessionStore staffSessionStore;
    private volatile InventoryJournalStore inventoryJournalStore;
    private volatile EconomyJournalStore economyJournalStore;
    private volatile NetworkIdentityStore networkIdentityStore;
    private volatile AccountLinkingStore accountLinkingStore;
    private volatile NetworkIdentityProtector networkIdentityProtector;
    private volatile boolean activeAuthorityObserved;
    private volatile ScheduledTask operationalStateTask;
    private volatile PersistentChannelServer channelServer;
    private volatile VelocityChatBridgeRelay chatBridgeRelay;
    private volatile VelocityChatArtifactRelay chatArtifactRelay;
    private volatile VelocityRenderedChatBridgeRelay renderedChatBridgeRelay;
    private volatile VelocityDiscordChatIngressRelay discordChatIngressRelay;
    private volatile VelocityChatBridgeRelay.Registration chatBridgeSinkRegistration;
    private volatile VelocityChatArtifactRelay.Registration chatArtifactSinkRegistration;
    private volatile VelocityRenderedChatBridgeRelay.Registration renderedChatBridgeSinkRegistration;
    private volatile NetworkOutboxWorker outboxWorker;
    private volatile DiscordOutboxWorker discordOutboxWorker;
    private volatile WebsiteModerationStore websiteModerationStore;
    private volatile WebsiteApiServer websiteApiServer;
    private volatile WebsiteTunnelConnector websiteTunnel;
    private volatile boolean websiteTunnelInstalled;
    private volatile ScheduledTask websiteMaintenanceTask;
    private volatile ScheduledTask shadowMigrationTask;
    private volatile VelocityBootstrapCoordinator bootstrapCoordinator;
    private volatile VelocityConfigurationReloadCoordinator reloadCoordinator;

    @Inject
    public EnthusiaStaffVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.playerSuggestions = new VelocityPlayerSuggestions(proxy, () -> playerDirectory,
                () -> databaseRuntime == null ? null : databaseRuntime.vanishStore(), () -> workers);
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.securityEventDispatcher = new VelocitySecurityEventDispatcher(() -> workers, shuttingDown::get);
        this.networkVerifier = new VelocityNetworkVerifier(new VelocityNetworkVerifier.Dependencies(
                authorityMode::get,
                () -> databaseRuntime,
                () -> configuration,
                () -> channelServer,
                () -> networkIdentityStore != null && networkIdentityProtector != null,
                () -> discordOutboxWorker != null,
                this::websiteBridgeReady
        ));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent ignored) {
        workers = createWorkers();
        staffTabBridge = VelocitabStaffBridge.start(this, proxy, logger, () -> databaseRuntime, workers).orElse(null);
        staffDutyContext = VelocityStaffDutyContext.start(
                this, proxy, logger, () -> staffSessionStore
        ).orElse(null);
        registerCommands();
        health.update(OperationalMode.BOOTSTRAP, Map.of("bootstrap", "MariaDB initialization is in progress"));
        VelocityBootstrapCoordinator coordinator = new VelocityBootstrapCoordinator(
                this::submitWorker,
                this::scheduleBootstrapRetry,
                this::initializeStorageAttempt,
                new BootstrapListener(),
                shuttingDown::get,
                VelocityBootstrapCoordinator.RetryPolicy.defaults()
        );
        bootstrapCoordinator = coordinator;
        coordinator.start();
    }

    private void registerCommands() {
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("estaff").plugin(this).build(),
                new StatusCommand()
        );
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("alts").plugin(this).build(),
                new AltsCommand()
        );
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("alt").plugin(this).build(),
                new AltCommand()
        );
    }

    private final class BootstrapListener implements VelocityBootstrapCoordinator.Listener {
        @Override
        public void attempting(int attempt, int maximumAttempts) {
            authorityMode.set(OperationalMode.BOOTSTRAP);
            health.update(OperationalMode.BOOTSTRAP, Map.of(
                    "mariadb-attempt",
                    "Velocity storage startup attempt " + attempt + " of " + maximumAttempts + " is in progress"
            ));
        }

        @Override
        public void retrying(
                int nextAttempt,
                int maximumAttempts,
                long delayMillis,
                RuntimeException failure
        ) {
            health.update(OperationalMode.BOOTSTRAP, Map.of(
                    "mariadb-retrying",
                    "Velocity storage startup attempt " + nextAttempt + " of " + maximumAttempts
                            + " is scheduled after " + delayMillis + " ms"
            ));
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Velocity storage startup failed; bounded retry {} of {} is scheduled after {} ms ({})",
                        nextAttempt,
                        maximumAttempts,
                        delayMillis,
                        failure.getClass().getSimpleName()
                );
            }
        }

        @Override
        public void recovered(int attempts) {
            if (attempts > INITIAL_BOOTSTRAP_ATTEMPT && logger.isInfoEnabled()) {
                logger.info("Velocity storage recovered on bounded attempt {}", attempts);
            }
        }

        @Override
        public void exhausted(int attempts, RuntimeException failure) {
            authorityMode.set(OperationalMode.DEGRADED);
            String component = failure instanceof VelocityBootstrapCoordinator.PermanentFailure
                    ? "configuration-or-cutover"
                    : "mariadb";
            health.update(OperationalMode.DEGRADED, Map.of(
                    component,
                    "Velocity storage startup is unavailable after " + attempts
                            + " attempt(s); use /estaff reload after correcting the cause"
            ));
            if (logger.isErrorEnabled()) {
                logger.error(
                        "Velocity storage startup stopped after {} attempt(s) ({})",
                        attempts,
                        failure.getClass().getSimpleName()
                );
            }
        }
    }

    @Subscribe
    @SuppressWarnings("PMD.NullAssignment")
    // Clearing volatile resource references prevents post-shutdown readers from using closed objects.
    public void onProxyShutdown(ProxyShutdownEvent ignored) {
        shuttingDown.set(true);
        authorityMode.set(OperationalMode.MAINTENANCE);
        health.update(OperationalMode.MAINTENANCE, Map.of("shutdown", "Velocity runtime is shutting down"));
        VelocityBootstrapCoordinator bootstrap = bootstrapCoordinator;
        if (bootstrap != null) {
            bootstrap.stop();
        }
        if (workers == null) {
            return;
        }
        cancelScheduledTask("operational state refresh", operationalStateTask);
        operationalStateTask = null;
        closeOutboxWorker();
        closeDiscordWorker();
        cancelScheduledTask("website maintenance", websiteMaintenanceTask);
        websiteMaintenanceTask = null;
        cancelScheduledTask("shadow migration", shadowMigrationTask);
        shadowMigrationTask = null;
        closeWebsiteServer();
        closeChannelServer();
        if (staffTabBridge != null) {
            staffTabBridge.close();
        }
        if (staffDutyContext != null) {
            staffDutyContext.close();
        }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
        MariaDbRuntime runtime = databaseRuntime;
        databaseRuntime = null;
        try {
            if (runtime != null) {
                runtime.close();
            }
        } finally {
            clearPublishedStores();
        }
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return securityEventDispatcher.submit(
                () -> enforceLogin(event),
                () -> denyUnavailable(event)
        );
    }

    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        return securityEventDispatcher.submit(
                () -> enforceSafeServerSwitch(event),
                () -> denyServerSwitch(event, "Asset safety status is temporarily unavailable.")
        );
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        PlayerDirectory directory = playerDirectory;
        if (directory == null || event.getPlayer().getCurrentServer().isEmpty()) {
            return;
        }
        String backend = event.getPlayer().getCurrentServer().orElseThrow().getServerInfo().getName();
        enqueuePresence(event.getPlayer().getUniqueId(), () -> directory.recordSeen(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername(),
                PlayerPlatform.JAVA,
                backend,
                Clock.systemUTC().instant()
        ));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        staffReconnects.disconnected(event.getPlayer().getUniqueId());
        // Drop any cached transfer snapshot: a disconnect mid-transfer must not leak state
        // into a later, unrelated transfer.
        transferSnapshots.evict(event.getPlayer().getUniqueId());
        PlayerDirectory directory = playerDirectory;
        VelocityConfiguration loaded = configuration;
        if (directory == null || loaded == null) {
            return;
        }
        String currentServer = event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(loaded.serverId());
        enqueuePresence(event.getPlayer().getUniqueId(), () -> directory.recordDisconnected(
                event.getPlayer().getUniqueId(),
                currentServer,
                Clock.systemUTC().instant()
        ));
    }

    private void initializeStorageAttempt() {
        VelocityConfiguration loaded = loadStorageConfiguration();
        MariaDbRuntime opened = null;
        try {
            opened = MariaDb.initialize(loaded.database(dataDirectory));
            OperationalStateSnapshot state = validateStorageState(opened);
            StorageBindings bindings = storageBindings(opened);
            initializeStorageResources(loaded, opened);
            if (shuttingDown.get()) {
                throw new IllegalStateException("Velocity shutdown started before storage publication");
            }
            publishStorageRuntime(loaded, opened, state, bindings);
        } catch (RuntimeException exception) {
            cleanupFailedInitialization(opened);
            throw exception;
        }
    }

    private VelocityConfiguration loadStorageConfiguration() {
        try {
            return VelocityConfiguration.load(dataDirectory);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new VelocityBootstrapCoordinator.PermanentFailure(
                    "Velocity configuration could not be loaded or validated", exception);
        }
    }

    private OperationalStateSnapshot validateStorageState(MariaDbRuntime runtime) {
        OperationalStateSnapshot state = runtime.operationalStateStore().current();
        if (state.mode() == OperationalMode.ACTIVE && !runtime.operationalStateStore().hasAuthorizedCutover()) {
            activeAuthorityObserved = true;
            throw new VelocityBootstrapCoordinator.PermanentFailure(
                    "Persistent ACTIVE state has no authorized cutover record");
        }
        if (shuttingDown.get()) {
            throw new IllegalStateException("Velocity shutdown started during storage initialization");
        }
        return state;
    }

    private static StorageBindings storageBindings(MariaDbRuntime runtime) {
        return new StorageBindings(
                runtime.sanctionLookup(),
                runtime.playerDirectory(),
                runtime.freezeStore(),
                runtime.staffSessionStore(),
                runtime.inventoryJournalStore(),
                runtime.economyJournalStore(),
                runtime.accountLinkingStore()
        );
    }

    private void initializeStorageResources(VelocityConfiguration loaded, MariaDbRuntime runtime) {
        initializeNetworkIdentity(loaded, runtime.networkIdentityStore());
        initializeChannel(loaded, runtime.networkOutboxStore());
        initializeDiscord(loaded, runtime.discordOutboxStore());
        initializeWebsiteApi(loaded, runtime);
    }

    @SuppressWarnings("PMD.GuardLogStatement")
    // SLF4J placeholders defer formatting; the argument is an enum.
    private void publishStorageRuntime(
            VelocityConfiguration loaded,
            MariaDbRuntime runtime,
            OperationalStateSnapshot state,
            StorageBindings bindings
    ) {
        configuration = loaded;
        databaseRuntime = runtime;
        sanctionLookup = bindings.sanctions();
        playerDirectory = bindings.players();
        freezeStore = bindings.freezes();
        staffSessionStore = bindings.sessions();
        inventoryJournalStore = bindings.inventories();
        economyJournalStore = bindings.economies();
        accountLinkingStore = bindings.accountLinks();
        reloadCoordinator = new VelocityConfigurationReloadCoordinator(
                loaded,
                () -> VelocityConfiguration.load(dataDirectory),
                candidate -> configuration = candidate,
                shuttingDown::get
        );
        authorityMode.set(state.mode());
        activeAuthorityObserved = state.mode() == OperationalMode.ACTIVE;
        health.update(state.mode(), operationalIssues(state.mode()));
        operationalStateTask = proxy.getScheduler().buildTask(this, this::refreshOperationalState)
                .repeat(5, TimeUnit.SECONDS)
                .schedule();
        initializeShadowMigrationSchedule(loaded);
        logger.info("MariaDB verified; Velocity moderation authority is {}", state.mode());
    }

    @SuppressWarnings("PMD.CloseResource") // Borrows the plugin-owned worker pool; shutdown owns its lifecycle.
    private boolean submitWorker(Runnable operation) {
        ExecutorService executor = workers;
        if (executor == null || executor.isShutdown() || shuttingDown.get()) {
            return false;
        }
        try {
            executor.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement")
    // SLF4J placeholders defer formatting of the exception class.
    private boolean scheduleBootstrapRetry(Runnable operation, long delayMillis) {
        if (shuttingDown.get()) {
            return false;
        }
        try {
            proxy.getScheduler().buildTask(this, operation)
                    .delay(Math.max(1L, delayMillis), TimeUnit.MILLISECONDS)
                    .schedule();
            return true;
        } catch (RuntimeException exception) {
            logger.error("Velocity bootstrap retry scheduling failed ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    @SuppressWarnings("PMD.NullAssignment")
    // References are cleared before retry so stale event readers cannot reach retired resources.
    private void cleanupFailedInitialization(MariaDbRuntime opened) {
        cancelScheduledTask("failed operational state refresh", operationalStateTask);
        operationalStateTask = null;
        cancelScheduledTask("failed website maintenance", websiteMaintenanceTask);
        websiteMaintenanceTask = null;
        cancelScheduledTask("failed shadow migration", shadowMigrationTask);
        shadowMigrationTask = null;
        closeOutboxWorker();
        closeDiscordWorker();
        closeWebsiteServer();
        closeChannelServer();
        MariaDbRuntime published = databaseRuntime;
        databaseRuntime = null;
        clearPublishedStores();
        if (published != null && published != opened) {
            published.close();
        }
        if (opened != null) {
            opened.close();
        }
    }

    @SuppressWarnings("PMD.NullAssignment")
    // Volatile null publication is the explicit unavailable-state fence.
    private void clearPublishedStores() {
        configuration = null;
        reloadCoordinator = null;
        sanctionLookup = null;
        playerDirectory = null;
        freezeStore = null;
        staffSessionStore = null;
        inventoryJournalStore = null;
        economyJournalStore = null;
        networkIdentityStore = null;
        accountLinkingStore = null;
        networkIdentityProtector = null;
        websiteModerationStore = null;
    }

    @SuppressWarnings("PMD.GuardLogStatement")
    // SLF4J placeholders defer formatting.
    private void cancelScheduledTask(String label, ScheduledTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException exception) {
            logger.warn("{} cleanup failed ({})", label, exception.getClass().getSimpleName());
        }
    }

    @SuppressWarnings({"PMD.NullAssignment", "PMD.GuardLogStatement"})
    // Clear the published reference before closing; SLF4J placeholders defer formatting.
    private void closeOutboxWorker() {
        NetworkOutboxWorker worker = outboxWorker;
        outboxWorker = null;
        if (worker != null) {
            try {
                worker.close();
            } catch (RuntimeException exception) {
                logger.warn("Network outbox worker cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings({"PMD.NullAssignment", "PMD.GuardLogStatement"})
    // Clear the published reference before closing; SLF4J placeholders defer formatting.
    private void closeDiscordWorker() {
        DiscordOutboxWorker worker = discordOutboxWorker;
        discordOutboxWorker = null;
        if (worker != null) {
            try {
                worker.close();
            } catch (RuntimeException exception) {
                logger.warn("Discord worker cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings({"PMD.NullAssignment", "PMD.GuardLogStatement"})
    // Clear the published reference before closing; SLF4J placeholders defer formatting.
    private void closeWebsiteServer() {
        WebsiteTunnelConnector connector = websiteTunnel;
        websiteTunnel = null;
        if (connector != null) connector.close();
        WebsiteApiServer server = websiteApiServer;
        websiteApiServer = null;
        if (server != null) {
            try {
                server.close();
            } catch (RuntimeException exception) {
                logger.warn("Website API cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings({"PMD.NullAssignment", "PMD.GuardLogStatement"})
    // Clear the published reference before closing; SLF4J placeholders defer formatting.
    private void closeChannelServer() {
        closeChatBridgeRelay();
        VelocityDiscordChatIngressRelay inboundRelay = discordChatIngressRelay;
        discordChatIngressRelay = null;
        PersistentChannelServer server = channelServer;
        channelServer = null;
        if (inboundRelay != null) {
            try {
                inboundRelay.unbind(server);
                inboundRelay.close();
            } catch (RuntimeException exception) {
                logger.warn("Velocity Discord chat ingress cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
        if (server != null) {
            try {
                server.close();
            } catch (RuntimeException exception) {
                logger.warn("Persistent channel cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings({"PMD.NullAssignment", "PMD.GuardLogStatement"})
    private void closeChatBridgeRelay() {
        VelocityChatArtifactRelay.Registration artifactRegistration =
                chatArtifactSinkRegistration;
        chatArtifactSinkRegistration = null;
        if (artifactRegistration != null) {
            try {
                artifactRegistration.close();
            } catch (RuntimeException exception) {
                logger.warn("Velocity chat artifact sink cleanup failed ({})",
                        exception.getClass().getSimpleName());
            }
        }
        VelocityChatArtifactRelay artifactRelay = chatArtifactRelay;
        chatArtifactRelay = null;
        if (artifactRelay != null) {
            try {
                artifactRelay.close();
            } catch (RuntimeException exception) {
                logger.warn("Velocity chat artifact relay cleanup failed ({})",
                        exception.getClass().getSimpleName());
            }
        }

        VelocityRenderedChatBridgeRelay.Registration renderedRegistration =
                renderedChatBridgeSinkRegistration;
        renderedChatBridgeSinkRegistration = null;
        if (renderedRegistration != null) {
            try {
                renderedRegistration.close();
            } catch (RuntimeException exception) {
                logger.warn("Velocity styled chat sink cleanup failed ({})",
                        exception.getClass().getSimpleName());
            }
        }
        VelocityRenderedChatBridgeRelay renderedRelay = renderedChatBridgeRelay;
        renderedChatBridgeRelay = null;
        if (renderedRelay != null) {
            try {
                renderedRelay.close();
            } catch (RuntimeException exception) {
                logger.warn("Velocity styled chat relay cleanup failed ({})",
                        exception.getClass().getSimpleName());
            }
        }

        VelocityChatBridgeRelay.Registration registration = chatBridgeSinkRegistration;
        chatBridgeSinkRegistration = null;
        if (registration != null) {
            try {
                registration.close();
            } catch (RuntimeException exception) {
                logger.warn("Velocity chat sink cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
        VelocityChatBridgeRelay relay = chatBridgeRelay;
        chatBridgeRelay = null;
        if (relay != null) {
            try {
                relay.close();
            } catch (RuntimeException exception) {
                logger.warn("Velocity chat relay cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // SLF4J placeholders defer formatting; the argument is an integer.
    private void initializeShadowMigrationSchedule(VelocityConfiguration loaded) {
        if (!loaded.liteBansShadowScheduleEnabled()) {
            logger.warn("Automatic LiteBans shadow summaries are disabled; the daily cutover gate must be satisfied manually");
            return;
        }
        shadowMigrationTask = proxy.getScheduler().buildTask(this, this::scheduleShadowMigration)
                .repeat(loaded.liteBansShadowIntervalHours(), TimeUnit.HOURS)
                .schedule();
        logger.info(
                "Automatic LiteBans shadow summaries scheduled every {} hours while in SHADOW_MIGRATION",
                loaded.liteBansShadowIntervalHours()
        );
    }

    @SuppressWarnings("PMD.GuardLogStatement") // SLF4J placeholders defer formatting; arguments are scalar accessors.
    private void scheduleShadowMigration() {
        MariaDbRuntime runtime = databaseRuntime;
        VelocityConfiguration loaded = configuration;
        if (runtime == null || loaded == null || authorityMode.get() != OperationalMode.SHADOW_MIGRATION
                || !migrationRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            workers.execute(() -> {
                try {
                    MigrationExecutionReport report = migrationService(runtime, MigrationMode.SHADOW).execute(
                            loaded.liteBansDatabase(dataDirectory),
                            loaded.liteBansTablePrefix(),
                            loaded.liteBansBatchSize(),
                            MigrationMode.SHADOW
                    );
                    long mismatches = report.shadowSummary().map(
                            net.enthusia.staff.persistence.migration.ShadowSummary::mismatchCount
                    ).orElse(0L);
                    if (mismatches == 0) {
                        logger.info(
                                "Scheduled LiteBans shadow run {} completed: source={}, imported={}, reconciled={}, replayed={}",
                                report.runId(),
                                report.sourceRecords(),
                                report.importedRecords(),
                                report.reconciledRecords(),
                                report.replayedRecords()
                        );
                    } else {
                        logger.error(
                                "Scheduled LiteBans shadow run {} recorded {} mismatches; cutover continuity reset",
                                report.runId(),
                                mismatches
                        );
                    }
                } catch (RuntimeException exception) {
                    logger.error("Scheduled LiteBans shadow run failed; cutover continuity is not advanced", exception);
                } finally {
                    migrationRunning.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            migrationRunning.set(false);
            logger.warn("Scheduled LiteBans shadow run skipped because the bounded worker queue is full");
        }
    }

    private LiteBansMigrationService migrationService(MariaDbRuntime runtime, MigrationMode mode) {
        if (mode == MigrationMode.DRY_RUN) {
            return runtime.liteBansMigrationService();
        }
        NetworkIdentityProtector protector = networkIdentityProtector;
        if (protector == null) {
            throw new IllegalStateException(
                    "Protected network identity support must be enabled before importing LiteBans history"
            );
        }
        return runtime.liteBansMigrationService(protector);
    }

    private void initializeChannel(VelocityConfiguration loaded, NetworkOutboxStore outbox) {
        if (!loaded.channelEnabled()) {
            logger.warn("Persistent backend channel is disabled; new network-wide punishment writes must remain disabled");
            return;
        }
        if (loaded.backendSecretEnvironments().isEmpty()) {
            throw new IllegalStateException("No authenticated channel peer secrets are configured");
        }
        Map<String, SecretKey> peerKeys = new LinkedHashMap<>();
        loaded.backendSecretEnvironments().forEach((serverId, environment) ->
                peerKeys.put(serverId, secretFromEnvironment(environment)));
        final Set<String> requiredBackends;
        try {
            requiredBackends = VelocityChannelPeerPolicy.requiredPaperBackends(peerKeys.keySet());
            VelocityChannelPeerPolicy.validateProxyIdentity(loaded.channelProxyId(), peerKeys.keySet());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Persistent channel peer policy is invalid", exception);
        }
        SecretKey proxyKey = secretFromEnvironment(loaded.channelProxySecretEnvironment());
        SSLContext tlsContext = serverTlsContext(loaded);
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(Clock.systemUTC());
        VelocityChatArtifactRelay artifactRelay = new VelocityChatArtifactRelay(Clock.systemUTC());
        VelocityRenderedChatBridgeRelay renderedRelay = new VelocityRenderedChatBridgeRelay(Clock.systemUTC());
        VelocityDiscordChatIngressRelay inboundRelay = new VelocityDiscordChatIngressRelay(
                Set.copyOf(requiredBackends), Clock.systemUTC());
        VelocityStaffBotChatHealthRelay healthRelay =
                new VelocityStaffBotChatHealthRelay(Set.copyOf(requiredBackends), Clock.systemUTC());
        chatBridgeRelay = relay;
        chatArtifactRelay = artifactRelay;
        renderedChatBridgeRelay = renderedRelay;
        discordChatIngressRelay = inboundRelay;
        try {
            PersistentChannelServer server = createChannelServer(
                    loaded,
                    outbox,
                    peerKeys,
                    Set.copyOf(requiredBackends),
                    proxyKey,
                    tlsContext,
                    relay,
                    artifactRelay,
                    renderedRelay,
                    inboundRelay,
                    healthRelay
            );
            server.start();
            inboundRelay.bind(server);
            healthRelay.bind(server);
            channelServer = server;
            if (peerKeys.containsKey(VelocityStaffBotChatSink.PEER_ID)) {
                chatBridgeSinkRegistration = relay.installSink(new VelocityStaffBotChatSink(server));
                chatArtifactSinkRegistration = artifactRelay.installSink(
                        new VelocityStaffBotChatArtifactSink(server));
                renderedChatBridgeSinkRegistration = renderedRelay.installSink(
                        new VelocityStaffBotRenderedChatSink(server));
            }
            outboxWorker = new NetworkOutboxWorker(
                    this,
                    proxy,
                    logger,
                    Clock.systemUTC(),
                    workers,
                    outbox,
                    server,
                    Set.copyOf(requiredBackends)
            );
            outboxWorker.start();
        } catch (java.io.IOException exception) {
            closeChannelServer();
            throw new IllegalStateException("Unable to bind the persistent backend channel", exception);
        } catch (RuntimeException exception) {
            closeChannelServer();
            throw exception;
        }
    }

    private PersistentChannelServer createChannelServer(
            VelocityConfiguration loaded,
            NetworkOutboxStore outbox,
            Map<String, SecretKey> peerKeys,
            Set<String> paperBackendIds,
            SecretKey proxyKey,
            SSLContext tlsContext,
            VelocityChatBridgeRelay chatRelay,
            VelocityChatArtifactRelay artifactRelay,
            VelocityRenderedChatBridgeRelay renderedChatRelay,
            VelocityDiscordChatIngressRelay discordIngressRelay,
            VelocityStaffBotChatHealthRelay chatHealthRelay
    ) throws java.net.UnknownHostException {
        return new PersistentChannelServer(
                new PersistentChannelServer.Configuration(
                        loaded.channelProxyId(),
                        InetAddress.getByName(loaded.channelBindAddress()),
                        loaded.channelPort(),
                        peerKeys,
                        proxyKey,
                        tlsContext,
                        peerKeys.size() + 2
                ),
                Clock.systemUTC(),
                new VelocityChannelMessageRouter(
                        paperBackendIds,
                        chatRelay,
                        artifactRelay,
                        renderedChatRelay,
                        discordIngressRelay,
                        chatHealthRelay,
                        envelope -> {
                            if (acceptTransferSnapshot(envelope)) {
                                return true;
                            }
                            if (networkVerifier.acceptReport(envelope) || acceptStaffModeReady(envelope)) {
                                return true;
                            }
                            outbox.recordInboxOnce(
                                    loaded.serverId(),
                                    envelope.messageId(),
                                    envelope.messageType(),
                                    "{\"outcome\":\"accepted\"}",
                                    Clock.systemUTC().instant()
                            );
                            return true;
                        }
                ),
                warning -> logger.warn("{}", warning)
        );
    }

    private static boolean allPaperBackendsConnected(
            VelocityConfiguration loaded,
            PersistentChannelServer channel
    ) {
        return loaded != null
                && channel != null
                && VelocityChannelPeerPolicy.allPaperBackendsConnected(
                        loaded.backendSecretEnvironments().keySet(),
                        channel.connectedServers());
    }

    private void initializeNetworkIdentity(VelocityConfiguration loaded, NetworkIdentityStore store) {
        networkIdentityStore = store;
        if (!loaded.networkIdentityEnabled()) {
            logger.warn("Network identity matching is disabled; automatic alt inheritance is unavailable");
            return;
        }
        SecretKey equalityKey = SecretKeyMaterial.hmacSha256FromBase64(
                loaded.networkIdentitySecret(dataDirectory, loaded.networkIdentityHmacSecretEnvironment())
        );
        SecretKey encryptionKey = SecretKeyMaterial.aesFromBase64(
                loaded.networkIdentitySecret(dataDirectory, loaded.networkIdentityEncryptionSecretEnvironment())
        );
        networkIdentityProtector = new NetworkIdentityProtector(
                new HmacTokenService(loaded.networkIdentityHmacKeyVersion(), equalityKey),
                loaded.networkIdentityEncryptionKeyVersion(),
                encryptionKey,
                new SecureRandom()
        );
    }

    private void initializeDiscord(VelocityConfiguration loaded, DiscordOutboxStore store) {
        if (!loaded.discordEnabled()) {
            logger.warn("Discord outbox delivery is disabled; events remain durable in MariaDB");
            return;
        }
        DiscordOutboxWorker worker = new DiscordOutboxWorker(
                this,
                proxy,
                logger,
                Clock.systemUTC(),
                workers,
                store,
                loaded.discordWebhooks(dataDirectory),
                loaded.discordMaximumAttempts(),
                loaded.discordFailureThreshold(),
                Duration.ofSeconds(loaded.discordCircuitOpenSeconds()),
                Duration.ofMillis(loaded.discordRequestTimeoutMillis())
        );
        worker.start();
        discordOutboxWorker = worker;
    }

    private void initializeWebsiteApi(VelocityConfiguration loaded, MariaDbRuntime runtime) {
        if (!loaded.websiteApiEnabled()) {
            logger.warn("Restricted website API is disabled; punishment appeals remain unavailable");
            return;
        }
        try {
            WebsiteRuntime website = startWebsiteApi(loaded, runtime);
            websiteModerationStore = website.store();
            websiteApiServer = website.server();
            websiteMaintenanceTask = website.maintenance();
            websiteTunnelInstalled = java.nio.file.Files.exists(dataDirectory.resolve("website-tunnel/connector-token"));
            websiteTunnel = WebsiteTunnelConnector.startIfInstalled(dataDirectory,
                    () -> logger.error("Website tunnel connector stopped; the loopback API remains protected"))
                    .orElse(null);
            if (logger.isInfoEnabled()) {
                logger.info(
                        "Restricted website API started on loopback; {} eligible punishment codes were backfilled",
                        website.backfilledCodes()
                );
            }
        } catch (RuntimeException | java.io.IOException exception) {
            logger.error(
                    "Restricted website API initialization failed; the moderation runtime remains available",
                    exception
            );
        }
    }

    private WebsiteRuntime startWebsiteApi(
            VelocityConfiguration loaded,
            MariaDbRuntime runtime
    ) throws java.io.IOException {
        WebsiteModerationStore store = runtime.websiteModerationStore(
                loaded.punishmentCodeProtector(dataDirectory)
        );
        AuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        Clock apiClock = Clock.systemUTC();
        SanctionChangeService sanctionChanges = new SanctionChangeService(
                authorization,
                runtime.sanctionMutationStore()
        );
        int created = store.ensureEligibleCodes(apiClock.instant(), 5_000);
        WebsiteApiServer server = new WebsiteApiServer(
                new WebsiteApiServerConfiguration(
                        InetAddress.getByName(loaded.websiteApiBindAddress()),
                        loaded.websiteApiPort(),
                        loaded.websiteApiMaximumBodyBytes(),
                        loaded.websiteApiWorkerThreads(),
                        loaded.websiteApiQueueCapacity()
                ),
                new WebsiteApiAuthenticator(
                        loaded.websiteApiBearerToken(dataDirectory),
                        loaded.websiteApiHmacSecret(dataDirectory),
                        Duration.ofSeconds(loaded.websiteApiTimestampSkewSeconds()),
                        store
                ),
                new WebsiteApiRouter(
                        store,
                        authorization,
                        sanctionChanges,
                        authorityMode::get,
                        apiClock,
                        WebsiteReviewerAuthority.production(logger),
                        new PolicyV2PublicWebsiteView(new JdbcPolicyV2Store(runtime.dataSource()))
                ),
                apiClock,
                (message, failure) -> logger.error(message, failure)
        );
        try {
            server.start();
            ScheduledTask maintenance = proxy.getScheduler()
                    .buildTask(this, this::maintainWebsiteApi)
                    .repeat(1, TimeUnit.MINUTES)
                    .schedule();
            return new WebsiteRuntime(store, server, maintenance, created);
        } catch (RuntimeException | java.io.IOException exception) {
            server.close();
            throw exception;
        }
    }

    private void maintainWebsiteApi() {
        WebsiteModerationStore store = websiteModerationStore;
        if (store == null) {
            return;
        }
        try {
            Instant now = Clock.systemUTC().instant();
            store.ensureEligibleCodes(now, 1_000);
            store.purgeExpiredApiNonces(now, 1_000);
        } catch (RuntimeException exception) {
            logger.error("Restricted website API maintenance failed", exception);
        }
    }

    private SecretKey secretFromEnvironment(String environment) {
        String encoded = PrivateRuntimeSecrets.required(dataDirectory, environment, System::getenv);
        return SecretKeyMaterial.hmacSha256FromBase64(encoded);
    }

    private SSLContext serverTlsContext(VelocityConfiguration configuration) {
        char[] password = passwordFromEnvironment(configuration.channelTlsKeyStorePasswordEnvironment());
        try {
            return TlsContextLoader.server(configuration.channelTlsKeyStorePath(), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private char[] passwordFromEnvironment(String environment) {
        String value = PrivateRuntimeSecrets.required(dataDirectory, environment, System::getenv);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("A required channel TLS store password environment variable is missing");
        }
        return value.toCharArray();
    }

    @SuppressWarnings("PMD.GuardLogStatement") // SLF4J placeholders defer formatting; arguments are enums.
    private void refreshOperationalState() {
        MariaDbRuntime runtime = databaseRuntime;
        if (runtime == null) {
            return;
        }
        try {
            OperationalStateSnapshot state = runtime.operationalStateStore().current();
            if (state.mode() == OperationalMode.ACTIVE && !runtime.operationalStateStore().hasAuthorizedCutover()) {
                activeAuthorityObserved = true;
                health.update(OperationalMode.DEGRADED, Map.of(
                        "cutover", "ACTIVE has no authorized cutover record; logins are failing closed"
                ));
                return;
            }
            OperationalMode previous = authorityMode.getAndSet(state.mode());
            activeAuthorityObserved |= state.mode() == OperationalMode.ACTIVE;
            health.update(state.mode(), operationalIssues(state.mode()));
            if (previous != state.mode()) {
                logger.info("Operational mode changed from {} to {}", previous, state.mode());
            }
        } catch (RuntimeException exception) {
            health.update(OperationalMode.DEGRADED, Map.of(
                    "operational-state", "State refresh failed; active authority fails closed"
            ));
            logger.error("Operational state refresh failed", exception);
        }
    }

    private void enforceLogin(LoginEvent event) {
        OperationalMode current = authorityMode.get();
        if (!recordPlayerAndNetworkIdentitySafely(event, current)) {
            return;
        }
        if (current != OperationalMode.ACTIVE) {
            enforceInactiveLoginPolicy(event, current);
            return;
        }
        enforceActiveLoginPolicy(event);
    }

    private boolean recordPlayerAndNetworkIdentitySafely(LoginEvent event, OperationalMode current) {
        try {
            recordPlayerAndNetworkIdentity(event, current);
            return true;
        } catch (RuntimeException exception) {
            health.update(OperationalMode.DEGRADED, Map.of(
                    "network-identity", "A protected identity observation failed; sensitive values were not logged"
            ));
            logger.error("Protected network identity observation failed", exception);
            if (current == OperationalMode.ACTIVE) {
                activeAuthorityObserved = true;
                denyUnavailable(event);
                return false;
            }
            return true;
        }
    }

    private void enforceInactiveLoginPolicy(LoginEvent event, OperationalMode current) {
        if (VelocityLoginAdmissionPolicy.blocksInactiveLogin(current, activeAuthorityObserved, failClosedConfigured())) {
            denyUnavailable(event);
        }
    }

    private void enforceActiveLoginPolicy(LoginEvent event) {
        SanctionLookup lookup = sanctionLookup;
        if (lookup == null) {
            denyUnavailable(event);
            return;
        }
        try {
            List<ActiveSanction> sanctions = lookup.activeFor(
                    event.getPlayer().getUniqueId(), LOGIN_BLOCKS, Clock.systemUTC().instant()
            );
            if (!sanctions.isEmpty()) {
                ActiveSanction sanction = sanctions.getFirst();
                String expiration = sanction.expiresAt().map(TIMESTAMP::format).orElse("Permanent");
                event.setResult(ResultedEvent.ComponentResult.denied(Component.text(
                        "Network access denied\nCase: " + sanction.caseId() + "\nReason: "
                                + sanction.publicReason() + "\nExpires: " + expiration
                                + appealInstructions(sanction)
                )));
            }
        } catch (RuntimeException exception) {
            activeAuthorityObserved = true;
            health.update(OperationalMode.DEGRADED, Map.of(
                    "mariadb", "An authoritative login lookup failed; logins are failing closed"
            ));
            logger.error("Authoritative login sanction lookup failed", exception);
            denyUnavailable(event);
        }
    }

    private void recordPlayerAndNetworkIdentity(LoginEvent event, OperationalMode current) {
        PlayerDirectory directory = playerDirectory;
        VelocityConfiguration loaded = configuration;
        if (directory == null || loaded == null) {
            return;
        }
        Instant now = Clock.systemUTC().instant();
        UUID playerId = event.getPlayer().getUniqueId();
        directory.recordSeen(playerId, event.getPlayer().getUsername(), PlayerPlatform.JAVA, loaded.serverId(), now);
        NetworkIdentityStore identityStore = networkIdentityStore;
        NetworkIdentityProtector protector = networkIdentityProtector;
        if (identityStore == null || protector == null) {
            return;
        }
        byte[] rawAddress = event.getPlayer().getRemoteAddress().getAddress().getAddress();
        boolean suppressEvidence = current != OperationalMode.ACTIVE;
        try {
            identityStore.observeAndInherit(playerId, protector.protect(rawAddress), now, suppressEvidence);
        } finally {
            java.util.Arrays.fill(rawAddress, (byte) 0);
        }
    }

    private Map<String, String> operationalIssues(OperationalMode mode) {
        Map<String, String> issues = new LinkedHashMap<>();
        VelocityConfiguration loaded = configuration;
        addChannelIssue(issues, loaded);
        addNetworkIdentityIssue(issues, loaded);
        addDiscordIssue(issues, loaded);
        addWebsiteIssue(issues, loaded);
        if (mode != OperationalMode.ACTIVE) {
            issues.put("authority", "LiteBans remains authoritative in " + mode);
        }
        return Map.copyOf(issues);
    }

    private void addChannelIssue(Map<String, String> issues, VelocityConfiguration loaded) {
        PersistentChannelServer channel = channelServer;
        if (!allPaperBackendsConnected(loaded, channel)) {
            issues.put("channel", "Every configured Paper backend is not authenticated and connected");
        }
    }

    private void addNetworkIdentityIssue(Map<String, String> issues, VelocityConfiguration loaded) {
        if (loaded == null || !loaded.networkIdentityEnabled() || networkIdentityStore == null) {
            issues.put("network-identity", "Protected network identity matching is disabled");
        }
    }

    private void addDiscordIssue(Map<String, String> issues, VelocityConfiguration loaded) {
        if (loaded == null || !loaded.discordEnabled() || discordOutboxWorker == null) {
            issues.put("discord", "Durable webhook delivery is disabled; queued events remain in MariaDB");
        }
    }

    private void addWebsiteIssue(Map<String, String> issues, VelocityConfiguration loaded) {
        if (loaded == null || !loaded.websiteApiEnabled()) {
            issues.put("website-api", "The private punishment and appeal bridge is disabled");
        } else if (websiteApiServer == null || websiteModerationStore == null) {
            issues.put("website-api", "The configured private punishment and appeal bridge failed to start");
        } else if (!websiteBridgeReady()) {
            issues.put("website-api", "The installed website tunnel connector is unavailable");
        }
    }

    private boolean websiteBridgeReady() {
        WebsiteTunnelConnector connector = websiteTunnel;
        return websiteApiServer != null && websiteModerationStore != null
                && (!websiteTunnelInstalled || connector != null && connector.running());
    }

    private void denyUnavailable(LoginEvent event) {
        if (failClosedConfigured()
                || VelocityLoginAdmissionPolicy.blocksInactiveLogin(authorityMode.get(), false, false)) {
            event.setResult(ResultedEvent.ComponentResult.denied(Component.text(
                    "The moderation service cannot safely verify network access. Please try again shortly."
            )));
        }
    }

    private void enforceSafeServerSwitch(ServerPreConnectEvent event) {
        InventoryJournalStore inventories = inventoryJournalStore;
        EconomyJournalStore economies = economyJournalStore;
        if (inventories == null || economies == null) {
            denyServerSwitchWhenActive(event, "Asset safety status is temporarily unavailable.");
            return;
        }
        if (!assetFencesAllowSwitch(event, inventories, economies)) {
            return;
        }
        if (event.getPreviousServer() == null) {
            // Initial asset routing already applies Staff reconnect only when no asset owns admission.
            return;
        }
        enforceModerationSwitchSafety(event);
    }

    private void enforceStaffReconnectOwnership(ServerPreConnectEvent event) {
        StaffSessionStore sessions = staffSessionStore;
        if (sessions == null) {
            // Do not make the proxy unavailable solely because Staff lifecycle storage is
            // temporarily missing. Paper will reconcile once storage is reachable.
            return;
        }
        try {
            var session = sessions.active(event.getPlayer().getUniqueId());
            if (session.isEmpty()) {
                staffReconnects.disconnected(event.getPlayer().getUniqueId());
                return;
            }

            var snapshot = session.orElseThrow();
            String requested = event.getOriginalServer().getServerInfo().getName();

            if (snapshot.state() == net.enthusia.staff.domain.staff.StaffSessionState.ACTIVE) {
                if (net.enthusia.staff.domain.staff.StaffSessionOwnership.detached(snapshot.serverId())
                        || snapshot.serverId().equalsIgnoreCase(requested)) {
                    staffReconnects.disconnected(event.getPlayer().getUniqueId());
                    return;
                }

                // An ACTIVE lease on another backend means that backend still has an
                // unrecovered local player-state snapshot (typically an extremely fast
                // reconnect or a backend restart). Recover there first, then STAFF_MODE_READY
                // automatically continues to the backend the player originally selected.
                staffReconnects.remember(
                        event.getPlayer().getUniqueId(),
                        snapshot,
                        requested,
                        Clock.systemUTC().instant()
                );
                var backend = proxy.getServer(snapshot.serverId());
                if (backend.isPresent()) {
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(backend.orElseThrow()));
                } else if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Staff snapshot owner {} is unavailable for {}; allowing requested backend {} without blocking login",
                            snapshot.serverId(),
                            event.getPlayer().getUniqueId(),
                            requested
                    );
                }
                return;
            }

            // EXITING/RECOVERY_REQUIRED owns an exact restoration. Route to the owner when
            // available, but never turn an unavailable Staff backend into a network login ban.
            if (!snapshot.serverId().equalsIgnoreCase(requested)) {
                proxy.getServer(snapshot.serverId()).ifPresent(owner ->
                        event.setResult(ServerPreConnectEvent.ServerResult.allowed(owner)));
            }
        } catch (RuntimeException exception) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Staff snapshot ownership lookup failed during reconnect; allowing requested backend",
                        exception
                );
            }
        }
    }

    private boolean assetFencesAllowSwitch(
            ServerPreConnectEvent event,
            InventoryJournalStore inventories,
            EconomyJournalStore economies
    ) {
        String requested = event.getOriginalServer().getServerInfo().getName();
        try {
            UUID playerId = event.getPlayer().getUniqueId();
            Instant now = Clock.systemUTC().instant();
            Optional<String> inventoryOwner = inventories.lockedOwningServer(playerId, now);
            if (event.getPreviousServer() == null) {
                return initialAssetFencesAllowSwitch(
                        event, inventories, inventoryOwner, economies.lockedOwningServer(playerId), requested, now
                );
            }
            if (denyOwnerMismatch(event, inventoryOwner, requested, "inventory")) {
                return false;
            }
            return !denyOwnerMismatch(event, economies.lockedOwningServer(playerId), requested, "economy");
        } catch (RuntimeException exception) {
            logger.error("Asset fence lookup failed during server connection", exception);
            denyServerSwitchWhenActive(event, "Asset safety status could not be verified.");
            return false;
        }
    }

    private boolean initialAssetFencesAllowSwitch(
            ServerPreConnectEvent event,
            InventoryJournalStore inventories,
            Optional<String> inventoryOwner,
            Optional<String> economyOwner,
            String requested,
            Instant now
    ) {
        if (inventoryOwner.isPresent() && economyOwner.isPresent()
                && !inventoryOwner.orElseThrow().equalsIgnoreCase(economyOwner.orElseThrow())) {
            logger.error(
                    "Conflicting asset recovery owners for {} ({}): inventory={}, economy={}",
                    event.getPlayer().getUsername(),
                    event.getPlayer().getUniqueId(),
                    inventoryOwner.orElseThrow(),
                    economyOwner.orElseThrow()
            );
            denyServerSwitch(event, "Your protected inventory and economy recovery states disagree. Please contact staff.");
            return false;
        }

        Optional<String> owner = inventoryOwner.isPresent() ? inventoryOwner : economyOwner;
        if (owner.isEmpty()) {
            enforceStaffReconnectOwnership(event);
            return true;
        }
        // Even when the requested backend already owns the asset, Staff reconnect
        // must not override that backend with a different snapshot owner.
        if (owner.orElseThrow().equalsIgnoreCase(requested)) {
            return true;
        }

        String required = owner.orElseThrow();
        Optional<com.velocitypowered.api.proxy.server.RegisteredServer> recoveryBackend = proxy.getServer(required);
        if (recoveryBackend.isPresent()) {
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(recoveryBackend.orElseThrow()));
            logger.info(
                    "Routing initial backend connection for {} ({}) from {} to recovery owner {}",
                    event.getPlayer().getUsername(),
                    event.getPlayer().getUniqueId(),
                    requested,
                    required
            );
            return true;
        }

        if (inventoryOwner.isPresent() && economyOwner.isEmpty()
                && inventories.resolveAbandonedOfflineEdit(
                        event.getPlayer().getUniqueId(), required, now
                )) {
            Optional<String> remainingOwner = inventories.lockedOwningServer(
                    event.getPlayer().getUniqueId(),
                    now
            );
            if (remainingOwner.isPresent() && !remainingOwner.orElseThrow().equalsIgnoreCase(requested)) {
                String remaining = remainingOwner.orElseThrow();
                Optional<com.velocitypowered.api.proxy.server.RegisteredServer> backend = proxy.getServer(remaining);
                if (backend.isPresent()) {
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(backend.orElseThrow()));
                    logger.warn(
                            "Resolved one abandoned offline inventory edit for {} ({}); routing remaining recovery to {}",
                            event.getPlayer().getUsername(),
                            event.getPlayer().getUniqueId(),
                            remaining
                    );
                    return true;
                }
                logger.warn(
                        "Resolved one abandoned offline inventory edit for {} ({}), but another recovery owner {} is unavailable",
                        event.getPlayer().getUsername(),
                        event.getPlayer().getUniqueId(),
                        remaining
                );
                denyServerSwitch(
                        event,
                        "Another protected inventory recovery is assigned to unavailable backend "
                                + remaining + ". Please contact staff."
                );
                return false;
            }
            logger.warn(
                    "Resolved abandoned offline inventory edit for {} ({}) owned by unavailable backend {}; allowing {}",
                    event.getPlayer().getUsername(),
                    event.getPlayer().getUniqueId(),
                    required,
                    requested
            );
            return true;
        }

        String assetType = inventoryOwner.isPresent() ? "inventory" : "economy";
        logger.warn(
                "Blocking initial backend connection for {} ({}): pending {} owner {} is unavailable; requested {}",
                event.getPlayer().getUsername(),
                event.getPlayer().getUniqueId(),
                assetType,
                required,
                requested
        );
        denyServerSwitch(
                event,
                "A protected " + assetType + " recovery is assigned to unavailable backend "
                        + required + ". Please contact staff."
        );
        return false;
    }

    private boolean denyOwnerMismatch(
            ServerPreConnectEvent event,
            Optional<String> owner,
            String requested,
            String assetType
    ) {
        if (owner.isEmpty() || owner.orElseThrow().equalsIgnoreCase(requested)) {
            return false;
        }
        String required = owner.orElseThrow();
        logger.warn(
                "Blocking backend switch for {} ({}): pending {} owner {}; requested {}",
                event.getPlayer().getUsername(),
                event.getPlayer().getUniqueId(),
                assetType,
                required,
                requested
        );
        denyServerSwitch(event, "A pending " + assetType + " operation must finish on " + required + '.');
        return true;
    }

    private void enforceModerationSwitchSafety(ServerPreConnectEvent event) {
        FreezeStore freezes = freezeStore;
        if (freezes == null) {
            denyServerSwitchWhenActive(event, "Server switching is unavailable while freeze status is verified.");
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        try {
            if (freezes.active(playerId, Clock.systemUTC().instant()).isPresent()) {
                denyServerSwitch(event, "You cannot switch servers while frozen by staff.");
                return;
            }
        } catch (RuntimeException exception) {
            logger.error("Freeze safety lookup failed during server switch", exception);
            denyServerSwitchWhenActive(event, "Server switching is unavailable while freeze status is verified.");
            return;
        }

        StaffSessionStore sessions = staffSessionStore;
        if (sessions == null) {
            return;
        }
        try {
            sessions.active(playerId).ifPresent(session ->
                    enforceStaffSessionSwitch(event, sessions, session));
        } catch (RuntimeException exception) {
            // Staff Mode lifecycle must not trap a player on one backend. Paper restores/detaches
            // on disconnect and the destination retries durable rebind on join.
            logger.warn("Staff Mode handoff lookup failed; allowing backend switch for {}", playerId, exception);
        }
    }

    private void enforceStaffSessionSwitch(
            ServerPreConnectEvent event,
            StaffSessionStore sessions,
            net.enthusia.staff.domain.staff.StaffSessionSnapshot session
    ) {
        String current = event.getPreviousServer().getServerInfo().getName();
        String requested = event.getResult().getServer()
                .orElse(event.getOriginalServer()).getServerInfo().getName();
        if (!StaffSessionTransferPolicy.activeHandoffAllowed(
                session.serverId(), session.state(), current, requested)) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        UUID transferId = UUID.randomUUID();
        if (!staffHandoffs.begin(playerId, current, requested, transferId, Clock.systemUTC().instant())) {
            return;
        }

        try {
            handoffCoordinator(sessions).transfer(
                    playerId,
                    session,
                    current,
                    requested,
                    transferId,
                    transferSnapshots::take
            );
            scheduleStaffHandoffTimeout(playerId, transferId);
        } catch (RuntimeException exception) {
            staffHandoffs.clear(playerId, transferId);
            logger.warn(
                    "Staff Mode optimized handoff failed; allowing {} to switch {} -> {} and using lifecycle recovery",
                    playerId,
                    current,
                    requested,
                    exception
            );
        }
    }

    /**
     * Caches a cross-server transfer snapshot uploaded by a source backend
     * (overnight/cross-server). The upload arrives before any database write on the source;
     * the proxy holds it just long enough to forward it to the destination backend inside
     * the handoff prepare message, so transfers never wait on persistence.
     */
    private boolean acceptTransferSnapshot(ProtocolEnvelope envelope) {
        if (!TransferSnapshotMessages.UPLOAD.equals(envelope.messageType())) {
            return false;
        }
        try {
            net.enthusia.staff.domain.staff.StaffTransferSnapshot snapshot =
                    TransferSnapshotMessages.decode(envelope.payloadJson());
            if (snapshot == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Rejected empty staff transfer snapshot from {}", envelope.serverId());
                }
                return true;
            }
            transferSnapshots.put(snapshot);
            if (logger.isDebugEnabled()) {
                logger.debug("Cached staff transfer snapshot for {} (transfer {})",
                        snapshot.playerId(), snapshot.transferId());
            }
            return true;
        } catch (IllegalArgumentException exception) {
            if (logger.isWarnEnabled()) {
                logger.warn("Rejected malformed staff transfer snapshot from {}", envelope.serverId());
            }
            return true;
        }
    }

    private boolean acceptStaffModeReady(ProtocolEnvelope envelope) {
        if (!STAFF_MODE_READY.equals(envelope.messageType())) {
            return false;
        }
        try {
            JsonNode payload = json.readTree(envelope.payloadJson());
            UUID playerId = UUID.fromString(payload.path("playerId").asText());
            UUID sessionId = UUID.fromString(payload.path("sessionId").asText());
            if (!completePendingStaffHandoff(playerId, envelope.serverId())) {
                continuePendingReconnect(playerId, sessionId, envelope.serverId());
            }
            return true;
        } catch (java.io.IOException | IllegalArgumentException exception) {
            if (logger.isWarnEnabled()) {
                logger.warn("Rejected malformed Staff Mode readiness message from {}", envelope.serverId());
            }
            return true;
        }
    }

    private void continuePendingReconnect(UUID playerId, UUID sessionId, String owner) {
        Player player = proxy.getPlayer(playerId).orElse(null);
        if (player == null || player.getCurrentServer().isEmpty()) {
            return;
        }
        String current = player.getCurrentServer().orElseThrow().getServerInfo().getName();
        Optional<String> destinationName = staffReconnects.claimDestination(
                playerId,
                sessionId,
                owner,
                current,
                Clock.systemUTC().instant()
        );
        if (destinationName.isEmpty()) {
            return;
        }
        var destination = proxy.getServer(destinationName.orElseThrow()).orElse(null);
        if (destination != null) {
            proxy.getScheduler().buildTask(this, () ->
                    player.createConnectionRequest(destination).fireAndForget()).schedule();
        }
    }

    private StaffModeBackendHandoffCoordinator handoffCoordinator(StaffSessionStore sessions) {
        java.util.function.Function<UUID, Optional<net.enthusia.staff.domain.staff.StaffSessionSnapshot>> lookup =
                sessions == null ? ignored -> Optional.empty() : sessions::active;
        return new StaffModeBackendHandoffCoordinator(
                () -> StaffModeBackendHandoffCoordinator.channelTransport(channelServer),
                lookup
        );
    }

    private boolean completePendingStaffHandoff(UUID playerId, String readyBackend) {
        Player player = proxy.getPlayer(playerId).orElse(null);
        String current = player == null || player.getCurrentServer().isEmpty()
                ? null
                : player.getCurrentServer().orElseThrow().getServerInfo().getName();
        return staffHandoffs.completeReady(playerId, readyBackend, current);
    }

    private void scheduleStaffHandoffTimeout(UUID playerId, UUID transferId) {
        proxy.getScheduler().buildTask(
                this,
                () -> handleStaffHandoffTimeout(playerId, transferId)
        ).delay(StaffModeHandoffTracker.TIMEOUT_SECONDS, TimeUnit.SECONDS).schedule();
    }

    private void handleStaffHandoffTimeout(UUID playerId, UUID transferId) {
        var pending = staffHandoffs.claimTimedOut(playerId, transferId, Clock.systemUTC().instant());
        if (pending.isEmpty()) {
            return;
        }
        var handoff = pending.orElseThrow();
        if (!submitWorker(() -> recoverTimedOutStaffHandoff(playerId, handoff))) {
            staffHandoffs.restore(handoff, Clock.systemUTC().instant().plusSeconds(2));
            proxy.getScheduler().buildTask(
                    this,
                    () -> handleStaffHandoffTimeout(playerId, transferId)
            ).delay(2, TimeUnit.SECONDS).schedule();
        }
    }

    private void recoverTimedOutStaffHandoff(UUID playerId, StaffModeHandoffTracker.Pending pending) {
        String current = currentBackend(playerId);
        StaffSessionStore sessions = staffSessionStore;
        var durable = activeStaffSession(sessions, playerId);
        if (durableStaffModeArrived(durable, pending.destination())) {
            return;
        }
        StaffModeBackendHandoffCoordinator coordinator = handoffCoordinator(sessions);
        if (sourceHandoffStillActive(current, durable, pending)) {
            stabilizeSourceHandoff(playerId, pending, coordinator);
            return;
        }
        if (retryTimedOutDestination(playerId, current, durable, pending, coordinator)) {
            return;
        }
        finishTimedOutStaffHandoff(playerId, pending, current, durable, coordinator);
    }

    private String currentBackend(UUID playerId) {
        return proxy.getPlayer(playerId)
                .flatMap(Player::getCurrentServer)
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
    }

    private static Optional<net.enthusia.staff.domain.staff.StaffSessionSnapshot> activeStaffSession(
            StaffSessionStore sessions,
            UUID playerId
    ) {
        return sessions == null
                ? Optional.empty()
                : sessions.active(playerId);
    }

    private static boolean sourceHandoffStillActive(
            String current,
            Optional<net.enthusia.staff.domain.staff.StaffSessionSnapshot> durable,
            StaffModeHandoffTracker.Pending pending
    ) {
        return current != null
                && current.equalsIgnoreCase(pending.source())
                && durableStaffModeArrived(durable, pending.source());
    }

    private boolean retryTimedOutDestination(
            UUID playerId,
            String current,
            Optional<net.enthusia.staff.domain.staff.StaffSessionSnapshot> durable,
            StaffModeHandoffTracker.Pending pending,
            StaffModeBackendHandoffCoordinator coordinator
    ) {
        if (!destinationRetryEligible(current, durable, pending)
                || !coordinator.retryDestination(playerId, pending.destination(), pending.transferId())) {
            return false;
        }
        staffHandoffs.retry(pending, Clock.systemUTC().instant());
        scheduleStaffHandoffTimeout(playerId, pending.transferId());
        return true;
    }

    private static boolean destinationRetryEligible(
            String current,
            Optional<net.enthusia.staff.domain.staff.StaffSessionSnapshot> durable,
            StaffModeHandoffTracker.Pending pending
    ) {
        return current != null
                && current.equalsIgnoreCase(pending.destination())
                && durable.isEmpty()
                && pending.retryCount() == 0;
    }

    private void stabilizeSourceHandoff(
            UUID playerId,
            StaffModeHandoffTracker.Pending pending,
            StaffModeBackendHandoffCoordinator coordinator
    ) {
        if (coordinator.abortSourceHandoff(playerId, pending.source(), pending.transferId())) {
            return;
        }
        if (pending.retryCount() == 0) {
            staffHandoffs.retry(pending, Clock.systemUTC().instant());
            scheduleStaffHandoffTimeout(playerId, pending.transferId());
            return;
        }
        proxy.getPlayer(playerId).ifPresent(player -> player.sendMessage(VelocityMessageStyle.style(Component.text(
                "Staff Mode stayed on the current backend, but the handoff abort could not be confirmed."
        ))));
    }

    private void finishTimedOutStaffHandoff(
            UUID playerId,
            StaffModeHandoffTracker.Pending pending,
            String current,
            Optional<net.enthusia.staff.domain.staff.StaffSessionSnapshot> durable,
            StaffModeBackendHandoffCoordinator coordinator
    ) {
        String message;
        if (current != null && current.equalsIgnoreCase(pending.source()) && durable.isEmpty()) {
            message = coordinator.recoverFailedConnection(
                    playerId, pending.source(), pending.destination(), pending.transferId()).message();
        } else {
            coordinator.cancelPreparedDestination(playerId, pending.destination(), pending.transferId());
            message = durable.isPresent()
                    ? "Staff Mode handoff needs recovery on backend " + durable.orElseThrow().serverId() + '.'
                    : "Staff Mode did not resume after the backend handoff; your original state remains restored.";
        }
        proxy.getPlayer(playerId).ifPresent(player ->
                player.sendMessage(VelocityMessageStyle.style(Component.text(message))));
    }

    private static boolean durableStaffModeArrived(
            Optional<net.enthusia.staff.domain.staff.StaffSessionSnapshot> session,
            String destination
    ) {
        return session.isPresent()
                && session.orElseThrow().state() == net.enthusia.staff.domain.staff.StaffSessionState.ACTIVE
                && session.orElseThrow().serverId().equalsIgnoreCase(destination);
    }

    private void denyServerSwitchWhenActive(ServerPreConnectEvent event, String message) {
        if (authorityMode.get() == OperationalMode.ACTIVE) {
            denyServerSwitch(event, message);
        }
    }

    private void denyServerSwitch(ServerPreConnectEvent event, String message) {
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        Component styled = VelocityMessageStyle.style(Component.text(message));
        if (event.getPreviousServer() == null) {
            event.getPlayer().disconnect(styled);
            return;
        }
        event.getPlayer().sendMessage(styled);
    }

    @SuppressWarnings("PMD.CloseResource") // Borrows the plugin-owned worker pool; shutdown owns its lifecycle.
    private void enqueuePresence(UUID playerId, Runnable update) {
        ExecutorService executor = workers;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        CompletableFuture<Void> next = presenceUpdates.compute(playerId, (ignored, previous) -> {
            CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((value, failure) -> null);
            return start.thenRunAsync(update, executor);
        });
        var unused = next.whenComplete((ignored, failure) -> {
            presenceUpdates.remove(playerId, next);
            if (failure != null) {
                logger.error("Unable to persist an ordered player-presence update", failure);
            }
        });
    }

    private boolean failClosedConfigured() {
        VelocityConfiguration loaded = configuration;
        return loaded == null || loaded.failClosedWhileActive();
    }

    private String appealsUrl() {
        VelocityConfiguration loaded = configuration;
        return loaded == null ? "https://enthusia.net/appeals" : loaded.appealsUrl();
    }

    private String appealInstructions(ActiveSanction sanction) {
        WebsiteModerationStore store = websiteModerationStore;
        if (store == null) {
            return "\nAppeal: " + appealsUrl();
        }
        try {
            return store.codeForSanction(sanction.sanctionId(), Clock.systemUTC().instant())
                    .map(code -> "\nAppeal: " + appealsUrl() + "\nPunishment code: " + code.code())
                    .orElse("\nAppeal: " + appealsUrl());
        } catch (RuntimeException exception) {
            logger.error("A punishment code could not be prepared for a denied login", exception);
            return "\nAppeal: " + appealsUrl();
        }
    }

    private static ExecutorService createWorkers() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "EnthusiaStaff-Velocity-Worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                4,
                4,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private void executeReload(CommandSource source, String[] arguments) {
        if (!source.hasPermission("enthusiastaff.reload")) {
            source.sendMessage(VelocityMessageStyle.style(Component.text("You do not have permission to reload EnthusiaStaff.")));
            return;
        }
        if (arguments.length != SINGLE_OPERATION_ARGUMENT) {
            source.sendMessage(VelocityMessageStyle.style(Component.text("Usage: /estaff reload")));
            return;
        }
        if (!reloadRunning.compareAndSet(false, true)) {
            source.sendMessage(VelocityMessageStyle.style(Component.text("Another Velocity configuration reload is already running.")));
            return;
        }
        if (!submitWorker(() -> {
            try {
                VelocityConfigurationReloadCoordinator coordinator = reloadCoordinator;
                if (coordinator == null) {
                    retryUnavailableBootstrap(source);
                    return;
                }
                VelocityConfigurationReloadResult result = coordinator.reload();
                publishReloadHealth(result);
                source.sendMessage(VelocityMessageStyle.style(Component.text(result.message())));
                result.details().forEach(detail -> source.sendMessage(VelocityMessageStyle.style(Component.text("- " + detail))));
            } finally {
                reloadRunning.set(false);
            }
        })) {
            reloadRunning.set(false);
            source.sendMessage(VelocityMessageStyle.style(Component.text("The bounded work queue is full; reload did not start.")));
        }
    }

    private void retryUnavailableBootstrap(CommandSource source) {
        try {
            VelocityConfiguration.load(dataDirectory);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            updateHealthIssue(
                    CONFIGURATION_RELOAD_ISSUE,
                    "The Velocity configuration candidate is invalid; the previous unavailable state is unchanged"
            );
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Velocity configuration validation failed; storage retry was not started."
            )));
            return;
        }
        VelocityBootstrapCoordinator coordinator = bootstrapCoordinator;
        if (coordinator != null && coordinator.requestImmediateRetry()) {
            updateHealthIssue(CONFIGURATION_RELOAD_ISSUE, null);
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Velocity configuration is valid; an immediate bounded storage retry was started."
            )));
        } else {
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Storage is already active, retrying, or shutting down; no duplicate attempt was started."
            )));
        }
    }

    private void publishReloadHealth(VelocityConfigurationReloadResult result) {
        switch (result.outcome()) {
            case APPLIED, NO_CHANGES -> {
                updateHealthIssue(CONFIGURATION_RELOAD_ISSUE, null);
                updateHealthIssue("configuration-restart-required", null);
            }
            case RESTART_REQUIRED -> updateHealthIssue(
                    "configuration-restart-required",
                    "A validated Velocity configuration candidate requires a proxy restart and was not applied"
            );
            case VALIDATION_FAILED -> updateHealthIssue(
                    CONFIGURATION_RELOAD_ISSUE,
                    "Velocity configuration validation failed; the live configuration is unchanged"
            );
            case UNAVAILABLE -> updateHealthIssue(
                    CONFIGURATION_RELOAD_ISSUE,
                    "Velocity configuration publication failed; inspect the sanitized proxy log"
            );
            case SHUTTING_DOWN -> updateHealthIssue(
                    CONFIGURATION_RELOAD_ISSUE,
                    "Velocity configuration reload was rejected during shutdown"
            );
            default -> throw new IllegalStateException("Unsupported reload outcome: " + result.outcome());
        }
    }

    private void updateHealthIssue(String component, String reason) {
        health.updateIssue(component, reason);
    }

    private static String normalizedArgument(String[] arguments, int index) {
        return index < arguments.length
                ? arguments[index].toLowerCase(java.util.Locale.ROOT)
                : "";
    }

    private boolean requireActiveStaffDuty(CommandSource source) {
        if (!(source instanceof Player player)) {
            return true;
        }
        StaffSessionStore store = staffSessionStore;
        String backend = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        boolean active = false;
        if (store != null && backend != null) {
            try {
                active = VelocityStaffDutyContext.matchesActiveSession(
                        store.active(player.getUniqueId()), backend
                );
            } catch (RuntimeException exception) {
                logger.warn("Unable to verify Staff Mode session for proxy command", exception);
            }
        }
        if (!active) {
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "That command requires active staff mode. Use /staff on a backend first."
            )));
        }
        return active;
    }

    private final class AltsCommand implements SimpleCommand {
        @Override
        public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
            String[] args = invocation.arguments();
            return args.length <= 1
                    ? playerSuggestions.suggest(invocation.source(), args.length == 0 ? "" : args[0],
                            "enthusiastaff.alts.view")
                    : CompletableFuture.completedFuture(List.of());
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!requireActiveStaffDuty(source)) {
                return;
            }
            String[] arguments = invocation.arguments();
            if (arguments.length != SINGLE_OPERATION_ARGUMENT) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Usage: /alts <player>")));
                return;
            }
            submitAltTask(source, () -> {
                PlayerDirectory directory = playerDirectory;
                NetworkIdentityStore store = networkIdentityStore;
                if (directory == null || store == null) {
                    source.sendMessage(VelocityMessageStyle.style(Component.text("The player directory or alt store is not ready.")));
                    return;
                }
                net.enthusia.staff.domain.player.PlayerIdentity target = directory.find(arguments[0]).orElse(null);
                if (target == null) {
                    source.sendMessage(VelocityMessageStyle.style(Component.text("That player has never joined the network.")));
                    return;
                }
                Optional<List<CurrentLinkedMinecraftAccount>> linkedAccounts = currentLinkedAccounts(target.playerId());
                List<AltRelationshipSummary> relationships = store.relationships(target.playerId());
                AltRelationshipPresentation.render(
                        target,
                        linkedAccounts.orElseGet(List::of),
                        linkedAccounts.isPresent(),
                        relationships,
                        new PlayerNames(directory)
                ).forEach(source::sendMessage);
            });
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("enthusiastaff.alts.view");
        }
    }

    private Optional<List<CurrentLinkedMinecraftAccount>> currentLinkedAccounts(UUID playerId) {
        AccountLinkingStore links = accountLinkingStore;
        if (links == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(links.currentLinkedMinecraftAccounts(playerId, MAX_LINKED_ACCOUNTS_SHOWN));
        } catch (RuntimeException exception) {
            if (logger.isWarnEnabled()) {
                logger.warn("Current linked-account view is unavailable ({})", exception.getClass().getSimpleName());
            }
            return Optional.empty();
        }
    }

    final class AltCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!requireActiveStaffDuty(source)) {
                return;
            }
            String[] arguments = invocation.arguments();
            if (arguments.length < MINIMUM_ALT_ARGUMENTS) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Usage: /alt <link|approve|household|notrelated|unlink|reopen> <player1> <player2> <reason>"
                )));
                return;
            }
            Optional<AltOperation> parsed = AltOperation.parse(normalizedArgument(arguments, ROOT_OPERATION_INDEX));
            if (parsed.isEmpty()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Unknown alt operation.")));
                return;
            }
            AltOperation operation = parsed.orElseThrow();
            if (operation == AltOperation.REOPEN && !source.hasPermission("enthusiastaff.alts.reopen")) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Admin permission is required to reopen a not-related decision.")));
                return;
            }
            String reason = String.join(" ", java.util.Arrays.copyOfRange(arguments, 3, arguments.length));
            submitAltTask(source, () -> changeRelationship(
                    source, operation, arguments[1], arguments[2], reason
            ));
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            return hasPermission(invocation) && args.length <= 1
                    ? VelocityPlayerSuggestions.operations(args.length == 0 ? "" : args[0],
                            invocation.source().hasPermission("enthusiastaff.alts.reopen")) : List.of();
        }

        @Override
        public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
            String[] args = invocation.arguments();
            if (!hasPermission(invocation) || (args.length > 0 && args[0].equalsIgnoreCase("reopen")
                    && !invocation.source().hasPermission("enthusiastaff.alts.reopen"))) {
                return CompletableFuture.completedFuture(List.of());
            }
            return VelocityPlayerSuggestions.targetPosition(args)
                    ? playerSuggestions.suggest(invocation.source(), args[args.length - 1], "enthusiastaff.alts.manage")
                    : CompletableFuture.completedFuture(suggest(invocation));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("enthusiastaff.alts.manage");
        }
    }

    private void changeRelationship(
            CommandSource source,
            AltOperation operation,
            String firstInput,
            String secondInput,
            String reason
    ) {
        PlayerDirectory directory = playerDirectory;
        NetworkIdentityStore store = networkIdentityStore;
        if (directory == null || store == null) {
            source.sendMessage(VelocityMessageStyle.style(Component.text("The player directory or alt store is not ready.")));
            return;
        }
        net.enthusia.staff.domain.player.PlayerIdentity first = directory.find(firstInput).orElse(null);
        net.enthusia.staff.domain.player.PlayerIdentity second = directory.find(secondInput).orElse(null);
        if (first == null || second == null) {
            source.sendMessage(VelocityMessageStyle.style(Component.text("Both players must have joined the network previously.")));
            return;
        }
        UUID actorId = source instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        boolean changed = operation == AltOperation.REOPEN
                ? store.reopen(first.playerId(), second.playerId(), actorId, Clock.systemUTC().instant(), reason)
                : store.setRelationship(
                        first.playerId(), second.playerId(), operation.relationshipState(),
                        actorId, Clock.systemUTC().instant(), reason
                );
        source.sendMessage(VelocityMessageStyle.style(Component.text(changed
                ? "Alt relationship change committed and audited."
                : "No change was made; a locked not-related decision may require explicit reopen.")));
    }

    private void submitAltTask(CommandSource source, Runnable operation) {
        try {
            workers.execute(() -> {
                try {
                    if (!requireActiveStaffDuty(source)) {
                        return;
                    }
                    operation.run();
                } catch (RuntimeException exception) {
                    logger.error("Alt command failed", exception);
                    source.sendMessage(VelocityMessageStyle.style(Component.text("Alt operation failed; inspect the sanitized proxy log.")));
                }
            });
        } catch (RejectedExecutionException exception) {
            source.sendMessage(VelocityMessageStyle.style(Component.text("The bounded work queue is full; alt operation did not start.")));
        }
    }

    final class StatusCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] arguments = invocation.arguments();
            String operation = normalizedArgument(arguments, ROOT_OPERATION_INDEX);
            if (!operation.isEmpty() && !requireActiveStaffDuty(source)) {
                return;
            }
            switch (operation) {
                case "reload" -> executeReload(source, arguments);
                case "verify" -> executeVerify(source, arguments);
                case "migration" -> executeMigration(source, arguments);
                case "cutover" -> executeCutover(source, arguments);
                case "discord" -> executeDiscord(source, arguments);
                case "website" -> executeWebsite(source, arguments);
                default -> showStatus(source);
            }
        }

        private void executeVerify(CommandSource source, String[] arguments) {
            if (arguments.length != 2 || !"full".equals(normalizedArgument(arguments, SUB_OPERATION_INDEX))) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Usage: /estaff verify full")));
                return;
            }
            if (!source.hasPermission("enthusiastaff.verify")
                    || !source.hasPermission("enthusiastaff.diagnostics")) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("You do not have permission to run full EnthusiaStaff diagnostics.")));
                return;
            }
            submitVerification(source);
        }

        private void submitVerification(CommandSource source) {
            try {
                workers.execute(() -> networkVerifier.verify().forEach(source::sendMessage));
            } catch (RejectedExecutionException exception) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("The bounded work queue is full; verification did not start.")));
            }
        }

        private void showStatus(CommandSource source) {
            VelocityRuntimeHealth.Snapshot snapshot = health.snapshot();
            source.sendMessage(VelocityMessageStyle.modeHeader("EnthusiaStaff", snapshot.mode()));
            VelocityBootstrapCoordinator bootstrap = bootstrapCoordinator;
            if (bootstrap != null && !bootstrap.completed()) {
                VelocityMessageStyle.Tone tone = bootstrap.exhausted()
                        ? VelocityMessageStyle.Tone.ERROR
                        : VelocityMessageStyle.Tone.WARNING;
                source.sendMessage(VelocityMessageStyle.statusRow(
                        "Storage",
                        bootstrap.exhausted() ? "Failed" : "Starting",
                        "attempts " + bootstrap.attempts()
                                + " • retry scheduled " + bootstrap.retryScheduled(),
                        tone
                ));
            }
            if (snapshot.issues().isEmpty()) {
                source.sendMessage(VelocityMessageStyle.statusRow(
                        "Runtime",
                        "Healthy",
                        "No active runtime health issues",
                        VelocityMessageStyle.Tone.SUCCESS
                ));
                return;
            }
            snapshot.issues().forEach((component, reason) -> {
                VelocityMessageStyle.Tone tone = VelocityMessageStyle.issueTone(
                        component,
                        reason,
                        snapshot.mode()
                );
                source.sendMessage(VelocityMessageStyle.statusRow(
                        VelocityMessageStyle.label(component),
                        tone == VelocityMessageStyle.Tone.ERROR ? "Blocked" : "Disabled",
                        reason,
                        tone
                ));
            });
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return VelocityStatusSuggestions.suggest(
                    invocation.arguments(),
                    invocation.source()::hasPermission
            );
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            String[] arguments = invocation.arguments();
            String operation = normalizedArgument(arguments, ROOT_OPERATION_INDEX);
            if (operation.equals("reload")) {
                return invocation.source().hasPermission("enthusiastaff.reload");
            }
            if (operation.equals("verify")) {
                return invocation.source().hasPermission("enthusiastaff.verify");
            }
            return invocation.source().hasPermission("enthusiastaff.status");
        }

        private void executeWebsite(CommandSource source, String[] arguments) {
            if (!source.hasPermission("enthusiastaff.website.manage")) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("You do not have permission to manage website bindings.")));
                return;
            }
            if (showWebsiteStatusWhenRequested(source, arguments)) {
                return;
            }
            WebsiteModerationStore store = websiteModerationStore;
            if (store == null) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("The website API store is not available.")));
                return;
            }
            if (!isWebsiteCodeCommand(arguments)) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Usage: /estaff website status | /estaff website code "
                                + "<show|rotate|revoke> <case|punishment-id> [confirmation]"
                )));
                return;
            }
            if (!(source instanceof Player staff)) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Punishment codes are only shown or changed in a verified in-game staff session."
                )));
                return;
            }
            executeWebsiteCodeOperation(source, staff, store, arguments);
        }

        private boolean showWebsiteStatusWhenRequested(CommandSource source, String[] arguments) {
            if (arguments.length != WEBSITE_STATUS_ARGUMENTS
                    || !normalizedArgument(arguments, SUB_OPERATION_INDEX).equals("status")) {
                return false;
            }
            VelocityConfiguration loaded = configuration;
            boolean listening = loaded != null && loaded.websiteApiEnabled() && websiteApiServer != null;
            source.sendMessage(VelocityMessageStyle.style(Component.text(listening
                    ? "Website API: LISTENING on loopback "
                    + loaded.websiteApiBindAddress() + ':' + loaded.websiteApiPort()
                    : "Website API: DISABLED or unavailable")));
            return true;
        }

        private boolean isWebsiteCodeCommand(String[] arguments) {
            return arguments.length >= WEBSITE_SHOW_ARGUMENTS
                    && normalizedArgument(arguments, SUB_OPERATION_INDEX).equals("code");
        }

        private void executeWebsiteCodeOperation(
                CommandSource source,
                Player staff,
                WebsiteModerationStore store,
                String[] arguments
        ) {
            switch (normalizedArgument(arguments, DETAIL_OPERATION_INDEX)) {
                case "show" -> executeWebsiteShow(source, store, arguments);
                case "rotate" -> executeWebsiteRotate(source, staff, store, arguments);
                case "revoke" -> executeWebsiteRevoke(source, staff, store, arguments);
                default -> showWebsiteConfirmationUsage(source);
            }
        }

        private void executeWebsiteShow(
                CommandSource source,
                WebsiteModerationStore store,
                String[] arguments
        ) {
            if (arguments.length != WEBSITE_SHOW_ARGUMENTS) {
                showWebsiteConfirmationUsage(source);
                return;
            }
            String target = arguments[TARGET_INDEX];
            submitWebsiteTask(source, () -> showPunishmentCodes(source, store, target));
        }

        private void executeWebsiteRotate(
                CommandSource source,
                Player staff,
                WebsiteModerationStore store,
                String[] arguments
        ) {
            if (!hasConfirmation(arguments, "CONFIRM-CODE-ROTATE")) {
                showWebsiteConfirmationUsage(source);
                return;
            }
            UUID punishmentId = parseUuid(arguments[TARGET_INDEX]);
            if (punishmentId == null) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Rotation requires a punishment UUID.")));
                return;
            }
            submitWebsiteTask(source, () -> rotatePunishmentCode(source, staff, store, punishmentId));
        }

        private void rotatePunishmentCode(
                CommandSource source,
                Player staff,
                WebsiteModerationStore store,
                UUID punishmentId
        ) {
            PunishmentCodeDisplay code = store.rotateCode(
                    punishmentId,
                    staff.getUniqueId(),
                    Clock.systemUTC().instant()
            );
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Rotated code for punishment " + code.punishmentId() + ": " + code.code()
            )));
        }

        private void executeWebsiteRevoke(
                CommandSource source,
                Player staff,
                WebsiteModerationStore store,
                String[] arguments
        ) {
            if (!hasConfirmation(arguments, "CONFIRM-CODE-REVOKE")) {
                showWebsiteConfirmationUsage(source);
                return;
            }
            UUID punishmentId = parseUuid(arguments[TARGET_INDEX]);
            if (punishmentId == null) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Revocation requires a punishment UUID.")));
                return;
            }
            submitWebsiteTask(source, () -> revokePunishmentCode(source, staff, store, punishmentId));
        }

        private void revokePunishmentCode(
                CommandSource source,
                Player staff,
                WebsiteModerationStore store,
                UUID punishmentId
        ) {
            boolean changed = store.revokeCode(
                    punishmentId,
                    staff.getUniqueId(),
                    Clock.systemUTC().instant()
            );
            source.sendMessage(VelocityMessageStyle.style(Component.text(changed
                    ? "The punishment code was revoked and its binding is now ineligible."
                    : "No active punishment code changed.")));
        }

        private static boolean hasConfirmation(String[] arguments, String confirmation) {
            return arguments.length == WEBSITE_MUTATION_ARGUMENTS
                    && arguments[CONFIRMATION_INDEX].equals(confirmation);
        }

        private static void showWebsiteConfirmationUsage(CommandSource source) {
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Use show without confirmation, or append CONFIRM-CODE-ROTATE / CONFIRM-CODE-REVOKE."
            )));
        }

        private void showPunishmentCodes(
                CommandSource source,
                WebsiteModerationStore store,
                String target
        ) {
            UUID punishmentId = parseUuid(target);
            List<PunishmentCodeDisplay> codes;
            if (punishmentId != null) {
                codes = store.codeForSanction(punishmentId, Clock.systemUTC().instant())
                        .map(List::of)
                        .orElseGet(List::of);
            } else {
                CaseId caseId;
                try {
                    caseId = new CaseId(target);
                } catch (IllegalArgumentException exception) {
                    source.sendMessage(VelocityMessageStyle.style(Component.text("Enter a case ID or punishment UUID.")));
                    return;
                }
                codes = store.codesForCase(caseId, Clock.systemUTC().instant());
            }
            if (codes.isEmpty()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("No active appeal-eligible punishment code exists.")));
                return;
            }
            for (PunishmentCodeDisplay code : codes) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        code.punishmentType() + " case " + code.caseId()
                                + " punishment " + code.punishmentId() + ": " + code.code()
                )));
            }
        }

        private void submitWebsiteTask(CommandSource source, Runnable task) {
            try {
                workers.execute(() -> {
                    try {
                        task.run();
                    } catch (RuntimeException exception) {
                        logger.error("Website administration command failed", exception);
                        source.sendMessage(VelocityMessageStyle.style(Component.text(
                                "Website operation failed; inspect the sanitized proxy log."
                        )));
                    }
                });
            } catch (RejectedExecutionException exception) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "The bounded work queue is full; the website operation did not start."
                )));
            }
        }

        private UUID parseUuid(String value) {
            try {
                UUID parsed = UUID.fromString(value);
                return parsed.toString().equalsIgnoreCase(value) ? parsed : null;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private void executeMigration(CommandSource source, String[] arguments) {
            if (!source.hasPermission("enthusiastaff.migration")) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("You do not have permission to run migration operations.")));
                return;
            }
            if (arguments.length != MIGRATION_ARGUMENTS) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Usage: /estaff migration <inspect|dry-run|import|shadow|final>")));
                return;
            }
            MariaDbRuntime runtime = databaseRuntime;
            VelocityConfiguration loaded = configuration;
            if (runtime == null || loaded == null) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("MariaDB is not ready; no migration action was taken.")));
                return;
            }
            Optional<MigrationMode> parsed = parseMigrationMode(arguments[SUB_OPERATION_INDEX]);
            if (parsed.isEmpty()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Unknown migration operation.")));
                return;
            }
            MigrationMode migrationMode = parsed.orElseThrow();
            Optional<String> blocker = migrationModeBlocker(migrationMode, authorityMode.get());
            if (blocker.isPresent()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(blocker.orElseThrow())));
                return;
            }
            startMigration(source, runtime, loaded, migrationMode);
        }

        private Optional<MigrationMode> parseMigrationMode(String operation) {
            return switch (operation.toLowerCase(java.util.Locale.ROOT)) {
                case "dry-run", "inspect" -> Optional.of(MigrationMode.DRY_RUN);
                case "import" -> Optional.of(MigrationMode.IMPORT);
                case "shadow" -> Optional.of(MigrationMode.SHADOW);
                case "final" -> Optional.of(MigrationMode.CUTOVER);
                default -> Optional.empty();
            };
        }

        private Optional<String> migrationModeBlocker(MigrationMode mode, OperationalMode authority) {
            return switch (mode) {
                case SHADOW -> authority == OperationalMode.SHADOW_MIGRATION
                        ? Optional.empty()
                        : Optional.of("Shadow runs require SHADOW_MIGRATION mode.");
                case CUTOVER -> authority == OperationalMode.MAINTENANCE
                        ? Optional.empty()
                        : Optional.of("The final import and comparison require MAINTENANCE mode.");
                case IMPORT -> authority == OperationalMode.SHADOW_MIGRATION
                        || authority == OperationalMode.MAINTENANCE
                        ? Optional.empty()
                        : Optional.of("Imports require SHADOW_MIGRATION or MAINTENANCE mode.");
                default -> Optional.empty();
            };
        }

        private void startMigration(
                CommandSource source,
                MariaDbRuntime runtime,
                VelocityConfiguration loaded,
                MigrationMode migrationMode
        ) {
            if (!migrationRunning.compareAndSet(false, true)) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Another migration operation is already running.")));
                return;
            }
            source.sendMessage(VelocityMessageStyle.style(Component.text("Migration operation accepted; results will be reported when durable.")));
            try {
                workers.execute(() -> runMigration(source, runtime, loaded, migrationMode));
            } catch (RejectedExecutionException exception) {
                migrationRunning.set(false);
                source.sendMessage(VelocityMessageStyle.style(Component.text("The bounded work queue is full; migration did not start.")));
            }
        }

        private void runMigration(
                CommandSource source,
                MariaDbRuntime runtime,
                VelocityConfiguration loaded,
                MigrationMode migrationMode
        ) {
            try {
                MigrationExecutionReport report = migrationService(runtime, migrationMode).execute(
                        loaded.liteBansDatabase(dataDirectory),
                        loaded.liteBansTablePrefix(),
                        loaded.liteBansBatchSize(),
                        migrationMode
                );
                showMigrationReport(source, report);
            } catch (RuntimeException exception) {
                logger.error("Migration command failed", exception);
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Migration failed; inspect the sanitized proxy log and durable run record."
                )));
            } finally {
                migrationRunning.set(false);
            }
        }

        private void showMigrationReport(CommandSource source, MigrationExecutionReport report) {
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Migration " + report.mode() + " run " + report.runId() + ": source="
                            + report.sourceRecords() + ", imported=" + report.importedRecords()
                            + ", reconciled=" + report.reconciledRecords()
                            + ", replayed=" + report.replayedRecords() + ", rejected="
                            + report.rejectedRows().size() + ", schema-blockers="
                            + report.schema().blockers().size() + ", protected-identities="
                            + report.protectedIdentityRecords() + '/' + report.networkIdentityRecords()
            )));
            report.shadowSummary().ifPresent(summary -> showShadowComparison(source, summary));
            showRejectedRows(source, report);
        }

        private void showShadowComparison(
                CommandSource source,
                net.enthusia.staff.persistence.migration.ShadowSummary summary
        ) {
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Comparison: mismatches=" + summary.mismatchCount()
                            + ", counts=" + summary.countsMatch()
                            + ", checksums=" + summary.checksumsMatch()
                            + ", active=" + summary.activeSanctionsMatch()
                            + ", UUIDs=" + summary.uuidMappingsMatch()
                            + ", expirations=" + summary.expirationsMatch()
                            + ", login=" + comparison(summary.loginDecisions())
                            + ", mute=" + comparison(summary.muteDecisions())
                            + ", IP-ban=" + comparison(summary.ipBanDecisions())
            )));
        }

        private void showRejectedRows(CommandSource source, MigrationExecutionReport report) {
            report.rejectedRows().stream().limit(MAX_REJECTED_ROWS_SHOWN).forEach(row ->
                    source.sendMessage(VelocityMessageStyle.style(Component.text(
                            "Rejected " + row.tableName() + '#' + row.externalId() + ": " + row.reasonCode()
                    ))));
            int hiddenRows = report.rejectedRows().size() - MAX_REJECTED_ROWS_SHOWN;
            if (hiddenRows > 0) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        hiddenRows + " additional rejected rows are recorded in the durable migration report."
                )));
            }
        }

        private void executeCutover(CommandSource source, String[] arguments) {
            if (!source.hasPermission("enthusiastaff.cutover")) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("You do not have permission to manage cutover.")));
                return;
            }
            if (arguments.length < CUTOVER_MINIMUM_ARGUMENTS) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Usage: /estaff cutover <status|maintenance|abort|freeze|activate|override>"
                )));
                return;
            }
            MariaDbRuntime runtime = databaseRuntime;
            if (runtime == null) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("MariaDB is not ready; no cutover action was taken.")));
                return;
            }
            UUID actorId = actorId(source);
            routeCutoverOperation(source, runtime, actorId, arguments);
        }

        private void routeCutoverOperation(
                CommandSource source,
                MariaDbRuntime runtime,
                UUID actorId,
                String[] arguments
        ) {
            switch (normalizedArgument(arguments, SUB_OPERATION_INDEX)) {
                case "status" -> executeCutoverStatus(source, runtime);
                case "maintenance" -> executeMaintenance(source, runtime, actorId);
                case "abort" -> executeMaintenanceAbort(source, runtime, actorId, arguments);
                case "freeze" -> executeAuthorityFreeze(source, runtime, actorId, arguments);
                case "activate" -> executeCutoverActivation(source, actorId, arguments);
                case "override" -> executeCutoverOverride(source, actorId, arguments);
                default -> source.sendMessage(VelocityMessageStyle.style(Component.text("Unknown cutover operation.")));
            }
        }

        private void executeCutoverStatus(CommandSource source, MariaDbRuntime runtime) {
            submitCutover(source, () -> showCutoverStatus(source, runtime));
        }

        private void showCutoverStatus(CommandSource source, MariaDbRuntime runtime) {
            net.enthusia.staff.persistence.migration.CutoverCoordinator coordinator = runtime.cutoverCoordinator();
            coordinator.latestEvidence().ifPresentOrElse(
                    evidence -> showCutoverEvidence(source, evidence),
                    () -> source.sendMessage(VelocityMessageStyle.style(Component.text("No complete shadow evidence is available.")))
            );
            CutoverAssessment assessment = coordinator.assess(Optional.empty());
            source.sendMessage(VelocityMessageStyle.style(Component.text("Cutover allowed: " + assessment.allowed()
                    + "; blockers: " + String.join(", ", assessment.blockers()))));
        }

        private void showCutoverEvidence(CommandSource source, CutoverEvidence evidence) {
            long observedHours = Duration.between(evidence.shadowStartedAt(), evidence.shadowEndedAt()).toHours();
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Shadow evidence: observed=" + observedHours + "h, summaries="
                            + evidence.successfulShadowSummaries().size() + ", unresolved="
                            + evidence.unresolvedOperations() + ", migration-idle="
                            + evidence.migrationIdle() + ", writes-frozen=" + evidence.writesFrozen()
                            + ", final-import=" + evidence.finalIncrementalImportComplete()
            )));
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Checks: counts=" + evidence.countsMatch()
                            + ", checksums=" + evidence.checksumsMatch()
                            + ", active=" + evidence.activeSanctionsMatch()
                            + ", UUIDs=" + evidence.uuidMappingsMatch()
                            + ", expirations=" + evidence.expirationsMatch()
                            + ", login=" + comparison(evidence.loginDecisions())
                            + ", mute=" + comparison(evidence.muteDecisions())
                            + ", IP-ban=" + comparison(evidence.ipBanDecisions())
            )));
        }

        private void executeMaintenance(CommandSource source, MariaDbRuntime runtime, UUID actorId) {
            submitCutover(source, () -> {
                boolean changed = runtime.cutoverCoordinator().enterMaintenance(
                        actorId, "Cutover preparation requested through Velocity"
                );
                source.sendMessage(VelocityMessageStyle.style(Component.text(changed
                        ? "Maintenance committed. Run the final incremental import, then reassess cutover."
                        : "Maintenance was not entered; the current mode is not SHADOW_MIGRATION or changed concurrently.")));
            });
        }

        private void executeMaintenanceAbort(
                CommandSource source,
                MariaDbRuntime runtime,
                UUID actorId,
                String[] arguments
        ) {
            if (!founderAuthorized(source, "Founder permission is required to abort cutover maintenance.")) {
                return;
            }
            Optional<String> reason = confirmedReason(arguments, "CONFIRM-ABORT-MAINTENANCE");
            if (reason.isEmpty()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Abort requires the exact acknowledgement and a written reason.")));
                return;
            }
            submitCutover(source, () -> {
                boolean changed = runtime.cutoverCoordinator().abortMaintenance(actorId, reason.orElseThrow());
                source.sendMessage(VelocityMessageStyle.style(Component.text(changed
                        ? "Maintenance aborted; LiteBans remains authoritative and the shadow gate must be reassessed."
                        : "Maintenance was not aborted because the current mode is not MAINTENANCE.")));
            });
        }

        private void executeAuthorityFreeze(
                CommandSource source,
                MariaDbRuntime runtime,
                UUID actorId,
                String[] arguments
        ) {
            if (!founderAuthorized(source, "Founder permission is required to freeze active authority.")) {
                return;
            }
            Optional<String> reason = confirmedReason(arguments, "CONFIRM-READ-ONLY-FAILURE");
            if (reason.isEmpty()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Emergency freeze requires the exact acknowledgement and a written reason."
                )));
                return;
            }
            submitCutover(source, () -> {
                boolean changed = runtime.cutoverCoordinator().freezeActiveAuthority(actorId, reason.orElseThrow());
                source.sendMessage(VelocityMessageStyle.style(Component.text(changed
                        ? "ACTIVE authority is now READ_ONLY_FAILURE; destructive writes are disabled and logins fail closed."
                        : "Authority was not frozen because the current mode is not ACTIVE.")));
            });
        }

        private void executeCutoverActivation(CommandSource source, UUID actorId, String[] arguments) {
            if (arguments.length != CUTOVER_ACTIVATION_ARGUMENTS
                    || !arguments[DETAIL_OPERATION_INDEX].equals("CONFIRM-ACTIVE-CUTOVER")) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Activation requires: /estaff cutover activate CONFIRM-ACTIVE-CUTOVER"
                )));
                return;
            }
            submitCutover(source, () -> activateCutover(source, actorId, Optional.empty()));
        }

        private void executeCutoverOverride(CommandSource source, UUID actorId, String[] arguments) {
            if (!founderAuthorized(
                    source,
                    "Founder permission is required for a blocked cutover override."
            )) {
                return;
            }
            Optional<String> reason = confirmedReason(arguments, FounderOverride.REQUIRED_ACKNOWLEDGEMENT);
            if (reason.isEmpty()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Override requires the exact acknowledgement and a written reason."
                )));
                return;
            }
            FounderOverride founderOverride = new FounderOverride(
                    actorId,
                    arguments[DETAIL_OPERATION_INDEX],
                    reason.orElseThrow()
            );
            submitCutover(source, () -> activateCutover(source, actorId, Optional.of(founderOverride)));
        }

        private boolean founderAuthorized(CommandSource source, String failureMessage) {
            if (source.hasPermission("enthusiastaff.cutover.founder")) {
                return true;
            }
            source.sendMessage(VelocityMessageStyle.style(Component.text(failureMessage)));
            return false;
        }

        private Optional<String> confirmedReason(String[] arguments, String acknowledgement) {
            if (arguments.length < CUTOVER_REASON_MINIMUM_ARGUMENTS
                    || !arguments[DETAIL_OPERATION_INDEX].equals(acknowledgement)) {
                return Optional.empty();
            }
            return Optional.of(String.join(
                    " ",
                    Arrays.copyOfRange(arguments, TARGET_INDEX, arguments.length)
            ));
        }

        private UUID actorId(CommandSource source) {
            return source instanceof Player player ? player.getUniqueId() : CONSOLE_ACTOR_ID;
        }

        private String comparison(DecisionComparison value) {
            return value.mismatched() + "/" + value.compared() + " mismatched";
        }

        private void executeDiscord(CommandSource source, String[] arguments) {
            if (!source.hasPermission("enthusiastaff.discord.manage")) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("You do not have permission to manage Discord delivery.")));
                return;
            }
            MariaDbRuntime runtime = databaseRuntime;
            if (runtime == null) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("MariaDB is not ready; Discord status is unavailable.")));
                return;
            }
            switch (normalizedArgument(arguments, SUB_OPERATION_INDEX)) {
                case "status" -> executeDiscordStatus(source, runtime, arguments);
                case "retry" -> executeDiscordRetry(source, runtime, arguments);
                default -> showDiscordUsage(source);
            }
        }

        private void executeDiscordStatus(
                CommandSource source,
                MariaDbRuntime runtime,
                String[] arguments
        ) {
            if (arguments.length != DISCORD_STATUS_ARGUMENTS) {
                showDiscordUsage(source);
                return;
            }
            try {
                workers.execute(() -> showDiscordStatus(source, runtime));
            } catch (RejectedExecutionException exception) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("The bounded work queue is full; status was not read.")));
            }
        }

        private void showDiscordStatus(CommandSource source, MariaDbRuntime runtime) {
            try {
                Instant now = Clock.systemUTC().instant();
                for (net.enthusia.staff.domain.discord.DiscordChannelStatus status
                        : runtime.discordOutboxStore().channelStatuses()) {
                    source.sendMessage(VelocityMessageStyle.style(Component.text(status.destination()
                            + ": pending=" + status.pendingMessages()
                            + ", dead=" + status.deadLetterMessages()
                            + ", failures=" + status.consecutiveFailures()
                            + ", circuit=" + (status.circuitOpen(now) ? "OPEN" : "CLOSED"))));
                }
            } catch (RuntimeException exception) {
                logger.error("Discord status command failed", exception);
                source.sendMessage(VelocityMessageStyle.style(Component.text("Discord status failed; inspect the sanitized proxy log.")));
            }
        }

        private void executeDiscordRetry(
                CommandSource source,
                MariaDbRuntime runtime,
                String[] arguments
        ) {
            if (arguments.length != DISCORD_RETRY_ARGUMENTS
                    || !arguments[TARGET_INDEX].equals("CONFIRM-DISCORD-RETRY")) {
                showDiscordUsage(source);
                return;
            }
            String destination = normalizedArgument(arguments, DETAIL_OPERATION_INDEX);
            if (!DISCORD_DESTINATIONS.contains(destination)) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Unknown Discord destination.")));
                return;
            }
            try {
                workers.execute(() -> retryDiscordDestination(source, runtime, destination));
            } catch (RejectedExecutionException exception) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("The bounded work queue is full; retry did not start.")));
            }
        }

        private void retryDiscordDestination(
                CommandSource source,
                MariaDbRuntime runtime,
                String destination
        ) {
            try {
                int retried = runtime.discordOutboxStore().retryDestination(
                        destination,
                        Clock.systemUTC().instant(),
                        DISCORD_RETRY_LIMIT
                );
                source.sendMessage(VelocityMessageStyle.style(Component.text("Discord circuit reset; queued " + retried
                        + " dead-letter events for another bounded attempt.")));
            } catch (RuntimeException exception) {
                logger.error("Discord retry command failed", exception);
                source.sendMessage(VelocityMessageStyle.style(Component.text("Discord retry failed; inspect the sanitized proxy log.")));
            }
        }

        private static void showDiscordUsage(CommandSource source) {
            source.sendMessage(VelocityMessageStyle.style(Component.text(
                    "Usage: /estaff discord status | /estaff discord retry <destination> CONFIRM-DISCORD-RETRY"
            )));
        }

        private void submitCutover(CommandSource source, Runnable action) {
            if (!migrationRunning.compareAndSet(false, true)) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Another migration or cutover operation is already running.")));
                return;
            }
            try {
                workers.execute(() -> {
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        logger.error("Cutover command failed", exception);
                        source.sendMessage(VelocityMessageStyle.style(Component.text("Cutover operation failed; inspect the sanitized proxy log.")));
                    } finally {
                        migrationRunning.set(false);
                    }
                });
            } catch (RejectedExecutionException exception) {
                migrationRunning.set(false);
                source.sendMessage(VelocityMessageStyle.style(Component.text("The bounded work queue is full; cutover operation did not start.")));
            }
        }

        private void activateCutover(
                CommandSource source,
                UUID actorId,
                java.util.Optional<FounderOverride> override
        ) {
            VelocityConfiguration loaded = configuration;
            PersistentChannelServer channel = channelServer;
            if (!allPaperBackendsConnected(loaded, channel)) {
                source.sendMessage(VelocityMessageStyle.style(Component.text(
                        "Cutover blocked: every configured Paper backend must have an authenticated persistent connection."
                )));
                return;
            }
            CutoverOutcome outcome = databaseRuntime.cutoverCoordinator().activate(actorId, override);
            if (outcome.activated()) {
                source.sendMessage(VelocityMessageStyle.style(Component.text("ACTIVE cutover committed as " + outcome.cutoverId().orElseThrow() + '.')));
            } else {
                source.sendMessage(VelocityMessageStyle.style(Component.text("Cutover blocked: " + String.join(", ", outcome.assessment().blockers()))));
            }
        }
    }

    private record StorageBindings(
            SanctionLookup sanctions,
            PlayerDirectory players,
            FreezeStore freezes,
            StaffSessionStore sessions,
            InventoryJournalStore inventories,
            EconomyJournalStore economies,
            AccountLinkingStore accountLinks
    ) {
    }

    private enum AltOperation {
        LINK,
        APPROVE,
        HOUSEHOLD,
        NOT_RELATED,
        UNLINK,
        REOPEN;

        private static Optional<AltOperation> parse(String operation) {
            return switch (operation) {
                case "link" -> Optional.of(LINK);
                case "approve" -> Optional.of(APPROVE);
                case "household" -> Optional.of(HOUSEHOLD);
                case "notrelated" -> Optional.of(NOT_RELATED);
                case "unlink" -> Optional.of(UNLINK);
                case "reopen" -> Optional.of(REOPEN);
                default -> Optional.empty();
            };
        }

        private AltRelationshipState relationshipState() {
            return switch (this) {
                case LINK -> AltRelationshipState.CONFIRMED_ALT;
                case APPROVE -> AltRelationshipState.APPROVED_ALT;
                case HOUSEHOLD -> AltRelationshipState.SHARED_HOUSEHOLD;
                case NOT_RELATED -> AltRelationshipState.NOT_RELATED;
                case UNLINK -> AltRelationshipState.LOW_CONFIDENCE;
                case REOPEN -> throw new IllegalStateException("Reopen does not set a relationship state");
            };
        }
    }

    private record WebsiteRuntime(
            WebsiteModerationStore store,
            WebsiteApiServer server,
            ScheduledTask maintenance,
            int backfilledCodes
    ) {
    }
}
