package net.enthusia.staff.domain.escalation;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EscalationEngine {
    private static final Duration RECENT_WINDOW = Duration.ofDays(30);
    private static final long DECAY_DAYS = 90L;

    public EscalationDecision decide(ReasonPolicy policy, List<PriorOffense> history, Instant now) {
        if (policy == null || history == null || now == null) {
            throw new IllegalArgumentException("policy, history and now must be present");
        }

        List<PriorOffense> related = history.stream()
                .filter(PriorOffense::contributes)
                .filter(offense -> !offense.overturned())
                .filter(offense -> offense.family().equals(policy.family()))
                .filter(offense -> !offense.endedAt().isAfter(now))
                .sorted(Comparator.comparing(PriorOffense::endedAt))
                .toList();

        int cleanPeriodDecay = cleanPeriodDecay(related, now);
        List<EscalationDecision.Contribution> contributions = new ArrayList<>();
        int rawOrdinal = 0;
        int effectiveOrdinal = 0;
        for (PriorOffense offense : related) {
            int base = offense.severity() > policy.severity() ? 2 : 1;
            int decayedBy = offense.decayEligibility().permitsDecay() ? cleanPeriodDecay : 0;
            int effective = Math.max(0, base - decayedBy);
            rawOrdinal = Math.addExact(rawOrdinal, base);
            effectiveOrdinal = Math.addExact(effectiveOrdinal, effective);
            contributions.add(new EscalationDecision.Contribution(
                    offense.severity(),
                    base,
                    offense.decayEligibility(),
                    decayedBy,
                    effective
            ));
        }

        int recencyBonus = related.stream()
                .max(Comparator.comparing(PriorOffense::endedAt))
                .filter(offense -> Duration.between(offense.endedAt(), now).compareTo(RECENT_WINDOW) <= 0)
                .map(ignored -> 1)
                .orElse(0);
        rawOrdinal = Math.addExact(rawOrdinal, recencyBonus);
        effectiveOrdinal = Math.addExact(effectiveOrdinal, recencyBonus);

        return new EscalationDecision(
                rawOrdinal,
                effectiveOrdinal,
                recencyBonus,
                contributions,
                policy.stepAt(effectiveOrdinal)
        );
    }

    private static int cleanPeriodDecay(List<PriorOffense> related, Instant now) {
        if (related.isEmpty()) {
            return 0;
        }
        Instant cleanPeriodStarted = related.getLast().endedAt();
        long cleanDays = ChronoUnit.DAYS.between(cleanPeriodStarted, now);
        return Math.toIntExact(cleanDays / DECAY_DAYS);
    }
}
