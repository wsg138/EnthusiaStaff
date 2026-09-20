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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
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
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

final class VelocitySecurityEventSubmissionTest {
    private static final long TIMEOUT_SECONDS = 5L;

    @Test
    void admittedLoginWorkRunsAndCleanBootstrapLoginRemainsAllowed() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            EnthusiaStaffVelocityPlugin plugin = plugin(executor);
            LoginEvent event = new LoginEvent(player(UUID.randomUUID()));

            await(plugin.onLogin(event));

            assertTrue(event.getResult().isAllowed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void admittedActiveBanStillDeniesLogin() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            UUID playerId = UUID.randomUUID();
            EnthusiaStaffVelocityPlugin plugin = plugin(executor);
            setAuthority(plugin, OperationalMode.ACTIVE);
            setField(plugin, "sanctionLookup", (SanctionLookup) (ignoredId, ignoredTypes, ignoredNow) ->
                    List.of(activeBan(playerId)));
            LoginEvent event = new LoginEvent(player(playerId));

            await(plugin.onLogin(event));

            assertFalse(event.getResult().isAllowed());
            assertTrue(event.getResult().getReasonComponent().orElseThrow().toString().contains("Network access denied"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void saturatedLoginExecutorFailsClosedWithoutEscapingRejection() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            LoginEvent event = new LoginEvent(player(UUID.randomUUID()));

            EventTask task = assertDoesNotThrow(() -> plugin.onLogin(event));

            assertFalse(event.getResult().isAllowed());
            await(task);
        }
    }

    @Test
    void saturatedLoginRejectionUsesConfiguredFailOpenSemantics(@TempDir Path directory) throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            setField(plugin, "configuration", configuration(directory, false));
            LoginEvent event = new LoginEvent(player(UUID.randomUUID()));

            EventTask task = assertDoesNotThrow(() -> plugin.onLogin(event));

            assertTrue(event.getResult().isAllowed());
            await(task);
        }
    }

    @Test
    void saturatedServerSwitchExecutorCannotLeaveDefaultAllowedResult() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            ServerPreConnectEvent event = new ServerPreConnectEvent(
                    player(UUID.randomUUID()),
                    registeredServer()
            );
            assertTrue(event.getResult().isAllowed());

            EventTask task = assertDoesNotThrow(() -> plugin.onServerPreConnect(event));

            assertFalse(event.getResult().isAllowed());
            await(task);
        }
    }

    @Test
    void submissionShutdownRaceFailsClosedWithoutHandlerException() throws Exception {
        ExecutorService executor = new RejectingShutdownRaceExecutor();
        EnthusiaStaffVelocityPlugin plugin = plugin(executor);
        LoginEvent event = new LoginEvent(player(UUID.randomUUID()));

        EventTask task = assertDoesNotThrow(() -> plugin.onLogin(event));

        assertFalse(event.getResult().isAllowed());
        await(task);
    }

    @Test
    void capacityRecoveryAllowsFreshLoginAndServerSwitchChecks() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            LoginEvent rejectedLogin = new LoginEvent(player(UUID.randomUUID()));
            ServerPreConnectEvent rejectedSwitch = new ServerPreConnectEvent(
                    player(UUID.randomUUID()), registeredServer());

            plugin.onLogin(rejectedLogin);
            plugin.onServerPreConnect(rejectedSwitch);
            assertFalse(rejectedLogin.getResult().isAllowed());
            assertFalse(rejectedSwitch.getResult().isAllowed());

            saturated.recover();
            LoginEvent recoveredLogin = new LoginEvent(player(UUID.randomUUID()));
            ServerPreConnectEvent recoveredSwitch = new ServerPreConnectEvent(
                    player(UUID.randomUUID()), registeredServer());
            await(plugin.onLogin(recoveredLogin));
            await(plugin.onServerPreConnect(recoveredSwitch));

            assertTrue(recoveredLogin.getResult().isAllowed());
            assertTrue(recoveredSwitch.getResult().isAllowed());
        }
    }

    @Test
    void rejectedLoginSubmissionDoesNotStartPlayerPersistence(@TempDir Path directory) throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            AtomicInteger recordSeenCalls = new AtomicInteger();
            EnthusiaStaffVelocityPlugin plugin = plugin(saturated.executor());
            setField(plugin, "configuration", configuration(directory, true));
            setField(plugin, "playerDirectory", playerDirectory(recordSeenCalls));
            LoginEvent event = new LoginEvent(player(UUID.randomUUID()));

            plugin.onLogin(event);

            assertFalse(event.getResult().isAllowed());
            assertEquals(0, recordSeenCalls.get());
        }
    }

    private static EnthusiaStaffVelocityPlugin plugin(ExecutorService executor) throws Exception {
        EnthusiaStaffVelocityPlugin plugin = new EnthusiaStaffVelocityPlugin(
                interfaceProxy(ProxyServer.class),
                interfaceProxy(Logger.class),
                Path.of(".")
        );
        setField(plugin, "workers", executor);
        return plugin;
    }

    private static Player player(UUID playerId) {
        return proxy(Player.class, (method, ignored) -> switch (method.getName()) {
            case "getUniqueId" -> playerId;
            case "getUsername" -> "R08Player";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static RegisteredServer registeredServer() {
        return interfaceProxy(RegisteredServer.class);
    }

    private static PlayerDirectory playerDirectory(AtomicInteger recordSeenCalls) {
        return proxy(PlayerDirectory.class, (method, ignored) -> {
            if (method.getName().equals("recordSeen")) {
                recordSeenCalls.incrementAndGet();
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ActiveSanction activeBan(UUID playerId) {
        return new ActiveSanction(
                UUID.randomUUID(),
                new CaseId("0123456789ABCDEF"),
                playerId,
                SanctionType.BAN,
                "Regression ban",
                Instant.EPOCH,
                Optional.empty(),
                Optional.empty()
        );
    }

    private static VelocityConfiguration configuration(Path directory, boolean failClosed) throws IOException {
        VelocityConfiguration.load(directory);
        Path file = directory.resolve("config.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        properties.setProperty("enforcement.fail-closed-while-active", Boolean.toString(failClosed));
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "R08-004 test");
        }
        return VelocityConfiguration.load(directory);
    }

    private static void setAuthority(EnthusiaStaffVelocityPlugin plugin, OperationalMode mode) throws Exception {
        Field field = EnthusiaStaffVelocityPlugin.class.getDeclaredField("authorityMode");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<OperationalMode> reference = (AtomicReference<OperationalMode>) field.get(plugin);
        reference.set(mode);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
        return proxy(type, (method, ignored) -> defaultValue(method.getReturnType()));
    }

    private static <T> T proxy(Class<T> type, MethodAnswer answer) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (ignoredProxy, method, arguments) -> answer.answer(method, arguments)
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) {
            return null;
        }
        if (!type.isPrimitive()) {
            if (type == Optional.class) {
                return Optional.empty();
            }
            if (type == List.class) {
                return List.of();
            }
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
        if (type == double.class) {
            return 0.0D;
        }
        throw new IllegalArgumentException("Unsupported primitive " + type.getName());
    }

    @FunctionalInterface
    private interface MethodAnswer {
        Object answer(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }

    private record SaturatedExecutor(
            ThreadPoolExecutor executor,
            CountDownLatch release,
            CountDownLatch queuedCompleted
    ) implements AutoCloseable {
        private static SaturatedExecutor create() throws InterruptedException {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1),
                    new ThreadPoolExecutor.AbortPolicy()
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
            assertTrue(
                    queuedCompleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "queued worker did not drain"
            );
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
