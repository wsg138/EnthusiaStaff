package net.enthusia.staff.paper.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import org.junit.jupiter.api.Test;

class PunishmentRequestAlertControllerTest {
    private static final String OWNER = "paper:SMP:plugin-instance";

    @Test
    void disabledToEnabledStartsExactlyOneLifecycleWithStableOwner() {
        Factory factory = new Factory();
        AtomicReference<PunishmentRequestAlertController.Status> status = new AtomicReference<>();
        PunishmentRequestAlertController controller = controller(disabled(), factory, status::set);

        PunishmentRequestAlertController.ApplyResult attached = controller.attachStorage(storage());
        PunishmentRequestAlertController.ApplyResult enabled = controller.apply(enabled(10));

        assertEquals(PunishmentRequestAlertController.Outcome.NO_CHANGES, attached.outcome());
        assertEquals(PunishmentRequestAlertController.Outcome.ENABLED, enabled.outcome());
        assertTrue(controller.active());
        assertEquals(1, factory.created.size());
        assertEquals(OWNER, factory.owners.getFirst());
        assertEquals(PunishmentRequestAlertController.State.ACTIVE, status.get().state());
    }

    @Test
    void enabledToDisabledClosesTheLifecycleWithoutDeletingStorage() {
        Factory factory = new Factory();
        PunishmentRequestAlertController controller = controller(enabled(10), factory, ignored -> { });
        PunishmentRequestAlertController.Storage storage = storage();
        controller.attachStorage(storage);

        PunishmentRequestAlertController.ApplyResult result = controller.apply(disabled());

        assertEquals(PunishmentRequestAlertController.Outcome.DISABLED, result.outcome());
        assertFalse(controller.active());
        assertEquals(1, factory.created.getFirst().closed.get());
        assertSame(storage, factory.storages.getFirst());
    }

    @Test
    void changedEnabledSettingsReplaceOneLifecycleAndReuseOwner() {
        Factory factory = new Factory();
        PunishmentRequestAlertController controller = controller(enabled(10), factory, ignored -> { });
        controller.attachStorage(storage());

        PunishmentRequestAlertController.ApplyResult result = controller.apply(enabled(20));

        assertEquals(PunishmentRequestAlertController.Outcome.REPLACED, result.outcome());
        assertEquals(2, factory.created.size());
        assertEquals(List.of(OWNER, OWNER), factory.owners);
        assertEquals(1, factory.created.get(0).closed.get());
        assertEquals(0, factory.created.get(1).closed.get());
        assertTrue(controller.active());
    }

    @Test
    void unchangedSettingsDoNotRestart() {
        Factory factory = new Factory();
        PunishmentRequestAlertWorkerSettings settings = enabled(10);
        PunishmentRequestAlertController controller = controller(settings, factory, ignored -> { });
        controller.attachStorage(storage());

        PunishmentRequestAlertController.ApplyResult result = controller.apply(settings);

        assertEquals(PunishmentRequestAlertController.Outcome.NO_CHANGES, result.outcome());
        assertEquals(1, factory.created.size());
    }

    @Test
    void failedReplacementRestoresPreviousSettingsAndLifecycle() {
        Factory factory = new Factory();
        factory.startResults.add(true);
        factory.startResults.add(false);
        factory.startResults.add(true);
        PunishmentRequestAlertWorkerSettings original = enabled(10);
        PunishmentRequestAlertController controller = controller(original, factory, ignored -> { });
        controller.attachStorage(storage());

        PunishmentRequestAlertController.ApplyResult result = controller.apply(enabled(20));

        assertEquals(PunishmentRequestAlertController.Outcome.RESTORED, result.outcome());
        assertEquals(original, controller.currentSettings());
        assertTrue(controller.active());
        assertEquals(3, factory.created.size());
        assertEquals(PunishmentRequestAlertController.State.RESTORED_AFTER_FAILURE,
                controller.currentStatus().state());
    }

