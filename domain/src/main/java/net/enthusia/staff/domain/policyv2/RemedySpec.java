package net.enthusia.staff.domain.policyv2;

import java.util.Optional;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementPolicy;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

public record RemedySpec(
        String id,
        Type type,
        String description,
        Optional<EnforcementBinding> enforcement
) {
    public enum Type { REMOVE_CONTENT, CONFISCATE, ACCESS_RESTRICTION, CORRECT_PROFILE, OTHER }

    public RemedySpec(String id, Type type, String description) {
        this(id, type, description, Optional.empty());
    }

    public RemedySpec {
        id = PolicyIds.require(id, "remedy id");
        if (type == null || description == null || description.isBlank()) {
            throw new IllegalArgumentException("remedy type and description must be present");
        }
        description = description.trim();
        enforcement = enforcement == null ? Optional.empty() : enforcement;
        enforcement.ifPresent(binding ->
                PolicyV2EnforcementPolicy.requireBinding(type, binding.scope(), binding.condition().type()));
    }

    public record EnforcementBinding(
            Scope scope,
            ConditionTemplate condition
    ) {
        public EnforcementBinding {
            if (scope == null || condition == null) {
                throw new IllegalArgumentException("remedy enforcement binding fields must be present");
            }
        }
    }

    public record ConditionTemplate(
            ConditionType type,
            Optional<String> component,
            Optional<String> componentAttribute,
            Optional<String> prohibitedValueAttribute
    ) {
        private static final int MAX_COMPONENT_LENGTH = 64;

        public ConditionTemplate {
            if (type == null) {
                throw new IllegalArgumentException("remedy enforcement condition type must be present");
            }
            component = normalizeLiteral(component, "profile component");
            componentAttribute = normalizeId(componentAttribute, "component attribute");
            prohibitedValueAttribute = normalizeId(prohibitedValueAttribute, "prohibited value attribute");
            validateShape(type, component, componentAttribute, prohibitedValueAttribute);
        }

        public static ConditionTemplate username(String prohibitedValueAttribute) {
            return new ConditionTemplate(
                    ConditionType.USERNAME,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(prohibitedValueAttribute)
            );
        }

        public static ConditionTemplate profileComponent(
                String component,
                String prohibitedValueAttribute
        ) {
            return new ConditionTemplate(
                    ConditionType.PROFILE_COMPONENT,
                    Optional.of(component),
                    Optional.empty(),
                    Optional.of(prohibitedValueAttribute)
            );
        }

        public static ConditionTemplate profileComponentFromAttribute(
                String componentAttribute,
                String prohibitedValueAttribute
        ) {
            return new ConditionTemplate(
                    ConditionType.PROFILE_COMPONENT,
                    Optional.empty(),
                    Optional.of(componentAttribute),
                    Optional.of(prohibitedValueAttribute)
            );
        }

        public static ConditionTemplate vpnApproval() {
            return new ConditionTemplate(
                    ConditionType.VPN_APPROVAL,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        public static ConditionTemplate manual() {
            return new ConditionTemplate(
                    ConditionType.MANUAL,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private static Optional<String> normalizeId(Optional<String> value, String field) {
            if (value == null) {
                return Optional.empty();
            }
            return value.map(item -> PolicyIds.require(item, field));
        }

        private static Optional<String> normalizeLiteral(Optional<String> value, String field) {
            if (value == null) {
                return Optional.empty();
            }
            return value.map(item -> {
                if (item == null || item.isBlank() || item.trim().length() > MAX_COMPONENT_LENGTH) {
                    throw new IllegalArgumentException(field + " is blank or too long");
                }
                return item.trim();
            });
        }

        private static void validateShape(
                ConditionType type,
                Optional<String> component,
                Optional<String> componentAttribute,
                Optional<String> prohibitedValueAttribute
        ) {
            boolean hasComponent = component.isPresent();
            boolean hasComponentAttribute = componentAttribute.isPresent();
            boolean hasValueAttribute = prohibitedValueAttribute.isPresent();

            boolean valid = switch (type) {
                case USERNAME -> !hasComponent && !hasComponentAttribute && hasValueAttribute;
                case PROFILE_COMPONENT ->
                        hasValueAttribute && (hasComponent ^ hasComponentAttribute);
                case VPN_APPROVAL, MANUAL ->
                        !hasComponent && !hasComponentAttribute && !hasValueAttribute;
            };
            if (!valid) {
                throw new IllegalArgumentException(
                        "remedy enforcement condition metadata does not match " + type
                );
            }
        }
    }
}
