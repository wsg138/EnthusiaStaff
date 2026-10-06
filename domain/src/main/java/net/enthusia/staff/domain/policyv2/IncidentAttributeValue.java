package net.enthusia.staff.domain.policyv2;

public sealed interface IncidentAttributeValue permits IncidentAttributeValue.BooleanValue,
        IncidentAttributeValue.IntegerValue, IncidentAttributeValue.EnumValue,
        IncidentAttributeValue.TextValue {

    record BooleanValue(boolean value) implements IncidentAttributeValue {
    }

    record IntegerValue(long value) implements IncidentAttributeValue {
    }

    record EnumValue(String value) implements IncidentAttributeValue {
        public EnumValue {
            value = PolicyIds.require(value, "enum value");
        }
    }

    record TextValue(String value) implements IncidentAttributeValue {
        public TextValue {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("text value must not be blank");
            }
            value = value.trim();
        }
    }
}
