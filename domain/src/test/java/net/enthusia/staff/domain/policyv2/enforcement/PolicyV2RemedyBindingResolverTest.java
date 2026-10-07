package net.enthusia.staff.domain.policyv2.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyV2RemedyBindingSpec;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyBindingResolver.ResolvedBinding;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import org.junit.jupiter.api.Test;

class PolicyV2RemedyBindingResolverTest {
    private final PolicyV2RemedyBindingResolver resolver = new PolicyV2RemedyBindingResolver();

    @Test
    void usernameBindingUsesStoredFindingAttribute() {
        RemedySpec remedy = new RemedySpec(
                "correct-username",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct username",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.NETWORK_ACCESS,
                        ConditionType.USERNAME,
                        Optional.of("prohibited-username"),
                        Optional.empty(),
                        Optional.empty()
                ))
        );

        ResolvedBinding binding = resolver.resolve(
                remedy,
                new IncidentFinding("profile.inappropriate-username", Map.of(
                        "prohibited-username",
                        new IncidentAttributeValue.TextValue("BadName")
                ))
        );

        assertEquals(Scope.NETWORK_ACCESS, binding.scope());
        assertEquals(PolicyV2RemedyEnforcement.Condition.username("BadName"), binding.condition());
    }

    @Test
    void profileBindingCanUseLiteralOrStoredComponent() {
        RemedySpec literal = new RemedySpec(
                "correct-skin",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct skin",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.NETWORK_ACCESS,
                        ConditionType.PROFILE_COMPONENT,
                        Optional.of("prohibited-value"),
                        Optional.of("skin"),
                        Optional.empty()
                ))
        );
        RemedySpec dynamic = new RemedySpec(
                "correct-profile",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct profile",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.NETWORK_ACCESS,
                        ConditionType.PROFILE_COMPONENT,
                        Optional.of("prohibited-value"),
                        Optional.empty(),
                        Optional.of("profile-component")
                ))
        );
        IncidentFinding finding = new IncidentFinding("profile.inappropriate-other", Map.of(
                "prohibited-value", new IncidentAttributeValue.TextValue("hash:bad"),
                "profile-component", new IncidentAttributeValue.EnumValue("cape")
        ));

        assertEquals(
                PolicyV2RemedyEnforcement.Condition.profileComponent("skin", "hash:bad"),
                resolver.resolve(literal, finding).condition()
        );
        assertEquals(
                PolicyV2RemedyEnforcement.Condition.profileComponent("cape", "hash:bad"),
                resolver.resolve(dynamic, finding).condition()
        );
    }

    @Test
    void vpnAndManualBindingsNeedNoDynamicFindingValue() {
        RemedySpec vpn = new RemedySpec(
                "vpn-access",
                RemedySpec.Type.ACCESS_RESTRICTION,
                "Disable VPN",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.NETWORK_ACCESS,
                        ConditionType.VPN_APPROVAL,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ))
        );
        RemedySpec content = new RemedySpec(
                "remove-content",
                RemedySpec.Type.REMOVE_CONTENT,
                "Remove content",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.CONTENT,
                        ConditionType.MANUAL,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ))
        );
        IncidentFinding finding = new IncidentFinding("access.vpn-compliance", Map.of());

        assertEquals(
                PolicyV2RemedyEnforcement.Condition.vpnApproval(),
                resolver.resolve(vpn, finding).condition()
        );
        assertEquals(
                PolicyV2RemedyEnforcement.Condition.manual(),
                resolver.resolve(content, finding).condition()
        );
    }

    @Test
    void missingOrNonStringDynamicAttributeFailsClosed() {
        RemedySpec remedy = new RemedySpec(
                "correct-username",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct username",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.NETWORK_ACCESS,
                        ConditionType.USERNAME,
                        Optional.of("prohibited-username"),
                        Optional.empty(),
                        Optional.empty()
                ))
        );

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                remedy,
                new IncidentFinding("profile.inappropriate-username", Map.of())
        ));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                remedy,
                new IncidentFinding("profile.inappropriate-username", Map.of(
                        "prohibited-username",
                        new IncidentAttributeValue.IntegerValue(42)
                ))
        ));
    }

    @Test
    void legacyRemedyWithoutBindingCannotBeSilentlyInvented() {
        RemedySpec legacy = new RemedySpec(
                "correct-username",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct username"
        );

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                legacy,
                new IncidentFinding("profile.inappropriate-username", Map.of())
        ));
    }
}
