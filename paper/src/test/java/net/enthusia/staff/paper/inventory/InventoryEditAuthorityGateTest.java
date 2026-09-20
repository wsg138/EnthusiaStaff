package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class InventoryEditAuthorityGateTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void permitsWhenCurrentOwnerSchedulerReadStillHasAuthority() {
        ImmediateQuery query = new ImmediateQuery(true, true, false);

        assertTrue(InventoryEditAuthorityGate.current(query, TIMEOUT));
    }

    @Test
    void rejectsWhenAuthorityWasRevokedBeforeScheduledReadRuns() throws Exception {
        DeferredQuery query = new DeferredQuery();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> decision = worker.submit(() -> InventoryEditAuthorityGate.current(query, TIMEOUT));
            assertTrue(query.awaitScheduled());

            query.permitted.set(false);
            query.runScheduled();

            assertFalse(decision.get(1, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void rejectsOfflineViewerEvenWhenPermissionSnapshotWouldAllow() {
        ImmediateQuery query = new ImmediateQuery(false, true, false);

        assertFalse(InventoryEditAuthorityGate.current(query, TIMEOUT));
    }

    @Test
    void schedulerRetirementFailsClosed() {
        ImmediateQuery query = new ImmediateQuery(true, true, true);

        assertFalse(InventoryEditAuthorityGate.current(query, TIMEOUT));
    }

    private record ImmediateQuery(
            boolean online,
            boolean permitted,
            boolean retired
    ) implements InventoryEditAuthorityGate.AuthorityQuery {
        @Override
        public void execute(Runnable query, Runnable retirement) {
            if (retired) {
                retirement.run();
            } else {
                query.run();
            }
        }

        @Override
        public boolean hasEditPermission() {
            return permitted;
        }
    }

    private static final class DeferredQuery implements InventoryEditAuthorityGate.AuthorityQuery {
        private final CountDownLatch scheduled = new CountDownLatch(1);
        private final AtomicBoolean permitted = new AtomicBoolean(true);
        private volatile Runnable query;

        @Override
        public void execute(Runnable nextQuery, Runnable retired) {
            query = nextQuery;
            scheduled.countDown();
        }

        @Override
        public boolean online() {
            return true;
        }

        @Override
        public boolean hasEditPermission() {
            return permitted.get();
        }

        boolean awaitScheduled() throws InterruptedException {
            return scheduled.await(1, TimeUnit.SECONDS);
        }

        void runScheduled() {
            query.run();
        }
    }
}
