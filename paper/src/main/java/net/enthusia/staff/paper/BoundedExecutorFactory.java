package net.enthusia.staff.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class BoundedExecutorFactory {
    private BoundedExecutorFactory() {
    }

    static ExecutorService create(int threads, int queueCapacity) {
        if (threads < 1 || threads > 16 || queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalArgumentException("invalid worker bounds");
        }
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "EnthusiaStaff-Paper-Worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new TerminationAwaitingThreadPoolExecutor(
                threads,
                new ArrayBlockingQueue<>(queueCapacity),
                factory
        );
    }

    private static final class TerminationAwaitingThreadPoolExecutor extends ThreadPoolExecutor {
        private TerminationAwaitingThreadPoolExecutor(
                int threads,
                ArrayBlockingQueue<Runnable> workQueue,
                ThreadFactory threadFactory
        ) {
            super(
                    threads,
                    threads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    workQueue,
                    threadFactory,
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }

        @Override
        public List<Runnable> shutdownNow() {
            List<Runnable> awaitingExecution = super.shutdownNow();
            boolean interrupted = awaitTerminationUninterruptibly();
            List<RuntimeException> failures = runAwaitingTasks(awaitingExecution);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (!failures.isEmpty()) {
                IllegalStateException failure = new IllegalStateException(
                        "Queued worker task failed during forced shutdown",
                        failures.getFirst()
                );
                failures.stream().skip(1).forEach(failure::addSuppressed);
                throw failure;
            }
            return List.of();
        }

        private boolean awaitTerminationUninterruptibly() {
            boolean interrupted = false;
            while (!isTerminated()) {
                try {
                    if (super.awaitTermination(1, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            return interrupted;
        }

        private static List<RuntimeException> runAwaitingTasks(List<Runnable> awaitingExecution) {
            List<RuntimeException> failures = new ArrayList<>();
            for (Runnable task : awaitingExecution) {
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    failures.add(exception);
                }
            }
            return failures;
        }
    }
}
