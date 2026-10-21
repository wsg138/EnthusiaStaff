package net.enthusia.staff.discordbot;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated TEST-to-private-channel SHADOW runner. This is a separate main class:
 * it never starts the regular StaffBot application or its moderation, age gate,
 * staff interactions, account linking, tunnel, DB, or role-sync runtime.
 *
 * <p>Run only in a separately controlled staging JVM. No config write or
 * production bot restart is performed by this class.</p>
 */
public final class StaffBotIsolatedChatApplication {
    private static final System.Logger LOGGER =
            System.getLogger(StaffBotIsolatedChatApplication.class.getName());
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(45);
    private static final String SMOKE_FLAG = "--smoke-test";
    private static final int CONFIG_ERROR = 2;
    private static final int RUNTIME_ERROR = 3;
    private static final StaffBotEnvironment ENVIRONMENT = StaffBotEnvironment.STAGING;
    private static final StaffBotChatBridgeConfiguration.Route TEST_GLOBAL =
            new StaffBotChatBridgeConfiguration.Route("TEST", "global");

    private StaffBotIsolatedChatApplication() {
    }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) {
        final Options options;
        final Selection selection;
        try {
            options = parse(args);
            // Intentionally do not import the main StaffBot's process environment,
            // primary Discord token, moderation settings, or interaction authority.
            selection = select(StaffBotChatSettingsFile.overlay(options.chatFile(), Map.of()));
        } catch (IllegalArgumentException failure) {
            LOGGER.log(System.Logger.Level.ERROR, "isolated_chat_configuration_invalid");
            return CONFIG_ERROR;
        }

        try (IsolatedRuntime runtime = IsolatedRuntime.create(selection)) {
            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("isolated-chat-shutdown").unstarted(runtime::close));
            runtime.start();
            if (options.smokeTest()) {
                boolean ready = runtime.awaitReady(READY_TIMEOUT);
                if (ready) {
                    LOGGER.log(System.Logger.Level.INFO, "isolated_chat_smoke_ready");
                    return 0;
                }
                LOGGER.log(System.Logger.Level.ERROR, "isolated_chat_smoke_not_ready");
                return RUNTIME_ERROR;
            }
            runtime.awaitTermination();
            return runtime.failed() ? RUNTIME_ERROR : 0;
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR, "isolated_chat_start_failed");
            return RUNTIME_ERROR;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.WARNING, "isolated_chat_interrupted");
            return 130;
        }
    }

    record Options(Path chatFile, boolean smokeTest) {
        Options {
            Objects.requireNonNull(chatFile, "chatFile");
        }
    }

    static Options parse(String[] args) {
        if (args == null || args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("chat-only requires --chat=FILE");
        }
        Path file = null;
        boolean smoke = false;
        for (String arg : args) {
            if (arg != null && arg.startsWith("--chat=") && file == null
                    && arg.length() > "--chat=".length()) {
                file = Path.of(arg.substring("--chat=".length()));
            } else if (SMOKE_FLAG.equals(arg) && !smoke) {
                smoke = true;
            } else {
                throw new IllegalArgumentException("unsupported chat-only option");
            }
        }
        if (file == null) {
            throw new IllegalArgumentException("chat-only requires a private config file");
        }
        return new Options(file, smoke);
    }

    record Selection(
            StaffBotChatBridgeConfiguration bridge,
            PublicChatDiscordConfiguration publicApp
    ) {
        Selection {
            Objects.requireNonNull(bridge, "bridge");
            Objects.requireNonNull(publicApp, "publicApp");
        }
    }

    static Selection select(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        StaffBotChatBridgeConfiguration bridge =
                StaffBotChatBridgeConfiguration.fromEnvironment(ENVIRONMENT, values)
                        .orElseThrow(() -> new IllegalArgumentException("chat bridge is disabled"));
        if (bridge.mode() != StaffBotChatBridgeConfiguration.Mode.SHADOW
                || !bridge.ingressRoutes().isEmpty()
                || !bridge.routes().equals(Map.of(
                        TEST_GLOBAL, ENVIRONMENT.testChannelId().orElseThrow()))) {
            throw new IllegalArgumentException(
                    "isolated chat may only export TEST/global to the pinned staging channel");
        }
        PublicChatDiscordConfiguration app =
                PublicChatDiscordConfiguration.fromEnvironment(values, true).orElseThrow();
        if (app.applicationId() == StaffBotEnvironment.STAGING.applicationId()
                || app.applicationId() == StaffBotEnvironment.PRODUCTION.applicationId()) {
            throw new IllegalArgumentException("chat-only application must differ from staff applications");
        }
        return new Selection(bridge, app);
    }

    static final class IsolatedRuntime implements AutoCloseable {
        private final DiscordGateway gateway;
        private final StaffBotChatTransport chat;
        private final long expectedApplicationId;
        private final AtomicBoolean discordReady = new AtomicBoolean();
        private final CountDownLatch terminated = new CountDownLatch(1);
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private IsolatedRuntime(DiscordGateway gateway, StaffBotChatTransport chat, long appId) {
            this.gateway = gateway;
            this.chat = chat;
            this.expectedApplicationId = appId;
        }

        static IsolatedRuntime create(Selection selected) {
            // Configuration is used only for JDA's environment identity and
            // reconnect policy. Its placeholder primary token is NEVER passed
            // to JDA, because only JdaDiscordGateway.publicChat() is built.
            StaffBotConfiguration core = new StaffBotConfiguration(
                    ENVIRONMENT,
                    "UNUSED_PRIMARY_TOKEN_NO_PRIMARY_GATEWAY",
                    new InetSocketAddress("127.0.0.1", 0),
                    1, 16, 16, Duration.ofMinutes(5));
            JdaDiscordGateway gateway = JdaDiscordGateway.publicChat(
                    core, selected.publicApp(), Optional.empty(), selected.bridge());
            try {
                StaffBotChatTransport chat =
                        StaffBotChatTransport.createIsolatedTest(selected.bridge(), gateway, gateway);
                return new IsolatedRuntime(gateway, chat, selected.publicApp().applicationId());
            } catch (RuntimeException failure) {
                gateway.shutdownNow();
                throw failure;
            }
        }

        void start() {
            chat.start();
            gateway.start(new DiscordGatewayObserver() {
                @Override
                public void onIdentityResolved(DiscordRuntimeIdentity identity) {
                    if (closed.get() || failed.get()) {
                        return;
                    }
                    DiscordRuntimeIdentityValidator.ValidationResult validated =
                            DiscordRuntimeIdentityValidator.validate(
                                    ENVIRONMENT, expectedApplicationId, identity);
                    if (!validated.valid()) {
                        fail("isolated_chat_identity_rejected");
                        return;
                    }
                    try {
                        chat.resume();
                        discordReady.set(true);
                        LOGGER.log(System.Logger.Level.INFO, "isolated_chat_discord_ready");
                    } catch (RuntimeException failure) {
                        fail("isolated_chat_resume_failed");
                    }
                }

                @Override
                public void onDisconnected() {
                    discordReady.set(false);
                    chat.pause();
                    LOGGER.log(System.Logger.Level.WARNING, "isolated_chat_disconnected");
                }

                @Override
                public void onFatal(String reason) {
                    fail("isolated_chat_gateway_failed");
                }

                @Override
                public void onShutdown() {
                    discordReady.set(false);
                    if (!closed.get()) {
                        fail("isolated_chat_gateway_stopped");
                    }
                    terminated.countDown();
                }
            });
        }

        private void fail(String reason) {
            if (failed.compareAndSet(false, true)) {
                discordReady.set(false);
                chat.pause();
                gateway.shutdownNow();
                terminated.countDown();
                LOGGER.log(System.Logger.Level.ERROR, reason);
            }
        }

        boolean awaitReady(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline && !failed.get() && !closed.get()) {
                if (readyForSmoke(discordReady.get(), chat.connected(), failed.get(), closed.get())) {
                    return true;
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }
            return false;
        }

        static boolean readyForSmoke(
                boolean discordReady, boolean transportConnected,
                boolean failed, boolean closed
        ) {
            return discordReady && transportConnected && !failed && !closed;
        }

        boolean failed() {
            return failed.get();
        }

        void awaitTermination() throws InterruptedException {
            terminated.await();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                discordReady.set(false);
                chat.pause();
                gateway.shutdownNow();
                chat.close();
                terminated.countDown();
            }
        }
    }
}