    @Test
    void failedReplacementAndRollbackLeaveOnlyAlertsUnavailable() {
        Factory factory = new Factory();
        factory.startResults.add(true);
        factory.startResults.add(false);
        factory.startResults.add(false);
        PunishmentRequestAlertWorkerSettings candidate = enabled(20);
        PunishmentRequestAlertController controller = controller(enabled(10), factory, ignored -> { });
        controller.attachStorage(storage());

        PunishmentRequestAlertController.ApplyResult result = controller.apply(candidate);

        assertEquals(PunishmentRequestAlertController.Outcome.UNAVAILABLE, result.outcome());
        assertEquals(candidate, controller.currentSettings());
        assertFalse(controller.active());
        assertEquals(PunishmentRequestAlertController.State.UNAVAILABLE_AFTER_FAILURE,
                controller.currentStatus().state());
    }

    @Test
    void reloadBeforeStorageUsesLatestDesiredSettingsWhenStorageArrives() {
        Factory factory = new Factory();
        PunishmentRequestAlertController controller = controller(enabled(10), factory, ignored -> { });

        PunishmentRequestAlertController.ApplyResult pending = controller.apply(enabled(20));
        PunishmentRequestAlertController.ApplyResult attached = controller.attachStorage(storage());

        assertEquals(PunishmentRequestAlertController.Outcome.WAITING_FOR_STORAGE, pending.outcome());
        assertEquals(PunishmentRequestAlertController.Outcome.ENABLED, attached.outcome());
        assertEquals(enabled(20), factory.settings.getFirst());
        assertEquals(1, factory.created.size());
    }

    @Test
    void shutdownWinsAndCloseIsIdempotent() {
        Factory factory = new Factory();
        PunishmentRequestAlertController controller = controller(enabled(10), factory, ignored -> { });
        controller.attachStorage(storage());

        controller.close();
        controller.close();
        PunishmentRequestAlertController.ApplyResult result = controller.apply(enabled(20));

        assertEquals(PunishmentRequestAlertController.Outcome.SHUTTING_DOWN, result.outcome());
        assertEquals(1, factory.created.getFirst().closed.get());
        assertFalse(controller.active());
    }


    @Test
    void shutdownDuringReplacementPublishesNoCandidate() throws InterruptedException {
        BlockingFactory factory = new BlockingFactory();
        PunishmentRequestAlertController controller = controller(enabled(10), factory, ignored -> { });
        controller.attachStorage(storage());
        AtomicReference<PunishmentRequestAlertController.ApplyResult> result = new AtomicReference<>();

        Thread reload = Thread.ofPlatform().start(() -> result.set(controller.apply(enabled(20))));
        assertTrue(factory.replacementEntered.await(5, TimeUnit.SECONDS));
        Thread shutdown = Thread.ofPlatform().start(controller::close);
        assertTrue(awaitState(shutdown, Thread.State.BLOCKED, Duration.ofSeconds(5)));
        factory.releaseReplacement.countDown();
        reload.join();
        shutdown.join();

        assertEquals(PunishmentRequestAlertController.Outcome.SHUTTING_DOWN, result.get().outcome());
        assertFalse(controller.active());
        assertEquals(1, factory.replacement.closed.get());
    }

    @Test
    void detachClosesCurrentLifecycleAndWaitsForStorageWithoutCreatingDuplicates() {
        Factory factory = new Factory();
        PunishmentRequestAlertController controller = controller(enabled(10), factory, ignored -> { });
        controller.attachStorage(storage());

        controller.detachStorage();
        controller.attachStorage(storage());

        assertEquals(2, factory.created.size());
        assertEquals(1, factory.created.getFirst().closed.get());
        assertTrue(controller.active());
    }

