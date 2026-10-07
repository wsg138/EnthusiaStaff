package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyV2RemedyBindingSpec;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.ConditionType;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

final class PolicyV2JsonCodecTest {
    private final PolicyV2JsonCodec codec = new PolicyV2JsonCodec();

    @Test
    void sanctionLengthsRoundTripWithoutDerivedBooleanProperties() {
        assertRoundTrip(SanctionLength.instant());
        assertRoundTrip(SanctionLength.permanent());
        assertRoundTrip(SanctionLength.temporary(Duration.ofMinutes(15)));
    }

    @Test
    void remedyOnlyActionRoundTripsThroughPersistedPolicyJson() {
        PolicyAction.RemedyOnly action = new PolicyAction.RemedyOnly();

        String json = codec.write(action);

        assertEquals(action, codec.read(json, PolicyAction.class));
    }

    @Test
    void exactWithApprovalActionRoundTripsThroughPersistedPolicyJson() {
        PolicyAction.ExactWithApproval action = new PolicyAction.ExactWithApproval(
                List.of(new SanctionSpec(
                        SanctionType.NETWORK_BAN,
                        SanctionLength.permanent()
                )),
                StaffRank.ADMIN
        );

        String json = codec.write(action);

        assertEquals(action, codec.read(json, PolicyAction.class));
    }

    @Test
    void remedyEnforcementBindingRoundTripsAndLegacyJsonDefaultsToUnbound() {
        RemedySpec current = new RemedySpec(
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

        assertEquals(current, codec.read(codec.write(current), RemedySpec.class));

        RemedySpec legacy = codec.read(
                "{\"id\":\"remove-content\",\"type\":\"REMOVE_CONTENT\",\"description\":\"Remove content\"}",
                RemedySpec.class
        );
        assertTrue(legacy.enforcementBinding().isEmpty());
    }

    @Test
    void legacyExponentialDecayJsonDefaultsMissingPatternHalfLife() {
        DecayPolicy current = DecayPolicy.exponential(
                Duration.ofDays(30),
                Duration.ofDays(120),
                0.25,
                2.0
        );
        ObjectNode legacy = (ObjectNode) codec.readTree(codec.write(current));
        legacy.remove("patternHalfLife");

        DecayPolicy decoded = codec.read(legacy.toString(), DecayPolicy.class);

        assertEquals(Duration.ofDays(30), decoded.halfLife());
        assertEquals(Duration.ofDays(30), decoded.patternHalfLife());
        assertEquals(0.25, decoded.repeatHalfLifeIncreasePerPrior(), 0.0);
        assertEquals(2.0, decoded.maximumHalfLifeMultiplier(), 0.0);
    }

    private void assertRoundTrip(SanctionLength length) {
        String json = codec.write(length);

        assertFalse(json.contains("\"instant\""));
        assertFalse(json.contains("\"permanent\""));
        assertEquals(length, codec.read(json, SanctionLength.class));
    }
}
