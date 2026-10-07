package net.enthusia.staff.domain.policyv2.enforcement;

import java.util.List;
import java.util.Set;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public final class PolicyV2EnforcementPolicy {
    private static final Set<String> NON_BANNABLE_COMPLIANCE = Set.of(
            "chat.language.non-english-public",
            "language.non-english-public-chat",
            "access.vpn-compliance"
    );
    private static final Set<Binding> ALLOWED_BINDINGS = Set.of(
            new Binding(RemedySpec.Type.CORRECT_PROFILE, Scope.NETWORK_ACCESS, ConditionType.USERNAME),
            new Binding(RemedySpec.Type.CORRECT_PROFILE, Scope.NETWORK_ACCESS, ConditionType.PROFILE_COMPONENT),
            new Binding(RemedySpec.Type.ACCESS_RESTRICTION, Scope.NETWORK_ACCESS, ConditionType.VPN_APPROVAL),
            new Binding(RemedySpec.Type.ACCESS_RESTRICTION, Scope.NETWORK_ACCESS, ConditionType.MANUAL),
            new Binding(RemedySpec.Type.ACCESS_RESTRICTION, Scope.REPORT_SUBMISSION, ConditionType.MANUAL),
            new Binding(RemedySpec.Type.ACCESS_RESTRICTION, Scope.MARKET_ACCESS, ConditionType.MANUAL),
            new Binding(RemedySpec.Type.ACCESS_RESTRICTION, Scope.REPUTATION_ACCESS, ConditionType.MANUAL),
            new Binding(RemedySpec.Type.REMOVE_CONTENT, Scope.CONTENT, ConditionType.MANUAL),
            new Binding(RemedySpec.Type.CONFISCATE, Scope.ASSET, ConditionType.MANUAL)
    );

    private PolicyV2EnforcementPolicy() {
    }

    public static void requireSafeOutcome(String offenseId, List<SanctionSpec> sanctions) {
        String checkedId = PolicyV2RemedyEnforcement.requireText(offenseId, "offense id", 96);
        if (sanctions == null || sanctions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("sanctions must be present");
        }
        if (NON_BANNABLE_COMPLIANCE.contains(checkedId)
                && sanctions.stream().anyMatch(sanction -> sanction.type().isBan())) {
            throw new IllegalArgumentException(checkedId + " cannot directly resolve to a ban");
        }
    }

    public static void requireBinding(RemedySpec remedy, Scope scope, Condition condition) {
        if (remedy == null || scope == null || condition == null) {
            throw new IllegalArgumentException("remedy enforcement binding must be present");
        }
        Binding binding = new Binding(remedy.type(), scope, condition.type());
        if (!ALLOWED_BINDINGS.contains(binding)) {
            throw new IllegalArgumentException(
                    "remedy " + remedy.id() + " cannot use " + scope + " / " + condition.type()
            );
        }
    }

    private record Binding(RemedySpec.Type remedyType, Scope scope, ConditionType conditionType) {
    }
}
