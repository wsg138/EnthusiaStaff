package net.enthusia.staff.domain.policyv2.enforcement;

import java.util.Objects;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyV2RemedyBindingSpec;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

/**
 * Resolves persisted/versioned remedy metadata against the stored finding.
 * No free-form remedy description text or mutable current player state is used.
 */
public final class PolicyV2RemedyBindingResolver {
    public ResolvedBinding resolve(RemedySpec remedy, IncidentFinding finding) {
        Objects.requireNonNull(remedy, "remedy");
        Objects.requireNonNull(finding, "finding");
        PolicyV2RemedyBindingSpec binding = remedy.enforcementBinding()
                .orElseThrow(() -> new IllegalArgumentException(
                        "remedy " + remedy.id() + " has no configured enforcement binding"));

        Condition condition = switch (binding.conditionType()) {
            case USERNAME -> Condition.username(stringAttribute(
                    finding,
                    binding.valueAttributeId().orElseThrow()
            ));
            case PROFILE_COMPONENT -> Condition.profileComponent(
                    binding.component().orElseGet(() -> stringAttribute(
                            finding,
                            binding.componentAttributeId().orElseThrow()
                    )),
                    stringAttribute(finding, binding.valueAttributeId().orElseThrow())
            );
            case VPN_APPROVAL -> Condition.vpnApproval();
            case MANUAL -> Condition.manual();
        };

        PolicyV2EnforcementPolicy.requireBinding(remedy, binding.scope(), condition);
        return new ResolvedBinding(binding.scope(), condition);
    }

    private static String stringAttribute(IncidentFinding finding, String attributeId) {
        IncidentAttributeValue value = finding.attributes().get(attributeId);
        if (value instanceof IncidentAttributeValue.TextValue text) {
            return text.value();
        }
        if (value instanceof IncidentAttributeValue.EnumValue enumerated) {
            return enumerated.value();
        }
        throw new IllegalArgumentException(
                "finding " + finding.offenseId() + " is missing string attribute " + attributeId
        );
    }

    public record ResolvedBinding(Scope scope, Condition condition) {
        public ResolvedBinding {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(condition, "condition");
        }
    }
}
