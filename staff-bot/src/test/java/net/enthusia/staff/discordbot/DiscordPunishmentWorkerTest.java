package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
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
    private static final String REMOVE_OPERATION = "remove";
    private static final String TARGET_NOT_IN_GUILD_ERROR = "TARGET_NOT_IN_GUILD";

    @Test
    void successfulWarningBecomesCompletedWithoutFollowUpWork() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(warning(), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.COMPLETED, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertEquals(DiscordDeliveryOutcome.DELIVERED, repository.current.punishment().dmOutcome());
        assertTrue(repository.work.isEmpty());
        assertEquals(0, gateway.applyCalls);
        assertEquals(1, gateway.notifyAppliedCalls);
    }

    @Test
    void terminalWarningDeliveryFailureIsARealApplyFailure() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(warning(), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.applyDelivery = DiscordDeliveryOutcome.FAILED_TERMINAL;
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.FAILED_APPLY, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(DiscordDeliveryOutcome.FAILED_TERMINAL, repository.current.punishment().dmOutcome());
        assertTrue(repository.work.isEmpty());
    }

    @Test
    void retryableApplyFailureSchedulesRetryAndPreservesTruthfulState() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(mute(Duration.ofHours(1)), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
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
    void targetLeavingBeforeMuteApplyRemainsRecoverable() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(mute(Duration.ofHours(1)), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.applyFailure = new DiscordPunishmentGateway.EffectException(TARGET_NOT_IN_GUILD_ERROR, true);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.RETRY_APPLY, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(Optional.of(TARGET_NOT_IN_GUILD_ERROR), repository.current.punishment().lastErrorCode());
        assertEquals(WorkType.APPLY, repository.work.peek().type());
        assertTrue(repository.work.peek().dueAt().isAfter(NOW));
    }

    @Test
    void applyNotificationRetryDoesNotRepeatExternalEffect() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(mute(Duration.ofHours(1)), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.applyDelivery = DiscordDeliveryOutcome.FAILED_RETRYABLE;
        repository.enqueue(WorkType.APPLY, NOW, 1);
        DiscordPunishmentWorker worker = newWorker(repository, gateway);

        worker.runCycle();

        assertEquals(DiscordPunishmentState.APPLIED, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertEquals(DiscordDeliveryOutcome.FAILED_RETRYABLE, repository.current.punishment().dmOutcome());
        assertEquals(1, gateway.applyCalls);
        assertEquals(1, gateway.notifyAppliedCalls);

        gateway.applyDelivery = DiscordDeliveryOutcome.DELIVERED;
        repository.makeNextDue();
        worker.runCycle();

        assertEquals(DiscordPunishmentState.APPLIED, repository.current.punishment().state());
        assertEquals(DiscordDeliveryOutcome.DELIVERED, repository.current.punishment().dmOutcome());
        assertEquals(1, gateway.applyCalls);
        assertEquals(2, gateway.notifyAppliedCalls);
    }

    @Test
    void expiredTemporaryPunishmentNeverTouchesDiscord() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(
                punishment(mute(Duration.ofMinutes(1)), NOW.minus(Duration.ofHours(1)))
        );
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.EXPIRED, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(0, gateway.applyCalls);
        assertEquals(0, gateway.notifyAppliedCalls);
    }

    @Test
    void restrictionSnapshotIsPersistedBeforeExternalApply() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(restriction(), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
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
        DiscordPunishment initial = appliedMute().requestRemoval(DiscordPunishmentTermination.END, REMOVE_OPERATION);
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(initial);
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.removeFailure = new DiscordPunishmentGateway.EffectException("HIERARCHY", false);
        repository.enqueue(WorkType.REMOVE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.FAILED_REMOVE, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertTrue(repository.work.isEmpty());
    }

    @Test
    void removalSuccessRemainsTerminalWhenNotificationFails() {
        DiscordPunishment initial = appliedMute().requestRemoval(DiscordPunishmentTermination.END, REMOVE_OPERATION);
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(initial);
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.removalDelivery = DiscordDeliveryOutcome.FAILED_TERMINAL;
        repository.enqueue(WorkType.REMOVE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.ENDED, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(DiscordDeliveryOutcome.FAILED_TERMINAL, repository.current.punishment().removalDmOutcome());
        assertEquals(1, gateway.removeCalls);
        assertEquals(1, gateway.notifyRemovedCalls);
        assertTrue(repository.work.isEmpty());
    }

    @Test
    void successfulRevokeUsesRevokedTerminalState() {
        RemovalResult result = successfulRemoval(DiscordPunishmentTermination.REVOKE);

        assertEquals(DiscordPunishmentState.REVOKED, result.punishment().state());
        assertFalse(result.punishment().externalApplied());
        assertEquals(1, result.removeCalls());
    }

    @Test
    void successfulOverturnUsesOverturnedTerminalState() {
        RemovalResult result = successfulRemoval(DiscordPunishmentTermination.OVERTURN);

        assertEquals(DiscordPunishmentState.OVERTURNED, result.punishment().state());
        assertFalse(result.punishment().externalApplied());
        assertEquals(1, result.removeCalls());
    }

    @Test
    void removalNotificationRetryDoesNotRepeatReversal() {
        DiscordPunishment initial = appliedMute().requestRemoval(DiscordPunishmentTermination.EXPIRE, "expire");
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(initial);
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.removalDelivery = DiscordDeliveryOutcome.FAILED_RETRYABLE;
        repository.enqueue(WorkType.REMOVE, NOW, 1);
        DiscordPunishmentWorker worker = newWorker(repository, gateway);

        worker.runCycle();

        assertEquals(DiscordPunishmentState.EXPIRED, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(DiscordDeliveryOutcome.FAILED_RETRYABLE, repository.current.punishment().removalDmOutcome());
        assertEquals(1, gateway.removeCalls);

        gateway.removalDelivery = DiscordDeliveryOutcome.DELIVERED;
        repository.makeNextDue();
        worker.runCycle();

        assertEquals(DiscordPunishmentState.EXPIRED, repository.current.punishment().state());
        assertEquals(DiscordDeliveryOutcome.DELIVERED, repository.current.punishment().removalDmOutcome());
        assertEquals(1, gateway.removeCalls);
        assertEquals(2, gateway.notifyRemovedCalls);
    }

    @Test
    void removalNotificationRetryExhaustionDoesNotUndoSuccessfulReversal() {
        DiscordPunishment initial = appliedMute()
                .requestRemoval(DiscordPunishmentTermination.END, REMOVE_OPERATION)
                .markRemoved("removed")
                .withRemovalDeliveryOutcome(
                        DiscordDeliveryOutcome.FAILED_RETRYABLE,
                        Optional.of("REMOVE_DM_RETRYABLE"),
                        "notify"
                );
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(initial);
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.removalDelivery = DiscordDeliveryOutcome.FAILED_RETRYABLE;
        repository.enqueue(WorkType.REMOVE, NOW, 5);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.ENDED, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(DiscordDeliveryOutcome.FAILED_TERMINAL, repository.current.punishment().removalDmOutcome());
        assertEquals(Optional.of("REMOVE_DM_RETRY_EXHAUSTED"), repository.current.punishment().lastErrorCode());
        assertEquals(0, gateway.removeCalls);
        assertTrue(repository.work.isEmpty());
    }

    @Test
    void applyForwardsDurableClaimAttemptToGateway() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(
                intent(DiscordConsequenceType.KICK, SanctionLength.instant(), Optional.empty()),
                NOW
        ));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 3);

        newWorker(repository, gateway).runCycle();

        assertEquals(3, gateway.lastApplyAttemptCount);
    }

    @Test
    void cycleClaimsOnlyOneBlockingDiscordWorkItem() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(warning(), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        int claimed = newWorker(repository, gateway).runCycle();

        assertEquals(1, claimed);
        assertEquals(1, repository.work.size());
        assertEquals(1, gateway.notifyAppliedCalls);
    }

    @Test
    void recoverablePermissionConfigurationUsesPeriodicReconciliation() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(appliedMute());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.reconcileFailure = new DiscordPunishmentGateway.EffectException(
                "RECONCILE_PERMISSION_DENIED", false
        );
        repository.enqueue(WorkType.RECONCILE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(1, repository.work.size());
        assertEquals(WorkType.RECONCILE, repository.work.peek().type());
        assertEquals(NOW.plus(Duration.ofMinutes(1)), repository.work.peek().dueAt());
    }

    @Test
    void retryableApplyFailureRemainsRecoverablePastAttemptLimit() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(punishment(mute(Duration.ofHours(1)), NOW));
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.applyFailure = new DiscordPunishmentGateway.EffectException(
                "MUTE_ROLE_OWNERSHIP_UNVERIFIED", true
        );
        repository.enqueue(WorkType.APPLY, NOW, 5);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.RETRY_APPLY, repository.current.punishment().state());
        assertEquals(Optional.of("MUTE_ROLE_OWNERSHIP_UNVERIFIED"),
                repository.current.punishment().lastErrorCode());
        assertEquals(1, repository.work.size());
        assertEquals(WorkType.APPLY, repository.work.peek().type());
        assertTrue(repository.work.peek().dueAt().isAfter(NOW));
    }

    @Test
    void absentRestrictionRemovalRemainsRecoverablePastAttemptLimit() {
        DiscordPunishment initial = appliedRestriction()
                .requestRemoval(DiscordPunishmentTermination.END, REMOVE_OPERATION);
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(initial);
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.removeFailure = new DiscordPunishmentGateway.EffectException(TARGET_NOT_IN_GUILD_ERROR, true);
        repository.enqueue(WorkType.REMOVE, NOW, 5);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.RETRY_REMOVE, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertEquals(Optional.of(TARGET_NOT_IN_GUILD_ERROR), repository.current.punishment().lastErrorCode());
        assertEquals(1, repository.work.size());
        assertEquals(WorkType.REMOVE, repository.work.peek().type());
        assertTrue(repository.work.peek().dueAt().isAfter(NOW));
    }

    @Test
    void restrictionDriftRemainsObservableAndScheduled() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(appliedRestriction());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.reconcileFailure = new DiscordPunishmentGateway.EffectException(
                "RESTRICTION_STATE_CHANGED", false
        );
        repository.enqueue(WorkType.RECONCILE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.APPLIED, repository.current.punishment().state());
        assertEquals(Optional.of("RESTRICTION_STATE_CHANGED"), repository.current.punishment().lastErrorCode());
        assertEquals(1, repository.work.size());
        assertEquals(WorkType.RECONCILE, repository.work.peek().type());
        assertEquals(NOW.plus(Duration.ofMinutes(1)), repository.work.peek().dueAt());
    }

    @Test
    void nativeBanReconciliationConflictRemainsObservableWithoutMutationLoop() {
        DiscordPunishment initial = punishment(ban(Duration.ofHours(1)), NOW).withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "applied"
        );
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(initial);
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.reconcileFailure = new DiscordPunishmentGateway.EffectException(
                "NATIVE_BAN_OWNERSHIP_CONFLICT", false
        );
        repository.enqueue(WorkType.RECONCILE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.APPLIED, repository.current.punishment().state());
        assertEquals(Optional.of("NATIVE_BAN_OWNERSHIP_CONFLICT"), repository.current.punishment().lastErrorCode());
        assertEquals(1, gateway.reconcileCalls);
        assertEquals(1, repository.work.size());
        assertEquals(WorkType.RECONCILE, repository.work.peek().type());
        assertTrue(repository.work.peek().dueAt().isAfter(NOW));
    }

    @Test
    void absentMemberReconciliationRemainsScheduledForRejoinRecovery() {
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(appliedMute());
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        gateway.reconcileFailure = new DiscordPunishmentGateway.EffectException(TARGET_NOT_IN_GUILD_ERROR, false);
        repository.enqueue(WorkType.RECONCILE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.APPLIED, repository.current.punishment().state());
        assertTrue(repository.current.punishment().externalApplied());
        assertEquals(Optional.of(TARGET_NOT_IN_GUILD_ERROR), repository.current.punishment().lastErrorCode());
        assertEquals(1, gateway.reconcileCalls);
        assertEquals(1, repository.work.size());
        assertEquals(WorkType.RECONCILE, repository.work.peek().type());
        assertTrue(repository.work.peek().dueAt().isAfter(NOW));
    }

    private static RemovalResult successfulRemoval(DiscordPunishmentTermination termination) {
        DiscordPunishment initial = appliedMute().requestRemoval(termination, REMOVE_OPERATION);
        DiscordPunishmentWorkerFakeRepository repository = new DiscordPunishmentWorkerFakeRepository(initial);
        DiscordPunishmentWorkerFakeGateway gateway = new DiscordPunishmentWorkerFakeGateway();
        repository.enqueue(WorkType.REMOVE, NOW, 1);

        newWorker(repository, gateway).runCycle();
        return new RemovalResult(repository.current.punishment(), gateway.removeCalls);
    }

    private record RemovalResult(DiscordPunishment punishment, int removeCalls) {
    }

    private static DiscordPunishmentWorker newWorker(DiscordPunishmentWorkerFakeRepository repository, DiscordPunishmentWorkerFakeGateway gateway) {
        return new DiscordPunishmentWorker(
                repository,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "worker-test",
                Duration.ofMinutes(1)
        );
    }

    private static DiscordPunishment appliedMute() {
        return punishment(mute(Duration.ofHours(1)), NOW).withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "applied"
        );
    }

    private static DiscordPunishment appliedRestriction() {
        return punishment(restriction(), NOW).withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.of(DiscordPermissionSnapshot.absent()),
                Optional.empty(),
                "applied"
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
        return intent(DiscordConsequenceType.WARNING, SanctionLength.instant(), Optional.empty());
    }

    private static DiscordPunishmentIntent mute(Duration duration) {
        return intent(DiscordConsequenceType.MUTE, SanctionLength.temporary(duration), Optional.empty());
    }

    private static DiscordPunishmentIntent ban(Duration duration) {
        return intent(DiscordConsequenceType.BAN, SanctionLength.temporary(duration), Optional.empty());
    }

    private static DiscordPunishmentIntent restriction() {
        return intent(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(Duration.ofHours(1)),
                Optional.of(new DiscordRestrictionTarget(
                        DiscordRestrictionTarget.Kind.CHANNEL,
                        "789",
                        DiscordRestrictionTarget.Mode.READ_ONLY
                ))
        );
    }

    private static DiscordPunishmentIntent intent(
            DiscordConsequenceType type,
            SanctionLength length,
            Optional<DiscordRestrictionTarget> restriction
    ) {
        return new DiscordPunishmentIntent(
                type,
                length,
                false,
                false,
                restriction,
                "Reason",
                "",
                0,
                true
        );
    }

}
