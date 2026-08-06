package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class VelocityBootstrapCoordinatorTest {
    @Test
    void transientFailureRecoversOnBoundedRetry() {
        Harness harness = new Harness(new VelocityBootstrapCoordinator.RetryPolicy(3, 100L, 500L));
        harness.failuresRemaining = 1;

        assertTrue(harness.coordinator.start());
        harness.workers.runNext();

        assertEquals(1, harness.coordinator.attempts());
        assertTrue(harness.coordinator.retryScheduled());
        assertEquals(List.of(100L), harness.delays);

        harness.scheduled.runNext();
        harness.workers.runNext();

        assertEquals(2, harness.coordinator.attempts());
        assertTrue(harness.coordinator.completed());
        assertFalse(harness.coordinator.exhausted());
        assertEquals(List.of("attempt:1", "retry:2:100", "attempt:2", "recovered:2"), harness.events);
    }

    @Test
    void retryExhaustionStopsAtConfiguredLimit() {
        Harness harness = new Harness(new VelocityBootstrapCoordinator.RetryPolicy(3, 100L, 500L));
        harness.failuresRemaining = 3;

        harness.coordinator.start();
        harness.workers.runNext();
        harness.scheduled.runNext();
        harness.workers.runNext();
        harness.scheduled.runNext();
        harness.workers.runNext();

        assertEquals(3, harness.coordinator.attempts());
        assertEquals(List.of(100L, 200L), harness.delays);
        assertTrue(harness.coordinator.exhausted());
        assertFalse(harness.coordinator.completed());
        assertTrue(harness.events.contains("exhausted:3"));
        assertEquals(0, harness.scheduled.size());
    }

    @Test
    void permanentFailureDoesNotScheduleRetry() {
        Harness harness = new Harness(new VelocityBootstrapCoordinator.RetryPolicy(5, 100L, 500L));
        harness.permanentFailure = true;

        harness.coordinator.start();
        harness.workers.runNext();

        assertTrue(harness.coordinator.exhausted());
        assertEquals(1, harness.coordinator.attempts());
        assertEquals(0, harness.scheduled.size());
        assertTrue(harness.delays.isEmpty());
    }

    @Test
    void shutdownBeforeScheduledRetrySuppressesLateAttempt() {
        Harness harness = new Harness(new VelocityBootstrapCoordinator.RetryPolicy(3, 100L, 500L));
        harness.failuresRemaining = 1;

        harness.coordinator.start();
        harness.workers.runNext();
        harness.stopping.set(true);
        harness.coordinator.stop();
        harness.scheduled.runNext();

        assertEquals(1, harness.coordinator.attempts());
        assertEquals(0, harness.workers.size());
        assertFalse(harness.coordinator.completed());
    }

    @Test
    void manualRetryCanRestartAnExhaustedCycleButCannotOverlap() {
        Harness harness = new Harness(new VelocityBootstrapCoordinator.RetryPolicy(1, 100L, 500L));
        harness.failuresRemaining = 1;

        harness.coordinator.start();
        assertFalse(harness.coordinator.requestImmediateRetry());
        harness.workers.runNext();
        assertTrue(harness.coordinator.exhausted());

        assertTrue(harness.coordinator.requestImmediateRetry());
        assertFalse(harness.coordinator.requestImmediateRetry());
        harness.workers.runNext();

        assertTrue(harness.coordinator.completed());
        assertEquals(1, harness.coordinator.attempts());
        assertTrue(harness.events.contains("recovered:1"));
    }

    @Test
    void rejectedWorkerSubmissionUsesSameRetryBudget() {
        Harness harness = new Harness(new VelocityBootstrapCoordinator.RetryPolicy(2, 75L, 75L));
        harness.workers.rejectNext = true;

        assertTrue(harness.coordinator.start());
        assertEquals(List.of(75L), harness.delays);
        assertEquals(1, harness.scheduled.size());

        harness.scheduled.runNext();
        harness.workers.runNext();

        assertTrue(harness.coordinator.completed());
        assertEquals(2, harness.coordinator.attempts());
    }

    private static final class Harness {
        private final TaskQueue workers = new TaskQueue();
        private final TaskQueue scheduled = new TaskQueue();
        private final AtomicBoolean stopping = new AtomicBoolean();
        private final List<Long> delays = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private int failuresRemaining;
        private boolean permanentFailure;
        private final VelocityBootstrapCoordinator coordinator;

        private Harness(VelocityBootstrapCoordinator.RetryPolicy policy) {
            coordinator = new VelocityBootstrapCoordinator(
                    workers::offer,
                    (operation, delayMillis) -> {
                        delays.add(delayMillis);
                        return scheduled.offer(operation);
                    },
                    () -> {
                        if (permanentFailure) {
                            throw new VelocityBootstrapCoordinator.PermanentFailure("permanent");
                        }
                        if (failuresRemaining > 0) {
                            failuresRemaining--;
                            throw new IllegalStateException("transient");
                        }
                    },
                    new VelocityBootstrapCoordinator.Listener() {
                        @Override
                        public void attempting(int attempt, int maximumAttempts) {
                            events.add("attempt:" + attempt);
                        }

                        @Override
                        public void retrying(
                                int nextAttempt,
                                int maximumAttempts,
                                long delayMillis,
                                RuntimeException failure
                        ) {
                            events.add("retry:" + nextAttempt + ':' + delayMillis);
                        }

                        @Override
                        public void recovered(int attempts) {
                            events.add("recovered:" + attempts);
                        }

                        @Override
                        public void exhausted(int attempts, RuntimeException failure) {
                            events.add("exhausted:" + attempts);
                        }
                    },
                    stopping::get,
                    policy
            );
        }
    }

    private static final class TaskQueue {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean rejectNext;

        private boolean offer(Runnable task) {
            if (rejectNext) {
                rejectNext = false;
                return false;
            }
            tasks.add(task);
            return true;
        }

        private int size() {
            return tasks.size();
        }

        private void runNext() {
            tasks.remove().run();
        }
    }
}
