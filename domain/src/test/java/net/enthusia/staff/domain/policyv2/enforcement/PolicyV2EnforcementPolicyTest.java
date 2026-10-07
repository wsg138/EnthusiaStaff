package net.enthusia.staff.domain.policyv2.enforcement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.PolicyV2RemedyBindingSpec;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2EnforcementPolicyTest {
    @Test
    void profileAndRestrictionBindingsAreExplicitAllowLists() {
        RemedySpec profile = new RemedySpec(
                "correct-username",
                RemedySpec.Type.CORRECT_PROFILE,
                "Correct the prohibited username"
        );
        RemedySpec access = new RemedySpec(
                "vpn-access",
                RemedySpec.Type.ACCESS_RESTRICTION,
                "Disable or approve the VPN"
        );

        assertDoesNotThrow(() -> PolicyV2EnforcementPolicy.requireBinding(
                profile, Scope.NETWORK_ACCESS, Condition.username("BadName")));
        assertDoesNotThrow(() -> PolicyV2EnforcementPolicy.requireBinding(
                access, Scope.NETWORK_ACCESS, Condition.vpnApproval()));
        assertDoesNotThrow(() -> PolicyV2EnforcementPolicy.requireBinding(
                access, Scope.REPORT_SUBMISSION, Condition.manual()));

        assertThrows(IllegalArgumentException.class, () -> PolicyV2EnforcementPolicy.requireBinding(
                profile, Scope.MARKET_ACCESS, Condition.username("BadName")));
        assertThrows(IllegalArgumentException.class, () -> PolicyV2EnforcementPolicy.requireBinding(
                access, Scope.CONTENT, Condition.manual()));
    }

    @Test
    void configuredBindingMetadataUsesTheSameAllowlistAsRuntimeRegistration() {
        assertDoesNotThrow(() -> new RemedySpec(
                "vpn-access",
                RemedySpec.Type.ACCESS_RESTRICTION,
                "Disable or approve VPN",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.NETWORK_ACCESS,
                        ConditionType.VPN_APPROVAL,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ))
        ));

        assertThrows(IllegalArgumentException.class, () -> new RemedySpec(
                "remove-content",
                RemedySpec.Type.REMOVE_CONTENT,
                "Remove content",
                Optional.of(new PolicyV2RemedyBindingSpec(
                        Scope.MARKET_ACCESS,
                        ConditionType.MANUAL,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ))
        ));
    }

    @Test
    void nonEnglishChatAndVpnComplianceCannotDirectlyResolveToBan() {
        SanctionSpec mute = new SanctionSpec(
                SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofHours(1))
        );
        SanctionSpec ban = new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(90))
        );

        assertDoesNotThrow(() -> PolicyV2EnforcementPolicy.requireSafeOutcome(
                "chat.language.non-english-public", List.of(mute)));
        assertThrows(IllegalArgumentException.class, () -> PolicyV2EnforcementPolicy.requireSafeOutcome(
                "chat.language.non-english-public", List.of(ban)));
        assertThrows(IllegalArgumentException.class, () -> PolicyV2EnforcementPolicy.requireSafeOutcome(
                "access.vpn-compliance", List.of(ban)));

        assertDoesNotThrow(() -> PolicyV2EnforcementPolicy.requireSafeOutcome(
                "access.vpn-evasion", List.of(ban)));
        assertDoesNotThrow(() -> PolicyV2EnforcementPolicy.requireSafeOutcome(
                "evasion.mute", List.of(ban)));
    }
}
