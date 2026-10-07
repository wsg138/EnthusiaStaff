package net.enthusia.staff.domain.policyv2.enforcement;

import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.RemedySpec.ConditionTemplate;
import net.enthusia.staff.domain.policyv2.RemedySpec.EnforcementBinding;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

public final class PolicyV2RemedyBindingResolver {
    public Optional<ResolvedBinding> resolve(RemedySpec remedy, IncidentFinding finding) {
        if (remedy == null || finding == null) {
            throw new IllegalArgumentException("remedy binding inputs must be present");
        }
        return remedy.enforcement().map(binding -> resolveRequired(remedy, finding, binding));
    }

    public ResolvedBinding require(RemedySpec remedy, IncidentFinding finding) {
        return resolve(remedy, finding)
                .orElseThrow(() -> new IllegalArgumentException(
                        "remedy " + remedy.id() + " has no configured enforcement binding"
                ));
    }

    private static ResolvedBinding resolveRequired(
            RemedySpec remedy,
            IncidentFinding finding,
            EnforcementBinding binding
    ) {
        Condition condition = condition(binding.condition(), finding.attributes());
        PolicyV2EnforcementPolicy.requireBinding(remedy, binding.scope(), condition);
        return new ResolvedBinding(binding.scope(), condition);
    }

    private static Condition condition(
            ConditionTemplate template,
            Map<String, IncidentAttributeValue> attributes
    ) {
        return switch (template.type()) {
            case USERNAME -> Condition.username(
                    stringAttribute(attributes, template.prohibitedValueAttribute().orElseThrow())
            );
            case PROFILE_COMPONENT -> Condition.profileComponent(
                    profileComponent(template, attributes),
                    stringAttribute(attributes, template.prohibitedValueAttribute().orElseThrow())
            );
            case VPN_APPROVAL -> Condition.vpnApproval();
            case MANUAL -> Condition.manual();
        };
    }

    private static String profileComponent(
            ConditionTemplate template,
            Map<String, IncidentAttributeValue> attributes
    ) {
        return template.component().orElseGet(() ->
                stringAttribute(attributes, template.componentAttribute().orElseThrow())
        );
    }

    private static String stringAttribute(
            Map<String, IncidentAttributeValue> attributes,
            String attributeId
    ) {
        IncidentAttributeValue value = attributes.get(attributeId);
        if (value instanceof IncidentAttributeValue.TextValue text) {
            return text.value();
        }
        if (value instanceof IncidentAttributeValue.EnumValue enumeration) {
            return enumeration.value();
        }
        throw new IllegalArgumentException(
                "remedy enforcement binding requires text/enum finding attribute " + attributeId
        );
    }

    public record ResolvedBinding(Scope scope, Condition condition) {
        public ResolvedBinding {
            if (scope == null || condition == null) {
                throw new IllegalArgumentException("resolved remedy binding fields must be present");
            }
        }
    }
}
