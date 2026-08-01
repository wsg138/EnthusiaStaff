package net.enthusia.staff.paper.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertBacklog;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertDeliveryId;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentRequestAlertWorkerTest {
    @Test
    void enforcesAsyncSyncAsyncOrderingAndAcknowledgesOnlyAfterPresentation() {
        Harness harness = Harness.direct();

        harness.deliver();
        assertEquals(1, harness.asynchronous.size());
        assertEquals(0, harness.alerts.delivered);

        harness.runAsync();
        assertEquals(1, harness.synchronous.size());
        assertEquals(1, harness.requests.findCalls);
        assertEquals(0, harness.presenter.presented);
        assertEquals(0, harness.alerts.delivered);

        harness.runSync();
        assertEquals(1, harness.presenter.presented);
        assertEquals(0, harness.alerts.delivered);
        assertEquals(1, harness.asynchronous.size());

        harness.runAsync();
        assertEquals(1, harness.alerts.delivered);
        assertEquals(0, harness.alerts.failed);
        assertEquals(0, harness.alerts.cancelled);
        assertTrue(harness.completed.get());
    }

    @Test
    void failedPresentationRetriesWithoutAcknowledging() {
        Harness harness = Harness.direct();
        harness.presenter.presentResult = false;

        harness.runComplete();

        assertEquals(0, harness.alerts.delivered);
        assertEquals(1, harness.alerts.failed);
        assertEquals(PunishmentRequestAlertWorker.PRESENTATION_UNAVAILABLE, harness.alerts.lastCode);
    }

    @Test
    void offlineRecipientRetriesRatherThanCancelling() {
        Harness harness = Harness.direct();
        harness.presenter.current = Optional.empty();

        harness.runComplete();

        assertEquals(0, harness.presenter.presented);
        assertEquals(0, harness.alerts.delivered);
        assertEquals(0, harness.alerts.cancelled);
        assertEquals(1, harness.alerts.failed);
        assertEquals(PunishmentRequestAlertWorker.PLAYER_OFFLINE, harness.alerts.lastCode);
    }

    @Test
    void authorizationLossCancelsBeforePresentation() {
        Harness harness = Harness.reviewer(StaffRank.ADMIN, StaffRank.ADMIN, StaffRank.MOD);

        harness.runComplete();

        assertEquals(0, harness.presenter.presented);
        assertEquals(0, harness.alerts.delivered);
        assertEquals(1, harness.alerts.cancelled);
        assertEquals(PunishmentRequestAlertWorker.RECIPIENT_INELIGIBLE, harness.alerts.lastCode);
    }

    @Test
    void finalRankIsResolvedImmediatelyBeforePresentation() {
        Harness harness = Harness.reviewer(StaffRank.MOD, StaffRank.ADMIN, StaffRank.MOD);

        harness.runComplete();

        assertEquals(1, harness.presenter.currentCalls);
        assertEquals(1, harness.presenter.presented);
        assertEquals(1, harness.alerts.delivered);
    }

    @Test
    void developerAndHelperNeverClaimReviewerAlerts() {
        for (StaffRank rank : List.of(StaffRank.DEVELOPER, StaffRank.HELPER)) {
            Harness harness = Harness.reviewer(StaffRank.MOD, rank, rank);

            harness.runComplete();

            assertEquals(0, harness.alerts.reviewerClaimCalls, rank.name());
            assertEquals(0, harness.presenter.presented, rank.name());
        }
    }

    @Test
    void reviewerRankPolicyMatchesModerationHierarchy() {
        assertReviewerResult(StaffRank.MOD, StaffRank.MOD, true);
        assertReviewerResult(StaffRank.MOD, StaffRank.ADMIN, false);
        assertReviewerResult(StaffRank.ADMIN, StaffRank.MOD, true);
        assertReviewerResult(StaffRank.ADMIN, StaffRank.ADMIN, true);
        assertReviewerResult(StaffRank.FOUNDER, StaffRank.ADMIN, true);
    }

    @Test
    void requesterNeverReceivesOwnReviewerAlert() {
        Harness harness = Harness.reviewer(
                StaffRank.MOD,
                StaffRank.MOD,
                StaffRank.MOD,
                PunishmentRequestAlertTestFixtures.REQUESTER_ID
        );

        harness.runComplete();

        assertEquals(0, harness.presenter.presented);
        assertEquals(1, harness.alerts.cancelled);
        assertEquals(PunishmentRequestAlertWorker.REQUESTER_CONFLICT, harness.alerts.lastCode);
    }

    @Test
    void operationalAlertsOnlyReachAdminAndFounderAfterFinalRecheck() {
        for (StaffRank rank : StaffRank.values()) {
            if (rank == StaffRank.SYSTEM) {
                continue;
            }
            Harness harness = Harness.operational(StaffRank.ADMIN, rank);

            harness.runComplete();

            boolean eligible = rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
            assertEquals(eligible ? 1 : 0, harness.presenter.presented, rank.name());
            assertEquals(eligible ? 1 : 0, harness.alerts.delivered, rank.name());
        }
    }

    @Test
    void shutdownAfterClaimLeavesLeaseUnresolved() {
        Harness harness = Harness.direct();
        harness.deliver();
        harness.runAsync();
        harness.stopping.set(true);

        harness.runSync();

        assertEquals(0, harness.presenter.presented);
        assertEquals(0, harness.alerts.delivered);
        assertEquals(0, harness.alerts.failed);
        assertEquals(0, harness.alerts.cancelled);
        assertTrue(harness.completed.get());
    }

    @Test
    void acknowledgementExceptionDoesNotIssueCompetingOutcome() {
        Harness harness = Harness.direct();
        harness.alerts.throwOnDelivered = true;

        harness.runComplete();

        assertEquals(1, harness.alerts.delivered);
        assertEquals(0, harness.alerts.failed);
        assertEquals(0, harness.alerts.cancelled);
    }

    @Test
    void missingRequestDeterministicallyDeadLettersCurrentAttempt() {
        Harness harness = Harness.direct();
        harness.requests.request = Optional.empty();

        harness.runComplete();

        assertEquals(0, harness.presenter.presented);
        assertEquals(1, harness.alerts.failed);
        assertEquals(PunishmentRequestAlertWorker.REQUEST_MISSING, harness.alerts.lastCode);
        assertEquals(1, harness.alerts.lastMaximumAttempts);
    }

    @Test
    void malformedPresentationDataDeterministicallyDeadLettersCurrentAttempt() {
        Harness harness = Harness.direct();
        PunishmentApprovalRequest original = PunishmentRequestAlertTestFixtures.request(
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED
        );
        harness.requests.request = Optional.of(new PunishmentApprovalRequest(
                UUID.fromString("66000000-0000-0000-0000-000000000099"),
                original.submissionKey(),
                original.proposal(),
                original.createdAt(),
                original.expiresAt(),
                original.status(),
                original.revision(),
                original.resolvedBy(),
                original.resolutionNote(),
                original.resultingCaseId(),
                original.resolvedAt()
        ));

        harness.runComplete();

        assertEquals(1, harness.alerts.failed);
        assertEquals(PunishmentRequestAlertWorker.INVALID_PRESENTATION_DATA, harness.alerts.lastCode);
        assertEquals(1, harness.alerts.lastMaximumAttempts);
    }

    @Test
    void directReviewerAndOperationalClaimLimitsRemainIndependent() {
        Harness harness = Harness.emptyClaims(StaffRank.ADMIN);

        harness.runComplete();

        assertEquals(harness.settings.directBatch(), harness.alerts.directLimit);
        assertEquals(harness.settings.reviewerBatch(), harness.alerts.reviewerLimit);
        assertEquals(harness.settings.operationalBatch(), harness.alerts.operationalLimit);
    }

    private static void assertReviewerResult(StaffRank recipientRank, StaffRank minimumRank, boolean delivered) {
        Harness harness = Harness.reviewer(minimumRank, recipientRank, recipientRank);

        harness.runComplete();

        assertEquals(delivered ? 1 : 0, harness.presenter.presented,
                recipientRank + " for " + minimumRank);
        assertEquals(delivered ? 1 : 0, harness.alerts.delivered,
                recipientRank + " for " + minimumRank);
    }

    private enum Role {
        NONE,
        ASYNC,
        SYNC
    }

    private static final class Harness {
        private final AtomicReference<Role> role = new AtomicReference<>(Role.NONE);
        private final QueueExecutor asynchronous = new QueueExecutor(role, Role.ASYNC);
        private final QueueExecutor synchronous = new QueueExecutor(role, Role.SYNC);
        private final RecordingAlertStore alerts = new RecordingAlertStore(role);
        private final RecordingRequestStore requests = new RecordingRequestStore(role);
        private final RecordingPresenter presenter = new RecordingPresenter(role);
        private final AtomicBoolean stopping = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final PunishmentRequestAlertWorkerSettings settings =
                PunishmentRequestAlertWorkerSettings.safeDefaults(true);
        private final PunishmentRequestAlertRecipient snapshot;
        private final PunishmentRequestAlertWorker worker;

        private Harness(PunishmentRequestAlertRecipient snapshot) {
            this.snapshot = snapshot;
            presenter.current = Optional.of(snapshot);
            worker = new PunishmentRequestAlertWorker(
                    Clock.fixed(PunishmentRequestAlertTestFixtures.NOW, ZoneOffset.UTC),
                    "test-owner",
                    settings,
                    alerts,
                    requests,
                    new EmptyPlayerDirectory(),
                    new PunishmentRequestAlertRenderer(),
                    presenter,
                    asynchronous,
                    synchronous::execute,
                    stopping::get,
                    Logger.getLogger("PunishmentRequestAlertWorkerTest")
            );
        }

        static Harness direct() {
            Harness harness = new Harness(new PunishmentRequestAlertRecipient(
                    PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                    "RequestingHelper",
                    StaffRank.HELPER
            ));
            harness.alerts.directClaims = List.of(PunishmentRequestAlertTestFixtures.claim(
                    PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                    PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                    PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                    null
            ));
            return harness;
        }

        static Harness reviewer(StaffRank minimumRank, StaffRank snapshotRank, StaffRank finalRank) {
            return reviewer(minimumRank, snapshotRank, finalRank,
                    PunishmentRequestAlertTestFixtures.REVIEWER_ID);
        }

        static Harness reviewer(
                StaffRank minimumRank,
                StaffRank snapshotRank,
                StaffRank finalRank,
                UUID recipientId
        ) {
            Harness harness = new Harness(new PunishmentRequestAlertRecipient(
                    recipientId,
                    "Reviewer",
                    snapshotRank
            ));
            harness.presenter.current = Optional.of(new PunishmentRequestAlertRecipient(
                    recipientId,
                    "Reviewer",
                    finalRank
            ));
            harness.requests.request = Optional.of(PunishmentRequestAlertTestFixtures.pending(minimumRank));
            harness.alerts.reviewerClaims = List.of(
                    PunishmentRequestAlertTestFixtures.reviewerClaim(minimumRank, recipientId)
            );
            return harness;
        }

        static Harness operational(StaffRank snapshotRank, StaffRank finalRank) {
            Harness harness = new Harness(new PunishmentRequestAlertRecipient(
                    PunishmentRequestAlertTestFixtures.ADMIN_ID,
                    "Operator",
                    snapshotRank
            ));
            harness.presenter.current = Optional.of(new PunishmentRequestAlertRecipient(
                    PunishmentRequestAlertTestFixtures.ADMIN_ID,
                    "Operator",
                    finalRank
            ));
            harness.alerts.operationalClaims = List.of(
                    PunishmentRequestAlertTestFixtures.operationalClaim(
                            PunishmentRequestAlertTestFixtures.ADMIN_ID)
            );
            return harness;
        }

        static Harness emptyClaims(StaffRank rank) {
            return new Harness(new PunishmentRequestAlertRecipient(
                    PunishmentRequestAlertTestFixtures.ADMIN_ID,
                    "Operator",
                    rank
            ));
        }

        void deliver() {
            worker.deliver(snapshot, new PunishmentRequestAlertWorker.ClaimBudget(100),
                    () -> completed.set(true));
        }

        void runComplete() {
            deliver();
            while (!asynchronous.isEmpty() || !synchronous.isEmpty()) {
                if (!asynchronous.isEmpty()) {
                    runAsync();
                }
                if (!synchronous.isEmpty()) {
                    runSync();
                }
            }
        }

        void runAsync() {
            asynchronous.runNext();
        }

        void runSync() {
            synchronous.runNext();
        }
    }

    private static final class QueueExecutor implements Executor {
        private final AtomicReference<Role> role;
        private final Role executionRole;
        private final Queue<Runnable> queue = new ArrayDeque<>();

        private QueueExecutor(AtomicReference<Role> role, Role executionRole) {
            this.role = role;
            this.executionRole = executionRole;
        }

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
            Runnable action = queue.remove();
            role.set(executionRole);
            try {
                action.run();
            } finally {
                role.set(Role.NONE);
            }
        }
    }

    private static final class RecordingPresenter implements PunishmentRequestAlertPresenter {
        private final AtomicReference<Role> role;
        private Optional<PunishmentRequestAlertRecipient> current = Optional.empty();
        private boolean presentResult = true;
        private int currentCalls;
        private int presented;

        private RecordingPresenter(AtomicReference<Role> role) {
            this.role = role;
        }

        @Override
        public Optional<PunishmentRequestAlertRecipient> current(UUID playerId) {
            assertEquals(Role.SYNC, role.get(), "Bukkit recipient lookup must be synchronous");
            currentCalls++;
            return current.filter(value -> value.playerId().equals(playerId));
        }

        @Override
        public boolean present(
                PunishmentRequestAlertRecipient recipient,
                PunishmentRequestAlertPresentation presentation
        ) {
            assertEquals(Role.SYNC, role.get(), "Bukkit presentation must be synchronous");
            presented++;
            return presentResult;
        }
    }

    private static final class RecordingAlertStore implements PunishmentRequestAlertStore {
        private final AtomicReference<Role> role;
        private List<PunishmentRequestAlertClaim> directClaims = List.of();
        private List<PunishmentRequestAlertClaim> reviewerClaims = List.of();
        private List<PunishmentRequestAlertClaim> operationalClaims = List.of();
        private int directLimit;
        private int reviewerLimit;
        private int operationalLimit;
        private int reviewerClaimCalls;
        private int delivered;
        private int failed;
        private int cancelled;
        private String lastCode;
        private int lastMaximumAttempts;
        private boolean throwOnDelivered;

        private RecordingAlertStore(AtomicReference<Role> role) {
            this.role = role;
        }

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
            requireAsync();
            directLimit = limit;
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
            requireAsync();
            if (audience == PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS) {
                reviewerClaimCalls++;
                reviewerLimit = limit;
                return reviewerClaims;
            }
            operationalLimit = limit;
            return operationalClaims;
        }

        @Override
        public boolean delivered(PunishmentRequestAlertDeliveryId deliveryId, String owner, Instant now) {
            requireAsync();
            delivered++;
            if (throwOnDelivered) {
                throw new IllegalStateException("simulated acknowledgement failure");
            }
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
            requireAsync();
            failed++;
            lastCode = errorCode;
            lastMaximumAttempts = maximumAttempts;
            return true;
        }

        @Override
        public boolean cancel(
                PunishmentRequestAlertDeliveryId deliveryId,
                String owner,
                String reason,
                Instant now
        ) {
            requireAsync();
            cancelled++;
            lastCode = reason;
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
            throw new UnsupportedOperationException();
        }

        @Override
        public int reclaimExpiredDeliveries(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PunishmentRequestAlertBacklog backlog(Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteTerminalIntentsBefore(Instant cutoff, int limit) {
            throw new UnsupportedOperationException();
        }

        private void requireAsync() {
            assertEquals(Role.ASYNC, role.get(), "alert persistence must be asynchronous");
        }
    }

    private static final class RecordingRequestStore implements PunishmentRequestStore {
        private final AtomicReference<Role> role;
        private Optional<PunishmentApprovalRequest> request = Optional.of(
                PunishmentRequestAlertTestFixtures.request(
                        PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED)
        );
        private int findCalls;

        private RecordingRequestStore(AtomicReference<Role> role) {
            this.role = role;
        }

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            assertEquals(Role.ASYNC, role.get(), "request lookup must be asynchronous");
            findCalls++;
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
