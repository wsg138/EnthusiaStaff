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
        boolean valid = switch (remedy.type()) {
            case CORRECT_PROFILE -> profileBinding(scope, condition.type());
            case ACCESS_RESTRICTION -> accessBinding(scope, condition.type());
            case REMOVE_CONTENT -> scope == Scope.CONTENT && condition.type() == ConditionType.MANUAL;
            case CONFISCATE -> scope == Scope.ASSET && condition.type() == ConditionType.MANUAL;
            case OTHER -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "remedy " + remedy.id() + " cannot use " + scope + " / " + condition.type()
            );
        }
    }

    private static boolean profileBinding(Scope scope, ConditionType condition) {
        return scope == Scope.NETWORK_ACCESS
                && (condition == ConditionType.USERNAME || condition == ConditionType.PROFILE_COMPONENT);
    }

    private static boolean accessBinding(Scope scope, ConditionType condition) {
        if (scope == Scope.NETWORK_ACCESS) {
            return condition == ConditionType.VPN_APPROVAL || condition == ConditionType.MANUAL;
        }
        return switch (scope) {
            case REPORT_SUBMISSION, MARKET_ACCESS, REPUTATION_ACCESS -> condition == ConditionType.MANUAL;
            default -> false;
        };
    }
}
