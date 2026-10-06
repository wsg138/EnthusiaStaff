package net.enthusia.staff.domain.policyv2;

import java.util.Set;

public record IncidentAttributeDefinition(
        String id,
        Kind kind,
        boolean required,
        Set<String> allowedValues,
        Long minimum,
        Long maximum,
        Integer maximumLength
) {
    public enum Kind { BOOLEAN, INTEGER, ENUM, TEXT }

    public IncidentAttributeDefinition {
        id = PolicyIds.require(id, "attribute id");
        if (kind == null) {
            throw new IllegalArgumentException("attribute kind must be present");
        }
        allowedValues = Set.copyOf(allowedValues == null ? Set.of() : allowedValues);
        validateShape(kind, allowedValues, minimum, maximum, maximumLength);
    }

    public static IncidentAttributeDefinition booleanValue(String id, boolean required) {
        return new IncidentAttributeDefinition(id, Kind.BOOLEAN, required, Set.of(), null, null, null);
    }

    public static IncidentAttributeDefinition integerValue(String id, boolean required, long minimum, long maximum) {
        return new IncidentAttributeDefinition(id, Kind.INTEGER, required, Set.of(), minimum, maximum, null);
    }

    public static IncidentAttributeDefinition enumValue(String id, boolean required, Set<String> allowedValues) {
        return new IncidentAttributeDefinition(id, Kind.ENUM, required, allowedValues, null, null, null);
    }

    public static IncidentAttributeDefinition textValue(String id, boolean required, int maximumLength) {
        return new IncidentAttributeDefinition(id, Kind.TEXT, required, Set.of(), null, null, maximumLength);
    }

    public boolean accepts(IncidentAttributeValue value) {
        return switch (kind) {
            case BOOLEAN -> value instanceof IncidentAttributeValue.BooleanValue;
            case INTEGER -> value instanceof IncidentAttributeValue.IntegerValue integer
                    && integer.value() >= minimum && integer.value() <= maximum;
            case ENUM -> value instanceof IncidentAttributeValue.EnumValue enumValue
                    && allowedValues.contains(enumValue.value());
            case TEXT -> value instanceof IncidentAttributeValue.TextValue text
                    && text.value().length() <= maximumLength;
        };
    }

    private static void validateShape(Kind kind, Set<String> allowed, Long minimum, Long maximum, Integer maxLength) {
        if (kind == Kind.ENUM && allowed.isEmpty()) {
            throw new IllegalArgumentException("enum attributes require allowed values");
        }
        allowed.forEach(value -> PolicyIds.require(value, "allowed enum value"));
        if (kind == Kind.INTEGER && (minimum == null || maximum == null || minimum > maximum)) {
            throw new IllegalArgumentException("integer attributes require an ordered range");
        }
        if (kind == Kind.TEXT && (maxLength == null || maxLength < 1)) {
            throw new IllegalArgumentException("text attributes require a positive maximum length");
        }
        if (kind != Kind.ENUM && !allowed.isEmpty()) {
            throw new IllegalArgumentException("allowed values apply only to enum attributes");
        }
        if (kind != Kind.INTEGER && (minimum != null || maximum != null)) {
            throw new IllegalArgumentException("numeric bounds apply only to integer attributes");
        }
        if (kind != Kind.TEXT && maxLength != null) {
            throw new IllegalArgumentException("maximum length applies only to text attributes");
        }
    }
}
