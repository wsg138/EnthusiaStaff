package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
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
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

final class VelocitySecurityEventSubmissionTest {
    private static final long TIMEOUT_SECONDS = 5L;

    @Test
    void admittedCleanLoginRemainsAllowed() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            VelocitySecurityEventDispatcher dispatcher = dispatcher(executor);
            LoginEvent event = loginEvent();

            await(dispatcher.submit(() -> {
            }, () -> denyLogin(event)));

            assertTrue(event.getResult().isAllowed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void admittedSanctionCheckCanStillDenyLogin() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            VelocitySecurityEventDispatcher dispatcher = dispatcher(executor);
            LoginEvent event = loginEvent();

            await(dispatcher.submit(() -> event.setResult(ResultedEvent.ComponentResult.denied(
                    Component.text("Network access denied"))), () -> denyLogin(event)));

            assertFalse(event.getResult().isAllowed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void saturatedLoginExecutorFailsClosedWithoutEscapingRejection() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            VelocitySecurityEventDispatcher dispatcher = dispatcher(saturated.executor());
            LoginEvent event = loginEvent();

            EventTask task = assertDoesNotThrow(() -> dispatcher.submit(
                    () -> {
                    },
                    () -> denyLogin(event)
            ));

            assertFalse(event.getResult().isAllowed());
            await(task);
        }
    }

    @Test
    void saturatedLoginRejectionCanPreserveConfiguredFailOpenCallback() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            VelocitySecurityEventDispatcher dispatcher = dispatcher(saturated.executor());
            LoginEvent event = loginEvent();

            await(dispatcher.submit(() -> {
            }, () -> {
            }));

            assertTrue(event.getResult().isAllowed());
        }
    }

    @Test
    void saturatedServerSwitchExecutorCannotLeaveDefaultAllowedResult() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            VelocitySecurityEventDispatcher dispatcher = dispatcher(saturated.executor());
            ServerPreConnectEvent event = serverSwitchEvent();
            assertTrue(event.getResult().isAllowed());

            EventTask task = assertDoesNotThrow(() -> dispatcher.submit(
                    () -> {
                    },
                    () -> denySwitch(event)
            ));

            assertFalse(event.getResult().isAllowed());
            await(task);
        }
    }

    @Test
    void submissionShutdownRaceFailsClosedWithoutHandlerException() throws Exception {
        VelocitySecurityEventDispatcher dispatcher = dispatcher(new RejectingShutdownRaceExecutor());
        LoginEvent event = loginEvent();

        EventTask task = assertDoesNotThrow(() -> dispatcher.submit(
                () -> {
                },
                () -> denyLogin(event)
        ));

        assertFalse(event.getResult().isAllowed());
        await(task);
    }

    @Test
    void capacityRecoveryAdmitsFreshSecurityChecks() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            VelocitySecurityEventDispatcher dispatcher = dispatcher(saturated.executor());
            LoginEvent rejected = loginEvent();
            dispatcher.submit(() -> {
            }, () -> denyLogin(rejected));
            assertFalse(rejected.getResult().isAllowed());

            saturated.recover();
            AtomicInteger admitted = new AtomicInteger();
            LoginEvent recoveredLogin = loginEvent();
            ServerPreConnectEvent recoveredSwitch = serverSwitchEvent();
            await(dispatcher.submit(admitted::incrementAndGet, () -> denyLogin(recoveredLogin)));
            await(dispatcher.submit(admitted::incrementAndGet, () -> denySwitch(recoveredSwitch)));

            assertEquals(2, admitted.get());
            assertTrue(recoveredLogin.getResult().isAllowed());
            assertTrue(recoveredSwitch.getResult().isAllowed());
        }
    }

    @Test
    void rejectedAdmissionDoesNotStartSecurityPersistenceWork() throws Exception {
        try (SaturatedExecutor saturated = SaturatedExecutor.create()) {
            VelocitySecurityEventDispatcher dispatcher = dispatcher(saturated.executor());
            AtomicInteger persistenceStarts = new AtomicInteger();
            LoginEvent event = loginEvent();

            await(dispatcher.submit(persistenceStarts::incrementAndGet, () -> denyLogin(event)));

            assertEquals(0, persistenceStarts.get());
            assertFalse(event.getResult().isAllowed());
        }
    }

    @Test
    void shutdownLifecycleRejectsWithoutSubmitting() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            VelocitySecurityEventDispatcher dispatcher = new VelocitySecurityEventDispatcher(() -> executor, () -> true);
            AtomicInteger operations = new AtomicInteger();
            LoginEvent event = loginEvent();

            await(dispatcher.submit(operations::incrementAndGet, () -> denyLogin(event)));

            assertEquals(0, operations.get());
            assertFalse(event.getResult().isAllowed());
        } finally {
            executor.shutdownNow();
        }
    }

    private static VelocitySecurityEventDispatcher dispatcher(ExecutorService executor) {
        return new VelocitySecurityEventDispatcher(() -> executor, () -> false);
    }

    private static LoginEvent loginEvent() {
        return new LoginEvent(interfaceProxy(Player.class));
    }

    private static ServerPreConnectEvent serverSwitchEvent() {
        return new ServerPreConnectEvent(interfaceProxy(Player.class), interfaceProxy(RegisteredServer.class));
    }

    private static void denyLogin(LoginEvent event) {
        event.setResult(ResultedEvent.ComponentResult.denied(Component.text("temporarily unavailable")));
    }

    private static void denySwitch(ServerPreConnectEvent event) {
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
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
                (proxy, method, arguments) -> primitiveDefault(method.getReturnType())
        ));
    }

    private static Object primitiveDefault(Class<?> type) {
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
        return 0;
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
