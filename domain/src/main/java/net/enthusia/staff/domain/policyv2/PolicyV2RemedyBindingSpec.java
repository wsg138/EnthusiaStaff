package net.enthusia.staff.domain.policyv2;

import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

/**
 * Versioned metadata that deterministically maps a configured remedy into the
 * typed W3B enforcement boundary.
 */
public record PolicyV2RemedyBindingSpec(
        Scope scope,
        ConditionType conditionType,
        Optional<String> valueAttributeId,
        Optional<String> component,
        Optional<String> componentAttributeId
) {
    public PolicyV2RemedyBindingSpec {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(conditionType, "conditionType");
        valueAttributeId = normalizeId(valueAttributeId, "value attribute id");
        component = normalizeId(component, "profile component");
        componentAttributeId = normalizeId(componentAttributeId, "component attribute id");
        validateShape(conditionType, valueAttributeId, component, componentAttributeId);
    }

    private static Optional<String> normalizeId(Optional<String> value, String field) {
        Objects.requireNonNull(value, field);
        return value.map(item -> PolicyIds.require(item, field));
    }

    private static void validateShape(
            ConditionType type,
            Optional<String> valueAttributeId,
            Optional<String> component,
            Optional<String> componentAttributeId
    ) {
        boolean hasValue = valueAttributeId.isPresent();
        boolean hasComponent = component.isPresent();
        boolean hasComponentAttribute = componentAttributeId.isPresent();

        boolean valid = switch (type) {
            case USERNAME -> hasValue && !hasComponent && !hasComponentAttribute;
            case PROFILE_COMPONENT -> hasValue && hasComponent != hasComponentAttribute;
            case VPN_APPROVAL, MANUAL -> !hasValue && !hasComponent && !hasComponentAttribute;
        };
        if (!valid) {
            throw new IllegalArgumentException("remedy enforcement binding shape does not match its condition type");
        }
    }
}
