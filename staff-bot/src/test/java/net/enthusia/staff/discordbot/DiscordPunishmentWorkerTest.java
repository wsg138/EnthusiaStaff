package net.enthusia.staff.discordbot;

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
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.discord.DiscordPunishmentTermination;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkType;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordPunishmentWorkerTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

    @Test
    void successfulWarningBecomesCompletedWithoutFollowUpWork() {
        FakeRepository repository = new FakeRepository(punishment(warning(), NOW));
        FakeGateway gateway = new FakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.COMPLETED, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertEquals(DiscordDeliveryOutcome.DELIVERED, repository.current.punishment().dmOutcome());
        assertTrue(repository.work.isEmpty());
        assertEquals(1, gateway.applyCalls);
    }

    @Test
    void retryableApplyFailureSchedulesRetryAndPreservesTruthfulState() {
        FakeRepository repository = new FakeRepository(punishment(mute(Duration.ofHours(1)), NOW));
        FakeGateway gateway = new FakeGateway();
        gateway.applyFailure = new DiscordPunishmentGateway.EffectException("RATE_LIMIT", true);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.RETRY_APPLY, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(1, repository.work.size());
        assertEquals(WorkType.APPLY, repository.work.peek().type());
        assertTrue(repository.work.peek().dueAt().isAfter(NOW));
    }

    @Test
    void expiredTemporaryPunishmentNeverTouchesDiscord() {
        FakeRepository repository = new FakeRepository(punishment(mute(Duration.ofMinutes(1)), NOW.minus(Duration.ofHours(1))));
        FakeGateway gateway = new FakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.EXPIRED, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(0, gateway.applyCalls);
    }

    @Test
    void restrictionSnapshotIsPersistedBeforeExternalApply() {
        FakeRepository repository = new FakeRepository(punishment(restriction(), NOW));
        FakeGateway gateway = new FakeGateway();
        gateway.snapshot = new DiscordPermissionSnapshot(true, 8L, 16L);
        repository.enqueue(WorkType.APPLY, NOW, 1);
        DiscordPunishmentWorker worker = newWorker(repository, gateway);

        worker.runCycle();

        assertEquals(1, gateway.snapshotCalls);
        assertEquals(0, gateway.applyCalls);
        assertEquals(Optional.of(gateway.snapshot), repository.current.punishment().previousRestriction());
        assertEquals(WorkType.APPLY, repository.work.peek().type());

        worker.runCycle();

        assertEquals(1, gateway.applyCalls);
        assertEquals(DiscordPunishmentState.APPLIED, repository.current.punishment().state());
        assertEquals(Optional.of(gateway.snapshot), gateway.lastApplied.previousRestriction());
    }

    @Test
    void nonRetryableRemovalFailureIsDurablyVisible() {
        DiscordPunishment initial = punishment(mute(Duration.ofHours(1)), NOW)
                .withProcessingResult(
                        DiscordPunishmentState.APPLIED,
                        DiscordDeliveryOutcome.DELIVERED,
                        true,
                        Optional.empty(),
                        Optional.empty(),
                        "applied"
                )
                .requestRemoval(DiscordPunishmentTermination.END, "remove");
        FakeRepository repository = new FakeRepository(initial);
        FakeGateway gateway = new FakeGateway();
        gateway.removeFailure = new DiscordPunishmentGateway.EffectException("HIERARCHY", false);
        repository.enqueue(WorkType.REMOVE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.FAILED_REMOVE, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertTrue(repository.work.isEmpty());
    }

    private static DiscordPunishmentWorker newWorker(FakeRepository repository, FakeGateway gateway) {
        return new DiscordPunishmentWorker(
                repository,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "worker-test",
                Duration.ofMinutes(1)
        );
    }

    private static DiscordPunishment punishment(DiscordPunishmentIntent intent, Instant issuedAt) {
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId("123"),
                new DiscordGuildId("456"),
                new Actor(UUID.randomUUID(), "staff", StaffRank.ADMIN),
                intent,
                issuedAt,
                "issue"
        );
    }

    private static DiscordPunishmentIntent warning() {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.WARNING,
                SanctionLength.instant(),
                false,
                false,
                Optional.empty(),
                "Reason",
                "",
                0,
                true
        );
    }

    private static DiscordPunishmentIntent mute(Duration duration) {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE,
                SanctionLength.temporary(duration),
                false,
                false,
                Optional.empty(),
                "Reason",
                "",
                0,
                true
        );
    }

    private static DiscordPunishmentIntent restriction() {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(Duration.ofHours(1)),
                false,
                false,
                Optional.of(new DiscordRestrictionTarget(
                        DiscordRestrictionTarget.Kind.CHANNEL,
                        "789",
                        DiscordRestrictionTarget.Mode.READ_ONLY
                )),
                "Reason",
                "",
                0,
                true
        );
    }

    private static final class FakeGateway implements DiscordPunishmentGateway {
        private int snapshotCalls;
        private int applyCalls;
        private DiscordPermissionSnapshot snapshot = DiscordPermissionSnapshot.absent();
        private DiscordPunishment lastApplied;
        private EffectException applyFailure;
        private EffectException removeFailure;

        @Override
        public void preflight(DiscordGuildId guildId, DiscordUserId target, DiscordPunishmentIntent intent) {
        }

        @Override
        public DiscordPermissionSnapshot captureRestrictionSnapshot(DiscordPunishment punishment) {
            snapshotCalls++;
            return snapshot;
        }

        @Override
        public ApplyResult apply(DiscordPunishment punishment) {
            applyCalls++;
            lastApplied = punishment;
            if (applyFailure != null) {
                throw applyFailure;
            }
            return new ApplyResult(DiscordDeliveryOutcome.DELIVERED, punishment.previousRestriction());
        }

        @Override
        public void remove(DiscordPunishment punishment) {
            if (removeFailure != null) {
                throw removeFailure;
            }
        }

        @Override
        public void reconcile(DiscordPunishment punishment) {
        }
    }

    private static final class FakeRepository implements DiscordPunishmentRepository {
        private StoredPunishment current;
        private final Queue<LeaseSeed> work = new ArrayDeque<>();

        private FakeRepository(DiscordPunishment punishment) {
            current = new StoredPunishment(punishment, 0, false);
        }

        void enqueue(WorkType type, Instant dueAt, int attempt) {
            work.add(new LeaseSeed(UUID.randomUUID(), type, dueAt, attempt));
        }

        @Override
        public StoredPunishment create(DiscordPunishment punishment, String operationKey, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredPunishment> find(UUID punishmentId) {
            return current.punishment().punishmentId().equals(punishmentId)
                    ? Optional.of(current)
                    : Optional.empty();
        }

        @Override
        public List<StoredPunishment> activeForTarget(
                DiscordGuildId guildId,
                DiscordUserId userId,
                DiscordConsequenceType type,
                int limit
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredPunishment transition(
                StoredPunishment expected,
                DiscordPunishment replacement,
                String operationKey,
                List<WorkSchedule> schedules,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<WorkLease> claimDue(Instant now, int limit, String leaseOwner, Instant leaseUntil) {
            List<WorkLease> leases = new ArrayList<>();
            while (!work.isEmpty() && leases.size() < limit && !work.peek().dueAt().isAfter(now)) {
                LeaseSeed seed = work.remove();
                leases.add(new WorkLease(
                        seed.id(), seed.type(), current.punishment().punishmentId(), seed.dueAt(),
                        leaseOwner, leaseUntil, seed.attempt(), seed.attempt()
                ));
            }
            return List.copyOf(leases);
        }

        @Override
        public StoredPunishment settle(
                WorkLease lease,
                StoredPunishment expected,
                DiscordPunishment replacement,
                List<WorkSchedule> nextWork,
                Instant now
        ) {
            if (expected.revision() != current.revision()) {
                throw new IllegalStateException("stale test revision");
            }
            current = new StoredPunishment(replacement, current.revision() + 1, false);
            nextWork.forEach(schedule -> enqueue(schedule.type(), schedule.dueAt(), 1));
            return current;
        }

        @Override
        public List<StoredPunishment> activeNativeBans(DiscordGuildId guildId, int limit) {
            return List.of();
        }

        private record LeaseSeed(UUID id, WorkType type, Instant dueAt, int attempt) {
        }
    }
}
