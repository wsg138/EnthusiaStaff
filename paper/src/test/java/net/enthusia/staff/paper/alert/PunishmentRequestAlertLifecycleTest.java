package net.enthusia.staff.paper.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertDeliveryId;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import org.junit.jupiter.api.Test;

class PunishmentRequestAlertLifecycleTest {
    @Test
    void startIsSingleUseAndCloseCancelsOnlyOwnedTasks() {
        Harness harness = new Harness();
        FakeTask unrelatedOperationalStateTask = new FakeTask();

        assertTrue(harness.lifecycle.start());
        assertFalse(harness.lifecycle.start());
        assertEquals(5, harness.runtime.tasks.size());
        assertFalse(harness.runtime.listenerClosed.get());

        harness.lifecycle.close();
        harness.lifecycle.close();

        assertTrue(harness.runtime.tasks.stream().allMatch(task -> task.cancelled.get()));
        assertTrue(harness.runtime.listenerClosed.get());
        assertFalse(unrelatedOperationalStateTask.cancelled.get());
        assertFalse(harness.lifecycle.active());
    }

    @Test
    void closeDisablesJoinDeliveryAndLateScheduledCallbacks() {
        Harness harness = new Harness();
        harness.lifecycle.start();
        Consumer<UUID> join = harness.runtime.joinListener;
        join.accept(PunishmentRequestAlertTestFixtures.REQUESTER_ID);
        assertEquals(1, harness.runtime.delayed.size());

        harness.lifecycle.close();
        join.accept(PunishmentRequestAlertTestFixtures.REQUESTER_ID);
        harness.runtime.delayed.remove().run();

        assertTrue(harness.asynchronous.isEmpty());
        assertEquals(0, harness.runtime.presented);
    }

    @Test
    void pollingAndReconnectCannotOverlapForOneRecipientWhileOthersProgress() {
        Harness harness = new Harness();
        UUID second = UUID.fromString("66000000-0000-0000-0000-000000000020");
        harness.runtime.online = List.of(
                recipient(PunishmentRequestAlertTestFixtures.REQUESTER_ID),
                recipient(second)
        );
        harness.runtime.current.put(PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                recipient(PunishmentRequestAlertTestFixtures.REQUESTER_ID));
        harness.runtime.current.put(second, recipient(second));
        harness.lifecycle.start();

        harness.runtime.synchronousRepeating.getFirst().run();
        assertEquals(2, harness.asynchronous.size());

        harness.runtime.joinListener.accept(PunishmentRequestAlertTestFixtures.REQUESTER_ID);
        harness.runtime.delayed.remove().run();
        assertEquals(2, harness.asynchronous.size());

        harness.asynchronous.runNext();
        harness.runtime.joinListener.accept(PunishmentRequestAlertTestFixtures.REQUESTER_ID);
        harness.runtime.delayed.remove().run();
        assertEquals(2, harness.asynchronous.size());
    }

    @Test
    void maintenanceUsesConfiguredBatchesSuppressesOverlapAndRecoversAfterFailure() throws Exception {
        Harness harness = new Harness();
        harness.lifecycle.start();

        for (Runnable action : harness.runtime.asynchronousRepeating) {
            action.run();
        }

        assertEquals(harness.settings.requestExpirationBatch(), harness.requests.lastExpirationBatch);
        assertEquals(harness.settings.intentExpirationBatch(), harness.alerts.lastIntentBatch);
        assertEquals(harness.settings.leaseReclaimBatch(), harness.alerts.lastReclaimBatch);
        assertEquals(harness.settings.retentionBatch(), harness.alerts.lastRetentionBatch);

        harness.requests.blockExpiration = true;
        Thread first = new Thread(harness.runtime.asynchronousRepeating.get(0));
        first.start();
        assertTrue(harness.requests.expirationEntered.await(2, TimeUnit.SECONDS));
        int beforeOverlap = harness.requests.expirationCalls;
        harness.runtime.asynchronousRepeating.get(0).run();
        assertEquals(beforeOverlap, harness.requests.expirationCalls);
        harness.requests.expirationRelease.countDown();
        first.join(2_000);

        harness.alerts.throwIntentExpirationOnce = true;
        int beforeFailure = harness.alerts.intentCalls;
        harness.runtime.asynchronousRepeating.get(1).run();
        harness.runtime.asynchronousRepeating.get(1).run();
        assertEquals(beforeFailure + 2, harness.alerts.intentCalls);
    }

