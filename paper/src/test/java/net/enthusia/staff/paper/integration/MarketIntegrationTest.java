package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.enthusia.market.api.moderation.MarketBlacklistRemoval;
import net.enthusia.market.api.moderation.MarketBlacklistRequest;
import net.enthusia.market.api.moderation.MarketBlacklistResult;
import net.enthusia.market.api.moderation.MarketConfiscationApproval;
import net.enthusia.market.api.moderation.MarketModerationApi;
import net.enthusia.market.api.moderation.MarketOperationRecord;
import net.enthusia.market.api.moderation.MarketOperationRequest;
import net.enthusia.market.api.moderation.MarketOperationResult;
import net.enthusia.market.api.moderation.MarketOwnership;
import net.enthusia.market.api.moderation.MarketRestoreRequest;
import net.enthusia.market.api.moderation.MarketStallRecord;
import net.enthusia.market.api.moderation.StallBlacklistState;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class MarketIntegrationTest {
    private static final UUID PLAYER_ID = UUID.fromString("1f4fd89e-0e52-4ea5-beb1-93df1e785ec8");

    @Test
    void missingPluginDoesNotAttemptToResolveAProvider() {
        MarketIntegration integration = MarketIntegration.discover(new StubServices(null), false);

        assertEquals(IntegrationAvailability.NOT_INSTALLED, integration.availability());
        assertThrows(IllegalStateException.class, integration::requireApi);
    }

    @Test
    void missingPluginRemainsLoadableWithoutProviderApiClasses() throws Throwable {
        URL providerApiLocation = MarketModerationApi.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        ArrayList<URL> isolatedClasspath = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            URL location = Path.of(entry).toUri().toURL();
            if (!location.equals(providerApiLocation)) {
                isolatedClasspath.add(location);
            }
        }
        try (URLClassLoader isolated = new URLClassLoader(
                isolatedClasspath.toArray(URL[]::new),
                ClassLoader.getPlatformClassLoader()
        )) {
            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName("net.enthusia.market.api.moderation.MarketModerationApi", false, isolated)
            );
            Class<?> isolatedServicesType = Class.forName(
                    "org.bukkit.plugin.ServicesManager",
                    true,
                    isolated
            );
            Object services = Proxy.newProxyInstance(
                    isolated,
                    new Class<?>[] {isolatedServicesType},
                    (proxy, method, arguments) -> null
            );
            Class<?> integrationType = isolated.loadClass(MarketIntegration.class.getName());
            Object integration = MethodHandles.publicLookup().findStatic(
                    integrationType,
                    "discover",
                    MethodType.methodType(integrationType, isolatedServicesType, boolean.class)
            ).invokeWithArguments(services, false);

            assertNotNull(integration);
        }
    }

    @Test
    void missingAndMismatchedServicesFailClosed() {
        MarketIntegration missing = MarketIntegration.discover(new StubServices(null), true);
        MarketIntegration mismatch = MarketIntegration.discover(new StubServices(new StubApi(2)), true);

        assertEquals(IntegrationAvailability.INCOMPATIBLE, missing.availability());
        assertEquals(IntegrationAvailability.INCOMPATIBLE, mismatch.availability());
        assertEquals("Market API version 2 is incompatible with required version 1", mismatch.issue());
    }

    @Test
    void typedProviderStatusMapsWithoutReflectiveModels() {
        Instant expiration = Instant.parse("2026-08-20T12:00:00Z");
        StubApi provider = new StubApi(MarketModerationApi.API_VERSION);
        provider.stalls = List.of(new MarketStallRecord(
                "stall-1",
                "market",
                "OWNED",
                new MarketOwnership(MarketOwnership.Type.SOLO, Optional.of(PLAYER_ID.toString())),
                4L,
                false,
                Optional.empty()
        ));
        provider.blacklist = Optional.of(new StallBlacklistState(
                PLAYER_ID,
                StallBlacklistState.Status.ACTIVE,
                Optional.of(expiration),
                "CASE-100",
                UUID.fromString("3d2a98dc-70af-40b6-b587-417c3decd740"),
                1L,
                Instant.parse("2026-08-13T12:00:00Z")
        ));

        MarketIntegration integration = MarketIntegration.discover(new StubServices(provider), true);
        MarketIntegration.PlayerMarketStatus status = integration.status(PLAYER_ID)
                .toCompletableFuture()
                .join();

        assertEquals(IntegrationAvailability.AVAILABLE, integration.availability());
        assertEquals("stall-1", status.stalls().getFirst().id());
        assertEquals("SOLO", status.stalls().getFirst().ownerType());
        assertEquals(expiration, status.blacklist().orElseThrow().expirationAt().orElseThrow());
    }

    private static final class StubServices implements ServicesManager {
        private final MarketModerationApi provider;

        private StubServices(MarketModerationApi provider) {
            this.provider = provider;
        }

        @Override
        public <T> void register(Class<T> service, T value, Plugin plugin, ServicePriority priority) {
        }

        @Override
        public void unregisterAll(Plugin plugin) {
        }

        @Override
        public void unregister(Class<?> service, Object value) {
        }

        @Override
        public void unregister(Object value) {
        }

        @Override
        public <T> T load(Class<T> service) {
            return provider == null ? null : service.cast(provider);
        }

        @Override
        public <T> RegisteredServiceProvider<T> getRegistration(Class<T> service) {
            return null;
        }

        @Override
        public List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin) {
            return List.of();
        }

        @Override
        public <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service) {
            return List.of();
        }

        @Override
        public Collection<Class<?>> getKnownServices() {
            return List.of();
        }

        @Override
        public <T> boolean isProvidedFor(Class<T> service) {
            return provider != null && service.isInstance(provider);
        }
    }

    private static final class StubApi implements MarketModerationApi {
        private final int version;
        private List<MarketStallRecord> stalls = List.of();
        private Optional<StallBlacklistState> blacklist = Optional.empty();

        private StubApi(int version) {
            this.version = version;
        }

        @Override
        public int apiVersion() {
            return version;
        }

        @Override
        public CompletionStage<List<MarketStallRecord>> findStalls(UUID playerId) {
            return CompletableFuture.completedFuture(stalls);
        }

        @Override
        public CompletionStage<Optional<StallBlacklistState>> getStallBlacklist(UUID playerId) {
            return CompletableFuture.completedFuture(blacklist);
        }

        @Override
        public CompletionStage<Boolean> canAcquire(UUID playerId) {
            return unsupported();
        }

        @Override
        public CompletionStage<MarketOperationResult> prepare(MarketOperationRequest request) {
            return unsupported();
        }

        @Override
        public CompletionStage<MarketOperationResult> confiscate(MarketConfiscationApproval approval) {
            return unsupported();
        }

        @Override
        public CompletionStage<MarketOperationResult> restore(MarketRestoreRequest request) {
            return unsupported();
        }

        @Override
        public CompletionStage<MarketOperationResult> release(UUID operationId, String checksum) {
            return unsupported();
        }

        @Override
        public CompletionStage<Optional<MarketOperationRecord>> findOperation(UUID operationId) {
            return unsupported();
        }

        @Override
        public CompletionStage<MarketBlacklistResult> applyBlacklist(MarketBlacklistRequest request) {
            return unsupported();
        }

        @Override
        public CompletionStage<MarketBlacklistResult> removeBlacklist(MarketBlacklistRemoval removal) {
            return unsupported();
        }

        private static <T> CompletionStage<T> unsupported() {
            return CompletableFuture.failedStage(new UnsupportedOperationException("not used by this test"));
        }
    }
}
