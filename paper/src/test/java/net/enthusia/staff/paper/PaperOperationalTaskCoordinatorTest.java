package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;
import org.junit.jupiter.api.Test;

class PaperOperationalTaskCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-01T20:00:00Z");

    @Test
    void schedulerTriggerOnlySubmitsWorkerAndDisabledAlertsStillExpireRequests() {
        QueueExecutor workers = new QueueExecutor();
        RecordingRequestStore requests = new RecordingRequestStore();
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger evidence = new AtomicInteger();
        PaperOperationalTaskCoordinator coordinator = coordinator(
                workers, requests, refreshes, evidence, settings(false), () -> false);

        coordinator.trigger();

        assertEquals(1, workers.size());
        assertEquals(0, refreshes.get());
        assertEquals(0, evidence.get());
        assertEquals(0, requests.expirationCalls);

        workers.runNext();

        assertEquals(1, refreshes.get());
        assertEquals(1, evidence.get());
        assertEquals(1, requests.expirationCalls);
        assertEquals(settings(false).requestExpirationBatch(), requests.lastBatch);
    }

    @Test
    void rejectionReleasesOverlapGuardAndClosePreventsNewSubmissions() {
        QueueExecutor workers = new QueueExecutor();
        workers.reject = true;
        PaperOperationalTaskCoordinator coordinator = coordinator(
                workers,
                new RecordingRequestStore(),
                new AtomicInteger(),
                new AtomicInteger(),
                settings(true),
                () -> false
        );

        coordinator.trigger();
        workers.reject = false;
        coordinator.trigger();
        assertEquals(1, workers.size());

        coordinator.close();
        workers.runNext();
        coordinator.trigger();
        assertEquals(0, workers.size());
    }

    @Test
    void submittedExpirationParticipatesInBoundedWorkerShutdown() throws Exception {
        RecordingRequestStore requests = new RecordingRequestStore();
        requests.block = true;
        ExecutorService workers = Executors.newSingleThreadExecutor();
        PaperOperationalTaskCoordinator coordinator = coordinator(
                workers,
                requests,
                new AtomicInteger(),
                new AtomicInteger(),
                settings(false),
                () -> false
        );

        coordinator.trigger();
        assertTrue(requests.entered.await(2, TimeUnit.SECONDS));
        coordinator.close();
        workers.shutdown();
        assertFalse(workers.awaitTermination(50, TimeUnit.MILLISECONDS));
        requests.release.countDown();
        assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
    }

    private static PaperOperationalTaskCoordinator coordinator(
            Executor workers,
            RecordingRequestStore requests,
            AtomicInteger refreshes,
            AtomicInteger evidence,
            PunishmentRequestAlertWorkerSettings settings,
            java.util.function.BooleanSupplier stopping
    ) {
        return new PaperOperationalTaskCoordinator(
                workers,
                stopping,
                refreshes::incrementAndGet,
                evidence::incrementAndGet,
                () -> requests,
                () -> settings,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Logger.getLogger("PaperOperationalTaskCoordinatorTest")
        );
    }

    private static PunishmentRequestAlertWorkerSettings settings(boolean enabled) {
        PunishmentRequestAlertWorkerSettings defaults = PunishmentRequestAlertWorkerSettings.safeDefaults(enabled);
        return new PunishmentRequestAlertWorkerSettings(
                enabled,
                defaults.pollInterval(),
                defaults.recipientLimit(),
                defaults.directBatch(),
                defaults.reviewerBatch(),
                defaults.operationalBatch(),
                defaults.totalClaimLimit(),
                defaults.presentationLimit(),
                defaults.leaseDuration(),
                defaults.maximumAttempts(),
                defaults.retryBase(),
                defaults.retryMaximum(),
                defaults.joinDelay(),
                Duration.ofSeconds(5),
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

    private static final class QueueExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean reject;

        @Override
        public void execute(Runnable command) {
            if (reject) {
                throw new RejectedExecutionException("full");
            }
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class RecordingRequestStore implements PunishmentRequestStore {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean block;
        private volatile int expirationCalls;
        private volatile int lastBatch;

        @Override
        public int expire(Instant now, int limit) {
            expirationCalls++;
            lastBatch = limit;
            if (block) {
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", exception);
                }
            }
            return 0;
        }

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            return Optional.empty();
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            return List.of();
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId, UUID ownerId, Instant now, Instant leaseExpiresAt) {
            return Optional.empty();
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease, Actor approver, CaseId caseId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease, Actor approver, String note, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int expire(Instant now) {
            return expire(now, Integer.MAX_VALUE);
        }
    }
}
