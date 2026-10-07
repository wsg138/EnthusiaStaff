package net.enthusia.staff.paper.punishment.policyv2;

import java.util.Comparator;
import java.util.Locale;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;

final class PolicyV2QuestionInput {
    private PolicyV2QuestionInput() {
    }

    static IncidentAttributeValue parse(IncidentAttributeDefinition definition, String input) {
        if (definition == null || input == null || input.isBlank()) {
            throw new IllegalArgumentException("A Policy v2 answer is required");
        }
        IncidentAttributeValue value = switch (definition.kind()) {
            case BOOLEAN -> booleanValue(input);
            case INTEGER -> integerValue(input);
            case ENUM -> enumValue(definition, input);
            case TEXT -> new IncidentAttributeValue.TextValue(input);
        };
        if (!definition.accepts(value)) {
            throw new IllegalArgumentException("That answer is outside the configured Policy v2 choices");
        }
        return value;
    }

    static String prompt(IncidentAttributeDefinition definition) {
        return switch (definition.kind()) {
            case BOOLEAN -> "Type yes or no";
            case INTEGER -> "Type a number from " + definition.minimum() + " to " + definition.maximum();
            case ENUM -> "Type one of: " + definition.allowedValues().stream()
                    .sorted(Comparator.naturalOrder())
                    .map(PolicyV2ReviewPresentation::humanize)
                    .collect(java.util.stream.Collectors.joining(", "));
            case TEXT -> "Type the factual answer";
        };
    }

    private static IncidentAttributeValue booleanValue(String input) {
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "yes", "y", "true" -> new IncidentAttributeValue.BooleanValue(true);
            case "no", "n", "false" -> new IncidentAttributeValue.BooleanValue(false);
            default -> throw new IllegalArgumentException("Type yes or no");
        };
    }

    private static IncidentAttributeValue integerValue(String input) {
        try {
            return new IncidentAttributeValue.IntegerValue(Long.parseLong(input.trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Type a whole number", exception);
        }
    }

    private static IncidentAttributeValue enumValue(
            IncidentAttributeDefinition definition,
            String input
    ) {
        String normalized = input.trim();
        return definition.allowedValues().stream()
                .filter(value -> value.equalsIgnoreCase(normalized)
                        || PolicyV2ReviewPresentation.humanize(value).equalsIgnoreCase(normalized))
                .findFirst()
                .map(IncidentAttributeValue.EnumValue::new)
                .orElseThrow(() -> new IllegalArgumentException("Choose one of the configured options"));
    }
}
