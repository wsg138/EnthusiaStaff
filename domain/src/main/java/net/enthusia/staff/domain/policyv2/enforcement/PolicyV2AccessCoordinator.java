package net.enthusia.staff.domain.policyv2.enforcement;

import java.time.Instant;
import java.util.Objects;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.Correction;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.Decision;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator.Observation;

public final class PolicyV2AccessCoordinator {
    private final PolicyV2AccessEvaluator evaluator;
    private final PolicyV2RemedyService remedies;

    public PolicyV2AccessCoordinator(PolicyV2AccessEvaluator evaluator, PolicyV2RemedyService remedies) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.remedies = Objects.requireNonNull(remedies, "remedies");
    }

    public Decision evaluateAndRepair(Observation observation, Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Decision initial = evaluator.evaluate(observation);
        for (Correction correction : initial.corrections()) {
            remedies.satisfyAutomatically(
                    correction.caseId(),
                    correction.remedyId(),
                    correction.expectedRevision(),
                    "Observed compliant profile/access state",
                    automaticKey(correction),
                    occurredAt
            );
        }
        return initial.corrections().isEmpty() ? initial : evaluator.evaluate(observation);
    }

    private static String automaticKey(Correction correction) {
        return "auto-correction|" + correction.caseId() + '|' + correction.remedyId()
                + '|' + correction.expectedRevision();
    }
}
