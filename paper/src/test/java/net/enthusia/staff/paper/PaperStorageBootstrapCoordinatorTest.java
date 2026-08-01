package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PaperStorageBootstrapCoordinatorTest {
    @Test
    void opensAsynchronouslyCompletesSynchronouslyAndFollowsUpAsynchronously() {
        AtomicBoolean stopping = new AtomicBoolean();
        List<Runnable> asynchronous = new ArrayList<>();
        List<Runnable> synchronous = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        PaperStorageBootstrapCoordinator<String> coordinator = coordinator(
                stopping,
                asynchronous,
                synchronous,
                calls,
                ignored -> {
                },
                ignored -> {
                }
        );

        coordinator.start();
        assertEquals(1, asynchronous.size());
        assertEquals(List.of(), calls);

        asynchronous.removeFirst().run();
        assertEquals(List.of("open", "publish"), calls);
        assertEquals(1, synchronous.size());

        synchronous.removeFirst().run();
        assertEquals(List.of("open", "publish", "synchronous"), calls);
        assertEquals(1, asynchronous.size());

        asynchronous.removeFirst().run();
        assertEquals(List.of("open", "publish", "synchronous", "follow-up"), calls);
    }

    @Test
    void shutdownAfterOpenBeforeSynchronousCompletionDiscardsPublishedStorageOnce() {
        AtomicBoolean stopping = new AtomicBoolean();
        List<Runnable> asynchronous = new ArrayList<>();
        List<Runnable> synchronous = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        AtomicInteger discarded = new AtomicInteger();
        PaperStorageBootstrapCoordinator<String> coordinator = coordinator(
                stopping,
                asynchronous,
                synchronous,
                calls,
                ignored -> discarded.incrementAndGet(),
                ignored -> {
                }
        );

        coordinator.start();
        asynchronous.removeFirst().run();
        stopping.set(true);
        synchronous.removeFirst().run();

        assertEquals(1, discarded.get());
        assertEquals(List.of("open", "publish"), calls);
        assertEquals(0, asynchronous.size());
    }

    @Test
    void shutdownDuringOpenClosesUnpublishedStorage() {
        AtomicBoolean stopping = new AtomicBoolean();
        List<Runnable> asynchronous = new ArrayList<>();
        AtomicInteger published = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        PaperStorageBootstrapCoordinator<String> coordinator = new PaperStorageBootstrapCoordinator<>(
                stopping::get,
                asynchronous::add,
                ignored -> {
                    throw new AssertionError("synchronous work must not be scheduled");
                },
                () -> {
                    stopping.set(true);
                    return "storage";
                },
                ignored -> {
                    published.incrementAndGet();
                    return true;
                },
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                },
                ignored -> closed.incrementAndGet(),
                ignored -> {
                }
        );

        coordinator.start();
        asynchronous.removeFirst().run();

        assertEquals(0, published.get());
        assertEquals(1, closed.get());
    }

    @Test
    void synchronousFailureDiscardsStorageAndReportsFailure() {
        AtomicBoolean stopping = new AtomicBoolean();
        List<Runnable> asynchronous = new ArrayList<>();
        List<Runnable> synchronous = new ArrayList<>();
        AtomicInteger discarded = new AtomicInteger();
        List<RuntimeException> failures = new ArrayList<>();
        RuntimeException expected = new RuntimeException("synchronous failure");
        PaperStorageBootstrapCoordinator<String> coordinator = new PaperStorageBootstrapCoordinator<>(
                stopping::get,
                asynchronous::add,
                synchronous::add,
                () -> "storage",
                ignored -> true,
                ignored -> {
                    throw expected;
                },
                ignored -> {
                },
                ignored -> discarded.incrementAndGet(),
                ignored -> {
                },
                failures::add
        );

        coordinator.start();
        asynchronous.removeFirst().run();
        synchronous.removeFirst().run();

        assertEquals(1, discarded.get());
        assertEquals(List.of(expected), failures);
        assertEquals(0, asynchronous.size());
    }

    @Test
    void cannotStartTwice() {
        List<Runnable> asynchronous = new ArrayList<>();
        PaperStorageBootstrapCoordinator<String> coordinator = coordinator(
                new AtomicBoolean(),
                asynchronous,
                new ArrayList<>(),
                new ArrayList<>(),
                ignored -> {
                },
                ignored -> {
                }
        );

        coordinator.start();

        assertThrows(IllegalStateException.class, coordinator::start);
        assertEquals(1, asynchronous.size());
    }

    private static PaperStorageBootstrapCoordinator<String> coordinator(
            AtomicBoolean stopping,
            List<Runnable> asynchronous,
            List<Runnable> synchronous,
            List<String> calls,
            java.util.function.Consumer<String> discard,
            java.util.function.Consumer<RuntimeException> failure
    ) {
        return new PaperStorageBootstrapCoordinator<>(
                stopping::get,
                asynchronous::add,
                synchronous::add,
                () -> {
                    calls.add("open");
                    return "storage";
                },
                ignored -> {
                    calls.add("publish");
                    return true;
                },
                ignored -> calls.add("synchronous"),
                ignored -> calls.add("follow-up"),
                discard,
                ignored -> calls.add("close-unpublished"),
                failure
        );
    }
}
