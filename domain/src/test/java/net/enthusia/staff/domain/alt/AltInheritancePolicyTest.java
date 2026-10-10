package net.enthusia.staff.domain.alt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AltInheritancePolicyTest {
    private final AltInheritancePolicy policy = new AltInheritancePolicy();

    @Test
    void eightyFivePercentRuleDoesNotTrustNetworkOverlapOrSeventyFivePercentConfidence() {
        assertFalse(policy.shouldInherit(AltRelationshipState.SAME_NETWORK, false));
        assertFalse(policy.shouldInherit(AltRelationshipState.LOW_CONFIDENCE, false));
        assertFalse(policy.shouldInherit(AltRelationshipState.SEMI_CONFIDENT, false));
        assertFalse(policy.shouldInherit(AltRelationshipState.CONFIDENT, false));
        assertTrue(policy.shouldInherit(AltRelationshipState.VERY_CONFIDENT, false));
        assertTrue(policy.shouldInherit(AltRelationshipState.CONFIRMED_ALT, false));
    }

    @Test
    void currentVerifiedDiscordLinkIsTrustedWithoutSharedIp() {
        assertTrue(policy.shouldInherit(null, true));
        assertTrue(policy.shouldInherit(AltRelationshipState.SAME_NETWORK, true));
    }

    @Test
    void explicitHouseholdOrUnrelatedDecisionsAlwaysOverrideVerifiedLinks() {
        for (AltRelationshipState state : new AltRelationshipState[]{
                AltRelationshipState.APPROVED_ALT,
                AltRelationshipState.SHARED_HOUSEHOLD,
                AltRelationshipState.NOT_RELATED
        }) {
            assertFalse(policy.shouldInherit(state, false));
            assertFalse(policy.shouldInherit(state, true));
        }
    }
}
