package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

final class VelocitySecurityEventSubmissionTest {
    private static final long TIMEOUT_SECONDS = 5L;
    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final VarHandle WORKERS = field("workers", ExecutorService.class);
    private static final VarHandle CONFIGURATION = field("configuration", VelocityConfiguration.class);
    private static final VarHandle AUTHORITY_MODE = field("authorityMode", AtomicReference.class);
    private static final VarHandle SANCTION_LOOKUP = field("sanctionLookup", SanctionLookup.class);
    private static final VarHandle INVENTORIES = field("inventoryJournalStore", InventoryJournalStore.class);
    private static final VarHandle ECONOMIES = field("economyJournalStore", EconomyJournalStore.class);
    private static final VarHandle FREEZES = field("freezeStore", FreezeStore.class);

    @TempDir
    Path tempDirectory;

    @Test
    void admittedCleanLoginUsesRealListenerAndRemainsAllowed() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            EnthusiaStaffVelocityPlugin plugin = plugin(executor);
            LoginEvent event = loginEvent(new AtomicInteger());

            await(plugin.onLogin(event));

            assertTrue(event.getResult().isAllowed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void admittedSanctionDenialUsesRealListenerResult() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            EnthusiaStaffVelocityPlugin plugin = plugin(executor);
            setMode(plugin, OperationalMode.ACTIVE);
            SANCTION_LOOKUP.set(plugin, (SanctionLookup) (playerId, types, now) -> List.of(activeBan()));
            LoginEvent event = loginEvent(new AtomicInteger());

            await(plugin.onLogin(event));

            assertFalse(event.getResult().isAllowed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void saturatedLoginUsesConfiguredFailClosedListenerCallback() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            CONFIGURATION.set(plugin, configuration(tempDirectory.resolve("closed"), true));
            AtomicInteger securityReads = new AtomicInteger();
            LoginEvent event = loginEvent(securityReads);

            EventTask task = assertDoesNotThrow(() -> plugin.onLogin(event));

            assertFalse(event.getResult().isAllowed());
            assertEquals(0, securityReads.get());
            await(task);
        }
    }

    @Test
    void saturatedLoginHonorsExplicitConfiguredFailOpenOnly() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            CONFIGURATION.set(plugin, configuration(tempDirectory.resolve("open"), false));
            AtomicInteger securityReads = new AtomicInteger();
            LoginEvent event = loginEvent(securityReads);

            EventTask task = assertDoesNotThrow(() -> plugin.onLogin(event));

