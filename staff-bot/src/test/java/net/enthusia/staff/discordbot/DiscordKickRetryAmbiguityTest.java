package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkType;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordKickRetryAmbiguityTest {
    private static final Instant NOW = Instant.parse("2026-09-21T18:30:00Z");

    @Test
    void cleanKickCompletesAndNotifiesOnce() {
        DiscordPunishmentWorkerFakeRepository repository = repository(kick());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);

        worker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.COMPLETED, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertEquals(1, gateway.applyCalls);
        assertEquals(1, gateway.notifyAppliedCalls);
        assertTrue(repository.work.isEmpty());
    }

    @Test
    void definitePreEffectRejectionFailsWithoutRetry() {
        DiscordPunishmentWorkerFakeRepository repository = repository(kick());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.applyFailure = new DiscordPunishmentGateway.EffectException("DISCORD_HIERARCHY_DENIED", false);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        worker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.FAILED_APPLY, repository.current.punishment().state());
        assertEquals(Optional.of("DISCORD_HIERARCHY_DENIED"), repository.current.punishment().lastErrorCode());
        assertFalse(repository.current.punishment().externalApplied());
        assertTrue(repository.work.isEmpty());
        assertEquals(0, gateway.notifyAppliedCalls);
    }

    @Test
    void preEffectTransportFailurePersistsSafeRetryBoundaryThenSucceeds() {
        DiscordPunishmentWorkerFakeRepository repository = repository(kick());
        DiscordPunishmentWorkerFakeGateway failingGateway = new DiscordPunishmentWorkerFakeGateway();
        failingGateway.applyFailure = new DiscordPunishmentGateway.EffectException("APPLY_TRANSPORT_FAILURE", true);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        worker(repository, failingGateway).runCycle();

        DiscordPunishment retry = repository.current.punishment();
        assertEquals(DiscordPunishmentState.RETRY_APPLY, retry.state());
        assertEquals(Optional.of(DiscordKickRetryPolicy.PRE_EFFECT_RETRY), retry.lastErrorCode());
        assertTrue(DiscordKickRetryPolicy.mayDispatch(retry, 2));
        assertFalse(retry.externalApplied());

        DiscordPunishmentWorkerFakeGateway retryGateway = new DiscordPunishmentWorkerFakeGateway();
        repository.makeNextDue();
        worker(repository, retryGateway).runCycle();

        assertEquals(DiscordPunishmentState.COMPLETED, repository.current.punishment().state());
        assertEquals(1, failingGateway.applyCalls);
        assertEquals(1, retryGateway.applyCalls);
        assertEquals(1, retryGateway.notifyAppliedCalls);
    }

    @Test
    void ambiguousKickGetsOneVerificationPassThenStopsWithoutClaimingSuccess() {
        DiscordPunishmentWorkerFakeRepository repository = repository(kick());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.applyFailure = ambiguousFailure();
        repository.enqueue(WorkType.APPLY, NOW, 1);

        worker(repository, gateway).runCycle();

        DiscordPunishment uncertain = repository.current.punishment();
        assertEquals(DiscordPunishmentState.RETRY_APPLY, uncertain.state());
        assertEquals(Optional.of(DiscordKickRetryPolicy.RESULT_AMBIGUOUS), uncertain.lastErrorCode());
        assertTrue(DiscordKickRetryPolicy.verificationOnly(uncertain, 2));
        assertFalse(uncertain.externalApplied());
        assertEquals(1, repository.work.size());

        repository.makeNextDue();
        worker(repository, gateway).runCycle();

        DiscordPunishment failed = repository.current.punishment();
        assertEquals(DiscordPunishmentState.FAILED_APPLY, failed.state());
        assertEquals(Optional.of(DiscordKickRetryPolicy.RESULT_AMBIGUOUS), failed.lastErrorCode());
        assertFalse(failed.externalApplied());
        assertEquals(DiscordDeliveryOutcome.NOT_ATTEMPTED, failed.dmOutcome());
        assertTrue(repository.work.isEmpty());
        assertEquals(0, gateway.notifyAppliedCalls);
    }

    @Test
    void restartCanSettleAmbiguousKickOnlyWhenVerificationProvesOwnership() {
        DiscordPunishmentWorkerFakeRepository repository = repository(kick());
        DiscordPunishmentWorkerFakeGateway failingGateway = new DiscordPunishmentWorkerFakeGateway();
        failingGateway.applyFailure = ambiguousFailure();
        repository.enqueue(WorkType.APPLY, NOW, 1);

        worker(repository, failingGateway).runCycle();
        DiscordPunishment uncertain = repository.current.punishment();
        assertTrue(DiscordKickRetryPolicy.verificationOnly(uncertain, 2));

        DiscordPunishmentWorkerFakeGateway restartedGateway = new DiscordPunishmentWorkerFakeGateway();
        repository.makeNextDue();
        worker(repository, restartedGateway).runCycle();

        assertEquals(DiscordPunishmentState.COMPLETED, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertEquals(1, failingGateway.applyCalls);
        assertEquals(1, restartedGateway.applyCalls);
        assertEquals(1, restartedGateway.notifyAppliedCalls);
        assertTrue(repository.work.isEmpty());
    }

    @Test
    void reclaimedLeaseAndLegacyRetryAreVerificationOnly() {
        DiscordPunishment pending = kick();
        DiscordPunishment legacyRetry = pending.withProcessingResult(
                DiscordPunishmentState.RETRY_APPLY,
                DiscordDeliveryOutcome.NOT_ATTEMPTED,
                false,
                Optional.empty(),
                Optional.of("KICK_OWNERSHIP_UNVERIFIED"),
                "legacy"
        );

        assertTrue(DiscordKickRetryPolicy.verificationOnly(pending, 2));
        assertTrue(DiscordKickRetryPolicy.verificationOnly(legacyRetry, 2));
        assertFalse(DiscordKickRetryPolicy.verificationOnly(pending, 1));
    }

    @Test
    void duplicateApplyWorkDoesNotRepeatCompletedKick() {
        DiscordPunishmentWorkerFakeRepository repository = repository(kick());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);
        repository.enqueue(WorkType.APPLY, NOW, 2);
        DiscordPunishmentWorker worker = worker(repository, gateway);

        worker.runCycle();
        worker.runCycle();

        assertEquals(DiscordPunishmentState.COMPLETED, repository.current.punishment().state());
        assertEquals(1, gateway.applyCalls);
        assertEquals(1, gateway.notifyAppliedCalls);
        assertTrue(repository.work.isEmpty());
    }

    @Test
    void nonKickRetryKeepsExistingFailureSemantics() {
        DiscordPunishmentWorkerFakeRepository repository = repository(mute());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.applyFailure = new DiscordPunishmentGateway.EffectException("RATE_LIMIT", true);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        worker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.RETRY_APPLY, repository.current.punishment().state());
        assertEquals(Optional.of("RATE_LIMIT"), repository.current.punishment().lastErrorCode());
        assertEquals(1, repository.work.size());
    }

    @Test
    void unknownDispatchFailureIsAmbiguousAndNeverMarkedRetryableByJdaLayer() {
        DiscordPunishmentGateway.EffectException failure = JdaKickEnforcer.classifyDispatchFailure(
                new IllegalStateException("connection reset after request write")
        );

        assertEquals(DiscordKickRetryPolicy.RESULT_AMBIGUOUS, failure.errorCode());
        assertFalse(failure.retryable());
    }

    private static DiscordPunishmentGateway.EffectException ambiguousFailure() {
        return new DiscordPunishmentGateway.EffectException(DiscordKickRetryPolicy.RESULT_AMBIGUOUS, false);
    }

    private static DiscordPunishmentWorkerFakeRepository repository(DiscordPunishment punishment) {
        return new DiscordPunishmentWorkerFakeRepository(punishment);
    }

    private static DiscordPunishmentWorker worker(
            DiscordPunishmentWorkerFakeRepository repository,
            DiscordPunishmentWorkerFakeGateway gateway
    ) {
        return new DiscordPunishmentWorker(
                repository,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "r09-001-worker",
                Duration.ofMinutes(1)
        );
    }

    private static DiscordPunishment kick() {
        return punishment(new DiscordPunishmentIntent(
                DiscordConsequenceType.KICK,
                SanctionLength.instant(),
                false,
                false,
                Optional.empty(),
                "Kick reason",
                "",
                0,
                true
        ));
    }

    private static DiscordPunishment mute() {
        return punishment(new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE,
                SanctionLength.temporary(Duration.ofMinutes(30)),
                false,
                false,
                Optional.empty(),
                "Mute reason",
                "",
                0,
                true
        ));
    }

    private static DiscordPunishment punishment(DiscordPunishmentIntent intent) {
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId("123"),
                new DiscordGuildId("456"),
                new Actor(UUID.randomUUID(), "staff", StaffRank.ADMIN),
                intent,
                NOW,
                "r09-001-create"
        );
    }
}
