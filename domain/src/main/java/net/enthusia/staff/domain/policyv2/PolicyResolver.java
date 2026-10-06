package net.enthusia.staff.domain.policyv2;

import java.time.Instant;
import java.util.List;

public final class PolicyResolver {
    private final HistoryEvaluator historyEvaluator;

    public PolicyResolver() {
        this(new HistoryEvaluator());
    }

    public PolicyResolver(HistoryEvaluator historyEvaluator) {
        if (historyEvaluator == null) {
            throw new IllegalArgumentException("history evaluator must be present");
        }
        this.historyEvaluator = historyEvaluator;
    }

    public PolicyResolution resolve(
            PolicySnapshot snapshot,
            IncidentFinding finding,
            Instant incidentAt,
            List<BehavioralHistoryEntry> history
    ) {
        if (snapshot == null || finding == null || incidentAt == null || history == null) {
            throw new IllegalArgumentException("resolution inputs must be present");
        }
        OffensePolicy offense = snapshot.offense(finding.offenseId()).orElse(null);
        if (offense == null) {
            return PolicyResolution.requiresReview(
                    snapshot.version(),
                    finding.offenseId(),
                    "policy-gap.unknown-offense",
                    HistoryAssessment.empty()
            );
        }
        if (!validFinding(offense, finding)) {
            return PolicyResolution.requiresReview(
                    snapshot.version(),
                    finding.offenseId(),
                    "policy-gap.invalid-attributes",
                    HistoryAssessment.empty()
            );
        }
        HistoryAssessment assessment;
        try {
            assessment = historyEvaluator.assess(offense, incidentAt, history, snapshot);
        } catch (IllegalArgumentException exception) {
            return PolicyResolution.requiresReview(
                    snapshot.version(),
                    finding.offenseId(),
                    "policy-gap.invalid-history",
                    HistoryAssessment.empty()
            );
        }
        List<ResolutionRule> matches = offense.rules().stream()
                .filter(rule -> rule.condition().matches(finding, assessment.totalContribution()))
                .toList();
        if (matches.size() != 1) {
            String reason = matches.isEmpty() ? "policy-gap.no-match" : "policy-gap.ambiguous-match";
            return PolicyResolution.requiresReview(snapshot.version(), finding.offenseId(), reason, assessment);
        }
        ResolutionRule rule = matches.getFirst();
        return new PolicyResolution(
                snapshot.version(),
                finding.offenseId(),
                rule.id(),
                rule.action(),
                rule.remedies(),
                assessment
        );
    }

    private static boolean validFinding(OffensePolicy offense, IncidentFinding finding) {
        if (finding.attributes().keySet().stream().anyMatch(key -> offense.attributes().stream()
                .noneMatch(definition -> definition.id().equals(key)))) {
            return false;
        }
        for (IncidentAttributeDefinition definition : offense.attributes()) {
            IncidentAttributeValue value = finding.attributes().get(definition.id());
            if (value == null && definition.required()) {
                return false;
            }
            if (value != null && !definition.accepts(value)) {
                return false;
            }
        }
        return true;
    }
}
