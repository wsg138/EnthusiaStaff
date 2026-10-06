package net.enthusia.staff.domain.policyv2;

import java.time.Instant;
import java.util.List;

public final class PolicyResolver {
    private static final int EXPECTED_RULE_MATCH_COUNT = 1;

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
        requireInputs(snapshot, finding, incidentAt, history);
        OffensePolicy offense = snapshot.offense(finding.offenseId()).orElse(null);
        if (offense == null) {
            return review(snapshot, finding, "policy-gap.unknown-offense", HistoryAssessment.empty());
        }
        if (!validFinding(offense, finding)) {
            return review(snapshot, finding, "policy-gap.invalid-attributes", HistoryAssessment.empty());
        }
        return resolveConfigured(snapshot, finding, incidentAt, history, offense);
    }

    private PolicyResolution resolveConfigured(
            PolicySnapshot snapshot,
            IncidentFinding finding,
            Instant incidentAt,
            List<BehavioralHistoryEntry> history,
            OffensePolicy offense
    ) {
        HistoryAssessment assessment;
        try {
            assessment = historyEvaluator.assess(offense, incidentAt, history, snapshot);
        } catch (IllegalArgumentException exception) {
            return review(snapshot, finding, "policy-gap.invalid-history", HistoryAssessment.empty());
        }
        List<ResolutionRule> matches = matchingRules(offense, finding, assessment);
        if (matches.size() != EXPECTED_RULE_MATCH_COUNT) {
            String reason = matches.isEmpty() ? "policy-gap.no-match" : "policy-gap.ambiguous-match";
            return review(snapshot, finding, reason, assessment);
        }
        return resolved(snapshot, finding, matches.getFirst(), assessment);
    }

    private static List<ResolutionRule> matchingRules(
            OffensePolicy offense,
            IncidentFinding finding,
            HistoryAssessment assessment
    ) {
        return offense.rules().stream()
                .filter(rule -> rule.condition().matches(finding, assessment.totalContribution()))
                .toList();
    }

    private static PolicyResolution resolved(
            PolicySnapshot snapshot,
            IncidentFinding finding,
            ResolutionRule rule,
            HistoryAssessment assessment
    ) {
        return new PolicyResolution(
                snapshot.version(),
                finding.offenseId(),
                rule.id(),
                rule.action(),
                rule.remedies(),
                assessment
        );
    }

    private static PolicyResolution review(
            PolicySnapshot snapshot,
            IncidentFinding finding,
            String reason,
            HistoryAssessment assessment
    ) {
        return PolicyResolution.requiresReview(
                snapshot.version(),
                finding.offenseId(),
                reason,
                assessment
        );
    }

    private static void requireInputs(
            PolicySnapshot snapshot,
            IncidentFinding finding,
            Instant incidentAt,
            List<BehavioralHistoryEntry> history
    ) {
        if (snapshot == null || finding == null || incidentAt == null || history == null) {
            throw new IllegalArgumentException("resolution inputs must be present");
        }
    }

    private static boolean validFinding(OffensePolicy offense, IncidentFinding finding) {
        if (hasUndeclaredAttributes(offense, finding)) {
            return false;
        }
        return offense.attributes().stream().allMatch(definition -> validAttribute(definition, finding));
    }

    private static boolean hasUndeclaredAttributes(OffensePolicy offense, IncidentFinding finding) {
        return finding.attributes().keySet().stream().anyMatch(key -> offense.attributes().stream()
                .noneMatch(definition -> definition.id().equals(key)));
    }

    private static boolean validAttribute(
            IncidentAttributeDefinition definition,
            IncidentFinding finding
    ) {
        IncidentAttributeValue value = finding.attributes().get(definition.id());
        if (value == null) {
            return !definition.required();
        }
        return definition.accepts(value);
    }
}
