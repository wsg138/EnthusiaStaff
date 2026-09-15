package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.discord.DiscordPunishmentTermination;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.StoredPunishment;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkLease;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkSchedule;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkType;

/** Leased D07 worker; every external effect is driven by durable intent and settled transactionally. */
final class DiscordPunishmentWorker {
    private static final int CLAIM_LIMIT = 1;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofSeconds(45);
    private static final Duration BASE_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofMinutes(5);
    private static final String NATIVE_BAN_OWNERSHIP_CONFLICT = "NATIVE_BAN_OWNERSHIP_CONFLICT";
    private static final String TARGET_NOT_IN_GUILD = "TARGET_NOT_IN_GUILD";
    private static final Set<String> PERIODIC_RECONCILIATION_ERRORS = Set.of(
            NATIVE_BAN_OWNERSHIP_CONFLICT,
            TARGET_NOT_IN_GUILD,
            "DISCORD_HIERARCHY_DENIED",
            "MUTE_ROLE_UNAVAILABLE",
            "MUTE_ROLE_HIERARCHY_DENIED",
            "RECONCILE_PERMISSION_DENIED"
    );

    private final DiscordPunishmentRepository repository;
    private final DiscordPunishmentGateway gateway;
    private final Clock clock;
    private final String workerId;
    private final Duration reconciliationInterval;

    DiscordPunishmentWorker(
            DiscordPunishmentRepository repository,
            DiscordPunishmentGateway gateway,
            Clock clock,
            String workerId,
            Duration reconciliationInterval
    ) {
        if (repository == null || gateway == null || clock == null || workerId == null || workerId.isBlank()
                || workerId.length() > 96 || reconciliationInterval == null
                || reconciliationInterval.isZero() || reconciliationInterval.isNegative()) {
            throw new IllegalArgumentException("Discord punishment worker configuration is invalid");
        }
        this.repository = repository;
        this.gateway = gateway;
        this.clock = clock;
        this.workerId = workerId;
        this.reconciliationInterval = reconciliationInterval;
    }

    int runCycle() {
        Instant now = clock.instant();
        List<WorkLease> work = repository.claimDue(now, CLAIM_LIMIT, workerId, now.plus(LEASE));
        for (WorkLease lease : work) {
            process(lease);
        }
        return work.size();
    }

    private void process(WorkLease work) {
        StoredPunishment stored = repository.find(work.punishmentId())
                .orElseThrow(() -> new IllegalStateException("D07 work references a missing punishment"));
        switch (work.type()) {
            case APPLY -> apply(work, stored);
            case REMOVE -> remove(work, stored);
            case RECONCILE -> reconcile(work, stored);
            default -> throw new IllegalStateException("unsupported D07 work type");
        }
    }

    private void apply(WorkLease work, StoredPunishment stored) {
        DiscordPunishment punishment = stored.punishment();
        if (needsApplyNotificationRetry(punishment)) {
            retryApplyNotification(work, stored);
            return;
        }
        if (!applyPending(punishment)) {
            settle(work, stored, punishment, List.of());
            return;
        }
        if (expired(punishment)) {
            settle(work, stored, terminalWithoutEffect(punishment, work), List.of());
            return;
        }
        if (requiresRestrictionSnapshot(punishment)) {
            captureRestrictionSnapshot(work, stored);
            return;
        }
        if (punishment.intent().type() == DiscordConsequenceType.WARNING) {
            applyWarning(work, stored);
            return;
        }
        applyExternalEffect(work, stored);
    }

    private void captureRestrictionSnapshot(WorkLease work, StoredPunishment stored) {
        try {
            DiscordPermissionSnapshot snapshot = gateway.captureRestrictionSnapshot(stored.punishment());
            DiscordPunishment replacement = stored.punishment().withProcessingResult(
                    stored.punishment().state(),
                    stored.punishment().dmOutcome(),
                    false,
                    Optional.of(snapshot),
                    Optional.empty(),
                    operationKey(work)
            );
            settle(work, stored, replacement, List.of(new WorkSchedule(WorkType.APPLY, clock.instant())));
        } catch (DiscordPunishmentGateway.EffectException failure) {
            settleApplyFailure(work, stored, failure);
        }
    }

