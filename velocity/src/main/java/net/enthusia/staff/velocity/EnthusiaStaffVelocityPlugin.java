package net.enthusia.staff.velocity;

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
import java.util.ArrayList;
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
import net.enthusia.staff.common.security.SecretKeyMaterial;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.migration.FounderOverride;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.domain.player.PlayerPlatform;
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
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.migration.CutoverOutcome;
import net.enthusia.staff.persistence.migration.LiteBansMigrationService;
import net.enthusia.staff.persistence.migration.MigrationExecutionReport;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.TlsContextLoader;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(
        id = "enthusiastaff",
        name = "EnthusiaStaff",
        version = "0.1.0-SNAPSHOT",
        description = "Enthusia Network staff and moderation runtime for Velocity",
        authors = {"P2wn"}
)
public final class EnthusiaStaffVelocityPlugin {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);
    private static final Set<SanctionType> LOGIN_BLOCKS = Set.of(
            SanctionType.BAN, SanctionType.NETWORK_BAN, SanctionType.NETWORK_IDENTITY_BAN
    );
    private static final String SUPPRESS_GUARD_LOG = "PMD.GuardLogStatement";
    private static final String SUPPRESS_NULL_ASSIGNMENT = "PMD.NullAssignment";
    private static final String CONFIGURATION_RELOAD_ISSUE = "configuration-reload";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final VelocityRuntimeHealth health = new VelocityRuntimeHealth();
    private final AtomicReference<OperationalMode> authorityMode = new AtomicReference<>(OperationalMode.BOOTSTRAP);
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final AtomicBoolean reloadRunning = new AtomicBoolean();
    private final AtomicBoolean migrationRunning = new AtomicBoolean();
    private final java.util.concurrent.ConcurrentHashMap<UUID, CompletableFuture<Void>> presenceUpdates =
            new java.util.concurrent.ConcurrentHashMap<>();

    private volatile ExecutorService workers;
    private volatile VelocityConfiguration configuration;
    private volatile MariaDbRuntime databaseRuntime;
    private volatile SanctionLookup sanctionLookup;
    private volatile PlayerDirectory playerDirectory;
    private volatile FreezeStore freezeStore;
    private volatile StaffSessionStore staffSessionStore;
    private volatile InventoryJournalStore inventoryJournalStore;
    private volatile EconomyJournalStore economyJournalStore;
    private volatile NetworkIdentityStore networkIdentityStore;
    private volatile NetworkIdentityProtector networkIdentityProtector;
    private volatile boolean activeAuthorityObserved;
    private volatile ScheduledTask operationalStateTask;
    private volatile PersistentChannelServer channelServer;
    private volatile NetworkOutboxWorker outboxWorker;
    private volatile DiscordOutboxWorker discordOutboxWorker;
    private volatile WebsiteModerationStore websiteModerationStore;
    private volatile WebsiteApiServer websiteApiServer;
    private volatile ScheduledTask websiteMaintenanceTask;
    private volatile ScheduledTask shadowMigrationTask;
    private volatile VelocityBootstrapCoordinator bootstrapCoordinator;
    private volatile VelocityConfigurationReloadCoordinator reloadCoordinator;

    @Inject
    public EnthusiaStaffVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    @SuppressWarnings({SUPPRESS_GUARD_LOG, "PMD.AvoidLiteralsInIfCondition"})
    // SLF4J placeholders defer formatting; attempt one is the only recovery-log threshold.
    public void onProxyInitialization(ProxyInitializeEvent ignored) {
        workers = createWorkers();
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
        health.update(OperationalMode.BOOTSTRAP, Map.of("bootstrap", "MariaDB initialization is in progress"));
        VelocityBootstrapCoordinator coordinator = new VelocityBootstrapCoordinator(
                this::submitWorker,
                this::scheduleBootstrapRetry,
                this::initializeStorageAttempt,
                new VelocityBootstrapCoordinator.Listener() {
                    @Override
                    public void attempting(int attempt, int maximumAttempts) {
                        authorityMode.set(OperationalMode.BOOTSTRAP);
                        health.update(OperationalMode.BOOTSTRAP, Map.of(
                                "mariadb-attempt",
                                "Velocity storage startup attempt " + attempt + " of " + maximumAttempts
                                        + " is in progress"
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
                        logger.warn(
                                "Velocity storage startup failed; bounded retry {} of {} is scheduled after {} ms ({})",
                                nextAttempt,
                                maximumAttempts,
                                delayMillis,
                                failure.getClass().getSimpleName()
                        );
                    }

                    @Override
                    public void recovered(int attempts) {
                        if (attempts > 1) {
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
                        logger.error(
                                "Velocity storage startup stopped after {} attempt(s) ({})",
                                attempts,
                                failure.getClass().getSimpleName()
                        );
                    }
                },
                shuttingDown::get,
                VelocityBootstrapCoordinator.RetryPolicy.defaults()
        );
        bootstrapCoordinator = coordinator;
        coordinator.start();
    }

    @Subscribe
    @SuppressWarnings(SUPPRESS_NULL_ASSIGNMENT)
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
        ExecutorService executor = workers;
        if (executor == null || executor.isShutdown()) {
            denyUnavailable(event);
            return EventTask.async(() -> {
            });
        }
        return EventTask.resumeWhenComplete(CompletableFuture.runAsync(() -> enforceLogin(event), executor));
    }

    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        ExecutorService executor = workers;
        if (executor == null || executor.isShutdown()) {
            denyServerSwitch(event, "Asset safety status is temporarily unavailable.");
            return EventTask.async(() -> {
            });
        }
        return EventTask.resumeWhenComplete(CompletableFuture.runAsync(() -> enforceSafeServerSwitch(event), executor));
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
            opened = MariaDb.initialize(loaded.databaseFromEnvironment());
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
                runtime.economyJournalStore()
        );
    }

    private void initializeStorageResources(VelocityConfiguration loaded, MariaDbRuntime runtime) {
        initializeNetworkIdentity(loaded, runtime.networkIdentityStore());
        initializeChannel(loaded, runtime.networkOutboxStore());
        initializeDiscord(loaded, runtime.discordOutboxStore());
        initializeWebsiteApi(loaded, runtime);
    }

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

    @SuppressWarnings(SUPPRESS_GUARD_LOG)
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

    @SuppressWarnings(SUPPRESS_NULL_ASSIGNMENT)
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

    @SuppressWarnings(SUPPRESS_NULL_ASSIGNMENT)
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
        networkIdentityProtector = null;
        websiteModerationStore = null;
    }

    @SuppressWarnings(SUPPRESS_GUARD_LOG)
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

    @SuppressWarnings({SUPPRESS_NULL_ASSIGNMENT, SUPPRESS_GUARD_LOG})
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

    @SuppressWarnings({SUPPRESS_NULL_ASSIGNMENT, SUPPRESS_GUARD_LOG})
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

    @SuppressWarnings({SUPPRESS_NULL_ASSIGNMENT, SUPPRESS_GUARD_LOG})
    // Clear the published reference before closing; SLF4J placeholders defer formatting.
    private void closeWebsiteServer() {
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

    @SuppressWarnings({SUPPRESS_NULL_ASSIGNMENT, SUPPRESS_GUARD_LOG})
    // Clear the published reference before closing; SLF4J placeholders defer formatting.
    private void closeChannelServer() {
        PersistentChannelServer server = channelServer;
        channelServer = null;
        if (server != null) {
            try {
                server.close();
            } catch (RuntimeException exception) {
                logger.warn("Persistent channel cleanup failed ({})", exception.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings(SUPPRESS_GUARD_LOG) // SLF4J placeholders defer formatting; the argument is an integer.
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

    @SuppressWarnings(SUPPRESS_GUARD_LOG) // SLF4J placeholders defer formatting; arguments are scalar accessors.
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
                            loaded.liteBansDatabaseFromEnvironment(),
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
            throw new IllegalStateException("No required backend channel secrets are configured");
        }
        Map<String, SecretKey> backendKeys = new LinkedHashMap<>();
        loaded.backendSecretEnvironments().forEach((serverId, environment) ->
                backendKeys.put(serverId, secretFromEnvironment(environment)));
        SecretKey proxyKey = secretFromEnvironment(loaded.channelProxySecretEnvironment());
        SSLContext tlsContext = serverTlsContext(loaded);
        try {
            PersistentChannelServer server = createChannelServer(
                    loaded, outbox, backendKeys, proxyKey, tlsContext
            );
            server.start();
            channelServer = server;
            outboxWorker = new NetworkOutboxWorker(
                    this,
                    proxy,
                    logger,
                    Clock.systemUTC(),
                    workers,
                    outbox,
                    server,
                    backendKeys.keySet()
            );
            outboxWorker.start();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to bind the persistent backend channel", exception);
        }
    }

    private PersistentChannelServer createChannelServer(
            VelocityConfiguration loaded,
            NetworkOutboxStore outbox,
            Map<String, SecretKey> backendKeys,
            SecretKey proxyKey,
            SSLContext tlsContext
    ) throws java.net.UnknownHostException {
        return new PersistentChannelServer(
                new PersistentChannelServer.Configuration(
                        loaded.channelProxyId(),
                        InetAddress.getByName(loaded.channelBindAddress()),
                        loaded.channelPort(),
                        backendKeys,
                        proxyKey,
                        tlsContext,
                        backendKeys.size() + 2
                ),
                Clock.systemUTC(),
                envelope -> {
                    outbox.recordInboxOnce(
                            loaded.serverId(),
                            envelope.messageId(),
                            envelope.messageType(),
                            "{\"outcome\":\"accepted\"}",
                            Clock.systemUTC().instant()
                    );
                    return true;
                },
                warning -> logger.warn("{}", warning)
        );
    }

    private void initializeNetworkIdentity(VelocityConfiguration loaded, NetworkIdentityStore store) {
        networkIdentityStore = store;
        if (!loaded.networkIdentityEnabled()) {
            logger.warn("Network identity matching is disabled; automatic alt inheritance is unavailable");
            return;
        }
        SecretKey equalityKey = SecretKeyMaterial.hmacSha256FromBase64(
                System.getenv(loaded.networkIdentityHmacSecretEnvironment())
        );
        SecretKey encryptionKey = SecretKeyMaterial.aesFromBase64(
                System.getenv(loaded.networkIdentityEncryptionSecretEnvironment())
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
                loaded.discordWebhooksFromEnvironment(),
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
            logger.info(
                    "Restricted website API started on loopback; {} eligible punishment codes were backfilled",
                    website.backfilledCodes()
            );
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
                loaded.punishmentCodeProtectorFromEnvironment()
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
                        loaded.websiteApiBearerTokenFromEnvironment(),
                        loaded.websiteApiHmacSecretFromEnvironment(),
                        Duration.ofSeconds(loaded.websiteApiTimestampSkewSeconds()),
                        store
                ),
                new WebsiteApiRouter(store, authorization, sanctionChanges, authorityMode::get, apiClock),
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

    private static SecretKey secretFromEnvironment(String environment) {
        String encoded = System.getenv(environment);
        return SecretKeyMaterial.hmacSha256FromBase64(encoded);
    }

    private static SSLContext serverTlsContext(VelocityConfiguration configuration) {
        char[] password = passwordFromEnvironment(configuration.channelTlsKeyStorePasswordEnvironment());
        try {
            return TlsContextLoader.server(configuration.channelTlsKeyStorePath(), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static char[] passwordFromEnvironment(String environment) {
        String value = System.getenv(environment);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("A required channel TLS store password environment variable is missing");
        }
        return value.toCharArray();
    }

    @SuppressWarnings(SUPPRESS_GUARD_LOG) // SLF4J placeholders defer formatting; arguments are enums.
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
        try {
            recordPlayerAndNetworkIdentity(event, current);
        } catch (RuntimeException exception) {
            health.update(OperationalMode.DEGRADED, Map.of(
                    "network-identity", "A protected identity observation failed; sensitive values were not logged"
            ));
            logger.error("Protected network identity observation failed", exception);
            if (current == OperationalMode.ACTIVE) {
                activeAuthorityObserved = true;
                denyUnavailable(event);
                return;
            }
        }
        if (current != OperationalMode.ACTIVE) {
            if (activeAuthorityObserved && failClosedConfigured()) {
                denyUnavailable(event);
            }
            return;
        }
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
        PersistentChannelServer channel = channelServer;
        if (loaded == null || channel == null
                || !channel.connectedServers().containsAll(loaded.backendSecretEnvironments().keySet())) {
            issues.put("channel", "Every configured Paper backend is not authenticated and connected");
        }
        if (loaded == null || !loaded.networkIdentityEnabled() || networkIdentityStore == null) {
            issues.put("network-identity", "Protected network identity matching is disabled");
        }
        if (loaded == null || !loaded.discordEnabled() || discordOutboxWorker == null) {
            issues.put("discord", "Durable webhook delivery is disabled; queued events remain in MariaDB");
        }
        if (loaded == null || !loaded.websiteApiEnabled()) {
            issues.put("website-api", "The private punishment and appeal bridge is disabled");
        } else if (websiteApiServer == null || websiteModerationStore == null) {
            issues.put("website-api", "The configured private punishment and appeal bridge failed to start");
        }
        if (mode != OperationalMode.ACTIVE) {
            issues.put("authority", "LiteBans remains authoritative in " + mode);
        }
        return Map.copyOf(issues);
    }

    private void denyUnavailable(LoginEvent event) {
        if (failClosedConfigured()) {
            event.setResult(ResultedEvent.ComponentResult.denied(Component.text(
                    "The moderation service cannot safely verify network access. Please try again shortly."
            )));
        }
    }

    private void enforceSafeServerSwitch(ServerPreConnectEvent event) {
        InventoryJournalStore inventories = inventoryJournalStore;
        EconomyJournalStore economies = economyJournalStore;
        if (inventories == null || economies == null) {
            if (authorityMode.get() == OperationalMode.ACTIVE) {
                denyServerSwitch(event, "Asset safety status is temporarily unavailable.");
            }
            return;
        }
        String requested = event.getOriginalServer().getServerInfo().getName();
        try {
            Optional<String> owningServer = inventories.lockedOwningServer(
                    event.getPlayer().getUniqueId(),
                    Clock.systemUTC().instant()
            );
            if (owningServer.isPresent() && !owningServer.orElseThrow().equalsIgnoreCase(requested)) {
                denyServerSwitch(event, "A pending inventory operation must finish on "
                        + owningServer.orElseThrow() + '.');
                return;
            }
            Optional<String> economyOwner = economies.lockedOwningServer(
                    event.getPlayer().getUniqueId()
            );
            if (economyOwner.isPresent() && !economyOwner.orElseThrow().equalsIgnoreCase(requested)) {
                denyServerSwitch(event, "A pending economy operation must finish on "
                        + economyOwner.orElseThrow() + '.');
                return;
            }
        } catch (RuntimeException exception) {
            logger.error("Asset fence lookup failed during server connection", exception);
            if (authorityMode.get() == OperationalMode.ACTIVE) {
                denyServerSwitch(event, "Asset safety status could not be verified.");
            }
            return;
        }
        if (event.getPreviousServer() == null) {
            return;
        }
        FreezeStore store = freezeStore;
        if (store == null) {
            if (authorityMode.get() == OperationalMode.ACTIVE) {
                denyServerSwitch(event, "Server switching is unavailable while moderation status is verified.");
            }
            return;
        }
        try {
            if (store.active(event.getPlayer().getUniqueId(), Clock.systemUTC().instant()).isPresent()) {
                denyServerSwitch(event, "You cannot switch servers while frozen by staff.");
                return;
            }
            StaffSessionStore sessions = staffSessionStore;
            if (sessions != null && sessions.active(event.getPlayer().getUniqueId()).isPresent()) {
                denyServerSwitch(event, "You cannot switch backends while a staff-mode snapshot is active.");
            }
        } catch (RuntimeException exception) {
            logger.error("Moderation safety lookup failed during server switch", exception);
            if (authorityMode.get() == OperationalMode.ACTIVE) {
                denyServerSwitch(event, "Server switching is unavailable while moderation status is verified.");
            }
        }
    }

    private static void denyServerSwitch(ServerPreConnectEvent event, String message) {
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        event.getPlayer().sendMessage(Component.text(message));
    }

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
        next.whenComplete((ignored, failure) -> {
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
            source.sendMessage(Component.text("You do not have permission to reload EnthusiaStaff."));
            return;
        }
        if (arguments.length != 1) {
            source.sendMessage(Component.text("Usage: /estaff reload"));
            return;
        }
        if (!reloadRunning.compareAndSet(false, true)) {
            source.sendMessage(Component.text("Another Velocity configuration reload is already running."));
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
                source.sendMessage(Component.text(result.message()));
                result.details().forEach(detail -> source.sendMessage(Component.text("- " + detail)));
            } finally {
                reloadRunning.set(false);
            }
        })) {
            reloadRunning.set(false);
            source.sendMessage(Component.text("The bounded work queue is full; reload did not start."));
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
            source.sendMessage(Component.text(
                    "Velocity configuration validation failed; storage retry was not started."
            ));
            return;
        }
        VelocityBootstrapCoordinator coordinator = bootstrapCoordinator;
        if (coordinator != null && coordinator.requestImmediateRetry()) {
            updateHealthIssue(CONFIGURATION_RELOAD_ISSUE, null);
            source.sendMessage(Component.text(
                    "Velocity configuration is valid; an immediate bounded storage retry was started."
            ));
        } else {
            source.sendMessage(Component.text(
                    "Storage is already active, retrying, or shutting down; no duplicate attempt was started."
            ));
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

    private final class AltsCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] arguments = invocation.arguments();
            if (arguments.length != 1) {
                source.sendMessage(Component.text("Usage: /alts <player>"));
                return;
            }
            submitAltTask(source, () -> {
                PlayerDirectory directory = playerDirectory;
                NetworkIdentityStore store = networkIdentityStore;
                if (directory == null || store == null) {
                    source.sendMessage(Component.text("The player directory or alt store is not ready."));
                    return;
                }
                net.enthusia.staff.domain.player.PlayerIdentity target = directory.find(arguments[0]).orElse(null);
                if (target == null) {
                    source.sendMessage(Component.text("That player has never joined the network."));
                    return;
                }
                List<AltRelationshipSummary> relationships = store.relationships(target.playerId());
                source.sendMessage(Component.text("Alt relationships for "
                        + target.currentUsername().orElse(target.playerId().toString()) + ": " + relationships.size()));
                for (AltRelationshipSummary relationship : relationships) {
                    source.sendMessage(Component.text("- " + relationship.otherPlayerId() + " "
                            + relationship.state() + " confidence="
                            + Math.round(relationship.confidence() * 100.0) + "%"
                            + (relationship.lockedUntilReopened() ? " locked" : "")));
                }
            });
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("enthusiastaff.alts.view");
        }
    }

    private final class AltCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] arguments = invocation.arguments();
            if (arguments.length < 4) {
                source.sendMessage(Component.text(
                        "Usage: /alt <link|approve|household|notrelated|unlink|reopen> <player1> <player2> <reason>"
                ));
                return;
            }
            String operation = arguments[0].toLowerCase(java.util.Locale.ROOT);
            if (operation.equals("reopen") && !source.hasPermission("enthusiastaff.alts.reopen")) {
                source.sendMessage(Component.text("Admin permission is required to reopen a not-related decision."));
                return;
            }
            AltRelationshipState state = switch (operation) {
                case "link" -> AltRelationshipState.CONFIRMED_ALT;
                case "approve" -> AltRelationshipState.APPROVED_ALT;
                case "household" -> AltRelationshipState.SHARED_HOUSEHOLD;
                case "notrelated" -> AltRelationshipState.NOT_RELATED;
                case "unlink" -> AltRelationshipState.LOW_CONFIDENCE;
                case "reopen" -> null;
                default -> {
                    source.sendMessage(Component.text("Unknown alt operation."));
                    yield null;
                }
            };
            if (state == null && !operation.equals("reopen")) {
                return;
            }
            String reason = String.join(" ", java.util.Arrays.copyOfRange(arguments, 3, arguments.length));
            submitAltTask(source, () -> changeRelationship(
                    source, operation, state, arguments[1], arguments[2], reason
            ));
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return invocation.arguments().length <= 1
                    ? List.of("link", "approve", "household", "notrelated", "unlink", "reopen")
                    : List.of();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("enthusiastaff.alts.manage");
        }
    }

    private void changeRelationship(
            CommandSource source,
            String operation,
            AltRelationshipState state,
            String firstInput,
            String secondInput,
            String reason
    ) {
        PlayerDirectory directory = playerDirectory;
        NetworkIdentityStore store = networkIdentityStore;
        if (directory == null || store == null) {
            source.sendMessage(Component.text("The player directory or alt store is not ready."));
            return;
        }
        net.enthusia.staff.domain.player.PlayerIdentity first = directory.find(firstInput).orElse(null);
        net.enthusia.staff.domain.player.PlayerIdentity second = directory.find(secondInput).orElse(null);
        if (first == null || second == null) {
            source.sendMessage(Component.text("Both players must have joined the network previously."));
            return;
        }
        UUID actorId = source instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        boolean changed = operation.equals("reopen")
                ? store.reopen(first.playerId(), second.playerId(), actorId, Clock.systemUTC().instant(), reason)
                : store.setRelationship(
                        first.playerId(), second.playerId(), state, actorId, Clock.systemUTC().instant(), reason
                );
        source.sendMessage(Component.text(changed
                ? "Alt relationship change committed and audited."
                : "No change was made; a locked not-related decision may require explicit reopen."));
    }

    private void submitAltTask(CommandSource source, Runnable operation) {
        try {
            workers.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    logger.error("Alt command failed", exception);
                    source.sendMessage(Component.text("Alt operation failed; inspect the sanitized proxy log."));
                }
            });
        } catch (RejectedExecutionException exception) {
            source.sendMessage(Component.text("The bounded work queue is full; alt operation did not start."));
        }
    }

    private final class StatusCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] arguments = invocation.arguments();
            if (arguments.length > 0 && arguments[0].equalsIgnoreCase("reload")) {
                executeReload(source, arguments);
                return;
            }
            if (arguments.length > 0 && arguments[0].equalsIgnoreCase("migration")) {
                executeMigration(source, arguments);
                return;
            }
            if (arguments.length > 0 && arguments[0].equalsIgnoreCase("cutover")) {
                executeCutover(source, arguments);
                return;
            }
            if (arguments.length > 0 && arguments[0].equalsIgnoreCase("discord")) {
                executeDiscord(source, arguments);
                return;
            }
            if (arguments.length > 0 && arguments[0].equalsIgnoreCase("website")) {
                executeWebsite(source, arguments);
                return;
            }
            VelocityRuntimeHealth.Snapshot snapshot = health.snapshot();
            source.sendMessage(Component.text("EnthusiaStaff mode: " + snapshot.mode()));
            VelocityBootstrapCoordinator bootstrap = bootstrapCoordinator;
            if (bootstrap != null && !bootstrap.completed()) {
                source.sendMessage(Component.text(
                        "Storage bootstrap: attempts=" + bootstrap.attempts()
                                + ", retry-scheduled=" + bootstrap.retryScheduled()
                                + ", exhausted=" + bootstrap.exhausted()
                ));
            }
            snapshot.issues().forEach((component, reason) ->
                    source.sendMessage(Component.text("DISABLED " + component + ": " + reason)));
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] arguments = invocation.arguments();
            if (arguments.length <= 1) {
                List<String> suggestions = new ArrayList<>(List.of("status", "verify"));
                if (sourceHas(invocation.source(), "enthusiastaff.reload")) {
                    suggestions.add("reload");
                }
                if (sourceHas(invocation.source(), "enthusiastaff.migration")) {
                    suggestions.add("migration");
                }
                if (sourceHas(invocation.source(), "enthusiastaff.cutover")) {
                    suggestions.add("cutover");
                }
                if (sourceHas(invocation.source(), "enthusiastaff.discord.manage")) {
                    suggestions.add("discord");
                }
                if (sourceHas(invocation.source(), "enthusiastaff.website.manage")) {
                    suggestions.add("website");
                }
                return List.copyOf(suggestions);
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("migration")) {
                return sourceHas(invocation.source(), "enthusiastaff.migration")
                        ? List.of("inspect", "dry-run", "import", "shadow", "final")
                        : List.of();
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("cutover")) {
                if (!sourceHas(invocation.source(), "enthusiastaff.cutover")) {
                    return List.of();
                }
                return sourceHas(invocation.source(), "enthusiastaff.cutover.founder")
                        ? List.of("status", "maintenance", "abort", "freeze", "activate", "override")
                        : List.of("status", "maintenance", "activate");
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("discord")) {
                return sourceHas(invocation.source(), "enthusiastaff.discord.manage")
                        ? List.of("status", "retry")
                        : List.of();
            }
            if (arguments.length == 3 && arguments[0].equalsIgnoreCase("discord")
                    && arguments[1].equalsIgnoreCase("retry")) {
                return sourceHas(invocation.source(), "enthusiastaff.discord.manage")
                        ? List.of("punishments", "reports", "logs-staffmode", "alerts")
                        : List.of();
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("website")) {
                return sourceHas(invocation.source(), "enthusiastaff.website.manage")
                        ? List.of("status", "code")
                        : List.of();
            }
            if (arguments.length == 3 && arguments[0].equalsIgnoreCase("website")
                    && arguments[1].equalsIgnoreCase("code")) {
                return sourceHas(invocation.source(), "enthusiastaff.website.manage")
                        ? List.of("show", "rotate", "revoke")
                        : List.of();
            }
            return List.of();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            String[] arguments = invocation.arguments();
            if (arguments.length > 0 && arguments[0].equalsIgnoreCase("reload")) {
                return invocation.source().hasPermission("enthusiastaff.reload");
            }
            return invocation.source().hasPermission("enthusiastaff.status");
        }

        private void executeWebsite(CommandSource source, String[] arguments) {
            if (!source.hasPermission("enthusiastaff.website.manage")) {
                source.sendMessage(Component.text("You do not have permission to manage website bindings."));
                return;
            }
            VelocityConfiguration loaded = configuration;
            WebsiteModerationStore store = websiteModerationStore;
            if (arguments.length == 2 && arguments[1].equalsIgnoreCase("status")) {
                source.sendMessage(Component.text(
                        loaded != null && loaded.websiteApiEnabled() && websiteApiServer != null
                                ? "Website API: LISTENING on loopback "
                                + loaded.websiteApiBindAddress() + ':' + loaded.websiteApiPort()
                                : "Website API: DISABLED or unavailable"
                ));
                return;
            }
            if (store == null) {
                source.sendMessage(Component.text("The website API store is not available."));
                return;
            }
            if (arguments.length < 4 || !arguments[1].equalsIgnoreCase("code")) {
                source.sendMessage(Component.text(
                        "Usage: /estaff website status | /estaff website code "
                                + "<show|rotate|revoke> <case|punishment-id> [confirmation]"
                ));
                return;
            }
            if (!(source instanceof Player staff)) {
                source.sendMessage(Component.text(
                        "Punishment codes are only shown or changed in a verified in-game staff session."
                ));
                return;
            }
            String operation = arguments[2].toLowerCase(java.util.Locale.ROOT);
            String target = arguments[3];
            if (operation.equals("show") && arguments.length == 4) {
                submitWebsiteTask(source, () -> showPunishmentCodes(source, store, target));
                return;
            }
            if (operation.equals("rotate") && arguments.length == 5
                    && arguments[4].equals("CONFIRM-CODE-ROTATE")) {
                UUID punishmentId = parseUuid(target);
                if (punishmentId == null) {
                    source.sendMessage(Component.text("Rotation requires a punishment UUID."));
                    return;
                }
                submitWebsiteTask(source, () -> {
                    PunishmentCodeDisplay code = store.rotateCode(
                            punishmentId,
                            staff.getUniqueId(),
                            Clock.systemUTC().instant()
                    );
                    source.sendMessage(Component.text(
                            "Rotated code for punishment " + code.punishmentId() + ": " + code.code()
                    ));
                });
                return;
            }
            if (operation.equals("revoke") && arguments.length == 5
                    && arguments[4].equals("CONFIRM-CODE-REVOKE")) {
                UUID punishmentId = parseUuid(target);
                if (punishmentId == null) {
                    source.sendMessage(Component.text("Revocation requires a punishment UUID."));
                    return;
                }
                submitWebsiteTask(source, () -> {
                    boolean changed = store.revokeCode(
                            punishmentId,
                            staff.getUniqueId(),
                            Clock.systemUTC().instant()
                    );
                    source.sendMessage(Component.text(changed
                            ? "The punishment code was revoked and its binding is now ineligible."
                            : "No active punishment code changed."));
                });
                return;
            }
            source.sendMessage(Component.text(
                    "Use show without confirmation, or append CONFIRM-CODE-ROTATE / CONFIRM-CODE-REVOKE."
            ));
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
                    source.sendMessage(Component.text("Enter a case ID or punishment UUID."));
                    return;
                }
                codes = store.codesForCase(caseId, Clock.systemUTC().instant());
            }
            if (codes.isEmpty()) {
                source.sendMessage(Component.text("No active appeal-eligible punishment code exists."));
                return;
            }
            for (PunishmentCodeDisplay code : codes) {
                source.sendMessage(Component.text(
                        code.punishmentType() + " case " + code.caseId()
                                + " punishment " + code.punishmentId() + ": " + code.code()
                ));
            }
        }

        private void submitWebsiteTask(CommandSource source, Runnable task) {
            try {
                workers.execute(() -> {
                    try {
                        task.run();
                    } catch (RuntimeException exception) {
                        logger.error("Website administration command failed", exception);
                        source.sendMessage(Component.text(
                                "Website operation failed; inspect the sanitized proxy log."
                        ));
                    }
                });
            } catch (RejectedExecutionException exception) {
                source.sendMessage(Component.text(
                        "The bounded work queue is full; the website operation did not start."
                ));
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
                source.sendMessage(Component.text("You do not have permission to run migration operations."));
                return;
            }
            if (arguments.length != 2) {
                source.sendMessage(Component.text("Usage: /estaff migration <inspect|dry-run|import|shadow|final>"));
                return;
            }
            MariaDbRuntime runtime = databaseRuntime;
            VelocityConfiguration loaded = configuration;
            if (runtime == null || loaded == null) {
                source.sendMessage(Component.text("MariaDB is not ready; no migration action was taken."));
                return;
            }
            MigrationMode migrationMode = switch (arguments[1].toLowerCase(java.util.Locale.ROOT)) {
                case "dry-run", "inspect" -> MigrationMode.DRY_RUN;
                case "import" -> MigrationMode.IMPORT;
                case "shadow" -> MigrationMode.SHADOW;
                case "final" -> MigrationMode.CUTOVER;
                default -> null;
            };
            if (migrationMode == null) {
                source.sendMessage(Component.text("Unknown migration operation."));
                return;
            }
            if (migrationMode == MigrationMode.SHADOW
                    && authorityMode.get() != OperationalMode.SHADOW_MIGRATION) {
                source.sendMessage(Component.text("Shadow runs require SHADOW_MIGRATION mode."));
                return;
            }
            if (migrationMode == MigrationMode.CUTOVER && authorityMode.get() != OperationalMode.MAINTENANCE) {
                source.sendMessage(Component.text("The final import and comparison require MAINTENANCE mode."));
                return;
            }
            if (migrationMode == MigrationMode.IMPORT && authorityMode.get() != OperationalMode.SHADOW_MIGRATION
                    && authorityMode.get() != OperationalMode.MAINTENANCE) {
                source.sendMessage(Component.text("Imports require SHADOW_MIGRATION or MAINTENANCE mode."));
                return;
            }
            if (!migrationRunning.compareAndSet(false, true)) {
                source.sendMessage(Component.text("Another migration operation is already running."));
                return;
            }
            source.sendMessage(Component.text("Migration operation accepted; results will be reported when durable."));
            try {
                workers.execute(() -> {
                    try {
                        MigrationExecutionReport report = migrationService(runtime, migrationMode).execute(
                                loaded.liteBansDatabaseFromEnvironment(),
                                loaded.liteBansTablePrefix(),
                                loaded.liteBansBatchSize(),
                                migrationMode
                        );
                        source.sendMessage(Component.text(
                                "Migration " + report.mode() + " run " + report.runId() + ": source="
                                        + report.sourceRecords() + ", imported=" + report.importedRecords()
                                        + ", reconciled=" + report.reconciledRecords()
                                        + ", replayed=" + report.replayedRecords() + ", rejected="
                                        + report.rejectedRows().size() + ", schema-blockers="
                                        + report.schema().blockers().size() + ", protected-identities="
                                        + report.protectedIdentityRecords() + '/' + report.networkIdentityRecords()
                        ));
                        report.shadowSummary().ifPresent(summary -> source.sendMessage(Component.text(
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
                        report.rejectedRows().stream().limit(20).forEach(row -> source.sendMessage(Component.text(
                                "Rejected " + row.tableName() + '#' + row.externalId() + ": " + row.reasonCode()
                        )));
                        if (report.rejectedRows().size() > 20) {
                            source.sendMessage(Component.text(
                                    (report.rejectedRows().size() - 20)
                                            + " additional rejected rows are recorded in the durable migration report."
                            ));
                        }
                    } catch (RuntimeException exception) {
                        logger.error("Migration command failed", exception);
                        source.sendMessage(Component.text("Migration failed; inspect the sanitized proxy log and durable run record."));
                    } finally {
                        migrationRunning.set(false);
                    }
                });
            } catch (RejectedExecutionException exception) {
                migrationRunning.set(false);
                source.sendMessage(Component.text("The bounded work queue is full; migration did not start."));
            }
        }

        private void executeCutover(CommandSource source, String[] arguments) {
            if (!source.hasPermission("enthusiastaff.cutover")) {
                source.sendMessage(Component.text("You do not have permission to manage cutover."));
                return;
            }
            if (arguments.length < 2) {
                source.sendMessage(Component.text(
                        "Usage: /estaff cutover <status|maintenance|abort|freeze|activate|override>"
                ));
                return;
            }
            MariaDbRuntime runtime = databaseRuntime;
            if (runtime == null) {
                source.sendMessage(Component.text("MariaDB is not ready; no cutover action was taken."));
                return;
            }
            String operation = arguments[1].toLowerCase(java.util.Locale.ROOT);
            if (operation.equals("status")) {
                submitCutover(source, () -> {
                    net.enthusia.staff.persistence.migration.CutoverCoordinator coordinator =
                            runtime.cutoverCoordinator();
                    coordinator.latestEvidence().ifPresentOrElse(evidence -> {
                        long observedHours = Duration.between(
                                evidence.shadowStartedAt(), evidence.shadowEndedAt()
                        ).toHours();
                        source.sendMessage(Component.text(
                                "Shadow evidence: observed=" + observedHours + "h, summaries="
                                        + evidence.successfulShadowSummaries().size() + ", unresolved="
                                        + evidence.unresolvedOperations() + ", migration-idle="
                                        + evidence.migrationIdle() + ", writes-frozen=" + evidence.writesFrozen()
                                        + ", final-import=" + evidence.finalIncrementalImportComplete()
                        ));
                        source.sendMessage(Component.text(
                                "Checks: counts=" + evidence.countsMatch()
                                        + ", checksums=" + evidence.checksumsMatch()
                                        + ", active=" + evidence.activeSanctionsMatch()
                                        + ", UUIDs=" + evidence.uuidMappingsMatch()
                                        + ", expirations=" + evidence.expirationsMatch()
                                        + ", login=" + comparison(evidence.loginDecisions())
                                        + ", mute=" + comparison(evidence.muteDecisions())
                                        + ", IP-ban=" + comparison(evidence.ipBanDecisions())
                        ));
                    }, () -> source.sendMessage(Component.text("No complete shadow evidence is available.")));
                    net.enthusia.staff.domain.migration.CutoverAssessment assessment = coordinator.assess(
                            java.util.Optional.empty()
                    );
                    source.sendMessage(Component.text("Cutover allowed: " + assessment.allowed()
                            + "; blockers: " + String.join(", ", assessment.blockers())));
                });
                return;
            }
            UUID actorId = source instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
            if (operation.equals("maintenance")) {
                submitCutover(source, () -> {
                    boolean changed = runtime.cutoverCoordinator().enterMaintenance(
                            actorId, "Cutover preparation requested through Velocity"
                    );
                    source.sendMessage(Component.text(changed
                            ? "Maintenance committed. Run the final incremental import, then reassess cutover."
                            : "Maintenance was not entered; the current mode is not SHADOW_MIGRATION or changed concurrently."));
                });
                return;
            }
            if (operation.equals("abort")) {
                if (!source.hasPermission("enthusiastaff.cutover.founder")) {
                    source.sendMessage(Component.text("Founder permission is required to abort cutover maintenance."));
                    return;
                }
                if (arguments.length < 4 || !arguments[2].equals("CONFIRM-ABORT-MAINTENANCE")) {
                    source.sendMessage(Component.text(
                            "Abort requires the exact acknowledgement and a written reason."
                    ));
                    return;
                }
                String reason = String.join(" ", java.util.Arrays.copyOfRange(arguments, 3, arguments.length));
                submitCutover(source, () -> {
                    boolean changed = runtime.cutoverCoordinator().abortMaintenance(actorId, reason);
                    source.sendMessage(Component.text(changed
                            ? "Maintenance aborted; LiteBans remains authoritative and the shadow gate must be reassessed."
                            : "Maintenance was not aborted because the current mode is not MAINTENANCE."));
                });
                return;
            }
            if (operation.equals("freeze")) {
                if (!source.hasPermission("enthusiastaff.cutover.founder")) {
                    source.sendMessage(Component.text("Founder permission is required to freeze active authority."));
                    return;
                }
                if (arguments.length < 4 || !arguments[2].equals("CONFIRM-READ-ONLY-FAILURE")) {
                    source.sendMessage(Component.text(
                            "Emergency freeze requires the exact acknowledgement and a written reason."
                    ));
                    return;
                }
                String reason = String.join(" ", java.util.Arrays.copyOfRange(arguments, 3, arguments.length));
                submitCutover(source, () -> {
                    boolean changed = runtime.cutoverCoordinator().freezeActiveAuthority(actorId, reason);
                    source.sendMessage(Component.text(changed
                            ? "ACTIVE authority is now READ_ONLY_FAILURE; destructive writes are disabled and logins fail closed."
                            : "Authority was not frozen because the current mode is not ACTIVE."));
                });
                return;
            }
            if (operation.equals("activate")) {
                if (arguments.length != 3 || !arguments[2].equals("CONFIRM-ACTIVE-CUTOVER")) {
                    source.sendMessage(Component.text(
                            "Activation requires: /estaff cutover activate CONFIRM-ACTIVE-CUTOVER"
                    ));
                    return;
                }
                submitCutover(source, () -> activateCutover(source, actorId, java.util.Optional.empty()));
                return;
            }
            if (operation.equals("override")) {
                if (!source.hasPermission("enthusiastaff.cutover.founder")) {
                    source.sendMessage(Component.text("Founder permission is required for a blocked cutover override."));
                    return;
                }
                if (arguments.length < 4
                        || !arguments[2].equals(FounderOverride.REQUIRED_ACKNOWLEDGEMENT)) {
                    source.sendMessage(Component.text(
                            "Override requires the exact acknowledgement and a written reason."
                    ));
                    return;
                }
                String reason = String.join(" ", java.util.Arrays.copyOfRange(arguments, 3, arguments.length));
                FounderOverride founderOverride = new FounderOverride(actorId, arguments[2], reason);
                submitCutover(source, () -> activateCutover(
                        source, actorId, java.util.Optional.of(founderOverride)
                ));
                return;
            }
            source.sendMessage(Component.text("Unknown cutover operation."));
        }

        private boolean sourceHas(CommandSource source, String permission) {
            return source.hasPermission(permission);
        }

        private String comparison(net.enthusia.staff.domain.migration.DecisionComparison value) {
            return value.mismatched() + "/" + value.compared() + " mismatched";
        }

        private void executeDiscord(CommandSource source, String[] arguments) {
            if (!source.hasPermission("enthusiastaff.discord.manage")) {
                source.sendMessage(Component.text("You do not have permission to manage Discord delivery."));
                return;
            }
            MariaDbRuntime runtime = databaseRuntime;
            if (runtime == null) {
                source.sendMessage(Component.text("MariaDB is not ready; Discord status is unavailable."));
                return;
            }
            if (arguments.length == 2 && arguments[1].equalsIgnoreCase("status")) {
                try {
                    workers.execute(() -> {
                        try {
                            Instant now = Clock.systemUTC().instant();
                            for (net.enthusia.staff.domain.discord.DiscordChannelStatus status
                                    : runtime.discordOutboxStore().channelStatuses()) {
                                source.sendMessage(Component.text(status.destination()
                                        + ": pending=" + status.pendingMessages()
                                        + ", dead=" + status.deadLetterMessages()
                                        + ", failures=" + status.consecutiveFailures()
                                        + ", circuit=" + (status.circuitOpen(now) ? "OPEN" : "CLOSED")));
                            }
                        } catch (RuntimeException exception) {
                            logger.error("Discord status command failed", exception);
                            source.sendMessage(Component.text("Discord status failed; inspect the sanitized proxy log."));
                        }
                    });
                } catch (RejectedExecutionException exception) {
                    source.sendMessage(Component.text("The bounded work queue is full; status was not read."));
                }
                return;
            }
            if (arguments.length == 4 && arguments[1].equalsIgnoreCase("retry")
                    && arguments[3].equals("CONFIRM-DISCORD-RETRY")) {
                String destination = arguments[2].toLowerCase(java.util.Locale.ROOT);
                if (!Set.of("punishments", "reports", "logs-staffmode", "alerts").contains(destination)) {
                    source.sendMessage(Component.text("Unknown Discord destination."));
                    return;
                }
                try {
                    workers.execute(() -> {
                        try {
                            int retried = runtime.discordOutboxStore().retryDestination(
                                    destination, Clock.systemUTC().instant(), 500
                            );
                            source.sendMessage(Component.text("Discord circuit reset; queued " + retried
                                    + " dead-letter events for another bounded attempt."));
                        } catch (RuntimeException exception) {
                            logger.error("Discord retry command failed", exception);
                            source.sendMessage(Component.text("Discord retry failed; inspect the sanitized proxy log."));
                        }
                    });
                } catch (RejectedExecutionException exception) {
                    source.sendMessage(Component.text("The bounded work queue is full; retry did not start."));
                }
                return;
            }
            source.sendMessage(Component.text(
                    "Usage: /estaff discord status | /estaff discord retry <destination> CONFIRM-DISCORD-RETRY"
            ));
        }

        private void submitCutover(CommandSource source, Runnable action) {
            if (!migrationRunning.compareAndSet(false, true)) {
                source.sendMessage(Component.text("Another migration or cutover operation is already running."));
                return;
            }
            try {
                workers.execute(() -> {
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        logger.error("Cutover command failed", exception);
                        source.sendMessage(Component.text("Cutover operation failed; inspect the sanitized proxy log."));
                    } finally {
                        migrationRunning.set(false);
                    }
                });
            } catch (RejectedExecutionException exception) {
                migrationRunning.set(false);
                source.sendMessage(Component.text("The bounded work queue is full; cutover operation did not start."));
            }
        }

        private void activateCutover(
                CommandSource source,
                UUID actorId,
                java.util.Optional<FounderOverride> override
        ) {
            VelocityConfiguration loaded = configuration;
            PersistentChannelServer channel = channelServer;
            if (loaded == null || channel == null
                    || !channel.connectedServers().containsAll(loaded.backendSecretEnvironments().keySet())) {
                source.sendMessage(Component.text(
                        "Cutover blocked: every configured Paper backend must have an authenticated persistent connection."
                ));
                return;
            }
            CutoverOutcome outcome = databaseRuntime.cutoverCoordinator().activate(actorId, override);
            if (outcome.activated()) {
                source.sendMessage(Component.text("ACTIVE cutover committed as " + outcome.cutoverId().orElseThrow() + '.'));
            } else {
                source.sendMessage(Component.text("Cutover blocked: " + String.join(", ", outcome.assessment().blockers())));
            }
        }
    }

    private record StorageBindings(
            SanctionLookup sanctions,
            PlayerDirectory players,
            FreezeStore freezes,
            StaffSessionStore sessions,
            InventoryJournalStore inventories,
            EconomyJournalStore economies
    ) {
    }

    private record WebsiteRuntime(
            WebsiteModerationStore store,
            WebsiteApiServer server,
            ScheduledTask maintenance,
            int backfilledCodes
    ) {
    }
}
