package net.enthusia.staff.domain.policyv2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PolicyConfigurationValidator {
    private PolicyConfigurationValidator() {
    }

    static void validate(List<OffensePolicy> offenses) {
        if (offenses.isEmpty()) {
            throw new IllegalArgumentException("policy snapshot must contain at least one offense");
        }
        Map<String, OffensePolicy> byId = indexOffenses(offenses);
        offenses.forEach(offense -> validateOffense(offense, byId));
    }

    private static Map<String, OffensePolicy> indexOffenses(List<OffensePolicy> offenses) {
        Map<String, OffensePolicy> byId = new HashMap<>();
        for (OffensePolicy offense : offenses) {
            if (offense == null || byId.putIfAbsent(offense.id(), offense) != null) {
                throw new IllegalArgumentException("policy snapshot contains null or duplicate offense IDs");
            }
        }
        return byId;
    }

    private static void validateOffense(OffensePolicy offense, Map<String, OffensePolicy> byId) {
        validateRelationships(offense, byId.keySet());
        Map<String, IncidentAttributeDefinition> attributes = indexAttributes(offense.attributes());
        validateRules(offense.rules(), attributes);
    }

    private static void validateRelationships(OffensePolicy offense, Set<String> knownOffenses) {
        for (String related : offense.historyPolicy().relationshipWeights().keySet()) {
            if (!knownOffenses.contains(related)) {
                throw new IllegalArgumentException("history relationship references unknown offense " + related);
            }
        }
    }

    private static Map<String, IncidentAttributeDefinition> indexAttributes(
            List<IncidentAttributeDefinition> definitions
    ) {
        Map<String, IncidentAttributeDefinition> attributes = new HashMap<>();
        for (IncidentAttributeDefinition definition : definitions) {
            if (definition == null || attributes.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("offense contains null or duplicate attribute IDs");
            }
        }
        return attributes;
    }

    private static void validateRules(
            List<ResolutionRule> rules,
            Map<String, IncidentAttributeDefinition> attributes
    ) {
        Set<String> ids = new HashSet<>();
        for (ResolutionRule rule : rules) {
            if (rule == null || !ids.add(rule.id())) {
                throw new IllegalArgumentException("offense contains null or duplicate resolution rule IDs");
            }
            validatePredicates(rule, attributes);
        }
        rejectOverlappingRules(rules);
    }

    private static void validatePredicates(
            ResolutionRule rule,
            Map<String, IncidentAttributeDefinition> attributes
    ) {
        rule.condition().acceptedValues().forEach((attributeId, values) -> {
            IncidentAttributeDefinition definition = attributes.get(attributeId);
            if (definition == null) {
                throw new IllegalArgumentException("resolution rule references undeclared attribute " + attributeId);
            }
            if (values.stream().anyMatch(value -> !definition.accepts(value))) {
                throw new IllegalArgumentException("resolution rule contains invalid value for " + attributeId);
            }
        });
    }

    private static void rejectOverlappingRules(List<ResolutionRule> rules) {
        for (int left = 0; left < rules.size(); left++) {
            for (int right = left + 1; right < rules.size(); right++) {
                if (rules.get(left).condition().overlaps(rules.get(right).condition())) {
                    throw new IllegalArgumentException("resolution rules overlap: "
                            + rules.get(left).id() + " and " + rules.get(right).id());
                }
            }
        }
    }
}