    private void applyWarning(WorkLease work, StoredPunishment stored) {
        DiscordDeliveryOutcome delivery = gateway.notifyApplied(stored.punishment());
        boolean retry = retryDelivery(delivery, work);
        DiscordDeliveryOutcome recorded = recordedDelivery(delivery, retry);
        DiscordPunishmentState state = warningState(recorded, retry);
        DiscordPunishment replacement = stored.punishment().withProcessingResult(
                state,
                recorded,
                recorded == DiscordDeliveryOutcome.DELIVERED,
                stored.punishment().previousRestriction(),
                deliveryError("WARNING", delivery, retry),
                operationKey(work)
        );
        settle(work, stored, replacement, retry ? List.of(retry(work, WorkType.APPLY)) : List.of());
    }

    private void applyExternalEffect(WorkLease work, StoredPunishment stored) {
        try {
            gateway.apply(stored.punishment());
            DiscordDeliveryOutcome delivery = gateway.notifyApplied(stored.punishment());
            boolean retry = retryDelivery(delivery, work);
            DiscordDeliveryOutcome recorded = recordedDelivery(delivery, retry);
            DiscordPunishmentState state = stored.punishment().intent().reversible()
                    ? DiscordPunishmentState.APPLIED
                    : DiscordPunishmentState.COMPLETED;
            DiscordPunishment replacement = stored.punishment().withProcessingResult(
                    state,
                    recorded,
                    true,
                    stored.punishment().previousRestriction(),
                    deliveryError("APPLY", delivery, retry),
                    operationKey(work)
            );
            settle(work, stored, replacement, applyFollowUp(replacement, work, retry));
        } catch (DiscordPunishmentGateway.EffectException failure) {
            settleApplyFailure(work, stored, failure);
        }
    }

    private void retryApplyNotification(WorkLease work, StoredPunishment stored) {
        DiscordDeliveryOutcome delivery = gateway.notifyApplied(stored.punishment());
        boolean retry = retryDelivery(delivery, work);
        DiscordDeliveryOutcome recorded = recordedDelivery(delivery, retry);
        DiscordPunishment replacement = stored.punishment().withApplyDeliveryOutcome(
                recorded,
                deliveryError("APPLY", delivery, retry),
                operationKey(work)
        );
        settle(work, stored, replacement, retry ? List.of(retry(work, WorkType.APPLY)) : List.of());
    }

    private void remove(WorkLease work, StoredPunishment stored) {
        DiscordPunishment punishment = stored.punishment();
        if (needsRemovalNotificationRetry(punishment)) {
            retryRemovalNotification(work, stored);
            return;
        }
        if (!punishment.state().removalPending()) {
            settle(work, stored, punishment, List.of());
            return;
        }
        if (!punishment.externalApplied()) {
            completeRemoval(work, stored, punishment);
            return;
        }
        try {
            gateway.remove(punishment);
            completeRemoval(work, stored, punishment);
        } catch (DiscordPunishmentGateway.EffectException failure) {
            settleRemoveFailure(work, stored, failure);
        }
    }

    private void completeRemoval(WorkLease work, StoredPunishment stored, DiscordPunishment punishment) {
        DiscordPunishment removed = punishment.markRemoved(operationKey(work));
        DiscordDeliveryOutcome delivery = gateway.notifyRemoved(removed);
        boolean retry = retryDelivery(delivery, work);
        DiscordDeliveryOutcome recorded = recordedDelivery(delivery, retry);
        DiscordPunishment replacement = removed.withRemovalDeliveryOutcome(
                recorded,
                deliveryError("REMOVE", delivery, retry),
                operationKey(work)
        );
        settle(work, stored, replacement, retry ? List.of(retry(work, WorkType.REMOVE)) : List.of());
    }

    private void retryRemovalNotification(WorkLease work, StoredPunishment stored) {
        DiscordDeliveryOutcome delivery = gateway.notifyRemoved(stored.punishment());
        boolean retry = retryDelivery(delivery, work);
        DiscordDeliveryOutcome recorded = recordedDelivery(delivery, retry);
        DiscordPunishment replacement = stored.punishment().withRemovalDeliveryOutcome(
                recorded,
                deliveryError("REMOVE", delivery, retry),
                operationKey(work)
        );
        settle(work, stored, replacement, retry ? List.of(retry(work, WorkType.REMOVE)) : List.of());
    }

