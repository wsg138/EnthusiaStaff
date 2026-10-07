package net.enthusia.staff.domain.policyv2;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HistoryEvaluator {
    private static final double LOG_TWO = Math.log(2.0);
    private static final double UNRELATED_WEIGHT = 0.0;

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
        double relationshipWeight = effectiveId == null ? UNRELATED_WEIGHT
                : current.historyPolicy().weightFor(effectiveId);
        if (relationshipWeight == UNRELATED_WEIGHT) {
            return java.util.Optional.empty();
        }
        OffensePolicy priorPolicy = snapshot.offense(effectiveId)
                .orElseThrow(() -> new IllegalArgumentException("history references unknown related offense"));
        PatternMemory pattern = patternBefore(ordered, index, entry.occurredAt(), priorPolicy);
        DecayResult decay = decay(
                priorPolicy.historyPolicy().decayPolicy(),
                entry.occurredAt(),
                asOf,
                pattern.persistence()
        );
        double contribution = relationshipWeight * decay.factor();
        return java.util.Optional.of(new HistoryAssessment.Contribution(
                entry.caseId(),
                effectiveId,
                relationshipWeight,
                decay.factor(),
                decay.multiplier(),
                pattern.relatedCount(),
                contribution,
                pattern.persistence()
        ));
    }

    private static PatternMemory patternBefore(
            List<BehavioralHistoryEntry> ordered,
            int beforeIndex,
            Instant beforeTime,
            OffensePolicy priorPolicy
    ) {
        int count = 0;
        double persistence = 0.0;
        DecayPolicy decayPolicy = priorPolicy.historyPolicy().decayPolicy();
        for (int index = 0; index < beforeIndex; index++) {
            BehavioralHistoryEntry candidate = ordered.get(index);
            if (!candidate.occurredAt().isBefore(beforeTime)) {
                continue;
            }
            String candidateId = candidate.contributingOffenseId().orElse(null);
            double relationshipWeight = candidateId == null
                    ? UNRELATED_WEIGHT
                    : priorPolicy.historyPolicy().weightFor(candidateId);
            if (relationshipWeight <= UNRELATED_WEIGHT) {
                continue;
            }
            count++;
            if (decayPolicy.mode() == DecayPolicy.Mode.EXPONENTIAL) {
                Duration age = Duration.between(candidate.occurredAt(), beforeTime);
                persistence += relationshipWeight * exponentialFactor(age, decayPolicy.patternHalfLife());
            }
        }
        return new PatternMemory(count, persistence);
    }

    private static DecayResult decay(
            DecayPolicy policy,
            Instant occurredAt,
            Instant asOf,
            double patternPersistence
    ) {
        if (policy.mode() == DecayPolicy.Mode.NON_DECAYING) {
            return new DecayResult(1.0, 1.0);
        }
        double multiplier = policy.halfLifeMultiplier(patternPersistence);
        Duration age = Duration.between(occurredAt, asOf);
        double effectiveHalfLifeMillis = millis(policy.halfLife()) * multiplier;
        return new DecayResult(
                Math.exp(-LOG_TWO * millis(age) / effectiveHalfLifeMillis),
                multiplier
        );
    }

    private static double exponentialFactor(Duration age, Duration halfLife) {
        return Math.exp(-LOG_TWO * millis(age) / millis(halfLife));
    }

    private static double millis(Duration duration) {
        return duration.getSeconds() * 1000.0 + duration.getNano() / 1_000_000.0;
    }

    private record PatternMemory(int relatedCount, double persistence) {
    }

    private record DecayResult(double factor, double multiplier) {
    }
}
