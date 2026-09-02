package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaffBotRuntimeTest {
    @Test
    void exactIdentityTransitionsRuntimeToReadyAndReconnects() throws Exception {
        Fixture fixture = new Fixture(true, true);
        try (StaffBotRuntime runtime = fixture.runtime()) {
            runtime.start();
            assertEquals(StaffBotHealth.Phase.CONNECTING, runtime.health().snapshot().phase());

            fixture.gateway.emitIdentity(validStagingIdentity());
            assertTrue(runtime.awaitReady(Duration.ofMillis(100)));
            assertEquals(StaffBotHealth.Phase.READY, runtime.health().snapshot().phase());

            fixture.gateway.emitDisconnect();
            assertEquals(StaffBotHealth.Phase.DISCONNECTED, runtime.health().snapshot().phase());
            fixture.gateway.emitIdentity(validStagingIdentity());
            assertEquals(StaffBotHealth.Phase.READY, runtime.health().snapshot().phase());
        }
        assertTrue(fixture.endpoint.closed);
        assertTrue(fixture.gateway.shutdownRequested);
    }

    @Test
    void previewRuntimeCreatesWithoutModerationDependencies() throws Exception {
        StaffBotConfiguration configuration = StaffBotConfiguration.fromEnvironment(Map.of(
                StaffBotConfiguration.ENVIRONMENT_KEY, "staging",
                StaffBotConfiguration.TOKEN_KEY, "preview-test-token",
                StaffBotConfiguration.UI_PREVIEW_KEY, "true",
                StaffBotConfiguration.HEALTH_PORT_KEY, "0"));

        try (StaffBotRuntime runtime = StaffBotRuntime.create(configuration)) {
            assertFalse(runtime.health().isReady());
            assertTrue(configuration.uiPreviewEnabled());
        }
    }

    @Test
    void stagingTunnelUnexpectedExitFailsClosed() throws Exception {
        Fixture fixture = new Fixture(true, true);
        FakeTunnel tunnel = new FakeTunnel(false);
        try (StaffBotRuntime runtime = fixture.runtime(tunnel)) {
            runtime.start();
            assertTrue(tunnel.started);
            assertTrue(fixture.gateway.started);

            tunnel.exitUnexpectedly();

            assertTrue(runtime.health().failedEver());
            assertEquals("staging_tunnel_exited", runtime.health().snapshot().reason());
            assertTrue(fixture.gateway.shutdownNowRequested);
        }
        assertTrue(tunnel.closed);
    }

    @Test
    void stagingTunnelStartupFailurePreventsGatewayAndRollsBack() {
        Fixture fixture = new Fixture(true, true);
        FakeTunnel tunnel = new FakeTunnel(true);
        StaffBotRuntime runtime = fixture.runtime(tunnel);

        assertThrows(IOException.class, runtime::start);
        assertFalse(fixture.gateway.started);
        assertTrue(runtime.health().failedEver());
        assertEquals("staging_tunnel_start_failed", runtime.health().snapshot().reason());

        runtime.close();
        assertTrue(tunnel.closed);
        assertFalse(fixture.gateway.shutdownRequested);
    }

    @Test
    void identityMismatchFailsClosedAndCannotReturnToReady() throws Exception {
        Fixture fixture = new Fixture(true, true);
        try (StaffBotRuntime runtime = fixture.runtime()) {
            runtime.start();
            fixture.gateway.emitIdentity(new DiscordRuntimeIdentity(
                    123L,
                    false,
                    Set.of(StaffBotEnvironment.STAGING.guildId()),
                    true,
                    true));

            assertFalse(runtime.awaitReady(Duration.ofMillis(100)));
            assertTrue(runtime.health().failedEver());
            assertEquals(StaffBotHealth.Phase.FAILED, runtime.health().snapshot().phase());
            assertTrue(fixture.gateway.shutdownNowRequested);

            fixture.gateway.emitIdentity(validStagingIdentity());
            assertEquals(StaffBotHealth.Phase.FAILED, runtime.health().snapshot().phase());
            assertFalse(runtime.health().isReady());
        }
    }

    @Test
    void gracefulShutdownEscalatesAfterTimeout() throws Exception {
        Fixture fixture = new Fixture(false, true);
        StaffBotRuntime runtime = fixture.runtime();
        runtime.start();
        fixture.gateway.emitIdentity(validStagingIdentity());

        runtime.close();

        assertTrue(fixture.gateway.shutdownRequested);
        assertTrue(fixture.gateway.shutdownNowRequested);
        assertTrue(fixture.endpoint.closed);
        assertEquals(StaffBotHealth.Phase.STOPPED, runtime.health().snapshot().phase());
    }

    @Test
    void forcedShutdownTimeoutFailsClosed() throws Exception {
        Fixture fixture = new Fixture(false, false);
        StaffBotRuntime runtime = fixture.runtime();
        runtime.start();
        fixture.gateway.emitIdentity(validStagingIdentity());

        assertThrows(IllegalStateException.class, runtime::close);

        assertTrue(fixture.gateway.shutdownRequested);
        assertTrue(fixture.gateway.shutdownNowRequested);
        assertTrue(fixture.endpoint.closed);
        assertTrue(runtime.health().failedEver());
        assertEquals(StaffBotHealth.Phase.FAILED, runtime.health().snapshot().phase());
        assertEquals("gateway_shutdown_timeout", runtime.health().snapshot().reason());
    }

    private static DiscordRuntimeIdentity validStagingIdentity() {
        return new DiscordRuntimeIdentity(
                StaffBotEnvironment.STAGING.applicationId(),
                false,
                Set.of(StaffBotEnvironment.STAGING.guildId()),
                true,
                true);
    }

    private static final class Fixture {
        private final StaffBotHealth health = new StaffBotHealth(StaffBotEnvironment.STAGING);
        private final FakeHealthEndpoint endpoint = new FakeHealthEndpoint();
        private final FakeGateway gateway;

        private Fixture(boolean gracefulShutdown, boolean forcedShutdown) {
            gateway = new FakeGateway(gracefulShutdown, forcedShutdown);
        }

        private StaffBotRuntime runtime() {
            return runtime(null);
        }

        private StaffBotRuntime runtime(FakeTunnel tunnel) {
            StaffBotConfiguration configuration = new StaffBotConfiguration(
                    StaffBotEnvironment.STAGING,
                    "test-only-token",
                    new InetSocketAddress("127.0.0.1", 0),
                    1,
                    4,
                    16,
                    Duration.ofMinutes(1));
            StaffBotWorkerPool workers = new StaffBotWorkerPool(1, 4, health);
            return new StaffBotRuntime(
                    configuration,
                    health,
                    workers,
                    new InteractionReplayGuard(16, Duration.ofMinutes(1)),
                    endpoint,
                    gateway,
                    Optional.empty(),
                    Optional.ofNullable(tunnel));
        }
    }

    private static final class FakeTunnel implements StagingTunnel {
        private final boolean failStartup;
        private boolean started;
        private boolean closed;
        private Runnable unexpectedExit;

        private FakeTunnel(boolean failStartup) {
            this.failStartup = failStartup;
        }

        @Override
        public void start(Runnable callback) throws IOException {
            started = true;
            unexpectedExit = callback;
            if (failStartup) {
                throw new IOException("test startup failure");
            }
        }

        private void exitUnexpectedly() {
            unexpectedExit.run();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeHealthEndpoint implements HealthEndpoint {
        private boolean closed;

        @Override
        public void start() {
            // No-op test endpoint.
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeGateway implements DiscordGateway {
        private final boolean gracefulShutdown;
        private final boolean forcedShutdown;
        private DiscordGatewayObserver observer;
        private boolean started;
        private boolean shutdownRequested;
        private boolean shutdownNowRequested;

        private FakeGateway(boolean gracefulShutdown, boolean forcedShutdown) {
            this.gracefulShutdown = gracefulShutdown;
            this.forcedShutdown = forcedShutdown;
        }

        @Override
        public void start(DiscordGatewayObserver observer) {
            this.observer = observer;
            started = true;
        }

        @Override
        public void shutdown() {
            shutdownRequested = true;
        }

        @Override
        public void shutdownNow() {
            shutdownNowRequested = true;
        }

        @Override
        public boolean awaitShutdown(Duration timeout) {
            return shutdownNowRequested ? forcedShutdown : gracefulShutdown;
        }

        private void emitIdentity(DiscordRuntimeIdentity identity) {
            observer.onIdentityResolved(identity);
        }

        private void emitDisconnect() {
            observer.onDisconnected();
        }
    }
}