    @Test
    void shutdownAfterClaimPreventsLatePresentationAndFalseAcknowledgement() {
        Harness harness = new Harness();
        harness.runtime.online = List.of(recipient(PunishmentRequestAlertTestFixtures.REQUESTER_ID));
        harness.runtime.current.put(PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                recipient(PunishmentRequestAlertTestFixtures.REQUESTER_ID));
        harness.alerts.directClaims = List.of(PunishmentRequestAlertTestFixtures.claim(
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                null
        ));
        harness.lifecycle.start();
        harness.runtime.synchronousRepeating.getFirst().run();
        harness.asynchronous.runNext();
        assertEquals(1, harness.runtime.synchronousHandoffs.size());

        harness.lifecycle.close();
        harness.runtime.synchronousHandoffs.remove().run();

        assertEquals(0, harness.runtime.presented);
        assertEquals(0, harness.alerts.delivered);
        assertEquals(0, harness.alerts.failed);
        assertEquals(0, harness.alerts.cancelled);
    }

    @Test
    void shutdownPreventsNewMaintenanceEvenWhenSchedulerInvokesCapturedAction() {
        Harness harness = new Harness();
        harness.lifecycle.start();
        Runnable captured = harness.runtime.asynchronousRepeating.getFirst();
        harness.lifecycle.close();

        captured.run();

        assertEquals(0, harness.requests.expirationCalls);
    }

    private static PunishmentRequestAlertRecipient recipient(UUID id) {
        return new PunishmentRequestAlertRecipient(id, "Player-" + id.toString().substring(0, 4), StaffRank.HELPER);
    }

    private static final class Harness {
        private final FakeRuntime runtime = new FakeRuntime();
        private final QueueExecutor asynchronous = new QueueExecutor();
        private final RecordingAlertStore alerts = new RecordingAlertStore();
        private final RecordingRequestStore requests = new RecordingRequestStore();
        private final PunishmentRequestAlertWorkerSettings settings =
                PunishmentRequestAlertWorkerSettings.safeDefaults(true);
        private final AtomicBoolean pluginStopping = new AtomicBoolean();
        private final PunishmentRequestAlertLifecycle lifecycle = new PunishmentRequestAlertLifecycle(
                runtime,
                Clock.fixed(PunishmentRequestAlertTestFixtures.NOW, ZoneOffset.UTC),
                "lifecycle-test-owner",
                settings,
                alerts,
                requests,
                new EmptyPlayerDirectory(),
                asynchronous,
                pluginStopping::get
        );
    }

    private static final class QueueExecutor implements Executor {
        private final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        int size() {
            return queue.size();
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }

        void runNext() {
            queue.remove().run();
        }
    }

    private static final class FakeRuntime implements PunishmentRequestAlertRuntime {
        private final List<FakeTask> tasks = new ArrayList<>();
        private final List<Runnable> synchronousRepeating = new ArrayList<>();
        private final List<Runnable> asynchronousRepeating = new ArrayList<>();
        private final Queue<Runnable> delayed = new ArrayDeque<>();
        private final Queue<Runnable> synchronousHandoffs = new ArrayDeque<>();
        private final java.util.Map<UUID, PunishmentRequestAlertRecipient> current = new java.util.HashMap<>();
        private final AtomicBoolean listenerClosed = new AtomicBoolean();
        private List<PunishmentRequestAlertRecipient> online = List.of();
        private Consumer<UUID> joinListener;
        private int presented;

        @Override
        public List<PunishmentRequestAlertRecipient> onlineRecipients(int limit) {
            return online.stream().limit(limit).toList();
        }

        @Override
        public Optional<PunishmentRequestAlertRecipient> currentRecipient(UUID playerId) {
            return Optional.ofNullable(current.get(playerId));
        }

        @Override
        public boolean present(
                PunishmentRequestAlertRecipient recipient,
                PunishmentRequestAlertPresentation presentation
        ) {
            presented++;
            return true;
        }

        @Override
        public AutoCloseable registerJoinListener(Consumer<UUID> listener) {
            joinListener = listener;
            return () -> listenerClosed.set(true);
        }

        @Override
        public Cancellable scheduleSynchronousRepeating(
                Runnable action,
                Duration initialDelay,
                Duration interval
        ) {
            synchronousRepeating.add(action);
            return task();
        }

        @Override
        public Cancellable scheduleSynchronousDelayed(Runnable action, Duration delay) {
            delayed.add(action);
            return task();
        }

