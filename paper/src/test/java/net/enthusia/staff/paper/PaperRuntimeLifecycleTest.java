package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PaperRuntimeLifecycleTest {
    private static final String STORAGE = "storage";

    @Test
    void emptyLifecycleHasNoHandlesOrStorageProjection() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();

        assertFalse(lifecycle.stopping());
        assertTrue(lifecycle.storage().isEmpty());
        assertNull(lifecycle.storageValue(String::toUpperCase));
        assertTrue(lifecycle.removeStorage().isEmpty());
        assertTrue(lifecycle.removeTask().isEmpty());
        assertTrue(lifecycle.removeChannel().isEmpty());
    }

    @Test
    void removesPublishedHandlesExactlyOnce() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();

        assertTrue(lifecycle.publishStorage(STORAGE));
        assertTrue(lifecycle.publishTask("task"));
        assertTrue(lifecycle.publishChannel("channel"));
        assertFalse(lifecycle.publishStorage("replacement"));
        assertFalse(lifecycle.publishTask("replacement"));
        assertFalse(lifecycle.publishChannel("replacement"));
        assertEquals("STORAGE", lifecycle.storageValue(String::toUpperCase));
        assertEquals("task", lifecycle.removeTask().orElseThrow());
        assertEquals("channel", lifecycle.removeChannel().orElseThrow());
        assertTrue(lifecycle.removeTask().isEmpty());
        assertTrue(lifecycle.removeChannel().isEmpty());
        assertTrue(lifecycle.removeStorageIf("other"::equals).isEmpty());
        assertEquals(STORAGE, lifecycle.removeStorageIf(STORAGE::equals).orElseThrow());
        assertTrue(lifecycle.removeStorage().isEmpty());
    }

    @Test
    void removedHandlesCanBeReplacedWhileStillRunning() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();

        assertTrue(lifecycle.publishStorage("first-storage"));
        assertTrue(lifecycle.publishTask("first-task"));
        assertTrue(lifecycle.publishChannel("first-channel"));
        assertEquals("first-storage", lifecycle.removeStorage().orElseThrow());
        assertEquals("first-task", lifecycle.removeTask().orElseThrow());
        assertEquals("first-channel", lifecycle.removeChannel().orElseThrow());

        assertTrue(lifecycle.publishStorage("second-storage"));
        assertTrue(lifecycle.publishTask("second-task"));
        assertTrue(lifecycle.publishChannel("second-channel"));
        assertEquals("second-storage", lifecycle.storage().orElseThrow());
    }

    @Test
    void runIfRunningExecutesExactlyWhileLifecycleIsRunning() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();
        AtomicInteger executions = new AtomicInteger();

        assertTrue(lifecycle.runIfRunning(executions::incrementAndGet));
        lifecycle.beginShutdown(() -> { });
        assertFalse(lifecycle.runIfRunning(executions::incrementAndGet));

        assertEquals(1, executions.get());
    }

    @Test
    void shutdownTransitionSeesStoppingStateAndCanDrainPublishedHandles() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();
        assertTrue(lifecycle.publishStorage(STORAGE));
        assertTrue(lifecycle.publishTask("task"));
        assertTrue(lifecycle.publishChannel("channel"));

        lifecycle.beginShutdown(() -> {
            assertTrue(lifecycle.stopping());
            assertFalse(lifecycle.publishStorage("late-storage"));
            assertFalse(lifecycle.publishTask("late-task"));
            assertFalse(lifecycle.publishChannel("late-channel"));
            assertEquals(STORAGE, lifecycle.removeStorage().orElseThrow());
            assertEquals("task", lifecycle.removeTask().orElseThrow());
            assertEquals("channel", lifecycle.removeChannel().orElseThrow());
        });

        assertTrue(lifecycle.storage().isEmpty());
    }

    @Test
    void shutdownPreventsLatePublicationAndModeChanges() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();
        AtomicBoolean transitioned = new AtomicBoolean();
        AtomicBoolean lateAction = new AtomicBoolean();

        lifecycle.beginShutdown(() -> transitioned.set(true));

        assertTrue(lifecycle.stopping());
        assertTrue(transitioned.get());
        assertFalse(lifecycle.publishStorage(STORAGE));
        assertFalse(lifecycle.publishTask("task"));
        assertFalse(lifecycle.publishChannel("channel"));
        assertFalse(lifecycle.runIfRunning(() -> lateAction.set(true)));
        assertFalse(lateAction.get());
    }

    @Test
    void concurrentStoragePublicationAcceptsExactlyOneHandle() throws InterruptedException {
        int workers = 24;
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<CompletableFuture<Boolean>> attempts = IntStream.range(0, workers)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return lifecycle.publishStorage("storage-" + index);
                    }, executor))
                    .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            release.countDown();

            long accepted = attempts.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();
            assertEquals(1, accepted);
            assertTrue(lifecycle.storage().isPresent());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
