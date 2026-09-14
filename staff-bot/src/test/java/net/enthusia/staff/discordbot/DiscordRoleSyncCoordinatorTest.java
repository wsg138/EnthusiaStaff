package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import org.junit.jupiter.api.Test;

class DiscordRoleSyncCoordinatorTest {
    private static final DiscordUserId USER = new DiscordUserId("123456789012345678");

    @Test
    void reconnectPreservesImmediateCycleRequest() throws Exception {
        RuntimeHarness runtime = new RuntimeHarness();
        BlockingReconciler first = new BlockingReconciler();
        CountingReconciler second = new CountingReconciler();
        try {
            runtime.coordinator.enable(first);
            assertTrue(first.entered.await(2, TimeUnit.SECONDS));

            runtime.coordinator.disable();
            assertTrue(first.cancelled.await(2, TimeUnit.SECONDS));
            runtime.coordinator.enable(second);
            assertFalse(second.invoked.await(100, TimeUnit.MILLISECONDS));

            first.release.countDown();
            assertTrue(second.invoked.await(2, TimeUnit.SECONDS));
        } finally {
            first.release.countDown();
            runtime.close();
        }
    }

    @Test
    void closeWaitsForActiveCycleBeforeReturning() throws Exception {
        RuntimeHarness runtime = new RuntimeHarness();
        BlockingReconciler reconciler = new BlockingReconciler();
        runtime.coordinator.enable(reconciler);
        assertTrue(reconciler.entered.await(2, TimeUnit.SECONDS));

        CompletableFuture<Void> closing = CompletableFuture.runAsync(runtime.coordinator::close);
        assertTrue(reconciler.cancelled.await(2, TimeUnit.SECONDS));
        assertFalse(closing.isDone());

        reconciler.release.countDown();
        closing.get(2, TimeUnit.SECONDS);
        runtime.workers.close();
    }

    private static final class RuntimeHarness implements AutoCloseable {
        private final StaffBotWorkerPool workers = new StaffBotWorkerPool(
                1, 4, new StaffBotHealth(StaffBotEnvironment.STAGING));
        private final DiscordRoleSyncCoordinator coordinator = new DiscordRoleSyncCoordinator(service(), workers);

        @Override
        public void close() {
            coordinator.close();
            workers.close();
        }
    }

    private static DiscordRoleSyncService service() {
        DiscordRoleSyncConfiguration configuration = new DiscordRoleSyncConfiguration(
                DiscordRoleSyncConfiguration.Mode.SHADOW,
                Map.of("helper", "1001"), Set.of(), Duration.ofSeconds(30), 1);
        return new DiscordRoleSyncService(new Store(), ignored -> Set.of(), configuration,
                Clock.fixed(Instant.parse("2026-09-14T20:00:00Z"), ZoneOffset.UTC));
    }

    private static final class Store implements DiscordRoleSyncService.StateStore {
        private final AtomicInteger revision = new AtomicInteger();

        @Override
        public List<DiscordUserId> discordUsersAfter(Optional<DiscordUserId> cursor, int limit) {
            return List.of(USER);
        }

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
            ModerationSubject subject = new ModerationSubject(
                    new ModerationSubjectId(java.util.UUID.randomUUID()),
                    Set.of(new DiscordIdentityRef(USER)), Optional.empty());
            return Optional.of(new VersionedSubject(subject, 0));
        }

        @Override
        public Optional<ReconciliationState> reconciliation(String key) {
            return Optional.empty();
        }

        @Override
        public ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now) {
            return new ReconciliationState(
                    state.reconciliationKey(), state.resourceType(), state.resourceId(),
                    state.desiredStateJson(), state.observedStateJson(), state.state(), state.attemptCount(),
                    state.nextAttemptAt(), state.lastErrorCode(), revision.getAndIncrement());
        }
    }

    private static final class BlockingReconciler implements DiscordRoleReconciler {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch cancelled = new CountDownLatch(1);

        @Override
        public Result reconcile(DiscordRoleSyncService.Evaluation evaluation) {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return new Result(Set.of(), "SHADOW_MATCH");
        }

        @Override
        public void cancel() {
            cancelled.countDown();
        }
    }

    private static final class CountingReconciler implements DiscordRoleReconciler {
        private final CountDownLatch invoked = new CountDownLatch(1);

        @Override
        public Result reconcile(DiscordRoleSyncService.Evaluation evaluation) {
            invoked.countDown();
            return new Result(Set.of(), "SHADOW_MATCH");
        }
    }
}
