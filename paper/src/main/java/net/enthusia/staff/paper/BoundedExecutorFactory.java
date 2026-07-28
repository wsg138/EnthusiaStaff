package net.enthusia.staff.paper;

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
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
