package net.enthusia.staff.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.nio.file.Path;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import javax.crypto.SecretKey;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import net.enthusia.staff.common.security.HmacTokenService;
import net.enthusia.staff.common.security.NetworkIdentityProtector;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.domain.migration.FounderOverride;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.migration.MigrationExecutionReport;
import net.enthusia.staff.persistence.migration.CutoverOutcome;
import net.enthusia.staff.protocol.PersistentChannelServer;
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

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final VelocityRuntimeHealth health = new VelocityRuntimeHealth();
    private final AtomicReference<OperationalMode> authorityMode = new AtomicReference<>(OperationalMode.BOOTSTRAP);
    private ExecutorService workers;
    private VelocityConfiguration configuration;
    private MariaDbRuntime databaseRuntime;
    private SanctionLookup sanctionLookup;
    private PlayerDirectory playerDirectory;
    private FreezeStore freezeStore;
    private StaffSessionStore staffSessionStore;
    private InventoryJournalStore inventoryJournalStore;
    private EconomyJournalStore economyJournalStore;
    private NetworkIdentityStore networkIdentityStore;
    private NetworkIdentityProtector networkIdentityProtector;
    private volatile boolean activeAuthorityObserved;
    private ScheduledTask operationalStateTask;
    private PersistentChannelServer channelServer;
    private NetworkOutboxWorker outboxWorker;
    private DiscordOutboxWorker discordOutboxWorker;
    private WebsiteModerationStore websiteModerationStore;
    private WebsiteApiServer websiteApiServer;
    private ScheduledTask websiteMaintenanceTask;
    private final java.util.concurrent.atomic.AtomicBoolean migrationRunning = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.ConcurrentHashMap<UUID, CompletableFuture<Void>> presenceUpdates =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public EnthusiaStaffVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
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
        workers.execute(this::initializeStorage);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent ignored) {
        if (workers == null) {
            return;
        }
        if (operationalStateTask != null) {
            operationalStateTask.cancel();
        }
        if (outboxWorker != null) {
            outboxWorker.close();
        }
        if (discordOutboxWorker != null) {
            discordOutboxWorker.close();
        }
        if (websiteMaintenanceTask != null) {
            websiteMaintenanceTask.cancel();
        }
        if (websiteApiServer != null) {
            websiteApiServer.close();
        }
        if (channelServer != null) {
            channelServer.close();
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
        if (databaseRuntime != null) {
            databaseRuntime.close();
        }
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.resumeWhenComplete(CompletableFuture.runAsync(() -> enforceLogin(event), workers));
    }

    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        return EventTask.resumeWhenComplete(CompletableFuture.runAsync(() -> enforceSafeServerSwitch(event), workers));
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

    private void initializeStorage() {
        try {
            VelocityConfiguration loaded = VelocityConfiguration.load(dataDirectory);
            MariaDbRuntime opened = MariaDb.initialize(loaded.databaseFromEnvironment());
            OperationalStateSnapshot state = opened.operationalStateStore().current();
            if (state.mode() == OperationalMode.ACTIVE && !opened.operationalStateStore().hasAuthorizedCutover()) {
                opened.close();
                health.update(OperationalMode.DEGRADED, Map.of(
                        "cutover", "Persistent ACTIVE state has no authorized cutover record; login enforcement is blocked"
                ));
                activeAuthorityObserved = true;
                logger.error("Refusing an unauthorised ACTIVE state; network logins will fail closed");
                return;
            }
            configuration = loaded;
            databaseRuntime = opened;
            sanctionLookup = opened.sanctionLookup();
            playerDirectory = opened.playerDirectory();
            freezeStore = opened.freezeStore();
            staffSessionStore = opened.staffSessionStore();
            inventoryJournalStore = opened.inventoryJournalStore();
            economyJournalStore = opened.economyJournalStore();
            initializeNetworkIdentity(loaded, opened.networkIdentityStore());
            initializeChannel(loaded, opened.networkOutboxStore());
            initializeDiscord(loaded, opened.discordOutboxStore());
            initializeWebsiteApi(loaded, opened);
            authorityMode.set(state.mode());
            activeAuthorityObserved = state.mode() == OperationalMode.ACTIVE;
            Map<String, String> issues = operationalIssues(state.mode());
            health.update(state.mode(), issues);
            operationalStateTask = proxy.getScheduler().buildTask(this, this::refreshOperationalState)
                    .repeat(5, TimeUnit.SECONDS)
                    .schedule();
            logger.info("MariaDB verified; Velocity moderation authority is {}", state.mode());
        } catch (RuntimeException | java.io.IOException exception) {
            health.update(OperationalMode.DEGRADED, Map.of(
                    "mariadb", "Configuration, connection, or schema validation failed; see sanitized console error"
            ));
            logger.error("Velocity moderation storage initialization failed", exception);
        }
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
        try {
            PersistentChannelServer server = new PersistentChannelServer(
                    loaded.channelProxyId(),
                    InetAddress.getByName(loaded.channelBindAddress()),
                    loaded.channelPort(),
                    backendKeys,
                    proxyKey,
                    Clock.systemUTC(),
                    backendKeys.size() + 2,
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
        WebsiteApiServer server = null;
        try {
            WebsiteModerationStore store = runtime.websiteModerationStore(
                    loaded.punishmentCodeProtectorFromEnvironment()
            );
            AuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
            int created = store.ensureEligibleCodes(Clock.systemUTC().instant(), 5_000);
            server = new WebsiteApiServer(
                    InetAddress.getByName(loaded.websiteApiBindAddress()),
                    loaded.websiteApiPort(),
                    loaded.websiteApiMaximumBodyBytes(),
                    loaded.websiteApiWorkerThreads(),
                    loaded.websiteApiQueueCapacity(),
                    loaded.websiteApiBearerTokenFromEnvironment(),
                    loaded.websiteApiHmacSecretFromEnvironment(),
                    Duration.ofSeconds(loaded.websiteApiTimestampSkewSeconds()),
                    store,
                    authorization,
                    new SanctionChangeService(
                            authorization,
                            runtime.sanctionMutationStore()
                    ),
                    authorityMode::get,
                    Clock.systemUTC(),
                    (message, failure) -> logger.error(message, failure)
            );
            server.start();
            websiteModerationStore = store;
            websiteApiServer = server;
            websiteMaintenanceTask = proxy.getScheduler()
                    .buildTask(this, this::maintainWebsiteApi)
                    .repeat(1, TimeUnit.MINUTES)
                    .schedule();
            logger.info(
                    "Restricted website API started on loopback; {} eligible punishment codes were backfilled",
                    created
            );
        } catch (RuntimeException | java.io.IOException exception) {
            if (server != null) {
                server.close();
            }
            logger.error(
                    "Restricted website API initialization failed; the moderation runtime remains available",
                    exception
            );
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
        boolean suppressEvidence = current == OperationalMode.MAINTENANCE
                || current == OperationalMode.BOOTSTRAP
                || current == OperationalMode.DEGRADED
                || current == OperationalMode.READ_ONLY_FAILURE;
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
        CompletableFuture<Void> next = presenceUpdates.compute(playerId, (ignored, previous) -> {
            CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((value, failure) -> null);
            return start.thenRunAsync(update, workers);
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
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            source.sendMessage(Component.text("The bounded work queue is full; alt operation did not start."));
        }
    }

    private final class StatusCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] arguments = invocation.arguments();
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
            snapshot.issues().forEach((component, reason) ->
                    source.sendMessage(Component.text("DISABLED " + component + ": " + reason)));
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] arguments = invocation.arguments();
            if (arguments.length <= 1) {
                return List.of("status", "verify", "migration", "cutover", "discord", "website");
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("migration")) {
                return List.of("inspect", "dry-run", "import", "shadow");
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("cutover")) {
                return List.of("status", "maintenance", "activate", "override");
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("discord")) {
                return List.of("status", "retry");
            }
            if (arguments.length == 3 && arguments[0].equalsIgnoreCase("discord")
                    && arguments[1].equalsIgnoreCase("retry")) {
                return List.of("punishments", "reports", "logs-staffmode", "alerts");
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("website")) {
                return List.of("status", "code");
            }
            if (arguments.length == 3 && arguments[0].equalsIgnoreCase("website")
                    && arguments[1].equalsIgnoreCase("code")) {
                return List.of("show", "rotate", "revoke");
            }
            return List.of();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
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
            } catch (java.util.concurrent.RejectedExecutionException exception) {
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
                source.sendMessage(Component.text("Usage: /estaff migration <inspect|dry-run|import|shadow>"));
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
                default -> null;
            };
            if (migrationMode == null) {
                source.sendMessage(Component.text("Unknown migration operation."));
                return;
            }
            if (migrationMode != MigrationMode.DRY_RUN && authorityMode.get() == OperationalMode.ACTIVE) {
                source.sendMessage(Component.text("Import and shadow writes are blocked while EnthusiaStaff is ACTIVE."));
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
                        MigrationExecutionReport report = runtime.liteBansMigrationService().execute(
                                loaded.liteBansDatabaseFromEnvironment(),
                                loaded.liteBansTablePrefix(),
                                loaded.liteBansBatchSize(),
                                migrationMode
                        );
                        source.sendMessage(Component.text(
                                "Migration " + report.mode() + " run " + report.runId() + ": source="
                                        + report.sourceRecords() + ", imported=" + report.importedRecords()
                                        + ", replayed=" + report.replayedRecords() + ", rejected="
                                        + report.rejectedRows().size() + ", schema-blockers="
                                        + report.schema().blockers().size()
                        ));
                    } catch (RuntimeException exception) {
                        logger.error("Migration command failed", exception);
                        source.sendMessage(Component.text("Migration failed; inspect the sanitized proxy log and durable run record."));
                    } finally {
                        migrationRunning.set(false);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException exception) {
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
                        "Usage: /estaff cutover <status|maintenance|activate|override>"
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
                    net.enthusia.staff.domain.migration.CutoverAssessment assessment =
                            runtime.cutoverCoordinator().assess(java.util.Optional.empty());
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
                if (arguments.length < 4 || !arguments[2].equals("I_UNDERSTAND_CUTOVER_BLOCKERS")) {
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
                } catch (java.util.concurrent.RejectedExecutionException exception) {
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
                } catch (java.util.concurrent.RejectedExecutionException exception) {
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
            } catch (java.util.concurrent.RejectedExecutionException exception) {
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
}