        @Override
        public Cancellable scheduleAsynchronousRepeating(
                Runnable action,
                Duration initialDelay,
                Duration interval
        ) {
            asynchronousRepeating.add(action);
            return task();
        }

        @Override
        public void executeSynchronously(Runnable action) {
            synchronousHandoffs.add(action);
        }

        @Override
        public Logger logger() {
            return Logger.getLogger("PunishmentRequestAlertLifecycleTest");
        }

        private FakeTask task() {
            FakeTask task = new FakeTask();
            tasks.add(task);
            return task;
        }
    }

    private static final class FakeTask implements PunishmentRequestAlertRuntime.Cancellable {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }

    private static final class RecordingRequestStore implements PunishmentRequestStore {
        private final CountDownLatch expirationEntered = new CountDownLatch(1);
        private final CountDownLatch expirationRelease = new CountDownLatch(1);
        private Optional<PunishmentApprovalRequest> request = Optional.of(
                PunishmentRequestAlertTestFixtures.request(
                        PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED)
        );
        private volatile boolean blockExpiration;
        private volatile int expirationCalls;
        private volatile int lastExpirationBatch;

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            return request;
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId,
                UUID ownerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int expire(Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int expire(Instant now, int limit) {
            expirationCalls++;
            lastExpirationBatch = limit;
            if (blockExpiration) {
                expirationEntered.countDown();
                try {
                    expirationRelease.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", exception);
                }
            }
            return 0;
        }
    }

    private static final class RecordingAlertStore implements PunishmentRequestAlertStore {
        private List<PunishmentRequestAlertClaim> directClaims = List.of();
        private int delivered;
        private int failed;
        private int cancelled;
        private int intentCalls;
        private int lastIntentBatch;
        private int lastReclaimBatch;
        private int lastRetentionBatch;
        private boolean throwIntentExpirationOnce;

        @Override
        public boolean insert(PunishmentRequestAlertIntent intent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PunishmentRequestAlertClaim> claimDirect(
                UUID recipientId,
                String owner,
                int limit,
                Duration lease,
                Instant now
        ) {
            return directClaims;
        }

        @Override
        public List<PunishmentRequestAlertClaim> claimAudience(
                PunishmentRequestAlertAudience audience,
                UUID recipientId,
                StaffRank recipientRank,
                String owner,
                int limit,
                Duration lease,
                Instant now
        ) {
            return List.of();
        }

        @Override
        public boolean delivered(PunishmentRequestAlertDeliveryId deliveryId, String owner, Instant now) {
            delivered++;
            return true;
        }

        @Override
        public boolean failed(
                PunishmentRequestAlertDeliveryId deliveryId,
                String owner,
                String errorCode,
                Instant availableAt,
                Instant now,
                int maximumAttempts
        ) {
            failed++;
            return true;
        }

        @Override
        public boolean cancel(
                PunishmentRequestAlertDeliveryId deliveryId,
                String owner,
                String reason,
                Instant now
        ) {
            cancelled++;
            return true;
        }

        @Override
        public boolean requeueDeadLetter(
                PunishmentRequestAlertDeliveryId deliveryId,
                Instant availableAt,
                String reason,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean resolveDeadLetter(
                PunishmentRequestAlertDeliveryId deliveryId,
                String reason,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean closeIntent(UUID alertId, String reason, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int expireIntents(Instant now, int limit) {
            intentCalls++;
            lastIntentBatch = limit;
            if (throwIntentExpirationOnce) {
                throwIntentExpirationOnce = false;
                throw new IllegalStateException("simulated intent expiration failure");
            }
            return 0;
        }

        @Override
        public int reclaimExpiredDeliveries(Instant now, int limit) {
            lastReclaimBatch = limit;
            return 0;
        }

        @Override
        public PunishmentRequestAlertBacklog backlog(Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteTerminalIntentsBefore(Instant cutoff, int limit) {
            lastRetentionBatch = limit;
            return 0;
        }
    }

    private static final class EmptyPlayerDirectory implements PlayerDirectory {
        @Override
        public Optional<PlayerIdentity> find(String uuidOrUsername) {
            return Optional.empty();
        }

        @Override
        public List<PlayerIdentity> search(String prefix, int limit) {
            return List.of();
        }

        @Override
        public Optional<PlayerPresence> presence(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public void recordSeen(
                UUID playerId,
                String username,
                PlayerPlatform platform,
                String serverId,
                Instant seenAt
        ) {
        }

        @Override
        public void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt) {
        }
    }
}