    private static boolean awaitState(
            Thread thread,
            Thread.State expected,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return true;
            }
            Thread.sleep(1L);
        }
        return thread.getState() == expected;
    }

    private static PunishmentRequestAlertController controller(
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertController.LifecycleFactory factory,
            java.util.function.Consumer<PunishmentRequestAlertController.Status> status
    ) {
        return new PunishmentRequestAlertController(OWNER, settings, factory, status);
    }

    private static PunishmentRequestAlertController.Storage storage() {
        return new PunishmentRequestAlertController.Storage(
                proxy(PunishmentRequestAlertStore.class),
                proxy(PunishmentRequestStore.class),
                proxy(PlayerDirectory.class)
        );
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> defaultValue(method.getReturnType())
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
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

    private static PunishmentRequestAlertWorkerSettings disabled() {
        return PunishmentRequestAlertWorkerSettings.safeDefaults(false);
    }

    private static PunishmentRequestAlertWorkerSettings enabled(long pollSeconds) {
        PunishmentRequestAlertWorkerSettings defaults = PunishmentRequestAlertWorkerSettings.safeDefaults(true);
        return new PunishmentRequestAlertWorkerSettings(
                true,
                Duration.ofSeconds(pollSeconds),
                defaults.recipientLimit(),
                defaults.directBatch(),
                defaults.reviewerBatch(),
                defaults.operationalBatch(),
                defaults.totalClaimLimit(),
                defaults.presentationLimit(),
                Duration.ofSeconds(Math.max(45, pollSeconds * 2 + 5)),
                defaults.maximumAttempts(),
                defaults.retryBase(),
                defaults.retryMaximum(),
                defaults.joinDelay(),
                defaults.requestExpirationInterval(),
                defaults.intentExpirationInterval(),
                defaults.leaseReclaimInterval(),
                defaults.retentionInterval(),
                defaults.requestExpirationBatch(),
                defaults.intentExpirationBatch(),
                defaults.leaseReclaimBatch(),
                defaults.retentionBatch(),
                defaults.retentionDuration()
        );
    }

    private static final class Factory implements PunishmentRequestAlertController.LifecycleFactory {
        private final Deque<Boolean> startResults = new ArrayDeque<>();
        private final List<FakeLifecycle> created = new ArrayList<>();
        private final List<String> owners = new ArrayList<>();
        private final List<PunishmentRequestAlertWorkerSettings> settings = new ArrayList<>();
        private final List<PunishmentRequestAlertController.Storage> storages = new ArrayList<>();

        @Override
        public PunishmentRequestAlertManagedLifecycle create(
                String owner,
                PunishmentRequestAlertWorkerSettings settings,
                PunishmentRequestAlertController.Storage storage
        ) {
            owners.add(owner);
            this.settings.add(settings);
            storages.add(storage);
            FakeLifecycle lifecycle = new FakeLifecycle(startResults.isEmpty() || startResults.removeFirst());
            created.add(lifecycle);
            return lifecycle;
        }
    }



    private static final class BlockingFactory implements PunishmentRequestAlertController.LifecycleFactory {
        private final CountDownLatch replacementEntered = new CountDownLatch(1);
        private final CountDownLatch releaseReplacement = new CountDownLatch(1);
        private int creations;
        private FakeLifecycle replacement;

        @Override
        public PunishmentRequestAlertManagedLifecycle create(
                String owner,
                PunishmentRequestAlertWorkerSettings settings,
                PunishmentRequestAlertController.Storage storage
        ) {
            creations++;
            if (creations == 1) {
                return new FakeLifecycle(true);
            }
            replacement = new FakeLifecycle(true) {
                @Override
                public boolean start() {
                    replacementEntered.countDown();
                    try {
                        if (!releaseReplacement.await(5, TimeUnit.SECONDS)) {
                            return false;
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return super.start();
                }
            };
            return replacement;
        }
    }

    private static class FakeLifecycle implements PunishmentRequestAlertManagedLifecycle {
        private final boolean starts;
        private final AtomicInteger closed = new AtomicInteger();
        private boolean active;

        private FakeLifecycle(boolean starts) {
            this.starts = starts;
        }

        @Override
        public boolean start() {
            active = starts;
            return starts;
        }

        @Override
        public boolean active() {
            return active && closed.get() == 0;
        }

        @Override
        public void close() {
            closed.incrementAndGet();
            active = false;
        }
    }
}
