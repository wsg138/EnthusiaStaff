package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaffBotRuntimeTest {
    @Test
    void exactIdentityTransitionsRuntimeToReadyAndReconnects() throws Exception {
        Fixture fixture = new Fixture(true);
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
    void identityMismatchFailsClosedAndForcesGatewayDown() throws Exception {
        Fixture fixture = new Fixture(true);
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
        }
    }

    @Test
    void gracefulShutdownEscalatesAfterTimeout() throws Exception {
        Fixture fixture = new Fixture(false);
        StaffBotRuntime runtime = fixture.runtime();
        runtime.start();
        fixture.gateway.emitIdentity(validStagingIdentity());

        runtime.close();

        assertTrue(fixture.gateway.shutdownRequested);
        assertTrue(fixture.gateway.shutdownNowRequested);
        assertTrue(fixture.endpoint.closed);
        assertEquals(StaffBotHealth.Phase.STOPPED, runtime.health().snapshot().phase());
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

        private Fixture(boolean gracefulShutdown) {
            gateway = new FakeGateway(gracefulShutdown);
        }

        private StaffBotRuntime runtime() {
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
                    gateway);
        }
    }

    private static final class FakeHealthEndpoint implements HealthEndpoint {
        private boolean started;
        private boolean closed;

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeGateway implements DiscordGateway {
        private final boolean gracefulShutdown;
        private DiscordGatewayObserver observer;
        private boolean shutdownRequested;
        private boolean shutdownNowRequested;

        private FakeGateway(boolean gracefulShutdown) {
            this.gracefulShutdown = gracefulShutdown;
        }

        @Override
        public void start(DiscordGatewayObserver observer) {
            this.observer = observer;
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
            return gracefulShutdown || shutdownNowRequested;
        }

        private void emitIdentity(DiscordRuntimeIdentity identity) {
            observer.onIdentityResolved(identity);
        }

        private void emitDisconnect() {
            observer.onDisconnected();
        }
    }
}