    private void reconcile(WorkLease work, StoredPunishment stored) {
        DiscordPunishment punishment = stored.punishment();
        if (punishment.state() != DiscordPunishmentState.APPLIED || !punishment.externalApplied()) {
            settle(work, stored, punishment, List.of());
            return;
        }
        if (expired(punishment)) {
            DiscordPunishment replacement = punishment.requestRemoval(
                    DiscordPunishmentTermination.EXPIRE, operationKey(work)
            );
            settle(work, stored, replacement, List.of(new WorkSchedule(WorkType.REMOVE, clock.instant())));
            return;
        }
        reconcileExternalEffect(work, stored);
    }

    private void reconcileExternalEffect(WorkLease work, StoredPunishment stored) {
        try {
            gateway.reconcile(stored.punishment());
            settle(work, stored, reconciled(stored.punishment(), Optional.empty(), work),
                    List.of(reconcileLater()));
        } catch (DiscordPunishmentGateway.EffectException failure) {
            settle(work, stored, reconciled(stored.punishment(), Optional.of(failure.errorCode()), work),
                    reconciliationFollowUp(work, failure));
        }
    }

    private List<WorkSchedule> reconciliationFollowUp(
            WorkLease work,
            DiscordPunishmentGateway.EffectException failure
    ) {
        if (failure.retryable()) {
            return List.of(retry(work, WorkType.RECONCILE));
        }
        if (keepsPeriodicReconciliation(failure.errorCode())) {
            return List.of(reconcileLater());
        }
        return List.of();
    }

    private static boolean keepsPeriodicReconciliation(String errorCode) {
        return PERIODIC_RECONCILIATION_ERRORS.contains(errorCode);
    }

    private DiscordPunishment reconciled(
            DiscordPunishment punishment,
            Optional<String> error,
            WorkLease work
    ) {
        return punishment.withProcessingResult(
                DiscordPunishmentState.APPLIED,
                punishment.dmOutcome(),
                punishment.externalApplied(),
                punishment.previousRestriction(),
                error,
                operationKey(work)
        );
    }

    private void settleApplyFailure(
            WorkLease work,
            StoredPunishment stored,
            DiscordPunishmentGateway.EffectException failure
    ) {
        boolean retry = failure.retryable() && work.attemptCount() < MAX_ATTEMPTS;
        DiscordPunishmentState state = retry
                ? DiscordPunishmentState.RETRY_APPLY
                : DiscordPunishmentState.FAILED_APPLY;
        DiscordPunishment replacement = stored.punishment().withProcessingResult(
                state,
                stored.punishment().dmOutcome(),
                stored.punishment().externalApplied(),
                stored.punishment().previousRestriction(),
                Optional.of(failure.errorCode()),
                operationKey(work)
        );
        settle(work, stored, replacement, retry ? List.of(retry(work, WorkType.APPLY)) : List.of());
    }

    private void settleRemoveFailure(
            WorkLease work,
            StoredPunishment stored,
            DiscordPunishmentGateway.EffectException failure
    ) {
        boolean retry = failure.retryable() && work.attemptCount() < MAX_ATTEMPTS;
        DiscordPunishmentState state = retry
                ? DiscordPunishmentState.RETRY_REMOVE
                : DiscordPunishmentState.FAILED_REMOVE;
        DiscordPunishment punishment = stored.punishment();
        DiscordPunishment replacement = punishment.withProcessingResult(
                state,
                punishment.dmOutcome(),
                punishment.externalApplied(),
                punishment.previousRestriction(),
                Optional.of(failure.errorCode()),
                operationKey(work)
        );
        settle(work, stored, replacement, retry ? List.of(retry(work, WorkType.REMOVE)) : List.of());
    }

    private List<WorkSchedule> applyFollowUp(
            DiscordPunishment punishment,
            WorkLease current,
            boolean retryNotification
    ) {
        List<WorkSchedule> schedules = new ArrayList<>(followUp(punishment));
        if (retryNotification) {
            schedules.add(retry(current, WorkType.APPLY));
        }
        return List.copyOf(schedules);
    }

