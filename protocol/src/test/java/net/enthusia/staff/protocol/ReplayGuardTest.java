package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private static final String BACKEND = "backend";
    private static final String BACKEND_ONE = "backend-1";

    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ReplayGuard(0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new ReplayGuard(1, null));
        assertThrows(IllegalArgumentException.class, () -> new ReplayGuard(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReplayGuard(1, Duration.ofSeconds(-1)));
    }

    @Test
    void rejectsTheSameServerAndNonceBeforeRetentionExpires() {
        ReplayGuard guard = new ReplayGuard(10, Duration.ofMinutes(1));

        assertTrue(guard.recordIfNew(BACKEND_ONE, "nonce", NOW));
        assertFalse(guard.recordIfNew(BACKEND_ONE, "nonce", NOW.plusSeconds(59)));
    }

    @Test
    void acceptsTheSameNonceForDifferentServersAndDifferentNoncesForOneServer() {
        ReplayGuard guard = new ReplayGuard(10, Duration.ofMinutes(1));

        assertTrue(guard.recordIfNew(BACKEND_ONE, "nonce-1", NOW));
        assertTrue(guard.recordIfNew("backend-2", "nonce-1", NOW));
        assertTrue(guard.recordIfNew(BACKEND_ONE, "nonce-2", NOW));
    }

    @Test
    void expirationIsInclusiveAtTheRetentionBoundary() {
        ReplayGuard guard = new ReplayGuard(10, Duration.ofMinutes(1));

        assertTrue(guard.recordIfNew(BACKEND_ONE, "nonce", NOW));
        assertTrue(guard.recordIfNew(BACKEND_ONE, "nonce", NOW.plus(Duration.ofMinutes(1))));
    }

    @Test
    void capacityEvictsTheOldestRecordedKey() {
        ReplayGuard guard = new ReplayGuard(2, Duration.ofHours(1));

        assertTrue(guard.recordIfNew(BACKEND, "first", NOW));
        assertTrue(guard.recordIfNew(BACKEND, "second", NOW));
        assertTrue(guard.recordIfNew(BACKEND, "third", NOW));

        assertTrue(guard.recordIfNew(BACKEND, "first", NOW));
        assertFalse(guard.recordIfNew(BACKEND, "third", NOW));
    }

    @Test
    void embeddedNullCharactersCannotCreateCompositeKeyCollisions() {
        ReplayGuard guard = new ReplayGuard(10, Duration.ofMinutes(1));

        assertTrue(guard.recordIfNew("a\u0000b", "c", NOW));
        assertTrue(guard.recordIfNew("a", "b\u0000c", NOW));
    }

    @Test
    void acceptsOnlyOneConcurrentClaimForANonce() throws InterruptedException {
        int workers = 32;
        ReplayGuard guard = new ReplayGuard(100, Duration.ofMinutes(3));
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<CompletableFuture<Boolean>> attempts = IntStream.range(0, workers)
                    .mapToObj(ignored -> CompletableFuture.supplyAsync(
                            () -> claimAfterRelease(guard, NOW, ready, release),
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
        return guard.recordIfNew(BACKEND_ONE, "same-nonce", now);
    }
}
