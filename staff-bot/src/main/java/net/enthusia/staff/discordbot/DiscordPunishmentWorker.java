package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
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
    private static final int CLAIM_LIMIT = 20;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofSeconds(45);
    private static final Duration BASE_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofMinutes(5);

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
        }
    }

    private void apply(WorkLease work, StoredPunishment stored) {
        DiscordPunishment punishment = stored.punishment();
        if (punishment.state() != DiscordPunishmentState.PENDING_APPLY
                && punishment.state() != DiscordPunishmentState.RETRY_APPLY) {
            settle(work, stored, punishment, List.of());
            return;
        }
        if (expired(punishment)) {
            settle(work, stored, terminalWithoutEffect(punishment, work), List.of());
            return;
        }
        try {
            DiscordPunishmentGateway.ApplyResult result = gateway.apply(punishment);
            DiscordPunishmentState state = punishment.intent().reversible()
                    ? DiscordPunishmentState.APPLIED
                    : DiscordPunishmentState.COMPLETED;
            DiscordPunishment replacement = punishment.withProcessingResult(
                    state, result.deliveryOutcome(), true, result.previousRestriction(),
                    Optional.empty(), operationKey(work)
            );
            settle(work, stored, replacement, followUp(replacement));
        } catch (DiscordPunishmentGateway.EffectException failure) {
            settleApplyFailure(work, stored, failure);
        }
    }

    private void remove(WorkLease work, StoredPunishment stored) {
        DiscordPunishment punishment = stored.punishment();
        if (!punishment.state().removalPending()) {
            settle(work, stored, punishment, List.of());
            return;
        }
        if (!punishment.externalApplied()) {
            settle(work, stored, punishment.markRemoved(operationKey(work)), List.of());
            return;
        }
        try {
            gateway.remove(punishment);
            settle(work, stored, punishment.markRemoved(operationKey(work)), List.of());
        } catch (DiscordPunishmentGateway.EffectException failure) {
            settleRemoveFailure(work, stored, failure);
        }
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
        try {
            gateway.reconcile(punishment);
            settle(work, stored, reconciled(punishment, Optional.empty(), work), List.of(reconcileLater()));
        } catch (DiscordPunishmentGateway.EffectException failure) {
            List<WorkSchedule> next = failure.retryable()
                    ? List.of(retry(work, WorkType.RECONCILE))
                    : List.of();
            settle(work, stored, reconciled(punishment, Optional.of(failure.errorCode()), work), next);
        }
    }

    private DiscordPunishment reconciled(
            DiscordPunishment punishment,
            Optional<String> error,
            WorkLease work
    ) {
        return punishment.withProcessingResult(
                DiscordPunishmentState.APPLIED,
                punishment.dmOutcome(),
                true,
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
        DiscordDeliveryOutcome delivery = failure.deliveryOutcome() == DiscordDeliveryOutcome.NOT_ATTEMPTED
                ? stored.punishment().dmOutcome()
                : failure.deliveryOutcome();
        DiscordPunishment replacement = stored.punishment().withProcessingResult(
                state, delivery, false, stored.punishment().previousRestriction(),
                Optional.of(failure.errorCode()), operationKey(work)
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
                state, punishment.dmOutcome(), true, punishment.previousRestriction(),
                Optional.of(failure.errorCode()), operationKey(work)
        );
        settle(work, stored, replacement, retry ? List.of(retry(work, WorkType.REMOVE)) : List.of());
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
