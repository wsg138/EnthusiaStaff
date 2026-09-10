package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns every resource in the isolated staff-bot process and provides deterministic shutdown semantics. */
public final class StaffBotRuntime implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(StaffBotRuntime.class.getName());

    private final StaffBotConfiguration configuration;
    private final StaffBotHealth health;
    private final StaffBotWorkerPool workerPool;
    private final InteractionReplayGuard interactionReplayGuard;
    private final HealthEndpoint healthEndpoint;
    private final DiscordGateway gateway;
    private final Optional<StaffModerationRuntime> moderationRuntime;
    private final Optional<StagingTunnel> stagingTunnel;
    private final Object startupGate = new Object();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean gatewayStarted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Boolean> readiness = new CompletableFuture<>();
    private final CountDownLatch terminated = new CountDownLatch(1);

    StaffBotRuntime(
            StaffBotConfiguration configuration,
            StaffBotHealth health,
            StaffBotWorkerPool workerPool,
            InteractionReplayGuard interactionReplayGuard,
            HealthEndpoint healthEndpoint,
            DiscordGateway gateway) {
        this(
                configuration,
                health,
                workerPool,
                interactionReplayGuard,
                healthEndpoint,
                gateway,
                Optional.empty(),
                Optional.empty());
    }

    StaffBotRuntime(
            StaffBotConfiguration configuration,
            StaffBotHealth health,
            StaffBotWorkerPool workerPool,
            InteractionReplayGuard interactionReplayGuard,
            HealthEndpoint healthEndpoint,
            DiscordGateway gateway,
            Optional<StaffModerationRuntime> moderationRuntime) {
        this(
                configuration,
                health,
                workerPool,
                interactionReplayGuard,
                healthEndpoint,
                gateway,
                moderationRuntime,
                Optional.empty());
    }

    StaffBotRuntime(
            StaffBotConfiguration configuration,
            StaffBotHealth health,
            StaffBotWorkerPool workerPool,
            InteractionReplayGuard interactionReplayGuard,
            HealthEndpoint healthEndpoint,
            DiscordGateway gateway,
            Optional<StaffModerationRuntime> moderationRuntime,
            Optional<StagingTunnel> stagingTunnel) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.health = Objects.requireNonNull(health, "health");
        this.workerPool = Objects.requireNonNull(workerPool, "workerPool");
        this.interactionReplayGuard = Objects.requireNonNull(interactionReplayGuard, "interactionReplayGuard");
        this.healthEndpoint = Objects.requireNonNull(healthEndpoint, "healthEndpoint");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.moderationRuntime = Objects.requireNonNull(moderationRuntime, "moderationRuntime");
        this.stagingTunnel = Objects.requireNonNull(stagingTunnel, "stagingTunnel");
    }

    public static StaffBotRuntime create(StaffBotConfiguration configuration) throws IOException {
        return create(configuration, Optional.empty(), Optional.empty());
    }

    static StaffBotRuntime create(
            StaffBotConfiguration configuration,
            Optional<Path> moderationConfigFile
    ) throws IOException {
        return create(configuration, moderationConfigFile, Optional.empty());
    }

    static StaffBotRuntime create(
            StaffBotConfiguration configuration,
            Optional<Path> moderationConfigFile,
            Optional<StaffBotCommandLine.TunnelFiles> tunnelFiles
    ) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(moderationConfigFile, "moderationConfigFile");
        Objects.requireNonNull(tunnelFiles, "tunnelFiles");
        Optional<StagingTunnel> tunnel = tunnelFiles.map(files -> createTunnel(configuration, files));
        StaffBotHealth health = new StaffBotHealth(configuration.environment());
        StaffBotWorkerPool workers = new StaffBotWorkerPool(
                configuration.workerThreads(), configuration.workerQueueCapacity(), health);
        InteractionReplayGuard replayGuard = new InteractionReplayGuard(
                configuration.interactionCapacity(), configuration.interactionTtl());
        Optional<StaffModerationRuntime> moderation = Optional.empty();
        try {
            moderation = StaffModerationRuntime.open(
                    moderationConfigFile,
                    configuration.interactionCapacity(),
                    configuration.interactionTtl());
            StaffBotHealthServer healthServer = new StaffBotHealthServer(configuration.healthAddress(), health);
            DiscordGateway gateway = new JdaDiscordGateway(configuration, workers, replayGuard, moderation);
            return new StaffBotRuntime(
                    configuration,
                    health,
                    workers,
                    replayGuard,
                    healthServer,
                    gateway,
                    moderation,
                    tunnel);
        } catch (IOException | RuntimeException exception) {
            moderation.ifPresent(StaffModerationRuntime::close);
            workers.close();
            throw exception;
        }
    }

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("staff bot runtime already started");
        }
        if (closed.get()) {
            throw new IllegalStateException("staff bot runtime already closed");
        }

        healthEndpoint.start();
        startTunnel();
        startGateway();
    }

    public boolean awaitReady(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("ready timeout must be positive");
        }
        try {
            return readiness.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException exception) {
            return false;
        }
    }

    public void awaitTermination() throws InterruptedException {
        terminated.await();
    }

    public StaffBotHealth health() {
        return health;
    }

    public StaffBotWorkerPool workerPool() {
        return workerPool;
    }

    public InteractionReplayGuard interactionReplayGuard() {
        return interactionReplayGuard;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        health.transition(StaffBotHealth.Phase.STOPPING, "process_stopping");
        readiness.complete(false);

        String shutdownFailure = shutdownGateway();
        stagingTunnel.ifPresent(StagingTunnel::close);
        healthEndpoint.close();
        moderationRuntime.ifPresent(StaffModerationRuntime::close);
        workerPool.close();
        terminated.countDown();

        if (shutdownFailure != null) {
            health.transition(StaffBotHealth.Phase.FAILED, shutdownFailure);
            logIfEnabled(System.Logger.Level.ERROR,
                    "staff_bot_failed environment={0} reason={1}", configuration.environment().label(), shutdownFailure);
            throw new IllegalStateException("staff bot gateway did not terminate cleanly");
        }

        health.transition(StaffBotHealth.Phase.STOPPED, "process_stopped");
        logIfEnabled(System.Logger.Level.INFO, "staff_bot_stopped environment={0}", configuration.environment().label());
    }

    private void startTunnel() throws IOException {
        if (stagingTunnel.isEmpty()) {
            return;
        }
        try {
            stagingTunnel.orElseThrow().start(this::tunnelExitedUnexpectedly);
        } catch (IOException | RuntimeException exception) {
            failClosed("staging_tunnel_start_failed");
            throw exception;
        }
    }

    private void startGateway() throws IOException {
        synchronized (startupGate) {
            if (health.failedEver()) {
                throw new IOException("staff bot runtime failed during tunnel startup");
            }
            health.transition(StaffBotHealth.Phase.CONNECTING, "gateway_connecting");
            logIfEnabled(System.Logger.Level.INFO,
                    "staff_bot_start environment={0}", configuration.environment().label());
            try {
                gateway.start(new RuntimeGatewayObserver());
                gatewayStarted.set(true);
            } catch (RuntimeException exception) {
                failClosed("gateway_start_failed");
                throw exception;
            }
        }
    }

    private void tunnelExitedUnexpectedly() {
        synchronized (startupGate) {
            if (!closed.get() && !health.failedEver()) {
                failClosed("staging_tunnel_exited");
            }
        }
    }

    private String shutdownGateway() {
        if (!gatewayStarted.get()) {
            return null;
        }
        gateway.shutdown();
        try {
            if (!gateway.awaitShutdown(configuration.shutdownTimeout())) {
                gateway.shutdownNow();
                if (!gateway.awaitShutdown(Duration.ofSeconds(2))) {
                    return "gateway_shutdown_timeout";
                }
            }
        } catch (InterruptedException exception) {
            gateway.shutdownNow();
            Thread.currentThread().interrupt();
            return "gateway_shutdown_interrupted";
        }
        return null;
    }

    private void failClosed(String reason) {
        health.transition(StaffBotHealth.Phase.FAILED, reason);
        readiness.complete(false);
        gateway.shutdownNow();
        terminated.countDown();
        logIfEnabled(System.Logger.Level.ERROR,
                "staff_bot_failed environment={0} reason={1}", configuration.environment().label(), reason);
    }

    private static StagingTunnel createTunnel(
            StaffBotConfiguration configuration,
            StaffBotCommandLine.TunnelFiles files
    ) {
        if (configuration.environment() != StaffBotEnvironment.STAGING || !configuration.uiPreviewEnabled()) {
            throw new IllegalArgumentException("staging tunnel requires the staging UI preview runtime");
        }
        return new CloudflaredStagingTunnel(files.binaryFile(), files.tokenFile());
    }

    private static void logIfEnabled(System.Logger.Level level, String message, Object... parameters) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, message, parameters);
        }
    }

    private final class RuntimeGatewayObserver implements DiscordGatewayObserver {
        @Override
        public void onIdentityResolved(DiscordRuntimeIdentity identity) {
            if (closed.get() || health.failedEver()) {
                return;
            }
            DiscordRuntimeIdentityValidator.ValidationResult result =
                    DiscordRuntimeIdentityValidator.validate(configuration.environment(), identity);
            if (!result.valid()) {
                failClosed(result.reason());
                return;
            }
            gateway.enableInteractions();
            health.transition(StaffBotHealth.Phase.READY, result.reason());
            readiness.complete(true);
            logIfEnabled(System.Logger.Level.INFO,
                    "staff_bot_ready environment={0}", configuration.environment().label());
        }

        @Override
        public void onDisconnected() {
            if (closed.get() || health.failedEver()) {
                return;
            }
            health.transition(StaffBotHealth.Phase.DISCONNECTED, "gateway_disconnected_reconnecting");
            logIfEnabled(System.Logger.Level.WARNING,
                    "staff_bot_gateway_disconnected environment={0}", configuration.environment().label());
        }

        @Override
        public void onFatal(String reason) {
            if (!closed.get() && !health.failedEver()) {
                failClosed(reason);
            }
        }

        @Override
        public void onShutdown() {
            if (!closed.get() && !health.failedEver()) {
                health.transition(StaffBotHealth.Phase.FAILED, "gateway_shutdown_unexpected");
            }
            readiness.complete(false);
            terminated.countDown();
        }
    }
}
