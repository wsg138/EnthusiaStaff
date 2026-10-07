package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
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
    private final Optional<StaffBotChatLifecycle> chatLifecycle;
    private final Optional<PublicChatRuntime> publicChatRuntime;
    private final Object startupGate = new Object();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean gatewayStarted = new AtomicBoolean();
    private final AtomicBoolean publicChatGatewayStarted = new AtomicBoolean();
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
                RuntimeServices.empty());
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
                RuntimeServices.empty());
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
        this(
                configuration,
                health,
                workerPool,
                interactionReplayGuard,
                healthEndpoint,
                gateway,
                moderationRuntime,
                new RuntimeServices(stagingTunnel, Optional.empty()));
    }

    StaffBotRuntime(
            StaffBotConfiguration configuration,
            StaffBotHealth health,
            StaffBotWorkerPool workerPool,
            InteractionReplayGuard interactionReplayGuard,
            HealthEndpoint healthEndpoint,
            DiscordGateway gateway,
            Optional<StaffModerationRuntime> moderationRuntime,
            RuntimeServices runtimeServices) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.health = Objects.requireNonNull(health, "health");
        this.workerPool = Objects.requireNonNull(workerPool, "workerPool");
        this.interactionReplayGuard = Objects.requireNonNull(interactionReplayGuard, "interactionReplayGuard");
        this.healthEndpoint = Objects.requireNonNull(healthEndpoint, "healthEndpoint");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.moderationRuntime = Objects.requireNonNull(moderationRuntime, "moderationRuntime");
        RuntimeServices services = Objects.requireNonNull(runtimeServices, "runtimeServices");
        this.stagingTunnel = services.stagingTunnel();
        this.chatLifecycle = services.chatLifecycle();
        this.publicChatRuntime = services.publicChatRuntime();
    }

    record RuntimeServices(
            Optional<StagingTunnel> stagingTunnel,
            Optional<StaffBotChatLifecycle> chatLifecycle,
            Optional<PublicChatRuntime> publicChatRuntime
    ) {
        RuntimeServices {
            Objects.requireNonNull(stagingTunnel, "stagingTunnel");
            Objects.requireNonNull(chatLifecycle, "chatLifecycle");
            Objects.requireNonNull(publicChatRuntime, "publicChatRuntime");
        }

        RuntimeServices(
                Optional<StagingTunnel> stagingTunnel,
                Optional<StaffBotChatLifecycle> chatLifecycle
        ) {
            this(stagingTunnel, chatLifecycle, Optional.empty());
        }

        static RuntimeServices empty() {
            return new RuntimeServices(Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    record PublicChatRuntime(DiscordGateway gateway, long applicationId) {
        PublicChatRuntime {
            Objects.requireNonNull(gateway, "gateway");
            if (applicationId <= 0L) {
                throw new IllegalArgumentException("public chat application ID must be positive");
            }
        }
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
        if (configuration.moderationWebUri().isPresent()
                && (tunnelFiles.isEmpty() || moderationConfigFile.isEmpty())) {
            throw new IllegalArgumentException("production moderation website requires its private read tunnel");
        }
        Optional<StagingTunnel> tunnel = tunnelFiles.map(files -> createTunnel(configuration, files));
        StaffBotHealth health = new StaffBotHealth(configuration.environment());
        StaffBotWorkerPool workers = new StaffBotWorkerPool(
                configuration.workerThreads(), configuration.workerQueueCapacity(), health);
        InteractionReplayGuard replayGuard = new InteractionReplayGuard(
                configuration.interactionCapacity(), configuration.interactionTtl());
        Optional<StaffModerationRuntime> moderation = Optional.empty();
        Optional<StaffBotChatTransport> chatTransport = Optional.empty();
        try {
            moderation = StaffModerationRuntime.open(
                    moderationConfigFile,
                    configuration.environment().guildId(),
                    configuration.interactionCapacity(),
                    configuration.interactionTtl());
            Map<String, String> environmentValues = System.getenv();
            Optional<StaffBotChatBridgeConfiguration> chatConfiguration =
                    StaffBotChatBridgeConfiguration.fromEnvironment(
                            configuration.environment(), environmentValues);
            Optional<PublicChatDiscordConfiguration> publicChatConfiguration =
                    PublicChatDiscordConfiguration.fromEnvironment(
                            environmentValues, chatConfiguration.isPresent());
            StaffBotHealthServer healthServer = new StaffBotHealthServer(configuration.healthAddress(), health);
            JdaDiscordGateway gateway = new JdaDiscordGateway(
                    configuration, workers, replayGuard, moderation, Optional.empty());
            Optional<JdaDiscordGateway> publicChatGateway = publicChatConfiguration.map(current ->
                    JdaDiscordGateway.publicChat(
                            configuration,
                            current,
                            moderation,
                            chatConfiguration.orElseThrow()
                    ));
            chatTransport = publicChatGateway.map(current ->
                    StaffBotChatTransport.create(chatConfiguration.orElseThrow(), current, current));
            if (chatConfiguration.map(current -> !current.ingressRoutes().isEmpty()).orElse(false)) {
                publicChatGateway.orElseThrow().installChatIngress(chatTransport.orElseThrow());
            }
            Optional<StaffBotChatLifecycle> chat =
                    chatTransport.map(current -> (StaffBotChatLifecycle) current);
            Optional<PublicChatRuntime> publicChat = publicChatGateway.map(current ->
                    new PublicChatRuntime(
                            current,
                            publicChatConfiguration.orElseThrow().applicationId()
                    ));
            return new StaffBotRuntime(
                    configuration,
                    health,
                    workers,
                    replayGuard,
                    healthServer,
                    gateway,
                    moderation,
                    new RuntimeServices(tunnel, chat, publicChat));
        } catch (IOException | RuntimeException exception) {
            chatTransport.ifPresent(StaffBotChatTransport::close);
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
        startChat();
        startPublicChatGateway();
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

        String chatShutdownFailure = shutdownChat();
        String publicChatShutdownFailure = shutdownPublicChatGateway();
        String gatewayShutdownFailure = shutdownGateway();
        String shutdownFailure = gatewayShutdownFailure != null
                ? gatewayShutdownFailure
                : publicChatShutdownFailure != null ? publicChatShutdownFailure : chatShutdownFailure;
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

    private void startChat() {
        try {
            chatLifecycle.ifPresent(StaffBotChatLifecycle::start);
        } catch (RuntimeException exception) {
            failClosed("chat_transport_start_failed");
            throw exception;
        }
    }

    private void startPublicChatGateway() {
        if (publicChatRuntime.isEmpty()) {
            return;
        }
        PublicChatRuntime publicChat = publicChatRuntime.orElseThrow();
        try {
            publicChat.gateway().start(new PublicChatGatewayObserver(publicChat.applicationId()));
            publicChatGatewayStarted.set(true);
        } catch (RuntimeException exception) {
            pauseChatQuietly();
            publicChat.gateway().shutdownNow();
            logIfEnabled(
                    System.Logger.Level.WARNING,
                    "public_chat_gateway_start_failed type={0}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void tunnelExitedUnexpectedly() {
        synchronized (startupGate) {
            if (!closed.get() && !health.failedEver()) {
                failClosed("staging_tunnel_exited");
            }
        }
    }

    private String shutdownChat() {
        if (chatLifecycle.isEmpty()) {
            return null;
        }
        StaffBotChatLifecycle chat = chatLifecycle.orElseThrow();
        boolean failed = false;
        try {
            chat.pause();
        } catch (RuntimeException exception) {
            failed = true;
        }
        try {
            chat.close();
        } catch (RuntimeException exception) {
            failed = true;
        }
        return failed ? "chat_transport_shutdown_failed" : null;
    }

    private String shutdownPublicChatGateway() {
        if (publicChatRuntime.isEmpty() || !publicChatGatewayStarted.get()) {
            return null;
        }
        DiscordGateway publicGateway = publicChatRuntime.orElseThrow().gateway();
        publicGateway.shutdown();
        try {
            if (!publicGateway.awaitShutdown(configuration.shutdownTimeout())) {
                publicGateway.shutdownNow();
                if (!publicGateway.awaitShutdown(Duration.ofSeconds(2))) {
                    return "public_chat_gateway_shutdown_timeout";
                }
            }
        } catch (InterruptedException exception) {
            publicGateway.shutdownNow();
            Thread.currentThread().interrupt();
            return "public_chat_gateway_shutdown_interrupted";
        }
        return null;
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
        pauseChatQuietly();
        health.transition(StaffBotHealth.Phase.FAILED, reason);
        readiness.complete(false);
        publicChatRuntime.ifPresent(current -> current.gateway().shutdownNow());
        gateway.shutdownNow();
        terminated.countDown();
        logIfEnabled(System.Logger.Level.ERROR,
                "staff_bot_failed environment={0} reason={1}", configuration.environment().label(), reason);
    }

    private void pauseChatQuietly() {
        if (chatLifecycle.isEmpty()) {
            return;
        }
        try {
            chatLifecycle.orElseThrow().pause();
        } catch (RuntimeException exception) {
            logIfEnabled(
                    System.Logger.Level.WARNING,
                    "staff_bot_chat_pause_failed type={0}",
                    exception.getClass().getSimpleName());
        }
    }

    private static StagingTunnel createTunnel(
            StaffBotConfiguration configuration,
            StaffBotCommandLine.TunnelFiles files
    ) {
        boolean staging = configuration.environment() == StaffBotEnvironment.STAGING
                && configuration.uiPreviewEnabled()
                && "cloudflared-token.txt".equals(files.tokenFile().getFileName().toString());
        boolean production = configuration.environment() == StaffBotEnvironment.PRODUCTION
                && configuration.moderationWebUri().isPresent()
                && "prod-tunnel".equals(files.tokenFile().getFileName().toString());
        if (!staging && !production) {
            throw new IllegalArgumentException("moderation tunnel configuration does not match the runtime");
        }
        return new CloudflaredStagingTunnel(files.binaryFile(), files.tokenFile());
    }

    private static void logIfEnabled(System.Logger.Level level, String message, Object... parameters) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, message, parameters);
        }
    }

    private final class PublicChatGatewayObserver implements DiscordGatewayObserver {
        private final long expectedApplicationId;

        private PublicChatGatewayObserver(long expectedApplicationId) {
            this.expectedApplicationId = expectedApplicationId;
        }

        @Override
        public void onIdentityResolved(DiscordRuntimeIdentity identity) {
            if (closed.get()) {
                return;
            }
            DiscordRuntimeIdentityValidator.ValidationResult result =
                    DiscordRuntimeIdentityValidator.validate(
                            configuration.environment(),
                            expectedApplicationId,
                            identity
                    );
            if (!result.valid()) {
                pauseChatQuietly();
                publicChatRuntime.ifPresent(current -> current.gateway().shutdownNow());
                logIfEnabled(
                        System.Logger.Level.ERROR,
                        "public_chat_identity_rejected environment={0} reason={1}",
                        configuration.environment().label(),
                        result.reason()
                );
                return;
            }
            try {
                chatLifecycle.ifPresent(StaffBotChatLifecycle::resume);
                logIfEnabled(
                        System.Logger.Level.INFO,
                        "public_chat_ready environment={0}",
                        configuration.environment().label()
                );
            } catch (RuntimeException exception) {
                pauseChatQuietly();
                publicChatRuntime.ifPresent(current -> current.gateway().shutdownNow());
                logIfEnabled(
                        System.Logger.Level.ERROR,
                        "public_chat_resume_failed type={0}",
                        exception.getClass().getSimpleName()
                );
            }
        }

        @Override
        public void onDisconnected() {
            if (!closed.get()) {
                pauseChatQuietly();
                logIfEnabled(
                        System.Logger.Level.WARNING,
                        "public_chat_gateway_disconnected environment={0}",
                        configuration.environment().label()
                );
            }
        }

        @Override
        public void onFatal(String reason) {
            if (!closed.get()) {
                pauseChatQuietly();
                publicChatRuntime.ifPresent(current -> current.gateway().shutdownNow());
                logIfEnabled(
                        System.Logger.Level.ERROR,
                        "public_chat_gateway_failed environment={0} reason={1}",
                        configuration.environment().label(),
                        reason
                );
            }
        }

        @Override
        public void onShutdown() {
            pauseChatQuietly();
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
            try {
                gateway.enableInteractions();
            } catch (RuntimeException exception) {
                failClosed("interaction_enable_failed");
                return;
            }
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
            pauseChatQuietly();
            if (!closed.get() && !health.failedEver()) {
                health.transition(StaffBotHealth.Phase.FAILED, "gateway_shutdown_unexpected");
            }
            readiness.complete(false);
            terminated.countDown();
        }
    }
}
