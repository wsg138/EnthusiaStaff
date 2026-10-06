package net.enthusia.staff.domain.policyv2;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HistoryEvaluator {
    private static final double LOG_TWO = Math.log(2.0);

    public HistoryAssessment assess(
            OffensePolicy current,
            Instant asOf,
            List<BehavioralHistoryEntry> history,
            PolicySnapshot snapshot
    ) {
        if (current == null || asOf == null || history == null || snapshot == null) {
            throw new IllegalArgumentException("history evaluation inputs must be present");
        }
        List<BehavioralHistoryEntry> ordered = history.stream()
                .sorted(Comparator.comparing(BehavioralHistoryEntry::occurredAt)
                        .thenComparing(BehavioralHistoryEntry::caseId))
                .toList();
        List<HistoryAssessment.Contribution> contributions = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            evaluateEntry(current, asOf, snapshot, ordered, index).ifPresent(contributions::add);
        }
        double total = contributions.stream().mapToDouble(HistoryAssessment.Contribution::contribution).sum();
        return new HistoryAssessment(total, contributions);
    }

    private java.util.Optional<HistoryAssessment.Contribution> evaluateEntry(
            OffensePolicy current,
            Instant asOf,
            PolicySnapshot snapshot,
            List<BehavioralHistoryEntry> ordered,
            int index
    ) {
        BehavioralHistoryEntry entry = ordered.get(index);
        if (entry.occurredAt().isAfter(asOf)) {
            throw new IllegalArgumentException("history entry occurs after incident time");
        }
        String effectiveId = entry.contributingOffenseId().orElse(null);
        double relationshipWeight = effectiveId == null ? 0.0 : current.historyPolicy().weightFor(effectiveId);
        if (relationshipWeight == 0.0) {
            return java.util.Optional.empty();
        }
        OffensePolicy priorPolicy = snapshot.offense(effectiveId)
                .orElseThrow(() -> new IllegalArgumentException("history references unknown related offense"));
        int priorRelatedCount = countPriorRelated(ordered, index, entry.occurredAt(), priorPolicy);
        DecayResult decay = decay(
                priorPolicy.historyPolicy().decayPolicy(),
                entry.occurredAt(),
                asOf,
                priorRelatedCount
        );
        double contribution = relationshipWeight * decay.factor();
        return java.util.Optional.of(new HistoryAssessment.Contribution(
                entry.caseId(),
                effectiveId,
                relationshipWeight,
                decay.factor(),
                decay.multiplier(),
                priorRelatedCount,
                contribution
        ));
    }

    private static int countPriorRelated(
            List<BehavioralHistoryEntry> ordered,
            int beforeIndex,
            Instant beforeTime,
            OffensePolicy priorPolicy
    ) {
        int count = 0;
        for (int index = 0; index < beforeIndex; index++) {
            BehavioralHistoryEntry candidate = ordered.get(index);
            if (!candidate.occurredAt().isBefore(beforeTime)) {
                continue;
            }
            String candidateId = candidate.contributingOffenseId().orElse(null);
            if (candidateId != null && priorPolicy.historyPolicy().weightFor(candidateId) > 0.0) {
                count++;
            }
        }
        return count;
    }

    private static DecayResult decay(
            DecayPolicy policy,
            Instant occurredAt,
            Instant asOf,
            int priorRelatedCount
    ) {
        if (policy.mode() == DecayPolicy.Mode.NON_DECAYING) {
            return new DecayResult(1.0, 1.0);
        }
        double multiplier = policy.halfLifeMultiplier(priorRelatedCount);
        Duration age = Duration.between(occurredAt, asOf);
        double ageMillis = millis(age);
        double halfLifeMillis = millis(policy.halfLife()) * multiplier;
        return new DecayResult(Math.exp(-LOG_TWO * ageMillis / halfLifeMillis), multiplier);
    }

    private static double millis(Duration duration) {
        return duration.getSeconds() * 1000.0 + duration.getNano() / 1_000_000.0;
    }

    private record DecayResult(double factor, double multiplier) {
    }
}
