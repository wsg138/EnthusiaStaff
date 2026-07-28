package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReplayGuardTest {
    @Test
    void acceptsOnlyOneConcurrentClaimForANonce() throws InterruptedException {
        int workers = 32;
        ReplayGuard guard = new ReplayGuard(100, Duration.ofMinutes(3));
        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<CompletableFuture<Boolean>> attempts = IntStream.range(0, workers)
                    .mapToObj(ignored -> CompletableFuture.supplyAsync(
                            () -> claimAfterRelease(guard, now, ready, release),
                            executor
                    ))
                    .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            release.countDown();

            long accepted = attempts.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();
            assertEquals(1, accepted);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static boolean claimAfterRelease(
            ReplayGuard guard,
            Instant now,
            CountDownLatch ready,
            CountDownLatch release
    ) {
        ready.countDown();
        try {
            release.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent replay test was interrupted", exception);
        }
        return guard.recordIfNew("backend-1", "same-nonce", now);
    }
}
