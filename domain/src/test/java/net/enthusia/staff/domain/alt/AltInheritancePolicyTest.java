package net.enthusia.staff.domain.alt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AltInheritancePolicyTest {
    private final AltInheritancePolicy policy = new AltInheritancePolicy();
    private final Instant cutover = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void newSameNetworkAccountAfterCutoverInheritsOnlyWhenObservationIsUnambiguous() {
        assertTrue(policy.shouldInherit(
                AltRelationshipState.SAME_NETWORK,
                true,
                cutover.plusSeconds(1),
                Optional.of(cutover),
                false
        ));
        assertFalse(policy.shouldInherit(
                AltRelationshipState.SAME_NETWORK,
                false,
                cutover.plusSeconds(1),
                Optional.of(cutover),
                false
        ));
    }

    @Test
    void establishedAccountDoesNotUseNewAccountRule() {
        assertFalse(policy.shouldInherit(
                AltRelationshipState.SAME_NETWORK,
                true,
                cutover.minusSeconds(1),
                Optional.of(cutover),
                false
        ));
    }

    @Test
    void confidentExistingRelationshipInherits() {
        assertTrue(policy.shouldInherit(
                AltRelationshipState.CONFIDENT,
                false,
                cutover.minusSeconds(1),
                Optional.of(cutover),
                true
        ));
    }

    @Test
    void protectedRelationshipStatesNeverInheritAutomatically() {
        for (AltRelationshipState state : new AltRelationshipState[]{
                AltRelationshipState.APPROVED_ALT,
                AltRelationshipState.SHARED_HOUSEHOLD,
                AltRelationshipState.NOT_RELATED
        }) {
            assertFalse(policy.shouldInherit(state, true, cutover.plusSeconds(1), Optional.of(cutover), false));
        }
    }
}