    private List<WorkSchedule> followUp(DiscordPunishment punishment) {
        if (!punishment.intent().reversible()) {
            return List.of();
        }
        List<WorkSchedule> work = new ArrayList<>(2);
        punishment.expiresAt().ifPresent(expires -> work.add(new WorkSchedule(WorkType.REMOVE, expires)));
        work.add(reconcileLater());
        return List.copyOf(work);
    }

    private WorkSchedule reconcileLater() {
        return new WorkSchedule(WorkType.RECONCILE, clock.instant().plus(reconciliationInterval));
    }

    private WorkSchedule retry(WorkLease work, WorkType type) {
        long multiplier = 1L << Math.min(work.attemptCount() - 1, 16);
        Duration delay = BASE_RETRY.multipliedBy(multiplier);
        if (delay.compareTo(MAX_RETRY) > 0) {
            delay = MAX_RETRY;
        }
        return new WorkSchedule(type, clock.instant().plus(delay));
    }

    private void settle(
            WorkLease work,
            StoredPunishment stored,
            DiscordPunishment replacement,
            List<WorkSchedule> next
    ) {
        repository.settle(work, stored, replacement, next, clock.instant());
    }

    private boolean expired(DiscordPunishment punishment) {
        return punishment.expiresAt().map(expiry -> !clock.instant().isBefore(expiry)).orElse(false);
    }

    private static boolean applyPending(DiscordPunishment punishment) {
        return punishment.state() == DiscordPunishmentState.PENDING_APPLY
                || punishment.state() == DiscordPunishmentState.RETRY_APPLY;
    }

    private static boolean requiresRestrictionSnapshot(DiscordPunishment punishment) {
        return punishment.intent().type() == DiscordConsequenceType.CHANNEL_RESTRICTION
                && punishment.previousRestriction().isEmpty();
    }

    private static boolean needsApplyNotificationRetry(DiscordPunishment punishment) {
        return (punishment.state() == DiscordPunishmentState.APPLIED
                || punishment.state() == DiscordPunishmentState.COMPLETED)
                && punishment.dmOutcome() == DiscordDeliveryOutcome.FAILED_RETRYABLE;
    }

    private static boolean needsRemovalNotificationRetry(DiscordPunishment punishment) {
        return switch (punishment.state()) {
            case ENDED, REVOKED, OVERTURNED, EXPIRED ->
                    punishment.removalDmOutcome() == DiscordDeliveryOutcome.FAILED_RETRYABLE;
            default -> false;
        };
    }

    private static boolean retryDelivery(DiscordDeliveryOutcome delivery, WorkLease work) {
        return delivery.retryable() && work.attemptCount() < MAX_ATTEMPTS;
    }

    private static DiscordDeliveryOutcome recordedDelivery(
            DiscordDeliveryOutcome delivery,
            boolean retry
    ) {
        if (delivery == DiscordDeliveryOutcome.FAILED_RETRYABLE && !retry) {
            return DiscordDeliveryOutcome.FAILED_TERMINAL;
        }
        return delivery;
    }

    private static DiscordPunishmentState warningState(
            DiscordDeliveryOutcome delivery,
            boolean retry
    ) {
        if (delivery == DiscordDeliveryOutcome.DELIVERED) {
            return DiscordPunishmentState.COMPLETED;
        }
        return retry ? DiscordPunishmentState.RETRY_APPLY : DiscordPunishmentState.FAILED_APPLY;
    }

    private static Optional<String> deliveryError(
            String phase,
            DiscordDeliveryOutcome delivery,
            boolean retry
    ) {
        if (!delivery.failed()) {
            return Optional.empty();
        }
        if (delivery == DiscordDeliveryOutcome.FAILED_RETRYABLE && !retry) {
            return Optional.of(phase + "_DM_RETRY_EXHAUSTED");
        }
        return Optional.of(phase + (retry ? "_DM_RETRYABLE" : "_DM_FAILED"));
    }

    private static DiscordPunishment terminalWithoutEffect(DiscordPunishment punishment, WorkLease work) {
        DiscordPunishment pendingRemoval = punishment.requestRemoval(
                DiscordPunishmentTermination.EXPIRE, operationKey(work)
        );
        return pendingRemoval.markRemoved(operationKey(work));
    }

    private static String operationKey(WorkLease work) {
        return "d07:work:" + work.workId() + ":" + work.attemptCount();
    }
}
