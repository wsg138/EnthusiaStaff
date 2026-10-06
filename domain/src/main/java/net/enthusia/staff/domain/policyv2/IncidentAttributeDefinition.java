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
    private static final int MIN_TEXT_LENGTH = 1;

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
            case BOOLEAN -> acceptsBoolean(value);
            case INTEGER -> acceptsInteger(value);
            case ENUM -> acceptsEnum(value);
            case TEXT -> acceptsText(value);
        };
    }

    private boolean acceptsBoolean(IncidentAttributeValue value) {
        return value instanceof IncidentAttributeValue.BooleanValue;
    }

    private boolean acceptsInteger(IncidentAttributeValue value) {
        if (!(value instanceof IncidentAttributeValue.IntegerValue integer)) {
            return false;
        }
        return integer.value() >= minimum && integer.value() <= maximum;
    }

    private boolean acceptsEnum(IncidentAttributeValue value) {
        return value instanceof IncidentAttributeValue.EnumValue enumValue
                && allowedValues.contains(enumValue.value());
    }

    private boolean acceptsText(IncidentAttributeValue value) {
        return value instanceof IncidentAttributeValue.TextValue text
                && text.value().length() <= maximumLength;
    }

    private static void validateShape(
            Kind kind,
            Set<String> allowed,
            Long minimum,
            Long maximum,
            Integer maxLength
    ) {
        switch (kind) {
            case BOOLEAN -> validateBooleanShape(allowed, minimum, maximum, maxLength);
            case INTEGER -> validateIntegerShape(allowed, minimum, maximum, maxLength);
            case ENUM -> validateEnumShape(allowed, minimum, maximum, maxLength);
            case TEXT -> validateTextShape(allowed, minimum, maximum, maxLength);
        }
    }

    private static void validateBooleanShape(Set<String> allowed, Long minimum, Long maximum, Integer maxLength) {
        requireNoAllowedValues(allowed);
        requireNoNumericBounds(minimum, maximum);
        requireNoMaximumLength(maxLength);
    }

    private static void validateIntegerShape(Set<String> allowed, Long minimum, Long maximum, Integer maxLength) {
        requireNoAllowedValues(allowed);
        if (minimum == null || maximum == null || minimum > maximum) {
            throw new IllegalArgumentException("integer attributes require an ordered range");
        }
        requireNoMaximumLength(maxLength);
    }

    private static void validateEnumShape(Set<String> allowed, Long minimum, Long maximum, Integer maxLength) {
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("enum attributes require allowed values");
        }
        allowed.forEach(value -> PolicyIds.require(value, "allowed enum value"));
        requireNoNumericBounds(minimum, maximum);
        requireNoMaximumLength(maxLength);
    }

    private static void validateTextShape(Set<String> allowed, Long minimum, Long maximum, Integer maxLength) {
        requireNoAllowedValues(allowed);
        requireNoNumericBounds(minimum, maximum);
        if (maxLength == null || maxLength < MIN_TEXT_LENGTH) {
            throw new IllegalArgumentException("text attributes require a positive maximum length");
        }
    }

    private static void requireNoAllowedValues(Set<String> allowed) {
        if (!allowed.isEmpty()) {
            throw new IllegalArgumentException("allowed values apply only to enum attributes");
        }
    }

    private static void requireNoNumericBounds(Long minimum, Long maximum) {
        if (minimum != null || maximum != null) {
            throw new IllegalArgumentException("numeric bounds apply only to integer attributes");
        }
    }

    private static void requireNoMaximumLength(Integer maxLength) {
        if (maxLength != null) {
            throw new IllegalArgumentException("maximum length applies only to text attributes");
        }
    }
}