            assertTrue(event.getResult().isAllowed());
            assertEquals(0, securityReads.get());
            await(task);
        }
    }

    @Test
    void loginShutdownAdmissionRaceFailsClosedThroughRealListener() throws Exception {
        EnthusiaStaffVelocityPlugin plugin = plugin(new RejectingShutdownRaceExecutor());
        AtomicInteger securityReads = new AtomicInteger();
        LoginEvent event = loginEvent(securityReads);

        EventTask task = assertDoesNotThrow(() -> plugin.onLogin(event));

        assertFalse(event.getResult().isAllowed());
        assertEquals(0, securityReads.get());
        await(task);
    }

    @Test
    void recoveredLoginCapacityAdmitsLaterRealListenerWork() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            LoginEvent rejected = loginEvent(new AtomicInteger());
            await(plugin.onLogin(rejected));
            assertFalse(rejected.getResult().isAllowed());

            saturated.recover();
            LoginEvent recovered = loginEvent(new AtomicInteger());
            await(plugin.onLogin(recovered));

            assertTrue(recovered.getResult().isAllowed());
        }
    }

    @Test
    void admittedServerSwitchRunsRealSecurityFencesAndAllowsCleanSwitch() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            EnthusiaStaffVelocityPlugin plugin = plugin(executor);
            setMode(plugin, OperationalMode.ACTIVE);
            AtomicInteger inventoryReads = installEmptySwitchStores(plugin);
            ServerPreConnectEvent event = serverSwitchEvent(new AtomicInteger());

            await(plugin.onServerPreConnect(event));

            assertTrue(event.getResult().isAllowed());
            assertEquals(1, inventoryReads.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void admittedServerSwitchSecurityDenialUsesRealEventResult() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            EnthusiaStaffVelocityPlugin plugin = plugin(executor);
            setMode(plugin, OperationalMode.ACTIVE);
            installEmptySwitchStores(plugin);
            AtomicInteger inventoryReads = new AtomicInteger();
            INVENTORIES.set(plugin, optionalStore(InventoryJournalStore.class, inventoryReads, Optional.of("owner")));
            AtomicInteger messages = new AtomicInteger();
            ServerPreConnectEvent event = serverSwitchEvent(messages);

            await(plugin.onServerPreConnect(event));

            assertFalse(event.getResult().isAllowed());
            assertEquals(1, inventoryReads.get());
            assertEquals(1, messages.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void saturatedServerSwitchFailsClosedOnceWithoutStartingSecurityWork() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            AtomicInteger inventoryReads = installEmptySwitchStores(plugin);
            AtomicInteger messages = new AtomicInteger();
            ServerPreConnectEvent event = serverSwitchEvent(messages);

            EventTask task = assertDoesNotThrow(() -> plugin.onServerPreConnect(event));

            assertFalse(event.getResult().isAllowed());
            assertEquals(0, inventoryReads.get());
            assertEquals(1, messages.get());
            await(task);
        }
    }

    @Test
    void serverSwitchShutdownAdmissionRaceFailsClosedThroughRealListener() throws Exception {
        EnthusiaStaffVelocityPlugin plugin = plugin(new RejectingShutdownRaceExecutor());
        AtomicInteger inventoryReads = installEmptySwitchStores(plugin);
        AtomicInteger messages = new AtomicInteger();
        ServerPreConnectEvent event = serverSwitchEvent(messages);

        EventTask task = assertDoesNotThrow(() -> plugin.onServerPreConnect(event));

        assertFalse(event.getResult().isAllowed());
        assertEquals(0, inventoryReads.get());
        assertEquals(1, messages.get());
        await(task);
    }

    @Test
    void recoveredServerSwitchCapacityAdmitsLaterRealListenerWork() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            setMode(plugin, OperationalMode.ACTIVE);
            installEmptySwitchStores(plugin);
            ServerPreConnectEvent rejected = serverSwitchEvent(new AtomicInteger());
            await(plugin.onServerPreConnect(rejected));
            assertFalse(rejected.getResult().isAllowed());

            saturated.recover();
            ServerPreConnectEvent recovered = serverSwitchEvent(new AtomicInteger());
            await(plugin.onServerPreConnect(recovered));

            assertTrue(recovered.getResult().isAllowed());
        }
    }

    @Test
    void lifecycleShutdownRefusesNewLoginAndServerSwitchWork() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        EnthusiaStaffVelocityPlugin plugin = plugin(executor);
        AtomicInteger inventoryReads = installEmptySwitchStores(plugin);
        plugin.onProxyShutdown(new ProxyShutdownEvent());
        LoginEvent login = loginEvent(new AtomicInteger());
        AtomicInteger messages = new AtomicInteger();
        ServerPreConnectEvent serverSwitch = serverSwitchEvent(messages);

        await(plugin.onLogin(login));
        await(plugin.onServerPreConnect(serverSwitch));

        assertFalse(login.getResult().isAllowed());
        assertFalse(serverSwitch.getResult().isAllowed());
        assertEquals(0, inventoryReads.get());
        assertEquals(1, messages.get());
    }

    private static EnthusiaStaffVelocityPlugin plugin(ExecutorService executor) {
        EnthusiaStaffVelocityPlugin plugin = new EnthusiaStaffVelocityPlugin(
                interfaceProxy(ProxyServer.class),
                interfaceProxy(Logger.class),
                Path.of(".")
        );
        WORKERS.set(plugin, executor);
        return plugin;
    }

    private static void setMode(EnthusiaStaffVelocityPlugin plugin, OperationalMode mode) {
        @SuppressWarnings("unchecked")
        AtomicReference<OperationalMode> authority = (AtomicReference<OperationalMode>) AUTHORITY_MODE.get(plugin);
        authority.set(mode);
    }

    private static AtomicInteger installEmptySwitchStores(EnthusiaStaffVelocityPlugin plugin) {
        AtomicInteger inventoryReads = new AtomicInteger();
        INVENTORIES.set(plugin, optionalStore(InventoryJournalStore.class, inventoryReads, Optional.empty()));
        ECONOMIES.set(plugin, optionalStore(EconomyJournalStore.class, new AtomicInteger(), Optional.empty()));
        FREEZES.set(plugin, optionalStore(FreezeStore.class, new AtomicInteger(), Optional.empty()));
        return inventoryReads;
    }

    private static VelocityConfiguration configuration(Path directory, boolean failClosed) throws Exception {
        VelocityConfiguration.load(directory);
        Path file = directory.resolve("config.properties");
        String configured = Files.readString(file).replace(
                "enforcement.fail-closed-while-active=true",
                "enforcement.fail-closed-while-active=" + failClosed
        );
        Files.writeString(file, configured);
        return VelocityConfiguration.load(directory);
    }

    private static ActiveSanction activeBan() {
        return new ActiveSanction(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                new CaseId("01ARZ3NDEKTSV4RR"),
                PLAYER_ID,
                SanctionType.BAN,
                "Regression test ban",
                Instant.parse("2026-09-20T00:00:00Z"),
                Optional.empty(),
                Optional.empty()
        );
    }

    private static LoginEvent loginEvent(AtomicInteger securityReads) {
        return new LoginEvent(player(securityReads, new AtomicInteger()));
    }

    private static ServerPreConnectEvent serverSwitchEvent(AtomicInteger messages) {
        Player player = player(new AtomicInteger(), messages);
        RegisteredServer target = server("target");
        RegisteredServer previous = server("previous");
        return new ServerPreConnectEvent(player, target, previous);
    }

    private static Player player(AtomicInteger securityReads, AtomicInteger messages) {
        return Player.class.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) {
                        securityReads.incrementAndGet();
                        return PLAYER_ID;
                    }
                    if (method.getName().equals("sendMessage")) {
                        messages.incrementAndGet();
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        ));
    }

    private static RegisteredServer server(String name) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return RegisteredServer.class.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, arguments) -> method.getName().equals("getServerInfo")
                        ? info
                        : defaultValue(method.getReturnType())
        ));
    }

    private static <T> T optionalStore(Class<T> type, AtomicInteger calls, Optional<?> result) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getReturnType() == Optional.class) {
                        calls.incrementAndGet();
                        return result;
                    }
                    return defaultValue(method.getReturnType());
                }
        ));
    }

    private static VarHandle field(String name, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(EnthusiaStaffVelocityPlugin.class, MethodHandles.lookup())
                    .findVarHandle(EnthusiaStaffVelocityPlugin.class, name, type);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void await(EventTask task) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        task.execute(new Continuation() {
            @Override
            public void resume() {
                completed.countDown();
            }

            @Override
            public void resumeWithException(Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
            }
        });
        assertTrue(completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "event task did not complete");
        assertNull(failure.get(), "event task completed exceptionally");
    }

    private static <T> T interfaceProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> defaultValue(method.getReturnType())
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return numericDefault(type);
    }

    private static Object numericDefault(Class<?> type) {
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private record SaturatedExecutor(
            ThreadPoolExecutor executor,
            CountDownLatch release,
            CountDownLatch queuedCompleted
    ) implements AutoCloseable {
        private static SaturatedExecutor create() throws InterruptedException {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy()
            );
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch queuedCompleted = new CountDownLatch(1);
            executor.execute(() -> {
                running.countDown();
                awaitUnchecked(release);
            });
            assertTrue(running.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "worker did not start");
            executor.execute(queuedCompleted::countDown);
            return new SaturatedExecutor(executor, release, queuedCompleted);
        }

        private void recover() throws InterruptedException {
            release.countDown();
            assertTrue(queuedCompleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "queued worker did not drain");
        }

        @Override
        public void close() {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RejectingShutdownRaceExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            shutdown = true;
            throw new RejectedExecutionException("shutdown won submission race");
        }
    }
}
